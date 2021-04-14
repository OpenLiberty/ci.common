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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import io.openliberty.tools.common.plugins.util.InstallFeatureUtil;
import io.openliberty.tools.common.plugins.util.PluginExecutionException;
import io.openliberty.tools.common.plugins.util.PluginScenarioException;
import io.openliberty.tools.common.plugins.util.InstallFeatureUtil.ProductProperties;

public class InstallFeatureUtilTest extends BaseInstallFeatureUtilTest {
    
    private static final String RESOLVER_JAR_PATH = "resolver/io/openliberty/features/repository-resolver/18.0.0.2/repository-resolver-18.0.0.2.jar";
    private static final String RESOLVER_SYMBOLIC_NAME = "com.ibm.ws.repository.resolver";

    @Test
    public void testConstructor() throws Exception {
        InstallFeatureUtil util = getNewInstallFeatureUtil();
        assertNotNull(util);
    }
    
    @Test(expected = PluginExecutionException.class)
    public void testConstructorNoProperties() throws Exception {
        File olProps = new File(installDir, "lib/versions/openliberty.properties");
        File wlProps = new File(installDir, "lib/versions/WebSphereApplicationServer.properties");
        assertTrue(olProps.delete());
        assertTrue(wlProps.delete());
        getNewInstallFeatureUtil(installDir, null, null, new HashSet<String>());
    }
    
    /**
     * No install map - expect a scenario exception
     */
    @Test(expected = PluginScenarioException.class)
    public void testConstructorNoInstallMap() throws Exception {
        File installMap = new File(installDir, "lib/com.ibm.ws.install.map_1.0.21.jar");
        assertTrue(installMap.delete());
        getNewInstallFeatureUtil(installDir, null, null, new HashSet<String>());
    }
    
    /**
     * No openliberty.properties, but it should find the runtime install map and not encounter an error
     */
    @Test
    public void testConstructorNoOpenLibertyProperties() throws Exception {
        File olProps = new File(installDir, "lib/versions/openliberty.properties");
        assertTrue(olProps.delete());
        getNewInstallFeatureUtil(installDir, null, null, new HashSet<String>());
    }
    
    /**
     * No install map or openliberty.properties - expect a scenario exception
     */
    @Test(expected = PluginScenarioException.class)
    public void testConstructorNoInstallMapNoOpenLibertyProperties() throws Exception {
        File olProps = new File(installDir, "lib/versions/openliberty.properties");
        assertTrue(olProps.delete());
        File installMap = new File(installDir, "lib/com.ibm.ws.install.map_1.0.21.jar");
        assertTrue(installMap.delete());
        getNewInstallFeatureUtil(installDir, null, null, new HashSet<String>());
    }
        
    @Test
    public void testConstructorTo() throws Exception {
        InstallFeatureUtil util = getNewInstallFeatureUtil(installDir, null, "myextension", new HashSet<String>());
        assertNotNull(util);
    }
    
    /**
     * TODO remove the expected exception when "from" scenario is supported
     */
    @Test(expected = PluginScenarioException.class)
    public void testConstructorFrom() throws Exception {
        getNewInstallFeatureUtil(installDir, installDir.getAbsolutePath(), null, new HashSet<String>());
    }
    
    /**
     * TODO remove the expected exception when installing from ESAs is supported
     */
    @Test(expected = PluginScenarioException.class)
    public void testConstructorEsas() throws Exception {
        Set<String> esas = new HashSet<String>();
        esas.add("abc.esa");
        getNewInstallFeatureUtil(installDir, null, null, esas);
    }
    
    /**
     * The installFeatures method should be tested from the actual project that
     * uses it. It will throw an exception here because the test install map jar
     * does not contain anything.
     */
    @Test(expected = PluginExecutionException.class)
    public void testInstallFeatures() throws Exception {
        InstallFeatureUtil util = getNewInstallFeatureUtil();
        List<String> featuresToInstall = new ArrayList<String>();
        featuresToInstall.add("a-1.0");
        util.installFeatures(true, featuresToInstall);
    }
    
