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
        assertEquals(util.readDockerfile(expected), util.readDockerfile(result));
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
    public void testBasicDockerfileFlow() throws Exception {
        File dockerfile = new File(dockerfiles, "basic.txt");

        List<String> dockerfileLines = util.readDockerfile(dockerfile);

        List<String> expectedDockerfileLines = new ArrayList<String>();
        expectedDockerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedDockerfileLines.add("");
        expectedDockerfileLines.add("# Add my app and config");
        expectedDockerfileLines.add("COPY --chown=1001:0  Sample1.war /config/dropins/");
        expectedDockerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedDockerfileLines.add("");
        expectedDockerfileLines.add("# Default setting for the verbose option");
        expectedDockerfileLines.add("ARG VERBOSE=false");
        expectedDockerfileLines.add("");
        expectedDockerfileLines.add("# This script will add the requested XML snippets, grow image to be fit-for-purpose and apply interim fixes");
        expectedDockerfileLines.add("RUN configure.sh");
        assertEquals(expectedDockerfileLines, dockerfileLines);

        char escape = util.getEscapeCharacter(dockerfileLines);
        assertEquals(String.valueOf('\\'), String.valueOf(escape));

        dockerfileLines = util.getCleanedLines(dockerfileLines);
        List<String> cleanedLines = new ArrayList<String>();
        expectedDockerfileLines.clear();
        expectedDockerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedDockerfileLines.add("COPY --chown=1001:0  Sample1.war /config/dropins/");
        expectedDockerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedDockerfileLines.add("ARG VERBOSE=false");
        expectedDockerfileLines.add("RUN configure.sh");
        assertEquals(expectedDockerfileLines, dockerfileLines);

        dockerfileLines = util.getCombinedLines(dockerfileLines, escape);
        expectedDockerfileLines.clear();
        expectedDockerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedDockerfileLines.add("COPY --chown=1001:0  Sample1.war /config/dropins/");
        expectedDockerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedDockerfileLines.add("ARG VERBOSE=false");
        expectedDockerfileLines.add("RUN configure.sh");
        assertEquals(expectedDockerfileLines, dockerfileLines);

        util.removeWarFileLines(dockerfileLines);
        expectedDockerfileLines.clear();
        expectedDockerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedDockerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedDockerfileLines.add("ARG VERBOSE=false");
        expectedDockerfileLines.add("RUN configure.sh");
        assertEquals(expectedDockerfileLines, dockerfileLines);

        util.processCopyLines(dockerfileLines, dockerfile.getParent());
        assertTrue(util.srcMount.get(0).endsWith("server.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/server.xml"));
        assertEquals(1, util.srcMount.size());
        assertEquals(1, util.destMount.size());

        util.processCopyLines(dockerfileLines, dockerfile.getParent()); // ensure retest still has only one line
        assertTrue(util.srcMount.get(0).endsWith("server.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/server.xml"));
        assertEquals(1, util.srcMount.size());
        assertEquals(1, util.destMount.size());

        util.disableOpenJ9SCC(dockerfileLines);
        expectedDockerfileLines.clear();
        expectedDockerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedDockerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedDockerfileLines.add("ARG VERBOSE=false");
        expectedDockerfileLines.add("ENV OPENJ9_SCC=false");
        expectedDockerfileLines.add("RUN configure.sh");
        assertEquals(expectedDockerfileLines, dockerfileLines);
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

    @Test
    public void testCopyParsing() throws Exception {
        testPrepareDockerfile("copyParsing.txt", "copyParsing-expected.txt");
        assertTrue(util.srcMount.get(0).endsWith("file1.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/file1.xml"));
        assertTrue(util.srcMount.get(1).endsWith("file2.xml"));
        assertTrue(util.destMount.get(1).endsWith("/config/file2.xml"));
        assertEquals(2, util.srcMount.size());
        assertEquals(2, util.destMount.size());
    }

    @Test
    public void testDisableOpenJ9SCC_lowercase() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedDockerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("run configure.sh");
        util.disableOpenJ9SCC(dockerfileLines);
        expectedDockerfileLines.add("FROM openliberty/open-liberty");
        expectedDockerfileLines.add("ENV OPENJ9_SCC=false");
        expectedDockerfileLines.add("run configure.sh");
        assertEquals(expectedDockerfileLines, dockerfileLines);
    }

    @Test
    public void testDisableOpenJ9SCC_uppercase() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedDockerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("RUN configure.sh");
        util.disableOpenJ9SCC(dockerfileLines);
        expectedDockerfileLines.add("FROM openliberty/open-liberty");
        expectedDockerfileLines.add("ENV OPENJ9_SCC=false");
        expectedDockerfileLines.add("RUN configure.sh");
        assertEquals(expectedDockerfileLines, dockerfileLines);
    }

    @Test
    public void testDisableOpenJ9SCC_mixedcase() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedDockerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("RuN configure.sh");
        util.disableOpenJ9SCC(dockerfileLines);
        expectedDockerfileLines.add("FROM openliberty/open-liberty");
        expectedDockerfileLines.add("ENV OPENJ9_SCC=false");
        expectedDockerfileLines.add("RuN configure.sh");
        assertEquals(expectedDockerfileLines, dockerfileLines);
    }

    @Test
    public void testDisableOpenJ9SCC_negative() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedDockerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("RUN configure.shaaaaaaaaaa");
        util.disableOpenJ9SCC(dockerfileLines);
        expectedDockerfileLines.add("FROM openliberty/open-liberty");
        expectedDockerfileLines.add("RUN configure.shaaaaaaaaaa");
        assertEquals(expectedDockerfileLines, dockerfileLines);
    }

    @Test
    public void testDetectFeaturesSh() throws Exception {
        List<String> test = new ArrayList<String>();
        test.add("FROM open-liberty");
        test.add("RUN configure.sh");
        util.detectFeaturesSh(test);
        assertEquals("Should not have detected features.sh", false, util.hasFeaturesSh.get());

        test.clear();
        test.add("FROM open-liberty");
        test.add("RUN features.sh");
        util.detectFeaturesSh(test);
        assertEquals("Should have detected features.sh", true, util.hasFeaturesSh.get());

        test.clear();
        test.add("FROM open-liberty");
        test.add("RUN configure.sh");
        util.detectFeaturesSh(test);
        assertEquals("Should have resetted to not detect features.sh", false, util.hasFeaturesSh.get());
    }

}