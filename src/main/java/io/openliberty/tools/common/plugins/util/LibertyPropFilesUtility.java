/**
 * (C) Copyright IBM Corporation 2024
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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LibertyPropFilesUtility {


    public static Map<String, File> getLibertyDirectoryPropertyFiles(File installDir, File userDir, File serverDir) throws IOException {
        Map<String, File> libertyDirectoryPropertyToFile = new HashMap<>();

        if (serverDir.exists()) {
            libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serverDir.getCanonicalFile());

            libertyDirectoryPropertyToFile.put(ServerFeatureUtil.WLP_INSTALL_DIR, installDir.getCanonicalFile());

            libertyDirectoryPropertyToFile.put(ServerFeatureUtil.WLP_USER_DIR, userDir.getCanonicalFile());

            File userExtDir = new File(userDir, "extension");
            libertyDirectoryPropertyToFile.put(ServerFeatureUtil.USR_EXTENSION_DIR, userExtDir.getCanonicalFile());

            File userSharedDir = new File(userDir, "shared");
            File userSharedAppDir = new File(userSharedDir, "app");
            File userSharedConfigDir = new File(userSharedDir, "config");
            File userSharedResourcesDir = new File(userSharedDir, "resources");
            File userSharedStackGroupsDir = new File(userSharedDir, "stackGroups");

            libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SHARED_APP_DIR, userSharedAppDir.getCanonicalFile());
            libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SHARED_CONFIG_DIR, userSharedConfigDir.getCanonicalFile());
            libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SHARED_RESOURCES_DIR, userSharedResourcesDir.getCanonicalFile());
            libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SHARED_STACKGROUP_DIR, userSharedStackGroupsDir.getCanonicalFile());
        }
        return libertyDirectoryPropertyToFile;
    }


}
