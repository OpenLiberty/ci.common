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
package io.openliberty.tools.common.plugins.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jline.reader.LineReader;
import org.jline.terminal.TerminalBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.openliberty.tools.common.ai.util.Utils;

public class DevUtilRunTestThreadTest extends BaseDevUtilTest {

    private class RunTestThreadUtil extends DevTestUtil {
        public int counter = 0;

        public RunTestThreadUtil(boolean hotTests) throws IOException {
            super(null, null, null, null, null, null, hotTests, false);
        }

        @Override
        public void runTests(boolean waitForApplicationUpdate, int messageOccurrences, ThreadPoolExecutor executor,
                boolean forceSkipTests, boolean forceSkipUTs, boolean forceSkipITs, File buildFile, String projectName) {
            counter++;
        }
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testMultiModuleTestThread() throws Exception {
        RunTestThreadUtil util = new RunTestThreadUtil(false);
        assertEquals(0, util.counter);

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(1, true));

        // only one thread should be created when multiple build files are passed in
        final File tempBuildFile1 = tempFolder.newFile("pom1.xml");
        final File tempBuildFile2 = tempFolder.newFile("pom2.xml");
        util.runTestThread(false, executor, -1, false, true, tempBuildFile1, tempBuildFile2);
        assertEquals(1, executor.getPoolSize());

        // shutdown executor
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testRunManualTestThread() throws Exception {
        RunTestThreadUtil util = new RunTestThreadUtil(false);
        assertEquals(0, util.counter);

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1, true));

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
        RunTestThreadUtil util = new RunTestThreadUtil(true);
        assertEquals(0, util.counter);

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1, true));

        // manualInvocation=false and hotTests=true should start a thread
        util.runTestThread(false, executor, -1, false, false);
        assertEquals(1, executor.getPoolSize());

        // shutdown executor
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // verify that runTests() was called once
        assertEquals(1, util.counter);
    }

    @Test
    public void testRunHotkeyReaderThread() throws Exception {
        RunTestThreadUtil util = new RunTestThreadUtil(false);
        assertEquals(0, util.counter);

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1, true));

        LineReader previousReader = Utils.reader;
        try {
            // replace line reader using a terminal on piped input stream
            PipedOutputStream out = new PipedOutputStream();
            PipedInputStream in = new PipedInputStream(out);
            Utils.reader = null;
            Utils.getReader(
                TerminalBuilder.builder()
                        .streams(in, OutputStream.nullOutputStream())
                        .build());
        
            // run test on newline input
            String enter = "\n";
            out.write(enter.getBytes());
            util.runHotkeyReaderThread(executor);

            // wait for executor to pickup test job
            int timeout = 5000;
            int waited = 0;
            while (executor.getPoolSize() == 0 && waited <= timeout) {
                int sleep = 10;
                Thread.sleep(sleep);
                waited += sleep;
            }
            if (waited > timeout) {
                fail("Timed out waiting for new line input");
            }
            assertEquals(1, executor.getPoolSize());

            // shutdown executor
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // verify that runTests() was called once
            assertEquals(1, util.counter);
        } finally {
            Utils.reader = previousReader;
        }

    }

}