/**
 * (C) Copyright IBM Corporation 2023
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

import com.fasterxml.jackson.core.Version;

public class VersionUtility {

    /*
     * Compare currentVersion to compareVersion. The version may only contain three parts such as 1.15.8.
     * If currentVersion is less than compareVersion, -1 is returned.
     * If currentVersion is the same as compareVersion, 0 is returned.
     * If currentVersion is greater than compareVersion, 1 is returned.
     * 
     * @return integer representing result of comparison
     */
    public static int compareArtifactVersion(String currentVersion, String compareVersion) {
        return compareArtifactVersion(currentVersion, compareVersion, false);

    }
    
    /*
     * Compare currentVersion to compareVersion. If the isLibertyVersion flag is set to true, only the first and last digits will be compared,
     * since the middle two digits are always zero.
     * 
     * If currentVersion is less than compareVersion, -1 is returned.
     * If currentVersion is the same as compareVersion, 0 is returned.
     * If currentVersion is greater than compareVersion, 1 is returned.
     * 
     * @return integer representing result of comparison
     */
    public static int compareArtifactVersion(String currentVersion, String compareVersion, boolean isLibertyVersion) {
		String[] compareVersionArray = compareVersion.split("\\.");
		int majorVersion = Integer.parseInt(compareVersionArray[0]);
        int minorVersion = isLibertyVersion ? 0 : Integer.parseInt(compareVersionArray[1]);
		int patchLevel = isLibertyVersion ? Integer.parseInt(compareVersionArray[3]) : Integer.parseInt(compareVersionArray[2]);
		Version minVersion = new Version(majorVersion, minorVersion, patchLevel, null, null, null);

        // check for and strip off any classifier
        if (currentVersion.contains("-")) {
            currentVersion = currentVersion.substring(0, currentVersion.indexOf("-"));
        }
		String[] currentVersionArray = currentVersion.split("\\.");
		majorVersion = Integer.parseInt(currentVersionArray[0]);
        minorVersion = isLibertyVersion ? 0 : Integer.parseInt(currentVersionArray[1]);
		patchLevel = isLibertyVersion ? Integer.parseInt(currentVersionArray[3]) : Integer.parseInt(currentVersionArray[2]);
		Version version = new Version(majorVersion, minorVersion, patchLevel, null, null, null);

        return version.compareTo(minVersion);

    }

}