    @Test
    public void testCombineToSet() throws Exception {
        Set<String> a = new HashSet<String>();
        a.add("1");
        a.add("2");
        List<String> b = new ArrayList<String>();
        b.add("1");
        b.add("3");
        List<String> c = new ArrayList<String>();
        c.add("4");
        c.add("5");
        Set<String> result = InstallFeatureUtil.combineToSet(a, b, c);
        assertEquals(5, result.size());
    }

    @Test
    public void testCombineToSetCaseInsensitive() throws Exception {
        Set<String> a = new HashSet<String>();
        a.add("mpconfig-1.3");
        a.add("mpOpenAPI-1.0");
        a.add("beanValidation-2.0");
        List<String> b = new ArrayList<String>();
        b.add("mpConfig-1.3");
        b.add("mpopenapi-1.0");
        b.add("ejbLite-3.2");
        List<String> c = new ArrayList<String>();
        c.add("mphealth-1.0");
        c.add("mpHealth-1.0");
        c.add("ejblite-3.2");
        c.add("EJBLITE-3.2");
        Set<String> result = InstallFeatureUtil.combineToSet(a, b, c);
        assertEquals(5, result.size());
        assertTrue(result.contains("mpconfig-1.3"));
        assertTrue(result.contains("mpOpenAPI-1.0"));
        assertTrue(result.contains("mphealth-1.0"));
        assertTrue(result.contains("beanValidation-2.0"));
        assertTrue(result.contains("ejbLite-3.2"));
    }

    @Test
    public void testExtractSymbolicName() throws Exception {
        String symbolicName = InstallFeatureUtil.extractSymbolicName(new File(RESOURCES_DIR, RESOLVER_JAR_PATH));
        assertEquals("Symbolic name does not match", RESOLVER_SYMBOLIC_NAME, symbolicName);
    }

    @Test
    public void testGetNextProductVersion() throws Exception {
        assertEquals("18.0.0.3", InstallFeatureUtil.getNextProductVersion("18.0.0.2"));
        assertEquals("18.0.0.10", InstallFeatureUtil.getNextProductVersion("18.0.0.9"));
        assertEquals("18.0.0.11", InstallFeatureUtil.getNextProductVersion("18.0.0.10"));
        assertEquals("1.1", InstallFeatureUtil.getNextProductVersion("1.0"));
        assertEquals("1.1.2", InstallFeatureUtil.getNextProductVersion("1.1.1"));
    }

    @Test(expected = PluginExecutionException.class)
    public void testGetNextProductVersionNoPeriods() throws Exception {
        InstallFeatureUtil.getNextProductVersion("18002");
    }

    @Test(expected = PluginExecutionException.class)
    public void testGetNextProductVersionNonNumeric() throws Exception {
        InstallFeatureUtil.getNextProductVersion("18.0.0.a");
    }

    @Test(expected = PluginExecutionException.class)
    public void testGetNextProductVersionNonNumeric2() throws Exception {
        InstallFeatureUtil.getNextProductVersion("18.0.0.2-a");
    }

