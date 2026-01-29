/**
 * (C) Copyright IBM Corporation 2023, 2026.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.openliberty.tools.common.TestLogger;
import io.openliberty.tools.common.plugins.config.ServerConfigDocument;

public class ServerConfigDocumentTest {
	public static final String RESOURCES_DIR = "src/test/resources";
    
    private static final String RESOURCES_INSTALL_DIR = RESOURCES_DIR + "/serverConfig";
    
	private File serverConfigDir = new File(RESOURCES_INSTALL_DIR);
		
	@Test
	public void testAppLocationUsesLibertyProperty() throws Exception {
		TestLogger log = new TestLogger();

		File serverDirectory = new File(serverConfigDir, "liberty/wlp/usr/servers/defaultServer");
		File appLocation = new File(serverDirectory, "apps/test-war.war");
		String compareAppLocation1 = appLocation.getCanonicalPath().replace("\\", "/");
		String compareAppLocation2 = "test-war-two.war";
		String compareAppLocation3 = "test-war-three.war";
		 // this next one won't resolve because the var referenced in the include location uses a variable with a non-default value in server.xml
		String compareAppLocation4 = "${project.artifactId.four}.ear"; 
		String compareAppLocation5 = "test-war-five.war";
		String compareAppLocation6 = "test-war-six.war";

		Map<String, File> libertyDirectoryPropertyToFile = getLibertyDirectoryPropertyFiles(log, serverDirectory);

		File serverXML = new File(serverDirectory, "server.xml");

		ServerConfigDocument scd = new ServerConfigDocument(log, serverXML, libertyDirectoryPropertyToFile);
		Set<String> locations = scd.getLocations();
		assertTrue("Expected six app locations", locations.size() == 6);

		boolean locOneFound = false;
		boolean locTwoFound = false;
		boolean locThreeFound = false;
		boolean locFourFound = false;
		boolean locFiveFound = false;
		boolean locSixFound = false;

		for (String loc : locations) {
			if (loc.contains("-two")) {
				assertTrue("Unexpected app location found: "+loc, loc.equals(compareAppLocation2));
				locTwoFound = true;
			} else if (loc.contains("-three")) {
				assertTrue("Unexpected app location found: "+loc, loc.equals(compareAppLocation3));
				locThreeFound = true;
			} else if (loc.endsWith(".ear")) {
				assertTrue("Unexpected app location found: "+loc, loc.equals(compareAppLocation4));
				locFourFound = true;
			} else if (loc.contains("-five")) {
				assertTrue("Unexpected app location found: "+loc, loc.equals(compareAppLocation5));
				locFiveFound = true;
			} else if (loc.contains("-six")) {
				assertTrue("Unexpected app location found: "+loc, loc.equals(compareAppLocation6));
				locSixFound = true;
			} else {
				assertTrue("Unexpected app location found: "+loc, loc.equals(compareAppLocation1));
				locOneFound = true;
			}
		}

		assertTrue("App location one not found.", locOneFound);
		assertTrue("App location two not found.", locTwoFound);
		assertTrue("App location three not found.", locThreeFound);
		assertTrue("App location four not found.", locFourFound);
		assertTrue("App location five not found.", locFiveFound);
		assertTrue("App location six not found.", locSixFound);
		if (OSUtil.isWindows()) {
			assertEquals("Variable Expanded for !VAR!", "DEFINED_VAL", scd.getProperties().getProperty("this2_value"));
			assertEquals("Variable Expanded for ${VAR}", "DEFINED\\old_value\\dir", scd.getProperties().getProperty("this5_value"));
		} else {
			assertEquals("Variable Expanded for ${VAR}", "DEFINED_VAL", scd.getProperties().getProperty("this3_value"));
			assertEquals("Variable Expanded for ${VAR}", "DEFINED/old_value/dir", scd.getProperties().getProperty("this4_value"));
		}
		assertEquals("Variable not Expanded for !this_val", "!this_val", scd.getProperties().getProperty("this6_value"));
	}

	/**
     * Initializes the pre-defined Liberty directory properties which will be used when resolving variable references in 
     * the include element location attribute, such as <include location="${server.config.dir}/xyz.xml"/>. 
     * Note that we are intentionally not including the wlp.output.dir property, as that location can be specified by the
     * user outside of the Liberty installation and does not make much sense as a location for server include files.
     * All other Liberty directory properties can be determined relative to the passed in serverDirectory, which is the 
     * server.config.dir.
     *
     * @param serverDirectory The server directory containing the server.xml
     */
    public Map<String, File> getLibertyDirectoryPropertyFiles(TestLogger log, File serverDirectory) {
        Map<String, File> libertyDirectoryPropertyToFile = new HashMap<String,File>();
        if (serverDirectory.exists()) {
            try {
                libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serverDirectory.getCanonicalFile());

                File wlpUserDir = serverDirectory.getParentFile().getParentFile();
                libertyDirectoryPropertyToFile.put(ServerFeatureUtil.WLP_USER_DIR, wlpUserDir.getCanonicalFile());

                File wlpInstallDir = wlpUserDir.getParentFile();
                libertyDirectoryPropertyToFile.put(ServerFeatureUtil.WLP_INSTALL_DIR, wlpInstallDir.getCanonicalFile());
 
                File userExtDir = new File(wlpUserDir, "extension");
                libertyDirectoryPropertyToFile.put(ServerFeatureUtil.USR_EXTENSION_DIR, userExtDir.getCanonicalFile());

                File userSharedDir = new File(wlpUserDir, "shared");
                File userSharedAppDir = new File(userSharedDir, "app");
                File userSharedConfigDir = new File(userSharedDir, "config");
                File userSharedResourcesDir = new File(userSharedDir, "resources");
                File userSharedStackGroupsDir = new File(userSharedDir, "stackGroups");

                libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SHARED_APP_DIR, userSharedAppDir.getCanonicalFile());
                libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SHARED_CONFIG_DIR, userSharedConfigDir.getCanonicalFile());
                libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SHARED_RESOURCES_DIR, userSharedResourcesDir.getCanonicalFile());
                libertyDirectoryPropertyToFile.put(ServerFeatureUtil.SHARED_STACKGROUP_DIR, userSharedStackGroupsDir.getCanonicalFile());
            } catch (Exception e) {
                log.warn("The properties for directories could not be initialized because an error occurred when accessing them.");
                log.debug("Exception received: "+e.getMessage(), e);
            }
        } else {
            log.warn("The " + serverDirectory + " directory cannot be accessed. Skipping its server features.");
        }
		return libertyDirectoryPropertyToFile;
    }
}
