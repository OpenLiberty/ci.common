/**
 * (C) Copyright IBM Corporation 2026.
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
package io.openliberty.tools.common.config;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.openliberty.tools.common.TestLogger;
import io.openliberty.tools.common.plugins.config.ServerConfigDocument;
import io.openliberty.tools.common.plugins.util.OSUtil;
import io.openliberty.tools.common.plugins.util.ServerFeatureUtil;

// Verifies the log message format produced by resolveExpansionProperties() after the fix for issue #2076
public class ExpansionVariableLogMessageTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // Captures info() calls for assertion; all other methods delegate to TestLogger
    private static class CapturingLogger extends TestLogger {
        final List<String> infoMessages = new ArrayList<>();

        @Override
        public void info(String msg) { infoMessages.add(msg); }
    }

    // server.env is placed in serverDir because SERVER_CONFIG_DIR maps there, making it visible to processServerEnv()
    private ServerConfigDocument buildDoc(CapturingLogger log, File serverDir, String serverEnvContent) throws Exception {
        Files.write(new File(serverDir, "server.env").toPath(), serverEnvContent.getBytes());

        Map<String, File> dirMap = new HashMap<>();
        dirMap.put(ServerFeatureUtil.WLP_INSTALL_DIR, serverDir);
        dirMap.put(ServerFeatureUtil.WLP_USER_DIR, serverDir);
        dirMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serverDir);
        dirMap.put(ServerFeatureUtil.SERVER_OUTPUT_DIR, serverDir);
        return new ServerConfigDocument(log, null, dirMap);
    }

    @Test
    public void testUnixStyleLogMessageFormat() throws Exception {
        Assume.assumeFalse("Skipped on Windows: Unix ${VAR} pattern not active", OSUtil.isWindows());

        CapturingLogger log = new CapturingLogger();
        File serverDir = tmp.newFolder("server-unix");

        String envContent = "BASE=TEST\nDERIVED=${BASE}_SUFFIX\n";
        buildDoc(log, serverDir, envContent).processServerEnv();

        String expectedMsg = "Resolved environment variable \"BASE\" in path \"${BASE}_SUFFIX\" to \"TEST\"";
        String expectedSummary = "Resolved path \"${BASE}_SUFFIX\" to \"TEST_SUFFIX\"";
        assertTrue("Expected log message not found.\nActual info messages: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedMsg)));
        assertTrue("Expected summary log message not found.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedSummary)));
    }

    @Test
    public void testUnixStyleMultipleVarsLogsBothVarNames() throws Exception {
        Assume.assumeFalse("Skipped on Windows: Unix ${VAR} pattern not active", OSUtil.isWindows());

        CapturingLogger log = new CapturingLogger();
        File serverDir = tmp.newFolder("server-unix-multi");

        String envContent = "EXP_VAR=TEST\nEXP_VAR2=UNIX\nCOMBINED=${EXP_VAR}_${EXP_VAR2}\n";
        buildDoc(log, serverDir, envContent).processServerEnv();

        String expectedMsg1 = "Resolved environment variable \"EXP_VAR\" in path \"${EXP_VAR}_${EXP_VAR2}\" to \"TEST\"";
        String expectedMsg2 = "Resolved environment variable \"EXP_VAR2\" in path \"${EXP_VAR}_${EXP_VAR2}\" to \"UNIX\"";
        String expectedSummary = "Resolved path \"${EXP_VAR}_${EXP_VAR2}\" to \"TEST_UNIX\"";
        assertTrue("Expected first log message not found.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedMsg1)));
        assertTrue("Expected second log message not found.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedMsg2)));
        assertTrue("Expected summary log message not found.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedSummary)));
    }

    @Test
    public void testNoExpansionReferenceNoLogEmitted() throws Exception {
        CapturingLogger log = new CapturingLogger();
        File serverDir = tmp.newFolder("server-no-expansion");

        String envContent = "PLAIN_VAR=just_a_value\n";
        buildDoc(log, serverDir, envContent).processServerEnv();

        assertTrue("No info log message should be emitted for plain values, but found: " + log.infoMessages,
                log.infoMessages.isEmpty());
    }

    @Test
    public void testWindowsStyleBackslashesPreservedInLog() throws Exception {
        Assume.assumeTrue("Skipped on non-Windows: !VAR! pattern only active on Windows", OSUtil.isWindows());

        CapturingLogger log = new CapturingLogger();
        File serverDir = tmp.newFolder("server-win");

        // Reproduces the exact scenario from issue #2076
        String envContent = "IBM_JAVA_SEMERU_HOME=C:\\MyData\\java\\ibm-semeru-certified\nJAVA_HOME=!IBM_JAVA_SEMERU_HOME!\\jdk-21.0.10+7\n";
        buildDoc(log, serverDir, envContent).processServerEnv();

        String expectedMsg = "Resolved environment variable \"IBM_JAVA_SEMERU_HOME\" in path \"!IBM_JAVA_SEMERU_HOME!\\jdk-21.0.10+7\" to \"C:\\MyData\\java\\ibm-semeru-certified\\jdk-21.0.10+7\"";
        String expectedSummary = "Resolved path \"!IBM_JAVA_SEMERU_HOME!\\jdk-21.0.10+7\" to \"C:\\MyData\\java\\ibm-semeru-certified\\jdk-21.0.10+7\"";
        assertTrue("Log message with backslashes not found — backslashes may have been dropped.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedMsg)));
        assertTrue("Expected summary log message not found.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedSummary)));
    }

    @Test
    public void testWindowsStyleMultipleVarsOneLogPerExpression() throws Exception {
        Assume.assumeTrue("Skipped on non-Windows: !VAR! pattern only active on Windows", OSUtil.isWindows());

        CapturingLogger log = new CapturingLogger();
        File serverDir = tmp.newFolder("server-win-multi");

        String envContent = "EXP_VAR=TEST\nEXP_VAR3=WINDOWS\nCOMBINED=!EXP_VAR!_!EXP_VAR3!\n";
        buildDoc(log, serverDir, envContent).processServerEnv();

        String expectedMsg1 = "Resolved environment variable \"EXP_VAR\" in path \"!EXP_VAR!_!EXP_VAR3!\" to \"TEST\"";
        String expectedMsg2 = "Resolved environment variable \"EXP_VAR3\" in path \"!EXP_VAR!_!EXP_VAR3!\" to \"WINDOWS\"";
        String expectedSummary = "Resolved path \"!EXP_VAR!_!EXP_VAR3!\" to \"TEST_WINDOWS\"";
        assertTrue("Expected first log message not found.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedMsg1)));
        assertTrue("Expected second log message not found.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedMsg2)));
        assertTrue("Expected summary log message not found.\nActual: " + log.infoMessages,
                log.infoMessages.stream().anyMatch(m -> m.equals(expectedSummary)));
    }
}
