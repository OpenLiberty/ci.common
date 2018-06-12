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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstallFeatureUtilGetInstallMapTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    private String[] input;
    private String expected;

    /**
     * Initialize test data for finding the install map jar
     * 
     * @param input Array of file names to test with
     * @param expected The expected file name that should be returned
     */
    public InstallFeatureUtilGetInstallMapTest(String[] input, String expected) {
        this.input = input;
        this.expected = expected;
    }
    
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new String[] { 
                        // all
                        "com.ibm.ws.install.map_1.0.100.jar",
                        "com.ibm.ws.install.map_1.0.21.cl170220180305-1441.jar",
                        "com.ibm.ws.install.map_1.0.21.cl170420180505-1441.jar", 
                        "com.ibm.ws.install.map_1.0.21.jar",
                        "com.ibm.ws.install.map_1.0.22.jar",
                        "com.ibm.ws.install.map.jar", 
                        "something.else_1.0.21.jar" },
                            "com.ibm.ws.install.map_1.0.100.jar" },
                { new String[] { 
                        // no version
                        "com.ibm.ws.install.map.jar" },
                            "com.ibm.ws.install.map.jar" },
                { new String[] { 
                        // no version, release version
                        "com.ibm.ws.install.map_1.0.21.jar",
                        "com.ibm.ws.install.map.jar" },
                            "com.ibm.ws.install.map_1.0.21.jar" },
                { new String[] { 
                        // something else, release version
                        "com.ibm.ws.install.map_1.0.21.jar",
                        "something.else_1.0.21.jar" },
                            "com.ibm.ws.install.map_1.0.21.jar" },
                { new String[] { 
                        // release version, fixpack version
                        "com.ibm.ws.install.map_1.0.21.cl170220180305-1441.jar",
                        "com.ibm.ws.install.map_1.0.21.jar" },
                            "com.ibm.ws.install.map_1.0.21.cl170220180305-1441.jar" },
                { new String[] { 
                        // release version, 2 fixpack versions
                        "com.ibm.ws.install.map_1.0.21.cl170220180305-1441.jar",
                        "com.ibm.ws.install.map_1.0.21.cl170420180505-1441.jar", 
                        "com.ibm.ws.install.map_1.0.21.jar" },
                            "com.ibm.ws.install.map_1.0.21.cl170420180505-1441.jar" },
                { new String[] { 
                        // different release versions
                        "com.ibm.ws.install.map_1.0.21.jar",
                        "com.ibm.ws.install.map_1.0.22.jar" },
                            "com.ibm.ws.install.map_1.0.22.jar" },
                { new String[] { 
                        // more different release versions
                        "com.ibm.ws.install.map_1.0.100.jar",
                        "com.ibm.ws.install.map_1.0.21.jar",
                        "com.ibm.ws.install.map_1.0.22.jar" },
                            "com.ibm.ws.install.map_1.0.100.jar" },
                });
    }
    
    @Test
    public void test() throws IOException {
        File installMapDir = temp.newFolder();
        for (String testFileName : input) {
            File testFile = new File(installMapDir, testFileName);
            assertTrue(testFile.createNewFile());
        }
        String result = InstallFeatureUtil.getMapBasedInstallKernelJar(installMapDir).getName();
        
        assertEquals(expected, result);
    }

}
