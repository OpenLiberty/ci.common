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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    private class RunTestThreadUtil extends DevTestUtil {
        public int counter = 0;

        public RunTestThreadUtil(File serverDirectory, boolean hotTests) {
            super(serverDirectory, null, null, null, null, hotTests);
        }

        @Override
        public void runTests(boolean waitForApplicationUpdate, int messageOccurrences, ThreadPoolExecutor executor,
                boolean forceSkipUTs) {
            counter++;
        }
    }

    @Test
    public void testRunManualTestThread() throws Exception {
        RunTestThreadUtil util = new RunTestThreadUtil(serverDirectory, false);

        assertEquals(0, util.counter);

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1, true));
        assertEquals(0, executor.getPoolSize());

        // manualInvocation=false should not start a thread
        util.runTestThread(false, executor, -1, false, false);
        assertEquals(0, executor.getPoolSize());

        // manualInvocation=true should start a thread
        util.runTestThread(false, executor, -1, false, true);
        assertEquals(1, executor.getPoolSize());
        
        // shutdown executor
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // verify that runTests() was called once
        assertEquals(1, util.counter);
    }

    @Test
    public void testRunHotTestThread() throws Exception {
        RunTestThreadUtil util = new RunTestThreadUtil(serverDirectory, true);

        assertEquals(0, util.counter);

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1, true));
        assertEquals(0, executor.getPoolSize());

        // manualInvocation=false and hotTests=true should start a thread
        util.runTestThread(false, executor, -1, false, false);
        assertEquals(1, executor.getPoolSize());

        // shutdown executor
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // verify that runTests() was called once
        assertEquals(1, util.counter);
    }

}