/**
 * (C) Copyright IBM Corporation 2020.
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DevUtilPrepareDockerfileTest extends BaseDevUtilTest {

    DevUtil util;
    File dockerfiles = new File("src/test/resources/dockerbuild");
    File result;

    @Before
    public void setUp() throws IOException {
        util = getNewDevUtil(null);
    }

    @After
    public void tearDown() {
        if (result != null && result.exists()) {
            result.delete();
            result = null;
        }
    }

    private void testPrepareDockerfile(String testFile, String expectedFile) throws PluginExecutionException, IOException {
        File test = new File(dockerfiles, testFile);
        File expected = new File(dockerfiles, expectedFile);
        result = util.prepareTempDockerfile(test);
        // trim the overall file content string since the file write can insert an extra line break at the end
        assertEquals(new String(Files.readAllBytes(expected.toPath())).trim(), new String(Files.readAllBytes(result.toPath())).trim());
    }

    @Test
    public void testCleanAndCombine() throws Exception {
        List<String> test = new ArrayList<String>();
        test.add("FROM open-liberty");
        test.add("  COPY --chown=1001:0 \\  ");
        test.add("  # COMMENT ");
        test.add("    src/main/liberty/config/server.xml \\");
        test.add("    /config/ #THE FOLLOWING MULTILINE SYMBOL SHOULD BE IGNORED \\");
        test.add("    \t   COPY secondline.xml /config/  \t  ");

        List<String> expectedCleaned = new ArrayList<String>();
        expectedCleaned.add("FROM open-liberty");
        expectedCleaned.add("COPY --chown=1001:0 \\");
        expectedCleaned.add("src/main/liberty/config/server.xml \\");
        expectedCleaned.add("/config/");
        expectedCleaned.add("COPY secondline.xml /config/");

        List<String> actualCleaned = DevUtil.getCleanedLines(test);
        assertEquals(expectedCleaned, actualCleaned);

        List<String> expectedCombined = new ArrayList<String>();
        expectedCombined.add("FROM open-liberty");
        expectedCombined.add("COPY --chown=1001:0 src/main/liberty/config/server.xml /config/");
        expectedCombined.add("COPY secondline.xml /config/");
        
        List<String> actualCombined = DevUtil.getCombinedLines(actualCleaned, '\\');
        assertEquals(expectedCombined, actualCombined);
    }

    @Test
    public void testBasicDockerfile() throws Exception {
        testPrepareDockerfile("basic.txt", "basic-expected.txt");
        assertTrue(util.srcMount.get(0).endsWith("server.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/server.xml"));
    }

    @Test
    public void testMultilineDockerfile() throws Exception {
        testPrepareDockerfile("multiline.txt", "multiline-expected.txt");
        assertTrue(util.srcMount.get(0).endsWith("file1.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/filenameWithoutExtension"));
        assertTrue(util.srcMount.get(1).endsWith("file2.xml"));
        assertTrue(util.destMount.get(1).endsWith("/config/directoryName/file2.xml"));
    }

    @Test
    public void testMultilineEscapeDockerfile() throws Exception {
        testPrepareDockerfile("multilineEscape.txt", "multilineEscape-expected.txt");
        assertTrue(util.srcMount.get(0).endsWith("server.xml"));
        assertTrue(util.destMount.get(0).endsWith("c:\\config\\server.xml"));
    }

    @Test
    public void testGetEscapeChar() throws Exception {
        List<String> test = new ArrayList<String>();
        test.add("#");
        assertEquals(String.valueOf('\\'), String.valueOf(DevUtil.getEscapeCharacter(test)));

        test.clear();
        test.add("#    EsCApE   =   `   ");
        assertEquals(String.valueOf('`'), String.valueOf(DevUtil.getEscapeCharacter(test)));

        test.clear();
        test.add("  #    EsCApE   =   \\   ");
        assertEquals(String.valueOf('\\'), String.valueOf(DevUtil.getEscapeCharacter(test)));

        test.clear();
        test.add("# some comment");
        test.add("# escape=`");
        assertEquals(String.valueOf('\\'), String.valueOf(DevUtil.getEscapeCharacter(test)));
    }

}