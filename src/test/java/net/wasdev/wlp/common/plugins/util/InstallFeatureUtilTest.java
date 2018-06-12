/**
 * (C) Copyright IBM Corporation 2018.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class InstallFeatureUtilTest extends BaseInstallFeatureUtilTest {
    
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
        new InstallFeatureTestUtil(installDir, null, null, new HashSet<String>());
    }
    
    @Test
    public void testConstructorTo() throws Exception {
        InstallFeatureUtil util = new InstallFeatureTestUtil(installDir, null, "myextension", new HashSet<String>());
        assertNotNull(util);
    }
    
    /**
     * TODO remove the expected exception when "from" scenario is supported
     */
    @Test(expected = PluginScenarioException.class)
    public void testConstructorFrom() throws Exception {
        new InstallFeatureTestUtil(installDir, installDir.getAbsolutePath(), null, new HashSet<String>());
    }
    
    /**
     * TODO remove the expected exception when installing from ESAs is supported
     */
    @Test(expected = PluginScenarioException.class)
    public void testConstructorEsas() throws Exception {
        Set<String> esas = new HashSet<String>();
        esas.add("abc.esa");
        new InstallFeatureTestUtil(installDir, null, null, esas);
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
        b.add("4");
        b.add("5");
        Set<String> result = InstallFeatureUtil.combineToSet(a, b, c);
        assertEquals(5, result.size());
    }

}
