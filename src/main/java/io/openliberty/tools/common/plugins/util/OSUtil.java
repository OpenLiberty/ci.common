/**
 * (C) Copyright IBM Corporation 2018, 2023.
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

public class OSUtil {

    /**
     * Determines if the current OS is a Windows OS.
     * 
     * @return true if running on Windows, false otherwise
     */
    public static boolean isWindows() {
        String osName = System.getProperty("os.name", "unknown").toLowerCase();
        return osName.indexOf("windows") >= 0;
    }

    /**
     * Determines if the current OS is a Linux OS.
     * 
     * @return true if running on Linux, false otherwise
     */
    public static boolean isLinux() {
        String osName = System.getProperty("os.name", "unknown").toLowerCase();
        return osName.indexOf("linux") >= 0;
    }

    /**
     * Determines if the current OS is a MAC OS.
     * 
     * @return true if running on Mac, false otherwise
     */
    public static boolean isMac() {
        String osName = System.getProperty("os.name", "unknown").toLowerCase();
        return osName.indexOf("mac") >= 0;
    }

}
