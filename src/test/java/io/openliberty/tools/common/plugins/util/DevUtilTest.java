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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.openliberty.tools.common.plugins.util.DevUtil;

public class DevUtilTest extends BaseDevUtilTest {

    File serverDirectory;
    File configDirectory;
    File srcDir;
    File targetDir;
    DevUtil util;

    @Before
    public void setUp() throws IOException {
        serverDirectory = Files.createTempDirectory("serverDirectory").toFile();
        configDirectory = Files.createTempDirectory("configDirectory").toFile();
        srcDir = Files.createTempDirectory("src").toFile();
        targetDir = Files.createTempDirectory("target").toFile();
        util = getNewDevUtil(serverDirectory);
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
        if (configDirectory != null && configDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(configDirectory);
            } catch (IOException e) {
                // nothing else can be done
            }
        }
        if (srcDir != null && srcDir.exists()) {
            try {
                FileUtils.deleteDirectory(srcDir);
            } catch (IOException e) {
                // nothing else can be done
            }
        }
        if (targetDir != null && targetDir.exists()) {
            try {
                FileUtils.deleteDirectory(targetDir);
            } catch (IOException e) {
                // nothing else can be done
            }
        }
    }

    @Test
    public void testCleanupServerEnv() throws Exception {
        File serverEnv = new File(serverDirectory, "server.env");
        Files.write(serverEnv.toPath(), "temp".getBytes());

        assertTrue(serverEnv.exists());

        util.cleanUpServerEnv();

        // verify the temporary server.env file was deleted
        assertFalse(serverEnv.exists());
    }

    @Test
    public void testCleanupServerEnvBak() throws Exception {
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
        assertEquals(serverEnvContents, "backup");
        assertFalse(serverEnvBak.exists());
    }

    @Test
    public void testFindAvailablePort() throws Exception {
        // prefer a port that is known to be available
        int preferredPort = getRandomPort();

        // verify that findAvailablePort gets the preferred port
        int availablePort = util.findAvailablePort(preferredPort);
        assertEquals(preferredPort, availablePort);

        // bind to it
        ServerSocket serverSocket = null;
        ServerSocket serverSocket2 = null;
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(false);
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName(null), availablePort), 1);

            // previous port is bound, so calling findAvailablePort again should get another port
            int availablePort2 = util.findAvailablePort(preferredPort);
            assertNotEquals(availablePort, availablePort2);

            // unbind the port
            serverSocket.close();

            // calling findAvailablePort again should return the previous port which was cached, even though the preferred port is available
            int availablePort3 = util.findAvailablePort(preferredPort);
            assertEquals(availablePort2, availablePort3);

            // bind to the previous port
            serverSocket2 = new ServerSocket();
            serverSocket2.setReuseAddress(false);
            serverSocket2.bind(new InetSocketAddress(InetAddress.getByName(null), availablePort2), 1);

            // previous port is also bound, so calling findAvailablePort again should get another port
            int availablePort4 = util.findAvailablePort(preferredPort);
            assertNotEquals(availablePort2, availablePort4);
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (serverSocket2 != null) {
                serverSocket2.close();
            }
        }
    }

    private int getRandomPort() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        try {
            return serverSocket.getLocalPort();
        } finally {
            serverSocket.close();
        }
    }

    @Test
    public void testGetDebugEnvironmentVariables() throws Exception {
        int port = getRandomPort();
        Map<String, String> map = util.getDebugEnvironmentVariables(port);
        assertEquals("n", map.get("WLP_DEBUG_SUSPEND"));
        assertEquals(String.valueOf(port), map.get("WLP_DEBUG_ADDRESS"));
    }
    
    @Test
    public void testEnableServerDebug() throws Exception {
        int port = getRandomPort();
        util.enableServerDebug(port);
        
        File serverEnv = new File(serverDirectory, "server.env");
        BufferedReader reader = new BufferedReader(new FileReader(serverEnv));
        
        assertEquals("WLP_DEBUG_SUSPEND=n", reader.readLine());
        assertEquals("WLP_DEBUG_ADDRESS=" + port, reader.readLine());
        
        reader.close();
    }
    
    @Test
    public void testEnableServerDebugBackup() throws Exception {
        String serverEnvContent = "abc=123\nxyz=321";
        
        File serverEnv = new File(serverDirectory, "server.env");
        serverEnv.createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(serverEnv));
        writer.write(serverEnvContent);
        writer.close();
        
        int port = getRandomPort();
        util.enableServerDebug(port);
        File serverEnvBackup = new File(serverDirectory, "server.env.bak");
        assertTrue(serverEnvBackup.exists());
        
        BufferedReader reader = new BufferedReader(new FileReader(serverEnv));
        try {
            assertEquals("abc=123", reader.readLine());
            assertEquals("xyz=321", reader.readLine());
            assertEquals("WLP_DEBUG_SUSPEND=n", reader.readLine());
            assertEquals("WLP_DEBUG_ADDRESS=" + port, reader.readLine());    
        } finally {
            reader.close();
        }
    }

    @Test
    public void testEnableServerDebugBackupAlreadyExists() throws Exception {
        // create initial server.env
        String serverEnvContent = "abc=123\nxyz=321";
        
        File serverEnv = new File(serverDirectory, "server.env");
        serverEnv.createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(serverEnv));
        writer.write(serverEnvContent);
        writer.close();
        
        // enable debug which makes a backup of the original .env
        int port = getRandomPort();
        util.enableServerDebug(port);
        File serverEnvBackup = new File(serverDirectory, "server.env.bak");
        assertTrue(serverEnvBackup.exists());

        // overwrite server.env with new content
        String serverEnvContent2 = "efg=456\njkl=654";
        writer = new BufferedWriter(new FileWriter(serverEnv));
        writer.write(serverEnvContent2);
        writer.close();
        
        // enable debug again while backup already exists from above
        int newPort = getRandomPort();
        util.enableServerDebug(newPort);
        assertTrue(serverEnvBackup.exists());
        
        // server.env should have the new content plus debug variables
        BufferedReader reader = new BufferedReader(new FileReader(serverEnv));
        try {
            assertEquals("efg=456", reader.readLine());
            assertEquals("jkl=654", reader.readLine());
            assertEquals("WLP_DEBUG_SUSPEND=n", reader.readLine());
            assertEquals("WLP_DEBUG_ADDRESS=" + newPort, reader.readLine());    
        } finally {
            reader.close();
        }

        // .bak should have the new content
        BufferedReader readerBak = new BufferedReader(new FileReader(serverEnvBackup));
        try {
            assertEquals("efg=456", readerBak.readLine());
            assertEquals("jkl=654", readerBak.readLine());
            assertEquals(null, readerBak.readLine());    
        } finally {
            readerBak.close();
        }
    }

    @Test
    public void testReadFileToString() throws Exception {
        File tempFile = new File(serverDirectory, "temp.txt");
        Files.write(tempFile.toPath(), "temp".getBytes());
        String fileString = util.readFile(tempFile);

        assertTrue(fileString.equals("temp"));
    }

    @Test
    public void testCopyFile() throws Exception {
        File configFile = new File(srcDir, "config.xml");
        Files.write(configFile.toPath(), "temp".getBytes());

        util.copyFile(configFile, srcDir, targetDir, "server.xml");

        File targetFile = new File(targetDir, "server.xml");
        assertTrue(targetFile.exists());
    }

    @Test
    public void testDeleteFile() throws Exception {
        File tempSrcFile = new File(srcDir, "temp.txt");
        Files.write(tempSrcFile.toPath(), "temp".getBytes());
        File tempTargetFile = new File(targetDir, "server.xml");
        Files.write(tempTargetFile.toPath(), "temp".getBytes());

        // verify that files have been properly created
        assertTrue(tempSrcFile.exists());
        assertTrue(tempTargetFile.exists());

        util.deleteFile(tempSrcFile, srcDir, targetDir, "server.xml");

        // verify that the target file has been deleted
        assertFalse(tempTargetFile.exists());
    }

    @Test
    public void testCleanTargetDir() throws Exception {
        File tempClass = new File(targetDir, "temp.class");
        Files.write(tempClass.toPath(), "temp".getBytes());

        assertTrue(tempClass.exists());

        util.cleanTargetDir(targetDir);

        // verify that the targetDir has been deleted
        assertFalse(targetDir.exists());

        File tempTextFile = new File(srcDir, "temp.txt");
        Files.write(tempTextFile.toPath(), "temp".getBytes());

        assertTrue(tempTextFile.exists());

        util.cleanTargetDir(srcDir);

        // verify that the srcDir still exists as it contained files other than
        // java classes
        assertTrue(srcDir.exists());
    }

    @Test
    public void testGetFileFromConfigDirectory() throws Exception {
        DevUtil util = new DevTestUtil(null, null, null, this.configDirectory, null, false, false);

        File tempTextFile = new File(configDirectory, "temp.txt");
        Files.write(tempTextFile.toPath(), "temp".getBytes());

        File configFile = util.getFileFromConfigDirectory("temp.txt");
        assertTrue(configFile.exists());
    }

    @Test
    public void testDeleteJavaFile() throws Exception {
        File javaFile = new File(srcDir, "temp.java");
        Files.write(javaFile.toPath(), "temp".getBytes());
        File javaClass = new File(targetDir, "temp.class");
        Files.write(javaClass.toPath(), "temp".getBytes());

        assertTrue(javaFile.exists());
        assertTrue(javaClass.exists());

        util.deleteJavaFile(javaFile, targetDir, srcDir);

        // verify that the corresponding class file has been deleted
        assertFalse(javaClass.exists());
    }

}
