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
package io.openliberty.tools.common.plugins.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class ServerStatusUtil {
    /**
     * Determines if the server is running.
     * 
     * @param outputDirectory
     * @param serverName
     * 
     * @return true if running, false otherwise
     */
    public static boolean isServerRunning(File outputDirectory, String serverName) {
        File sLock = new File(outputDirectory, serverName + "/workarea/.sLock");
        File sCommand = new File(outputDirectory, serverName + "/workarea/.sCommand");
        File pidFile = new File(outputDirectory, ".pid/" + serverName +".pid");
        
        // for windows, check .sLock file
        if (OSUtil.isWindows()) {
            File sLockBak = new File(sLock.getPath() + ".bak");
            if (!sLock.exists() || sLock.renameTo(sLockBak)) {
                sLockBak.renameTo(sLock);
                return false;
            }
        } else {
            if (pidFile.exists()) {
                try {
                    String pid = new String(Files.readAllBytes(Paths.get(pidFile.getPath())));
                    Process p = Runtime.getRuntime().exec("ps " + pid);
                    p.waitFor(10, TimeUnit.SECONDS);
                    if (p.exitValue() != 0) {
                        return false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (sLock.exists()) {
                if (!sCommand.exists()) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }
}