    @Test
    public void testDownloadOverrideBundle() throws Exception {
        List<ProductProperties> propertiesList = InstallFeatureUtil.loadProperties(installDir);
        String openLibertyVersion = InstallFeatureUtil.getOpenLibertyVersion(propertiesList);
        List<String> additionalJsons = new ArrayList<String>();
        InstallFeatureUtil util = new InstallFeatureTestUtil(installDir, null, null, new HashSet<String>(), propertiesList, openLibertyVersion, additionalJsons) {
            @Override
            public File downloadArtifact(String groupId, String artifactId, String type, String version)
                    throws PluginExecutionException {
                if (artifactId.equals(InstallFeatureUtil.REPOSITORY_RESOLVER_ARTIFACT_ID)) {
                    assertEquals("[18.0.0.2, 18.0.0.3)", version);
                    String downloadVersion = "18.0.0.2";

                    String[] groupComponents = groupId.split("\\.");
                    StringBuilder sb = new StringBuilder("resolver");
                    for (String groupComponent : groupComponents) {
                        sb.append("/" + groupComponent);
                    }
                    sb.append("/" + artifactId + "/" + downloadVersion + "/" + artifactId + "-" + downloadVersion
                            + "." + type);
                    return new File(RESOURCES_DIR, sb.toString());
                } else {
                    return super.downloadArtifact(groupId, artifactId, type, version);
                }
            }
        };
        String result = util.getOverrideBundleDescriptor(InstallFeatureUtil.OPEN_LIBERTY_GROUP_ID,
                InstallFeatureUtil.REPOSITORY_RESOLVER_ARTIFACT_ID);
        String expectedEndsWith = RESOLVER_JAR_PATH + ";" + RESOLVER_SYMBOLIC_NAME;
        String expectedEndsWithWindows = expectedEndsWith.replaceAll("/", "\\\\");
        assertTrue(
                "downloadOverrideBundle should return a string that ends with " + expectedEndsWith + " or "
                        + expectedEndsWithWindows + ", but actual result was " + result,
                result.endsWith(expectedEndsWith) || result.endsWith(expectedEndsWithWindows));
    }
    
    @Test
    public void testGetLibertyFeatureSet() throws Exception {
        Set<File> jsons = new HashSet<File>();
        jsons.add(new File(RESOURCES_DIR, "jsons/ol.json"));
        jsons.add(new File(RESOURCES_DIR, "jsons/wlp.json"));
        jsons.add(new File(RESOURCES_DIR, "jsons/other.json"));
        
        Set<String> features = InstallFeatureUtil.getOpenLibertyFeatureSet(jsons);
        
        String featuresString = features.toString();
        assertEquals("Feature set " + featuresString + " does not have the expected number of features.", 2, features.size());
        assertTrue("Feature set " + featuresString + " does not contain expected Open Liberty feature com.ibm.websphere.appserver.anno-1.0", features.contains("com.ibm.websphere.appserver.anno-1.0"));
        assertTrue("Feature set " + featuresString + " does not contain expected Open Liberty feature appClientSupport-1.0", features.contains("appClientSupport-1.0"));
        assertFalse("Feature set " + featuresString + " contains unexpected WebSphere Liberty feature adminCenter-1.0", features.contains("adminCenter-1.0"));
        assertFalse("Feature set " + featuresString + " contains unexpected WebSphere Liberty feature com.ibm.websphere.appserver.adminCenter.collectiveController-1.0", features.contains("com.ibm.websphere.appserver.adminCenter.collectiveController-1.0"));
    }
    
    @Test
    public void testContainsIgnoreCase() throws Exception {
        List<String> reference = new ArrayList<String>();
        reference.add("featureNameA");
        reference.add("featureNameB");
        reference.add("featureNameC");
        
        List<String> target = new ArrayList<String>();
        target.add("featureNameA");
        target.add("FEATURENAMEB");
        assertTrue("Collection " + reference + " should contain all of the elements from " + target + " ignoring case", InstallFeatureUtil.containsIgnoreCase(reference, target));

        target = new ArrayList<String>();
        target.add("featurenamec");
        assertTrue("Collection " + reference + " should contain all of the elements from " + target + " ignoring case", InstallFeatureUtil.containsIgnoreCase(reference, target));

        target = new ArrayList<String>();
        target.add("feature");
        assertFalse("Collection " + reference + " should not contain all of the elements from " + target + " ignoring case", InstallFeatureUtil.containsIgnoreCase(reference, target));

        target = new ArrayList<String>();
        target.add("featureNameA");
        target.add("featureNameB");
        target.add("featureNameC");
        target.add("other");
        assertFalse("Collection " + reference + " should not contain all of the elements from " + target + " ignoring case", InstallFeatureUtil.containsIgnoreCase(reference, target));
    }

}
