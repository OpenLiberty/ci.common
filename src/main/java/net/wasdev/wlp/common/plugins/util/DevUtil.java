/**
 * (C) Copyright IBM Corporation 2019.
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

package net.wasdev.wlp.common.plugins.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Utility class for dev mode.
 */
public abstract class DevUtil {

    /**
     * Log debug
     * @param msg
     */
    public abstract void debug(String msg);

    /**
     * Log debug
     * @param msg
     * @param e
     */
    public abstract void debug(String msg, Throwable e);

    /**
     * Log debug
     * @param e
     */
    public abstract void debug(Throwable e);

    /**
     * Log warning
     * @param msg
     */
    public abstract void warn(String msg);

    /**
     * Log info
     * @param msg
     */
    public abstract void info(String msg);

    /**
     * Log error
     * @param msg
     */
    public abstract void error(String msg);

    /**
     * Returns whether debug is enabled by the current logger
     * @return whether debug is enabled
     */
    public abstract boolean isDebugEnabled();

    /**
     * Stop the server
     */
    public abstract void stopServer();

    private List<String> jvmOptions;

    private File serverDirectory;

    public DevUtil(List<String> jvmOptions, File serverDirectory) {
        this.jvmOptions = jvmOptions;
        this.serverDirectory = serverDirectory;
    }

    public void addShutdownHook(final ThreadPoolExecutor executor) {
        // shutdown hook to stop server when x mode is terminated
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                debug("Inside Shutdown Hook, shutting down server");

                // cleaning up jvm.options files
                if (jvmOptions == null || jvmOptions.isEmpty()) {
                    File jvmOptionsFile = new File(serverDirectory.getAbsolutePath() + "/jvm.options");
                    File jvmOptionsBackup = new File(serverDirectory.getAbsolutePath() + "/jvmBackup.options");
                    if (jvmOptionsFile.exists()) {
                        debug("Deleting liberty:dev jvm.options file");
                        if (jvmOptionsBackup.exists()) {
                            try {
                                Files.copy(jvmOptionsBackup.toPath(), jvmOptionsFile.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                                jvmOptionsBackup.delete();
                            } catch (IOException e) {
                                error("Could not restore jvm.options: " + e.getMessage());
                            }
                        } else {
                            boolean deleted = jvmOptionsFile.delete();
                            if (deleted) {
                                info("Sucessfully deleted liberty:dev jvm.options file");
                            } else {
                                error("Could not delete liberty:dev jvm.options file");
                            }
                        }
                    }
                }

                // shutdown tests
                executor.shutdown();

                // stopping server
                stopServer();
            }
        });
    }

    public void enableServerDebug(int libertyDebugPort) throws IOException {
        // creating jvm.options file to open a debug port
        debug("jvmOptions: " + jvmOptions);
        if (jvmOptions != null && !jvmOptions.isEmpty()) {
            warn(
                    "Cannot start liberty:dev in debug mode because jvmOptions are specified in the server configuration");
        } else {
            File jvmOptionsFile = new File(serverDirectory.getAbsolutePath() + "/jvm.options");
            if (jvmOptionsFile.exists()) {
                debug("jvm.options already exists");
                File jvmOptionsBackup = new File(serverDirectory.getAbsolutePath() + "/jvmBackup.options");
                Files.copy(jvmOptionsFile.toPath(), jvmOptionsBackup.toPath());
                boolean deleted = jvmOptionsFile.delete();
                if (!deleted) {
                    error("Could not move existing liberty:dev jvm.options file");
                }
            }
            debug("Creating jvm.options file: " + jvmOptionsFile.getAbsolutePath());
            StringBuilder sb = new StringBuilder("# Generated by liberty:dev \n");
            sb.append("-Dwas.debug.mode=true\n");
            sb.append("-Dcom.ibm.websphere.ras.inject.at.transform=true\n");
            sb.append("-Dsun.reflect.noInflation=true\n");
            sb.append("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + libertyDebugPort);
            BufferedWriter writer = new BufferedWriter(new FileWriter(jvmOptionsFile));
            writer.write(sb.toString());
            writer.close();
            if (jvmOptionsFile.exists()) {
                info("Successfully created liberty:dev jvm.options file");
            }
        }
    }

}
