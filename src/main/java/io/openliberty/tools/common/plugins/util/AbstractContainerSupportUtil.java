package io.openliberty.tools.common.plugins.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public abstract class AbstractContainerSupportUtil {

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
     * @param timeout unit is seconds
     * @return the stdout of the command or null for no output on stdout
     */
    protected String execDockerCmd(String command, int timeout, boolean throwExceptionOnError) {
        String result = null;
        try {
            debug("execDocker, timeout=" + timeout + ", cmd=" + command);
            Process p = Runtime.getRuntime().exec(command);

            p.waitFor(timeout, TimeUnit.SECONDS);

            // After waiting for the process, handle the error case and normal termination.
            if (p.exitValue() != 0) {
                debug("Error running docker command, return value="+p.exitValue());
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
            // the timeout was too short and the docker command has not yet completed. There is no exit value.
            debug("IllegalThreadStateException, message="+e.getMessage());
            error("The docker command did not complete within the timeout period: " + timeout + " seconds.", e);
            throw new RuntimeException("The docker command did not complete within the timeout period: " + timeout + " seconds. ");
        } catch (InterruptedException e) {
            // If a runtime exception occurred in the server task, log and rethrow
            error("An interruption error occurred while running a docker command: " + e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            // If a runtime exception occurred in the server task, log and rethrow
            error("An error occurred while running a docker command: " + e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }
        return result;
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
