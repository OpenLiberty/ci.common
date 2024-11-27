/**
 * (C) Copyright IBM Corporation 2024.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openliberty.tools.common.plugins.util;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LibertyPropFilesUtilityTest {

    File serverDir;
    File installDir;
    File userDir;
    File userExtensionDir;
    File sharedResourceDir;
    File sharedStackGroupDir;
    File sharedAppDir;
    File sharedConfigDir;

    @Before
    public void setUp() throws IOException {
        serverDir = new File("src/test/resources/serverConfig/liberty/wlp/usr/servers/defaultServer");
        installDir = new File("src/test/resources/serverConfig/liberty/wlp");
        userDir = new File("src/test/resources/serverConfig/liberty/wlp/usr");
        userExtensionDir = new File("src/test/resources/serverConfig/liberty/wlp/usr/extension");
        sharedResourceDir = new File("src/test/resources/serverConfig/liberty/wlp/usr/shared/resources");
        sharedStackGroupDir = new File("src/test/resources/serverConfig/liberty/wlp/usr/shared/stackGroups");
        sharedAppDir = new File("src/test/resources/serverConfig/liberty/wlp/usr/shared/app");
        sharedConfigDir = new File("src/test/resources/serverConfig/liberty/wlp/usr/shared/config");
    }

    @Test
    public void testGetLibertyDirectoryPropertyFiles() throws Exception {

        Map<String, File> libProperties = LibertyPropFilesUtility.getLibertyDirectoryPropertyFiles(installDir, userDir, serverDir);
        // verify the libPropFiles
        assertFalse("Liberty Directory Property files map should not be empty", libProperties.isEmpty());
        assertEquals(libProperties.get(ServerFeatureUtil.WLP_INSTALL_DIR).getCanonicalPath(), installDir.getCanonicalPath());
        assertEquals(libProperties.get(ServerFeatureUtil.WLP_USER_DIR).getCanonicalPath(), userDir.getCanonicalPath());
        assertEquals(libProperties.get(ServerFeatureUtil.SERVER_CONFIG_DIR).getCanonicalPath(), serverDir.getCanonicalPath());
        assertEquals(libProperties.get(ServerFeatureUtil.USR_EXTENSION_DIR).getCanonicalPath(), userExtensionDir.getCanonicalPath());
        assertEquals(libProperties.get(ServerFeatureUtil.SHARED_CONFIG_DIR).getCanonicalPath(), sharedConfigDir.getCanonicalPath());
        assertEquals(libProperties.get(ServerFeatureUtil.SHARED_APP_DIR).getCanonicalPath(), sharedAppDir.getCanonicalPath());
        assertEquals(libProperties.get(ServerFeatureUtil.SHARED_RESOURCES_DIR).getCanonicalPath(), sharedResourceDir.getCanonicalPath());
        assertEquals(libProperties.get(ServerFeatureUtil.SHARED_STACKGROUP_DIR).getCanonicalPath(), sharedStackGroupDir.getCanonicalPath());

    }

    @Test
    public void testGetLibertyDirectoryPropertyFilesEmptyResult() throws Exception {

        Map<String, File> libProperties = LibertyPropFilesUtility.getLibertyDirectoryPropertyFiles(installDir, userDir,
                new File("src/test/resources/invalidPath"));
        // should be empty because serverDir does not exist
        assertTrue("Liberty Directory Property files map should be empty since invalid serverDirectory is specified",
                libProperties.isEmpty());
    }
}
