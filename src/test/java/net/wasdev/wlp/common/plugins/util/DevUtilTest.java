/**
 * (C) Copyright IBM Corporation 2019.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DevUtilTest extends BaseDevUtilTest {

    File serverDirectory;

    @Before
    public void setUp() throws IOException {
        serverDirectory = Files.createTempDirectory("serverDirectory").toFile();
    }

    @After
    public void tearDown() {
        if (serverDirectory != null && serverDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(serverDirectory);
            } catch (IOException e) {
                // nothing else can be done
            }
        }
    }

    @Test
    public void testCleanupServerEnv() throws Exception {
        DevUtil util = new DevTestUtil(serverDirectory, null, null, null, null, false);

        File serverEnv = new File(serverDirectory, "server.env");
        Files.write(serverEnv.toPath(), "temp".getBytes());

        assertTrue(serverEnv.exists());

        util.cleanUpServerEnv();

        // verify the temporary server.env file was deleted
        assertFalse(serverEnv.exists());
    }

    @Test
    public void testCleanupServerEnvBak() throws Exception {
        DevUtil util = new DevTestUtil(serverDirectory, null, null, null, null, false);

        File serverEnv = new File(serverDirectory, "server.env");
        Files.write(serverEnv.toPath(), "temp".getBytes());
        File serverEnvBak = new File(serverDirectory, "server.env.bak");
        Files.write(serverEnvBak.toPath(), "backup".getBytes());

        assertTrue(serverEnv.exists());
        assertTrue(serverEnvBak.exists());

        util.cleanUpServerEnv();
        
        // verify the backup env file was restored as server.env
        assertTrue(serverEnv.exists());
        String serverEnvContents = new String(Files.readAllBytes(serverEnv.toPath()));
        assertEquals("backup", serverEnvContents);
        assertFalse(serverEnvBak.exists());
    }

}