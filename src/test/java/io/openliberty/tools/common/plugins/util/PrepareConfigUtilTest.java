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
import java.io.IOException;

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

        // Verify the directory structure
        File tmpDir = new File(buildDirectory, "tmp");
        assertTrue("tmp directory should exist", tmpDir.exists());

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
        assertEquals("Mock install directory should be build/tmp/wlp",
                new File(new File(buildDirectory, "tmp"), "wlp"), mockInstallDir);
    }

    /**
     * Test getMockUserDirectory method.
     */
    @Test
    public void testGetMockUserDirectory() {
        File mockUserDir = PrepareConfigUtil.getMockUserDirectory(buildDirectory);

        assertNotNull("Mock user directory should not be null", mockUserDir);
        assertEquals("Mock user directory should be build/tmp/wlp/usr",
                new File(new File(new File(buildDirectory, "tmp"), "wlp"), "usr"), mockUserDir);
    }

    /**
     * Test getMockServersDirectory method.
     */
    @Test
    public void testGetMockServersDirectory() {
        File mockServersDir = PrepareConfigUtil.getMockServersDirectory(buildDirectory);

        assertNotNull("Mock servers directory should not be null", mockServersDir);
        assertEquals("Mock servers directory should be build/tmp/wlp/usr/servers",
                new File(new File(new File(new File(buildDirectory, "tmp"), "wlp"), "usr"), "servers"),
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
        File tmpDir = new File(buildDirectory, "tmp");
        assertFalse("tmp directory should not exist after cleanup", tmpDir.exists());
    }

    /**
     * Test cleanMockServerStructure when structure doesn't exist.
     * Should not throw an exception.
     */
    @Test
    public void testCleanMockServerStructureWhenNotExists() throws IOException {
        File tmpDir = new File(buildDirectory, "tmp");
        assertFalse("tmp directory should not exist initially", tmpDir.exists());

        // Should not throw exception
        PrepareConfigUtil.cleanMockServerStructure(buildDirectory);

        assertFalse("tmp directory should still not exist", tmpDir.exists());
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
}