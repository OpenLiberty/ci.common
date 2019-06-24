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
    File configDirectory;

    @Before
    public void setUp() throws IOException {
        serverDirectory = Files.createTempDirectory("serverDirectory").toFile();
        configDirectory = Files.createTempDirectory("configDirectory").toFile();
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

    @Test
    public void testReadFileToString() throws Exception {
        DevUtil util = new DevTestUtil(null, null, null, null, null, false);

        File serverDirectory = Files.createTempDirectory("serverDirectory").toFile();
        File tempFile = new File(serverDirectory, "temp.txt");
        Files.write(tempFile.toPath(), "temp".getBytes());
        String fileString = util.readFile(tempFile);

        assertTrue(fileString.equals("temp"));

        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    public void testCopyConfigFolder() throws Exception {
    }

    @Test
    public void testCopyFile() throws Exception {
        DevUtil util = new DevTestUtil(null, null, null, null, null, false);

        File srcDir = Files.createTempDirectory("src").toFile();
        File targetDir = Files.createTempDirectory("target").toFile();
        File configFile = new File(srcDir, "config.xml");
        Files.write(configFile.toPath(), "temp".getBytes());

        util.copyFile(configFile, srcDir, targetDir, "server.xml");

        File targetFile = new File(targetDir, "server.xml");
        assertTrue(targetFile.exists());

        // clean up
        if (srcDir.exists()) {
            FileUtils.deleteDirectory(srcDir);
        }
        if (targetDir.exists()) {
            FileUtils.deleteDirectory(targetDir);
        }
    }

    @Test
    public void testDeleteFile() throws Exception {
        DevUtil util = new DevTestUtil(null, null, null, null, null, false);

        File srcDir = Files.createTempDirectory("src").toFile();
        File targetDir = Files.createTempDirectory("target").toFile();

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

        // clean up
        if (srcDir.exists()) {
            FileUtils.deleteDirectory(srcDir);
        }
        if (targetDir.exists()) {
            FileUtils.deleteDirectory(targetDir);
        }
    }

    @Test
    public void testCleanTargetDir() throws Exception {
        DevUtil util = new DevTestUtil(null, null, null, null, null, false);

        File outputDirectory = Files.createTempDirectory("outputDirectory").toFile();
        File tempClass = new File(outputDirectory, "temp.class");
        Files.write(tempClass.toPath(), "temp".getBytes());

        assertTrue(tempClass.exists());

        util.cleanTargetDir(outputDirectory);

        assertFalse(tempClass.exists());

        outputDirectory = Files.createTempDirectory("outputDirectory").toFile();
        File tempTextFile = new File(outputDirectory, "temp.txt");
        Files.write(tempTextFile.toPath(), "temp".getBytes());

        assertTrue(tempTextFile.exists());

        util.cleanTargetDir(outputDirectory);
        assertTrue(outputDirectory.exists());

        if (outputDirectory.exists()) {
            FileUtils.deleteDirectory(outputDirectory);
        }
    }

    @Test
    public void testGetFileFromConfigDirectory() throws Exception {
        DevUtil util = new DevTestUtil(null, null, null, this.configDirectory, null, false);
        
        File tempTextFile = new File(configDirectory, "temp.txt");
        Files.write(tempTextFile.toPath(), "temp".getBytes());
        
        File configFile = util.getFileFromConfigDirectory("temp.txt");
        assertTrue(configFile.exists());
        
        if (configDirectory.exists()){
            FileUtils.deleteDirectory(configDirectory);
        }
    }
    
    @Test
    public void testDeleteJavaFile() throws Exception {
        DevUtil util = new DevTestUtil(null, null, null, null, null, false);

        File compileSourceRoot = Files.createTempDirectory("compileSourceRoot").toFile();
        File javaFile = new File(compileSourceRoot, "temp.java");
        Files.write(javaFile.toPath(), "temp".getBytes());
        File classesDir = Files.createTempDirectory("classesDir").toFile();
        File javaClass = new File(classesDir, "temp.class");
        Files.write(javaClass.toPath(), "temp".getBytes());
        
        assertTrue(javaFile.exists());
        assertTrue(javaClass.exists());
        
        util.deleteJavaFile(javaFile, classesDir, compileSourceRoot);
        assertFalse(javaClass.exists());
    }
    
}