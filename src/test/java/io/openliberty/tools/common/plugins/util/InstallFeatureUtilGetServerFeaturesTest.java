/**
 * (C) Copyright IBM Corporation 2018, 2021.
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.openliberty.tools.common.plugins.util.ServerFeatureUtil.FeaturesPlatforms;

public class InstallFeatureUtilGetServerFeaturesTest extends BaseInstallFeatureUtilTest {
    private static File serverDirectory = null;
    private static File src = null;
    private static InstallFeatureUtil util = null;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUpClass() throws Exception {
        src = new File("src/test/resources/servers");
    }
    
    @Before
    public void setUp() throws Exception {
        serverDirectory = tempFolder.newFolder();
        util = getNewInstallFeatureUtil();
    }

    private void copyAsName(String origName, String newName) throws IOException {
        File file = new File(src, origName);
        FileUtils.copyFile(file, new File(serverDirectory, newName));
    }
    
    private void copy(String origName) throws IOException {
        File file = new File(src, origName);
        if (file.isFile()) {
            FileUtils.copyFileToDirectory(file, serverDirectory);
        } else {
            FileUtils.copyDirectoryToDirectory(file, serverDirectory);
        }
    }
    
    private void verifyServerFeatures(Set<String> expected) throws Exception {
    	FeaturesPlatforms getServerResult = util.getServerFeatures(serverDirectory, null);
    	Set<String> featuresToInstall = new HashSet<String>();
        if (getServerResult != null) {
        	featuresToInstall = getServerResult.getFeatures();
        }
        assertEquals("The features returned from getServerFeatures do not equal the expected features.", expected, featuresToInstall);
    }
    private void verifyServerFeaturesAndPlatforms(Set<String> expectedFeatures,Set<String> expectedPlatforms) throws Exception {
    	FeaturesPlatforms getServerResult = util.getServerFeatures(serverDirectory, null);
    	Set<String> featuresToInstall = new HashSet<String>();
        Set<String> platformsToInstall = new HashSet<String>();
        if (getServerResult != null) {
        	featuresToInstall = getServerResult.getFeatures();
        	platformsToInstall = getServerResult.getPlatforms();
        }
        assertEquals("The features returned from getServerFeatures do not equal the expected features.", expectedFeatures, featuresToInstall);
        assertEquals("The platforms returned from getServerFeatures do not equal the expected platforms.", expectedPlatforms, platformsToInstall);
    }
    private void verifyServerFeatures(Set<String> expected, Set<String> ignoreFiles) throws Exception {
    	FeaturesPlatforms getServerResult = util.getServerFeatures(serverDirectory, null, ignoreFiles);
    	Set<String> featuresToInstall = new HashSet<String>();
        if (getServerResult != null) {
        	featuresToInstall = getServerResult.getFeatures();
        }
        assertEquals("The features returned from getServerFeatures do not equal the expected features.", expected, featuresToInstall);
    }
    
    private void verifyServerFeaturesPreserveCase(Set<String> expected) throws Exception {
        util.setLowerCaseFeatures(false);
        FeaturesPlatforms getServerResult = util.getServerFeatures(serverDirectory, null);
        Set<String> featuresToInstall = new HashSet<String>();
        if (getServerResult != null) {
        	featuresToInstall = getServerResult.getFeatures();
        }
        assertEquals("The features returned from getServerFeatures do not equal the expected features.", expected, featuresToInstall);
        util.setLowerCaseFeatures(true); // restore default
    }
    
    /**
     * Tests base server.xml without any include locations or config dropins
     * 
     * @throws Exception
     */
    @Test
    public void testServerBaseXML() throws Exception{
        copy("server.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");

        verifyServerFeatures(expected);
    }

    /**
     * Tests server.xml with IGNORE function
     * 
     * @throws Exception
     */
    @Test
    public void testIgnoreServerXML() throws Exception{
        copyAsName("server_ignore.xml", "server.xml");
        copy("extraFeatures.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");

        verifyServerFeatures(expected);
    }

    /**
     * Tests server.xml with MERGE function
     * 
     * @throws Exception
     */
    @Test
    public void testMergeServerXML() throws Exception{
        copyAsName("server_merge.xml", "server.xml");
        copy("extraFeatures.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with MERGE function with nested include files, one of which contains no featureManager
     * 
     * @throws Exception
     */
    @Test
    public void testNestedMergeServerXML() throws Exception{
        copyAsName("server_merge_nested.xml", "server.xml");
        copy("extraFeatures.xml");
        copy("noList_nested_features.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra");

        verifyServerFeatures(expected);
    }

    /**
     * Tests server.xml with REPLACE function
     * 
     * @throws Exception
     */
    @Test
    public void testReplaceServerXML() throws Exception{
        copyAsName("server_replace.xml", "server.xml");
        copy("extraFeatures.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("extra");

        verifyServerFeatures(expected);
    }

    /**
     * Tests server.xml with nested REPLACE functions
     * 
     * @throws Exception
     */
    @Test
    public void testNestedReplaceXML() throws Exception{
        copyAsName("server_nested_replace.xml", "server.xml");
        copy("nestedReplace2.xml");
        copy("nestedReplace3.xml");

    	Set<String> expected = new HashSet<String>();
    	expected.add("nested3");

        verifyServerFeatures(expected);
    }

    /**
     * Tests server.xml with config dropins overrides 
     * 
     * @throws Exception
     */
    @Test
    public void testConfigOverrides() throws Exception{
        copy("server.xml");
        copyAsName("config_overrides.xml", "configDropins/overrides/config_overrides.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("overrides");

        verifyServerFeatures(expected);
    }

    /**
     * Tests server.xml with config dropins overrides, ignoring a specific file
     * 
     * @throws Exception
     */
    @Test
    public void testConfigOverridesIgnoreFile() throws Exception{
        copy("server.xml");
        copyAsName("generated-features.xml", "configDropins/overrides/generated-features.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("generated");

        verifyServerFeatures(expected); // without ignore

        Set<String> ignoreSet1 = new HashSet<String>();
        ignoreSet1.add("some-other-ignored-features.xml");

        verifyServerFeatures(expected, ignoreSet1); // ignoring some unrelated file

        Set<String> ignoreSet2 = new HashSet<String>();
        ignoreSet2.add("generated-features.xml");

        Set<String> expected2 = new HashSet<String>();
        expected2.add("orig");

        verifyServerFeatures(expected2, ignoreSet2); // ignore specified file

    }

    /**
     * Tests server.xml with config dropins defaults
     * 
     * @throws Exception
     */
    @Test
    public void testConfigDefaults() throws Exception{
        copy("server.xml");
        copyAsName("config_defaults.xml", "configDropins/defaults/config_defaults.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("defaults");

        verifyServerFeatures(expected);
    }

    /**
     * Tests server.xml with both config dropins overrides and defaults
     * 
     * @throws Exception
     */
    @Test
    public void testOverridesAndDefaults() throws Exception{
        copy("server.xml");
        copyAsName("config_overrides.xml", "configDropins/overrides/config_overrides.xml");
        copyAsName("config_defaults.xml", "configDropins/defaults/config_defaults.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("overrides");
        expected.add("defaults");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with both config dropins overrides and defaults
     * 
     * @throws Exception
     */
    @Test
    public void testOverridesAndDefaultsWithPlatforms() throws Exception{
        copy("server.xml");
        copyAsName("config_overrides.xml", "configDropins/overrides/config_overrides.xml");
        copyAsName("config_defaults.xml", "configDropins/defaults/config_defaults.xml");
        
        Set<String> expectedFeatures = new HashSet<String>();
        expectedFeatures.add("orig");
        expectedFeatures.add("overrides");
        expectedFeatures.add("defaults");
        
        Set<String> expectedPlatforms = new HashSet<String>();
        expectedPlatforms.add("plat-1.0");
        expectedPlatforms.add("plat-overrides");
        expectedPlatforms.add("plat-defaults");

        verifyServerFeaturesAndPlatforms(expectedFeatures,expectedPlatforms);
    }
    
    /**
     * Tests server.xml with multiple MERGE functions
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleMerge() throws Exception{
        copyAsName("server_multiple_merge.xml", "server.xml");
        copy("extraFeatures2.xml");
        copy("extraFeatures3.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra2");
        expected.add("extra3");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with multiple MERGE functions
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleMergeWithPlatforms() throws Exception{
        copyAsName("server_multiple_merge.xml", "server.xml");
        copy("extraFeatures2.xml");
        copy("extraFeatures3.xml");

        Set<String> expectedFeatures = new HashSet<String>();
        expectedFeatures.add("orig");
        expectedFeatures.add("extra2");
        expectedFeatures.add("extra3");
        
        Set<String> expectedPlatforms = new HashSet<String>();
        expectedPlatforms.add("plat-1.0");
        expectedPlatforms.add("plat-2.0");

        verifyServerFeaturesAndPlatforms(expectedFeatures,expectedPlatforms);
    }
    
    /**
     * Tests server.xml with multiple REPLACE functions
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleReplace() throws Exception{
        copyAsName("server_multiple_replace.xml", "server.xml");
        copy("extraFeatures2.xml");
        copy("extraFeatures3.xml");

        // only the last replace should be kept
        Set<String> expected = new HashSet<String>();
        expected.add("extra3");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with REPLACE function that has no featureManager section
     * 
     * @throws Exception
     */
    @Test
    public void testNoListReplace() throws Exception{
        copyAsName("server_no_list_replace.xml", "server.xml");
        copy("noList.xml");

        // parent is kept, since child list does not exist
        Set<String> expected = new HashSet<String>();
        expected.add("orig");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with REPLACE function that has empty featureManager section
     * 
     * @throws Exception
     */
    @Test
    public void testEmptyListReplace() throws Exception{
        copyAsName("server_empty_list_replace.xml", "server.xml");
        copy("emptyList.xml");

        // parent is kept, since child list is empty
        Set<String> expected = new HashSet<String>();
        expected.add("orig");

        verifyServerFeatures(expected);
    }

    /**
     * Tests server.xml with IGNORE function when parent has no featureManager section
     * 
     * @throws Exception
     */
    @Test
    public void testParentNoListIgnore() throws Exception{
        copyAsName("server_parent_no_list_ignore.xml", "server.xml");
        copy("extraFeatures.xml");

        // do not ignore the child list since parent has no featureManager section
        Set<String> expected = new HashSet<String>();
        expected.add("extra");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with IGNORE function when parent has empty featureManager section
     * 
     * @throws Exception
     */
    @Test
    public void testParentEmptyListIgnore() throws Exception{
        copyAsName("server_parent_empty_list_ignore.xml", "server.xml");
        copy("extraFeatures.xml");

        // ignore the child list since parent has a featureManager section which counts as a conflict
        Set<String> expected = new HashSet<String>();

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with a combination of everything, including a replace that is empty
     * 
     * @throws Exception
     */
    @Test
    public void testCombinationEmptyReplace() throws Exception{
        copyAsName("server_combination_empty_replace.xml", "server.xml");
        copyAsName("config_overrides.xml", "configDropins/overrides/config_overrides.xml");
        copyAsName("config_defaults.xml", "configDropins/defaults/config_defaults.xml");
        
        // includes
        copy("extraFeatures.xml"); // merge
        copy("extraFeatures2.xml"); // ignore
        copy("emptyList.xml"); // replace
        copy("extraFeatures4.xml"); // merge

        // Keep everything except 2nd include (since it is ignored) and 3rd include (since it is empty)
        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("overrides");
        expected.add("defaults");
        expected.add("extra");
        expected.add("extra4");
        
        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with a combination of everything, including a replace
     * 
     * @throws Exception
     */
    @Test
    public void testCombinationWithReplace() throws Exception{
        copyAsName("server_combination_replace.xml", "server.xml");
        copyAsName("config_overrides.xml", "configDropins/overrides/config_overrides.xml");
        copyAsName("config_defaults.xml", "configDropins/defaults/config_defaults.xml");
        
        // includes
        copy("extraFeatures.xml"); // merge
        copy("extraFeatures2.xml"); // ignore
        copy("extraFeatures3.xml"); // replace
        copy("extraFeatures4.xml"); // merge

        // Only keep overrides, 3rd include (since it is replace), and 4th include (since it comes after)
        Set<String> expected = new HashSet<String>();
        expected.add("overrides");
        expected.add("extra3");
        expected.add("extra4");
        
        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with a combination of everything, but the featureManager section is after the includes
     * 
     * @throws Exception
     */
    @Test
    public void testCombinationFeaturesAtEnd() throws Exception{
        copyAsName("server_combination_features_at_end.xml", "server.xml");
        copyAsName("config_overrides.xml", "configDropins/overrides/config_overrides.xml");
        copyAsName("config_defaults.xml", "configDropins/defaults/config_defaults.xml");
        
        // includes
        copy("extraFeatures.xml"); // merge
        copy("extraFeatures2.xml"); // ignore
        copy("extraFeatures3.xml"); // replace
        copy("extraFeatures4.xml"); // merge

        // Only keep overrides, 3rd include (since it is replace), 4th include and featureManager section (since they come after)
        Set<String> expected = new HashSet<String>();
        expected.add("overrides");
        expected.add("extra3");
        expected.add("extra4");
        expected.add("orig");
        
        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with a recursive include
     * 
     * @throws Exception
     */
    @Test
    public void testRecursive() throws Exception{
        copyAsName("server_recursive.xml", "server.xml");
        copyAsName("config_overrides.xml", "configDropins/overrides/config_overrides.xml");
        copyAsName("config_defaults.xml", "configDropins/defaults/config_defaults.xml");
        copy("extraFeatures.xml");
        copy("recursiveFeatures.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("defaults");
        expected.add("overrides");
        expected.add("orig");
        expected.add("extra");
        expected.add("recursive");
        
        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with an overrides that has a combination of all types of includes, including replace
     * 
     * @throws Exception
     */
    @Test
    public void testOverridesWithReplace() throws Exception {
        // server.xml with some content
        copyAsName("server_nested_replace.xml", "server.xml");
        copy("nestedReplace2.xml");
        copy("nestedReplace3.xml");

        // defaults
        copyAsName("config_defaults.xml", "configDropins/defaults/config_defaults.xml");

        // includes for the overrides directory
        copyAsName("extraFeatures.xml", "configDropins/overrides/extraFeatures.xml"); // merge
        copyAsName("extraFeatures2.xml", "configDropins/overrides/extraFeatures2.xml"); // ignore
        copyAsName("extraFeatures3.xml", "configDropins/overrides/extraFeatures3.xml"); // replace
        copyAsName("extraFeatures4.xml", "configDropins/overrides/extraFeatures4.xml"); // merge

        // overrides file that specifies includes (alphabetically after the other files in the same directory)
        copyAsName("extraFeaturesOverride.xml", "configDropins/overrides/extraFeaturesOverride.xml");

        // another overrides file that comes afterwards
        copyAsName("extraFeaturesOverride2.xml", "configDropins/overrides/extraFeaturesOverride2.xml");

        // Only keep 3rd include (since it is replace), 4th include (since it comes after), 
        // and the additional overrides after it
        Set<String> expected = new HashSet<String>();
        expected.add("extra3");
        expected.add("extra4");
        expected.add("override2");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with spaces and case sensitivity
     * 
     * @throws Exception
     */
    @Test
    public void testSpaceAndCaseSensitivity() throws Exception{
        copyAsName("server_space_case_sensitivity.xml", "server.xml");
        copy("extraFeaturesSpaceCaseSensitivity.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extralowercase");
        
        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with spaces and case sensitivity 
     * when we wish to preserve the case.
     * 
     * @throws Exception
     */
    @Test
    public void testSpaceAndCaseSensitivityPreserveCase() throws Exception{
        copyAsName("server_space_case_sensitivity.xml", "server.xml");
        copy("extraFeaturesSpaceCaseSensitivity.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("oRiG");
        expected.add("EXTRALOWERcase");
        expected.add("extralowerCaSE");
        
        verifyServerFeaturesPreserveCase(expected);
    }
    
    /**
     * Tests server.xml with include url
     * 
     * @throws Exception
     */
    @Test
    public void testIncludeUrl() throws Exception {
        replaceIncludeUrl("extraFeatures.xml");
        
        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with invalid include url
     * 
     * @throws Exception
     */
    @Test
    public void testIncludeInvalidUrl() throws Exception {
        replaceIncludeUrl("NON_EXISTENT_FILE.xml");
        
        Set<String> expected = new HashSet<String>();
        expected.add("orig");

        verifyServerFeatures(expected);
    }

    @Test
    public void testIncludeUrlFromVariable() throws Exception {
        replaceIncludeUrlFromVariable();

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra");

        verifyServerFeatures(expected);
    }

    private void replaceIncludeUrl(String includeFileName) throws Exception {
        File includeFile = new File(src, includeFileName);
        replaceIncludeLocation(includeFile.toURI().toURL().toString());
    }

    private void replaceIncludeUrlFromVariable() throws Exception {
        copy("bootstrap.properties");
        replaceIncludeLocation(src.toURI().toURL() + "\\$\\{extras.filename\\}");
    }

    private void replaceIncludeLocation(String includeLocation) throws Exception {
        copyAsName("server_url.xml", "server.xml");

        String includeReplacement = "<include location=\"" + includeLocation + "\" onConflict=\"MERGE\"/>\n";

        Path serverXmlPath = Paths.get(new File(serverDirectory, "server.xml").toURI());
        Charset charset = StandardCharsets.UTF_8;
        String content = new String(Files.readAllBytes(serverXmlPath), StandardCharsets.UTF_8);
        content = content.replaceAll("@includeReplacementToken@", includeReplacement);
        Files.write(serverXmlPath, content.getBytes(charset));
    }

    /**
     * Tests server.xml with include dir (must end with trailing slash)
     * @throws Exception
     */
    @Test
    public void testIncludeDir() throws Exception {
        // Note: Both the product code and test code end up converting Windows \ into /
        replaceIncludeLocation("includeDir/"); 
        copy("includeDir");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra");
        expected.add("extra2");
        expected.add("extra4");

        verifyServerFeatures(expected);
    }

    /**
     * Tests include directory without the trailing slash.
     * Liberty treats this as a file, which conflicts with it being a dir, and throws an error.
     * @throws Exception
     */
    @Test
    public void testInvalidIncludeDir() throws Exception {
        replaceIncludeLocation("includeDir"); 
        copy("includeDir");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");

        verifyServerFeatures(expected);
        assertTrue(util.containsErrorMessage("Path specified a file, but resource exists as a directory (path=includeDir)"));
    }

    @Test
    public void testIncludeDirReplace() throws Exception {
        copyAsName("server_dir_replace.xml", "server.xml");
        copy("includeDir");

        // only the last replace should be kept
        Set<String> expected = new HashSet<String>();
        expected.add("extra4");

        verifyServerFeatures(expected);
    }

    @Test
    public void testIncludeDirIgnore() throws Exception {
        copyAsName("server_dir_ignore.xml", "server.xml");
        copy("includeDir");

        // only the last replace should be kept
        Set<String> expected = new HashSet<String>();
        expected.add("orig");

        verifyServerFeatures(expected);
    }
    
    /**
     * Tests server.xml with user features
     * 
     * @throws Exception
     */
    @Test
    public void testUserFeaturesImproper() throws Exception{
        copyAsName("server_user_features.xml", "server.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("myExt:feature2");
        expected.add("feature3");
        

        verifyServerFeatures(expected);
    }

    @Test
    public void testIncludeUrlFromVariableServerXMLDefaultValue() throws Exception{
        copyAsName("server_include_variable_default_value.xml", "server.xml");
        copy("extraFeatures.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra");

        verifyServerFeatures(expected);
    }

    @Test
    public void testIncludeUrlFromVariableServerXML() throws Exception{
        copyAsName("server_include_variable.xml", "server.xml");
        copy("extraFeatures.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra");

        verifyServerFeatures(expected);
    }

    @Test
    public void testIncludeUrlFromVariableServerEnv() throws Exception{
        replaceIncludeLocation(src.toURI().toURL() + "\\$\\{extras.filename\\}");
        copy("server.env");
        copy("extraFeatures.xml");

        Set<String> expected = new HashSet<String>();
        expected.add("orig");
        expected.add("extra");

        verifyServerFeatures(expected);
    }
}
