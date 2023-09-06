/**
 * (C) Copyright IBM Corporation 2020, 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.openliberty.tools.common.plugins.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public abstract class AbstractContainerSupportUtil {

    private static final String CONTAINER_DOCKER_PREFIX = "docker ";
    private static final String CONTAINER_PODMAN_PREFIX = "podman ";

    private static final int CONTAINER_TIMEOUT = 20; // seconds
    
    private   boolean checkedContainerType = false;
    protected boolean isDocker = true;

    /**
     * Log debug
     * @param msg
     */
    public abstract void debug(String msg);

    /**
     * Log error
     * 
     * @param msg
     * @param e
     */
    public abstract void error(String msg, Throwable e);

    /**
     * Log info
     * @param msg
     */
    public abstract void info(String msg);

    protected String getContainerCommandPrefix() throws PluginExecutionException {
        if (!checkedContainerType) {
            checkDockerVersion();
        }

        return !isDocker ? CONTAINER_PODMAN_PREFIX : CONTAINER_DOCKER_PREFIX;
    }

    /**
     * Retrieve the current docker version and compare to a known value.
     * The Maven class ComparableVersion allows for numbers, letters and certain words.
     * Throw an exception if there is a problem with the version.
     */
    private static final String MIN_DOCKER_VERSION = "18.03.0"; // Must use Docker 18.03.0 or higher
    protected void checkDockerVersion() throws PluginExecutionException {
        String versionCmd = "docker version --format {{.Client.Version}}";
        String dockerVersion = execContainerCmd(versionCmd, CONTAINER_TIMEOUT);
        if (dockerVersion == null) {
            checkPodmanVersion(); // Check Podman version if no Docker 
            return;
        }
        debug("Detected Docker version: " + dockerVersion);
        
        if (VersionUtility.compareArtifactVersion(dockerVersion, MIN_DOCKER_VERSION, false) < 0) {
            checkPodmanVersion(); // Check that bad Docker version isn't just a Podman version
            if (!isDocker) {
                return;
            }
            throw new PluginExecutionException("The detected Docker client version number is not supported:" + dockerVersion.trim() + ". Docker version must be " + MIN_DOCKER_VERSION + " or higher.");
        }
        isDocker = true;
        checkedContainerType = true;
    }

    private static final String MIN_PODMAN_VERSION = "4.4.4"; // Must use Docker 4.4.4 or higher
    private void checkPodmanVersion() throws PluginExecutionException  {
        String versionCmd = "podman version --format {{.Client.Version}}";
        String podmanVersion = execContainerCmd(versionCmd, CONTAINER_TIMEOUT);
        if (podmanVersion == null) {
            return; // Can't tell if the version is valid.
        }
        debug("Detected Podman version: " + podmanVersion);
        
        if (VersionUtility.compareArtifactVersion(podmanVersion, MIN_PODMAN_VERSION, false) < 0) {
            throw new PluginExecutionException("The detected Podman client version number is not supported:" + podmanVersion.trim() + ". Podman version must be " + MIN_PODMAN_VERSION + " or higher.");
        }
        isDocker = false;
        checkedContainerType = true;
    }

    /**
     * @param timeout unit is seconds
     * @return the stdout of the command or null for no output on stdout
     */
    protected String execContainerCmd(String command, int timeout, boolean throwExceptionOnError) {
        String result = null;
        try {
            debug("execContainer, timeout=" + timeout + ", cmd=" + command);
            Process p = Runtime.getRuntime().exec(command);

            p.waitFor(timeout, TimeUnit.SECONDS);

            // After waiting for the process, handle the error case and normal termination.
            if (p.exitValue() != 0) {
                debug("Error running container command, return value="+p.exitValue());
                // read messages from standard err
                char[] d = new char[1023];
                new InputStreamReader(p.getErrorStream()).read(d);
                String errorMessage = new String(d).trim()+" RC="+p.exitValue();
                if (throwExceptionOnError) {
                    throw new RuntimeException(errorMessage);
                } else {
                    return errorMessage;
                }
            }
            result = readStdOut(p);
        } catch (IllegalThreadStateException  e) {
            // the timeout was too short and the container command has not yet completed. There is no exit value.
            debug("IllegalThreadStateException, message="+e.getMessage());
            error("The container command did not complete within the timeout period: " + timeout + " seconds.", e);
            throw new RuntimeException("The container command did not complete within the timeout period: " + timeout + " seconds. ");
        } catch (InterruptedException e) {
            // If a runtime exception occurred in the server task, log and rethrow
            error("An interruption error occurred while running a container command: " + e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            // Logging IOExceptions in info stream. This is thrown if Docker or Podman are not installed on the system.
            info("An error occurred while running a container command: " + e.getMessage());
            info("This message will occur when Docker or Podman are not installed.");
        }
        return result;
    }

    protected String execContainerCmd(String command, int timeout) {
        return execContainerCmd(command, timeout, true);
    }

    protected String readStdOut(Process p) throws IOException, InterruptedException {
        String result = null;
        // Read all the output on stdout and return it to the caller
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuffer allLines = new StringBuffer();
        while ((line = in.readLine())!= null) {
            allLines.append(line).append(" ");
        }
        if (allLines.length() > 0) {
            result = allLines.toString();
        }
        return result;
    }
}
