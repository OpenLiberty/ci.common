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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ServerStatusUtilTest {

    public static final String RESOURCES_DIR = "src/test/resources";
    
    private static final String RESOURCES_INSTALL_DIR = RESOURCES_DIR + "/serverStatus";
    
    public File installDir;
    
    public FileLock lock;
    
    public RandomAccessFile sLock;
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Before
    public void setupInstallDir() throws IOException {
        installDir = temp.newFolder();
        File src = new File(RESOURCES_INSTALL_DIR);
        FileUtils.copyDirectory(src, installDir);
        new File(installDir, "bin/server").setExecutable(true);
        if (OSUtil.isWindows()) {
            sLock = new RandomAccessFile(installDir.getPath() + 
                     "/outputDir/defaultServer/workarea/.sLock", "rw");
            lock = sLock.getChannel().tryLock();
        }
    }
    
    @After
    public void release() throws IOException {
        if (OSUtil.isWindows()) {
            lock.release();
            sLock.close();
        }
    }
    
    @Test
    public void testRunningServerStatus() throws Exception {
        File outputDir = new File(installDir, "outputDir");
        assertTrue(ServerStatusUtil.isServerRunning(installDir, outputDir, "defaultServer"));
    }
    
    @Test
    public void testKilledServerStatus() throws Exception {
        File outputDir = new File(installDir, "outputDir");
        assertFalse(ServerStatusUtil.isServerRunning(installDir, outputDir, "stoppedServer"));
    }
    
    @Test
    public void testStoppedServerStatus() throws Exception {
        File outputDir = new File(installDir, "usr/servers");
        assertFalse(ServerStatusUtil.isServerRunning(installDir, outputDir, "defaultServer"));
    }
}
