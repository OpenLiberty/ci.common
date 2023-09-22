/**
 * (C) Copyright IBM Corporation 2020, 2023.
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DevUtilPrepareContainerfileTest extends BaseDevUtilTest {

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

    private void testPrepareContainerfile(String testFile, String expectedFile) throws PluginExecutionException, IOException {
        File test = new File(dockerfiles, testFile);
        File expected = new File(dockerfiles, expectedFile);
        result = util.prepareTempContainerfile(test, dockerfiles.getAbsolutePath());
        // trim the overall file content string since the file write can insert an extra line break at the end
        assertEquals(util.readContainerfile(expected), util.readContainerfile(result));
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
    public void testBasicContainerfile() throws Exception {
        testPrepareContainerfile("basic.txt", "basic-expected.txt");
        assertTrue(util.srcMount.get(0).endsWith("server.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/server.xml"));
    }

    @Test
    public void testBasicContainerfileFlow() throws Exception {
        File dockerfile = new File(dockerfiles, "basic.txt");

        List<String> dockerfileLines = util.readContainerfile(dockerfile);

        List<String> expectedContainerfileLines = new ArrayList<String>();
        expectedContainerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedContainerfileLines.add("");
        expectedContainerfileLines.add("# Add my app and config");
        expectedContainerfileLines.add("COPY --chown=1001:0  Sample1.war /config/dropins/");
        expectedContainerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedContainerfileLines.add("");
        expectedContainerfileLines.add("# Default setting for the verbose option");
        expectedContainerfileLines.add("ARG VERBOSE=false");
        expectedContainerfileLines.add("");
        expectedContainerfileLines.add("# This script will add the requested XML snippets, grow image to be fit-for-purpose and apply interim fixes");
        expectedContainerfileLines.add("RUN configure.sh");
        assertEquals(expectedContainerfileLines, dockerfileLines);

        char escape = util.getEscapeCharacter(dockerfileLines);
        assertEquals(String.valueOf('\\'), String.valueOf(escape));

        dockerfileLines = util.getCleanedLines(dockerfileLines);
        List<String> cleanedLines = new ArrayList<String>();
        expectedContainerfileLines.clear();
        expectedContainerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedContainerfileLines.add("COPY --chown=1001:0  Sample1.war /config/dropins/");
        expectedContainerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedContainerfileLines.add("ARG VERBOSE=false");
        expectedContainerfileLines.add("RUN configure.sh");
        assertEquals(expectedContainerfileLines, dockerfileLines);

        dockerfileLines = util.getCombinedLines(dockerfileLines, escape);
        expectedContainerfileLines.clear();
        expectedContainerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedContainerfileLines.add("COPY --chown=1001:0  Sample1.war /config/dropins/");
        expectedContainerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedContainerfileLines.add("ARG VERBOSE=false");
        expectedContainerfileLines.add("RUN configure.sh");
        assertEquals(expectedContainerfileLines, dockerfileLines);

        util.removeWarFileLines(dockerfileLines);
        expectedContainerfileLines.clear();
        expectedContainerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedContainerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedContainerfileLines.add("ARG VERBOSE=false");
        expectedContainerfileLines.add("RUN configure.sh");
        assertEquals(expectedContainerfileLines, dockerfileLines);

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
        expectedContainerfileLines.clear();
        expectedContainerfileLines.add("FROM openliberty/open-liberty:kernel-java8-openj9-ubi");
        expectedContainerfileLines.add("COPY --chown=1001:0  server.xml /config/");
        expectedContainerfileLines.add("ARG VERBOSE=false");
        expectedContainerfileLines.add("ENV OPENJ9_SCC=false");
        expectedContainerfileLines.add("RUN configure.sh");
        assertEquals(expectedContainerfileLines, dockerfileLines);
    }

    @Test
    public void testMultilineContainerfile() throws Exception {
        testPrepareContainerfile("multiline.txt", "multiline-expected.txt");
        assertTrue(util.srcMount.get(0).endsWith("file1.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/filenameWithoutExtension"));
        assertTrue(util.srcMount.get(1).endsWith("file2.xml"));
        assertTrue(util.destMount.get(1).endsWith("/config/directoryName/file2.xml"));
    }

    @Test
    public void testMultilineEscapeContainerfile() throws Exception {
        testPrepareContainerfile("multilineEscape.txt", "multilineEscape-expected.txt");
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
        testPrepareContainerfile("copyParsing.txt", "copyParsing-expected.txt");
        assertTrue(util.srcMount.get(0).endsWith("file1.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/file1.xml"));
        assertTrue(util.srcMount.get(1).endsWith("file2.xml"));
        assertTrue(util.destMount.get(1).endsWith("/config/file2.xml"));
        assertEquals(2, util.srcMount.size());
        assertEquals(2, util.destMount.size());
    }

    @Test
    public void testContainerBuildContext() throws Exception {
        File test = new File(dockerfiles, "dockerBuildContext.txt");
        result = util.prepareTempContainerfile(test, new File("my/context").getAbsolutePath());
        // use Paths comparison to be OS agnostic
        assertTrue(Paths.get(util.srcMount.get(0)).endsWith("my/context/path1/path2/file1.xml"));
        assertTrue(util.destMount.get(0).endsWith("/config/file1.xml"));
        assertTrue(Paths.get(util.srcMount.get(1)).endsWith("my/context/path3/file2.xml"));
        assertTrue(util.destMount.get(1).endsWith("/config/file2.xml"));
        assertEquals(2, util.srcMount.size());
        assertEquals(2, util.destMount.size());
    }

    @Test
    public void testDisableOpenJ9SCC_lowercase() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedContainerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("run configure.sh");
        util.disableOpenJ9SCC(dockerfileLines);
        expectedContainerfileLines.add("FROM openliberty/open-liberty");
        expectedContainerfileLines.add("ENV OPENJ9_SCC=false");
        expectedContainerfileLines.add("run configure.sh");
        assertEquals(expectedContainerfileLines, dockerfileLines);
    }

    @Test
    public void testDisableOpenJ9SCC_uppercase() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedContainerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("RUN configure.sh");
        util.disableOpenJ9SCC(dockerfileLines);
        expectedContainerfileLines.add("FROM openliberty/open-liberty");
        expectedContainerfileLines.add("ENV OPENJ9_SCC=false");
        expectedContainerfileLines.add("RUN configure.sh");
        assertEquals(expectedContainerfileLines, dockerfileLines);
    }

    @Test
    public void testDisableOpenJ9SCC_mixedcase() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedContainerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("RuN configure.sh");
        util.disableOpenJ9SCC(dockerfileLines);
        expectedContainerfileLines.add("FROM openliberty/open-liberty");
        expectedContainerfileLines.add("ENV OPENJ9_SCC=false");
        expectedContainerfileLines.add("RuN configure.sh");
        assertEquals(expectedContainerfileLines, dockerfileLines);
    }

    @Test
    public void testDisableOpenJ9SCC_negative() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedContainerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("RUN configure.shaaaaaaaaaa");
        util.disableOpenJ9SCC(dockerfileLines);
        expectedContainerfileLines.add("FROM openliberty/open-liberty");
        expectedContainerfileLines.add("RUN configure.shaaaaaaaaaa");
        assertEquals(expectedContainerfileLines, dockerfileLines);
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

    @Test
    public void testRemoveEarFileLines() throws Exception {
        List<String> dockerfileLines = new ArrayList<String>();
        List<String> expectedContainerfileLines = new ArrayList<String>();
        dockerfileLines.add("FROM openliberty/open-liberty");
        dockerfileLines.add("COPY --chown=1001:0  target/guide-maven-multimodules-ear.ear /config/apps/");
        util.removeEarFileLines(dockerfileLines);
        expectedContainerfileLines.add("FROM openliberty/open-liberty");
        assertEquals(expectedContainerfileLines, dockerfileLines);
    }

}