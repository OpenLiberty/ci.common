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
package io.openliberty.tools.common.plugins.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test class for PrepareConfigUtil.
 * Tests the creation and management of mock Liberty server structures.
 */
public class PrepareConfigUtilTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File buildDirectory;
    private static final String SERVER_NAME = "testServer";
    private static final String ANOTHER_SERVER_NAME = "anotherServer";

    @Before
    public void setUp() throws IOException {
        buildDirectory = tempFolder.newFolder("build");
    }

    @After
    public void tearDown() {
        // Cleanup is handled by TemporaryFolder rule
    }

    /**
     * Test creating a mock Liberty server structure.
     * Verifies that all required directories are created.
     */
    @Test
    public void testCreateMockLibertyServerStructure() throws IOException {
        File mockServerDir = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME);

        assertNotNull("Mock server directory should not be null", mockServerDir);
        assertTrue("Mock server directory should exist", mockServerDir.exists());
        assertTrue("Mock server directory should be a directory", mockServerDir.isDirectory());

        // Verify the directory structure using default temp dir name
        File tmpDir = new File(buildDirectory, PrepareConfigUtil.DEFAULT_TEMP_DIR_NAME);
        assertTrue("liberty-var-cache directory should exist", tmpDir.exists());

        File wlpDir = new File(tmpDir, "wlp");
        assertTrue("wlp directory should exist", wlpDir.exists());

        File usrDir = new File(wlpDir, "usr");
        assertTrue("usr directory should exist", usrDir.exists());

        File serversDir = new File(usrDir, "servers");
        assertTrue("servers directory should exist", serversDir.exists());

        File serverDir = new File(serversDir, SERVER_NAME);
        assertTrue("Server directory should exist", serverDir.exists());
        assertEquals("Mock server directory should match expected path", serverDir, mockServerDir);
    }

    /**
     * Test creating a mock Liberty server structure with custom temp directory name.
     */
    @Test
    public void testCreateMockLibertyServerStructureWithCustomTempDir() throws IOException {
        String customTempDir = "my-custom-temp";
        File mockServerDir = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME, customTempDir);

        assertNotNull("Mock server directory should not be null", mockServerDir);
        assertTrue("Mock server directory should exist", mockServerDir.exists());
        assertTrue("Mock server directory should be a directory", mockServerDir.isDirectory());

        // Verify the directory structure with custom temp dir
        File tmpDir = new File(buildDirectory, customTempDir);
        assertTrue("Custom temp directory should exist", tmpDir.exists());

        File wlpDir = new File(tmpDir, "wlp");
        assertTrue("wlp directory should exist", wlpDir.exists());

        File usrDir = new File(wlpDir, "usr");
        assertTrue("usr directory should exist", usrDir.exists());

        File serversDir = new File(usrDir, "servers");
        assertTrue("servers directory should exist", serversDir.exists());

        File serverDir = new File(serversDir, SERVER_NAME);
        assertTrue("Server directory should exist", serverDir.exists());
        assertEquals("Mock server directory should match expected path", serverDir, mockServerDir);
    }

    /**
     * Test creating mock server structure with null build directory.
     * Should throw IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateMockServerStructureWithNullBuildDir() throws IOException {
        PrepareConfigUtil.createMockLibertyServerStructure(null, SERVER_NAME);
    }

    /**
     * Test creating mock server structure with null server name.
     * Should throw IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateMockServerStructureWithNullServerName() throws IOException {
        PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, null);
    }

    /**
     * Test creating mock server structure with empty server name.
     * Should throw IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateMockServerStructureWithEmptyServerName() throws IOException {
        PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, "");
    }

    /**
     * Test creating mock server structure with whitespace-only server name.
     * Should throw IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateMockServerStructureWithWhitespaceServerName() throws IOException {
        PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, "   ");
    }

    /**
     * Test creating mock server structure when it already exists.
     * Should not fail and should return the existing directory.
     */
    @Test
    public void testCreateMockServerStructureWhenAlreadyExists() throws IOException {
        // Create the structure first time
        File mockServerDir1 = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME);
        assertTrue("First creation should succeed", mockServerDir1.exists());

        // Create the structure second time
        File mockServerDir2 = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME);
        assertTrue("Second creation should succeed", mockServerDir2.exists());
        assertEquals("Both calls should return the same directory", mockServerDir1, mockServerDir2);
    }

    /**
     * Test getMockInstallDirectory method.
     */
    @Test
    public void testGetMockInstallDirectory() {
        File mockInstallDir = PrepareConfigUtil.getMockInstallDirectory(buildDirectory);

        assertNotNull("Mock install directory should not be null", mockInstallDir);
        assertEquals("Mock install directory should be build/liberty-var-cache/wlp",
                new File(new File(buildDirectory, PrepareConfigUtil.DEFAULT_TEMP_DIR_NAME), "wlp"), mockInstallDir);
    }

    /**
     * Test getMockInstallDirectory with custom temp directory.
     */
    @Test
    public void testGetMockInstallDirectoryWithCustomTempDir() {
        String customTempDir = "my-temp";
        File mockInstallDir = PrepareConfigUtil.getMockInstallDirectory(buildDirectory, customTempDir);

        assertNotNull("Mock install directory should not be null", mockInstallDir);
        assertEquals("Mock install directory should use custom temp dir",
                new File(new File(buildDirectory, customTempDir), "wlp"), mockInstallDir);
    }

    /**
     * Test getMockUserDirectory method.
     */
    @Test
    public void testGetMockUserDirectory() {
        File mockUserDir = PrepareConfigUtil.getMockUserDirectory(buildDirectory);

        assertNotNull("Mock user directory should not be null", mockUserDir);
        assertEquals("Mock user directory should be build/liberty-var-cache/wlp/usr",
                new File(new File(new File(buildDirectory, PrepareConfigUtil.DEFAULT_TEMP_DIR_NAME), "wlp"), "usr"), mockUserDir);
    }

    /**
     * Test getMockUserDirectory with custom temp directory.
     */
    @Test
    public void testGetMockUserDirectoryWithCustomTempDir() {
        String customTempDir = "my-temp";
        File mockUserDir = PrepareConfigUtil.getMockUserDirectory(buildDirectory, customTempDir);

        assertNotNull("Mock user directory should not be null", mockUserDir);
        assertEquals("Mock user directory should use custom temp dir",
                new File(new File(new File(buildDirectory, customTempDir), "wlp"), "usr"), mockUserDir);
    }

    /**
     * Test getMockServersDirectory method.
     */
    @Test
    public void testGetMockServersDirectory() {
        File mockServersDir = PrepareConfigUtil.getMockServersDirectory(buildDirectory);

        assertNotNull("Mock servers directory should not be null", mockServersDir);
        assertEquals("Mock servers directory should be build/liberty-var-cache/wlp/usr/servers",
                new File(new File(new File(new File(buildDirectory, PrepareConfigUtil.DEFAULT_TEMP_DIR_NAME), "wlp"), "usr"), "servers"),
                mockServersDir);
    }

    /**
     * Test getMockServersDirectory with custom temp directory.
     */
    @Test
    public void testGetMockServersDirectoryWithCustomTempDir() {
        String customTempDir = "my-temp";
        File mockServersDir = PrepareConfigUtil.getMockServersDirectory(buildDirectory, customTempDir);

        assertNotNull("Mock servers directory should not be null", mockServersDir);
        assertEquals("Mock servers directory should use custom temp dir",
                new File(new File(new File(new File(buildDirectory, customTempDir), "wlp"), "usr"), "servers"),
                mockServersDir);
    }

    /**
     * Test getMockServerDirectory method.
     */
    @Test
    public void testGetMockServerDirectory() {
        File mockServerDir = PrepareConfigUtil.getMockServerDirectory(buildDirectory, SERVER_NAME);

        assertNotNull("Mock server directory should not be null", mockServerDir);
        assertTrue("Mock server directory path should end with server name",
                mockServerDir.getPath().endsWith(SERVER_NAME));
    }

    /**
     * Test mockServerStructureExists method when structure does not exist.
     */
    @Test
    public void testMockServerStructureExistsWhenNotExists() {
        boolean exists = PrepareConfigUtil.mockServerStructureExists(buildDirectory, SERVER_NAME);
        assertFalse("Mock server structure should not exist initially", exists);
    }

    /**
     * Test mockServerStructureExists method when structure exists.
     */
    @Test
    public void testMockServerStructureExistsWhenExists() throws IOException {
        // Create the structure
        PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME);

        // Check if it exists
        boolean exists = PrepareConfigUtil.mockServerStructureExists(buildDirectory, SERVER_NAME);
        assertTrue("Mock server structure should exist after creation", exists);
    }

    /**
     * Test creating multiple server structures in the same build directory.
     */
    @Test
    public void testCreateMultipleServerStructures() throws IOException {
        File mockServerDir1 = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME);
        File mockServerDir2 = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, ANOTHER_SERVER_NAME);

        assertTrue("First server directory should exist", mockServerDir1.exists());
        assertTrue("Second server directory should exist", mockServerDir2.exists());
        assertTrue("Server directories should be different", !mockServerDir1.equals(mockServerDir2));

        // Verify both exist
        assertTrue("First server structure should exist",
                PrepareConfigUtil.mockServerStructureExists(buildDirectory, SERVER_NAME));
        assertTrue("Second server structure should exist",
                PrepareConfigUtil.mockServerStructureExists(buildDirectory, ANOTHER_SERVER_NAME));
    }

    /**
     * Test cleanMockServerStructure method.
     */
    @Test
    public void testCleanMockServerStructure() throws IOException {
        // Create the structure
        File mockServerDir = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME);
        assertTrue("Mock server structure should exist before cleanup", mockServerDir.exists());

        // Clean the structure
        PrepareConfigUtil.cleanMockServerStructure(buildDirectory);

        // Verify it's cleaned
        assertFalse("Mock server structure should not exist after cleanup", mockServerDir.exists());
        File tmpDir = new File(buildDirectory, PrepareConfigUtil.DEFAULT_TEMP_DIR_NAME);
        assertFalse("liberty-var-cache directory should not exist after cleanup", tmpDir.exists());
    }

    /**
     * Test cleanMockServerStructure with custom temp directory.
     */
    @Test
    public void testCleanMockServerStructureWithCustomTempDir() throws IOException {
        String customTempDir = "my-temp";
        // Create the structure with custom temp dir
        File mockServerDir = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME, customTempDir);
        assertTrue("Mock server structure should exist before cleanup", mockServerDir.exists());

        // Clean the structure
        PrepareConfigUtil.cleanMockServerStructure(buildDirectory, customTempDir);

        // Verify it's cleaned
        assertFalse("Mock server structure should not exist after cleanup", mockServerDir.exists());
        File tmpDir = new File(buildDirectory, customTempDir);
        assertFalse("Custom temp directory should not exist after cleanup", tmpDir.exists());
    }

    /**
     * Test cleanMockServerStructure when structure doesn't exist.
     * Should not throw an exception.
     */
    @Test
    public void testCleanMockServerStructureWhenNotExists() throws IOException {
        File tmpDir = new File(buildDirectory, PrepareConfigUtil.DEFAULT_TEMP_DIR_NAME);
        assertFalse("liberty-var-cache directory should not exist initially", tmpDir.exists());

        // Should not throw exception
        PrepareConfigUtil.cleanMockServerStructure(buildDirectory);

        assertFalse("liberty-var-cache directory should still not exist", tmpDir.exists());
    }

    /**
     * Test that directory paths are consistent across utility methods.
     */
    @Test
    public void testDirectoryPathConsistency() throws IOException {
        File mockServerDir = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME);
        File expectedServerDir = PrepareConfigUtil.getMockServerDirectory(buildDirectory, SERVER_NAME);

        assertEquals("Created server directory should match getMockServerDirectory result",
                expectedServerDir, mockServerDir);

        // Verify parent directories
        File mockInstallDir = PrepareConfigUtil.getMockInstallDirectory(buildDirectory);
        File mockUserDir = PrepareConfigUtil.getMockUserDirectory(buildDirectory);
        File mockServersDir = PrepareConfigUtil.getMockServersDirectory(buildDirectory);

        assertEquals("User directory parent should be install directory",
                mockInstallDir, mockUserDir.getParentFile());
        assertEquals("Servers directory parent should be user directory",
                mockUserDir, mockServersDir.getParentFile());
        assertEquals("Server directory parent should be servers directory",
                mockServersDir, mockServerDir.getParentFile());
    }

    /**
     * Test creating server structure with special characters in server name.
     */
    @Test
    public void testCreateServerStructureWithSpecialCharacters() throws IOException {
        String specialServerName = "test-server_123";
        File mockServerDir = PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, specialServerName);

        assertTrue("Server directory with special characters should be created", mockServerDir.exists());
        assertTrue("Server directory name should contain special characters",
                mockServerDir.getName().equals(specialServerName));
    }

    /**
     * Test getBuildFileModificationTime with Maven pom.xml.
     */
    @Test
    public void testGetBuildFileModificationTimeWithMaven() throws IOException {
        File pomFile = new File(buildDirectory, "pom.xml");
        pomFile.createNewFile();
        
        Long modTime = PrepareConfigUtil.getBuildFileModificationTime(buildDirectory);
        
        assertNotNull("Modification time should not be null", modTime);
        assertTrue("Modification time should be positive", modTime > 0);
    }

    /**
     * Test getBuildFileModificationTime with Gradle build.gradle.
     */
    @Test
    public void testGetBuildFileModificationTimeWithGradle() throws IOException {
        File gradleFile = new File(buildDirectory, "build.gradle");
        gradleFile.createNewFile();
        
        Long modTime = PrepareConfigUtil.getBuildFileModificationTime(buildDirectory);
        
        assertNotNull("Modification time should not be null", modTime);
        assertTrue("Modification time should be positive", modTime > 0);
    }

    /**
     * Test getBuildFileModificationTime with Gradle build.gradle.kts.
     */
    @Test
    public void testGetBuildFileModificationTimeWithGradleKts() throws IOException {
        File gradleKtsFile = new File(buildDirectory, "build.gradle.kts");
        gradleKtsFile.createNewFile();
        
        Long modTime = PrepareConfigUtil.getBuildFileModificationTime(buildDirectory);
        
        assertNotNull("Modification time should not be null", modTime);
        assertTrue("Modification time should be positive", modTime > 0);
    }

    /**
     * Test getBuildFileModificationTime with no build file.
     */
    @Test
    public void testGetBuildFileModificationTimeWithNoBuildFile() {
        Long modTime = PrepareConfigUtil.getBuildFileModificationTime(buildDirectory);
        
        assertEquals("Modification time should be null when no build file exists", null, modTime);
    }

    /**
     * Test getBuildFileModificationTime with null directory.
     */
    @Test
    public void testGetBuildFileModificationTimeWithNullDirectory() {
        Long modTime = PrepareConfigUtil.getBuildFileModificationTime(null);
        
        assertEquals("Modification time should be null for null directory", null, modTime);
    }

    /**
     * Test getBuildFileModificationTime prefers pom.xml over build.gradle.
     */
    @Test
    public void testGetBuildFileModificationTimePrefersMaven() throws IOException, InterruptedException {
        File pomFile = new File(buildDirectory, "pom.xml");
        pomFile.createNewFile();
        
        // Sleep to ensure different timestamps
        Thread.sleep(10);
        
        File gradleFile = new File(buildDirectory, "build.gradle");
        gradleFile.createNewFile();
        
        Long modTime = PrepareConfigUtil.getBuildFileModificationTime(buildDirectory);
        Long pomTime = pomFile.lastModified();
        
        assertNotNull("Modification time should not be null", modTime);
        assertEquals("Should return pom.xml modification time", pomTime, modTime);
    }

    /**
     * Test getConfigFilePath with Maven target directory.
     */
    @Test
    public void testGetConfigFilePathWithMaven() throws IOException {
        File targetDir = new File(buildDirectory, "target");
        targetDir.mkdirs();
        File configFile = new File(targetDir, "liberty-plugin-config.xml");
        configFile.createNewFile();
        
        File result = PrepareConfigUtil.getConfigFilePath(buildDirectory);
        
        assertNotNull("Config file path should not be null", result);
        assertTrue("Config file should exist", result.exists());
        assertEquals("Should return Maven config file", configFile, result);
    }

    /**
     * Test getConfigFilePath with Gradle build directory.
     */
    @Test
    public void testGetConfigFilePathWithGradle() throws IOException {
        File buildDir = new File(buildDirectory, "build");
        buildDir.mkdirs();
        File configFile = new File(buildDir, "liberty-plugin-config.xml");
        configFile.createNewFile();
        
        File result = PrepareConfigUtil.getConfigFilePath(buildDirectory);
        
        assertNotNull("Config file path should not be null", result);
        assertTrue("Config file should exist", result.exists());
        assertEquals("Should return Gradle config file", configFile, result);
    }

    /**
     * Test getConfigFilePath with no config file (returns default Maven path).
     */
    @Test
    public void testGetConfigFilePathWithNoConfigFile() {
        File result = PrepareConfigUtil.getConfigFilePath(buildDirectory);
        
        assertNotNull("Config file path should not be null", result);
        assertFalse("Config file should not exist", result.exists());
        assertTrue("Should return Maven target path by default", 
                result.getPath().contains("target"));
    }

    /**
     * Test getConfigFilePath with null directory.
     */
    @Test
    public void testGetConfigFilePathWithNullDirectory() {
        File result = PrepareConfigUtil.getConfigFilePath(null);
        
        assertEquals("Config file path should be null for null directory", null, result);
    }

    /**
     * Test getConfigFilePath prefers Maven over Gradle.
     */
    @Test
    public void testGetConfigFilePathPrefersMaven() throws IOException {
        // Create both Maven and Gradle config files
        File targetDir = new File(buildDirectory, "target");
        targetDir.mkdirs();
        File mavenConfig = new File(targetDir, "liberty-plugin-config.xml");
        mavenConfig.createNewFile();
        
        File buildDir = new File(buildDirectory, "build");
        buildDir.mkdirs();
        File gradleConfig = new File(buildDir, "liberty-plugin-config.xml");
        gradleConfig.createNewFile();
        
        File result = PrepareConfigUtil.getConfigFilePath(buildDirectory);
        
        assertEquals("Should prefer Maven config file", mavenConfig, result);
    }

    /**
     * Test isMockServerInConfig with mock server reference.
     */
    @Test
    public void testIsMockServerInConfigWithMockServer() throws IOException {
        File configFile = new File(buildDirectory, "liberty-plugin-config.xml");
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<liberty-plugin-config>\n" +
                "  <installDirectory>/path/to/.libertyls-var-cache/wlp</installDirectory>\n" +
                "</liberty-plugin-config>";
        Files.write(configFile.toPath(), content.getBytes());
        
        boolean result = PrepareConfigUtil.isMockServerInConfig(configFile);
        
        assertTrue("Should detect mock server reference", result);
    }

    /**
     * Test isMockServerInConfig with custom temp directory reference.
     */
    @Test
    public void testIsMockServerInConfigWithCustomTempDir() throws IOException {
        String customTempDir = "my-custom-temp";
        File configFile = new File(buildDirectory, "liberty-plugin-config.xml");
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<liberty-plugin-config>\n" +
                "  <installDirectory>/path/to/" + customTempDir + "/wlp</installDirectory>\n" +
                "</liberty-plugin-config>";
        Files.write(configFile.toPath(), content.getBytes());
        
        boolean result = PrepareConfigUtil.isMockServerInConfig(configFile, customTempDir);
        
        assertTrue("Should detect custom temp dir reference", result);
    }

    /**
     * Test isMockServerInConfig without mock server reference.
     */
    @Test
    public void testIsMockServerInConfigWithoutMockServer() throws IOException {
        File configFile = new File(buildDirectory, "liberty-plugin-config.xml");
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<liberty-plugin-config>\n" +
                "  <installDirectory>/path/to/wlp</installDirectory>\n" +
                "</liberty-plugin-config>";
        Files.write(configFile.toPath(), content.getBytes());
        
        boolean result = PrepareConfigUtil.isMockServerInConfig(configFile);
        
        assertFalse("Should not detect mock server reference", result);
    }

    /**
     * Test isMockServerInConfig with null file.
     */
    @Test
    public void testIsMockServerInConfigWithNullFile() {
        boolean result = PrepareConfigUtil.isMockServerInConfig(null);
        
        assertFalse("Should return false for null file", result);
    }

    /**
     * Test isMockServerInConfig with non-existent file.
     */
    @Test
    public void testIsMockServerInConfigWithNonExistentFile() {
        File configFile = new File(buildDirectory, "non-existent.xml");
        
        boolean result = PrepareConfigUtil.isMockServerInConfig(configFile);
        
        assertFalse("Should return false for non-existent file", result);
    }

    /**
     * Test validateMockServerStructure with valid structure.
     */
    @Test
    public void testValidateMockServerStructureWithValidStructure() throws IOException {
        PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME);
        
        boolean result = PrepareConfigUtil.validateMockServerStructure(buildDirectory, SERVER_NAME);
        
        assertTrue("Should validate successfully with complete structure", result);
    }

    /**
     * Test validateMockServerStructure with missing structure.
     */
    @Test
    public void testValidateMockServerStructureWithMissingStructure() {
        boolean result = PrepareConfigUtil.validateMockServerStructure(buildDirectory, SERVER_NAME);
        
        assertFalse("Should fail validation when structure doesn't exist", result);
    }

    /**
     * Test validateMockServerStructure with null build directory.
     */
    @Test
    public void testValidateMockServerStructureWithNullBuildDir() {
        boolean result = PrepareConfigUtil.validateMockServerStructure(null, SERVER_NAME);
        
        assertFalse("Should fail validation with null build directory", result);
    }

    /**
     * Test validateMockServerStructure with null server name.
     */
    @Test
    public void testValidateMockServerStructureWithNullServerName() {
        boolean result = PrepareConfigUtil.validateMockServerStructure(buildDirectory, null);
        
        assertFalse("Should fail validation with null server name", result);
    }

    /**
     * Test validateMockServerStructure with empty server name.
     */
    @Test
    public void testValidateMockServerStructureWithEmptyServerName() {
        boolean result = PrepareConfigUtil.validateMockServerStructure(buildDirectory, "");
        
        assertFalse("Should fail validation with empty server name", result);
    }

    /**
     * Test validateMockServerStructure with incomplete structure (missing temp directory).
     */
    @Test
    public void testValidateMockServerStructureWithIncompleteStructure() throws IOException {
        // Create only the server directory without proper parent structure
        File serverDir = new File(buildDirectory, "servers/" + SERVER_NAME);
        serverDir.mkdirs();
        
        boolean result = PrepareConfigUtil.validateMockServerStructure(buildDirectory, SERVER_NAME);
        
        assertFalse("Should fail validation with incomplete structure", result);
    }

    /**
     * Test validateMockServerStructure with custom temp directory.
     */
    @Test
    public void testValidateMockServerStructureWithCustomTempDir() throws IOException {
        String customTempDir = "my-temp";
        PrepareConfigUtil.createMockLibertyServerStructure(buildDirectory, SERVER_NAME, customTempDir);
        
        boolean result = PrepareConfigUtil.validateMockServerStructure(buildDirectory, SERVER_NAME, customTempDir);
        
        assertTrue("Should validate successfully with custom temp dir", result);
    }

    /**
     * Test that default constant is set correctly.
     */
    @Test
    public void testDefaultTempDirNameConstant() {
        assertEquals("Default temp dir name should be .libertyls-var-cache",
                ".libertyls-var-cache", PrepareConfigUtil.DEFAULT_TEMP_DIR_NAME);
    }
}