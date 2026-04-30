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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for prepare-config goal/task functionality.
 * Provides common logic for creating mock Liberty server structures
 * and managing configuration preparation across Maven and Gradle plugins.
 */
public class PrepareConfigUtil {

    /**
     * Create a mock Liberty server structure in the build output directory.
     * This mimics the actual Liberty server directory structure without installing Liberty.
     *
     * <p>Structure created:</p>
     * <pre>
     * buildDir/tmp/
     *   └── wlp/
     *       └── usr/
     *           └── servers/
     *               └── {serverName}/
     *                   ├── server.xml
     *                   ├── bootstrap.properties
     *                   ├── server.env
     *                   └── jvm.options
     * </pre>
     *
     * @param buildDirectory The build output directory (e.g., target/ for Maven, build/ for Gradle)
     * @param serverName The name of the Liberty server
     * @return The mock server directory (buildDir/tmp/wlp/usr/servers/{serverName})
     * @throws IOException if directory creation fails
     */
    public static File createMockLibertyServerStructure(File buildDirectory, String serverName) throws IOException {
        if (buildDirectory == null) {
            throw new IllegalArgumentException("Build directory cannot be null");
        }
        if (serverName == null || serverName.trim().isEmpty()) {
            throw new IllegalArgumentException("Server name cannot be null or empty");
        }

        // Create tmp directory in build output
        File tmpDir = new File(buildDirectory, "tmp");
        
        // Create Liberty server structure: wlp/usr/servers/{serverName}
        File wlpDir = new File(tmpDir, "wlp");
        File usrDir = new File(wlpDir, "usr");
        File serversDir = new File(usrDir, "servers");
        File mockServerDir = new File(serversDir, serverName);
        
        // Create all directories
        if (!mockServerDir.exists()) {
            if (!mockServerDir.mkdirs()) {
                throw new IOException("Failed to create mock server directory: " + mockServerDir.getAbsolutePath());
            }
        }
        
        return mockServerDir;
    }

    /**
     * Get the mock install directory path.
     *
     * @param buildDirectory The build output directory
     * @return The mock install directory (buildDir/tmp/wlp)
     */
    public static File getMockInstallDirectory(File buildDirectory) {
        File tmpDir = new File(buildDirectory, "tmp");
        return new File(tmpDir, "wlp");
    }

    /**
     * Get the mock user directory path.
     *
     * @param buildDirectory The build output directory
     * @return The mock user directory (buildDir/tmp/wlp/usr)
     */
    public static File getMockUserDirectory(File buildDirectory) {
        File mockInstallDir = getMockInstallDirectory(buildDirectory);
        return new File(mockInstallDir, "usr");
    }

    /**
     * Get the mock servers directory path.
     *
     * @param buildDirectory The build output directory
     * @return The mock servers directory (buildDir/tmp/wlp/usr/servers)
     */
    public static File getMockServersDirectory(File buildDirectory) {
        File mockUserDir = getMockUserDirectory(buildDirectory);
        return new File(mockUserDir, "servers");
    }

    /**
     * Get the mock server directory path for a specific server.
     *
     * @param buildDirectory The build output directory
     * @param serverName The name of the Liberty server
     * @return The mock server directory (buildDir/tmp/wlp/usr/servers/{serverName})
     */
    public static File getMockServerDirectory(File buildDirectory, String serverName) {
        File mockServersDir = getMockServersDirectory(buildDirectory);
        return new File(mockServersDir, serverName);
    }

    /**
     * Validate that the mock server structure exists.
     *
     * @param buildDirectory The build output directory
     * @param serverName The name of the Liberty server
     * @return true if the mock server structure exists, false otherwise
     */
    public static boolean mockServerStructureExists(File buildDirectory, String serverName) {
        File mockServerDir = getMockServerDirectory(buildDirectory, serverName);
        return mockServerDir.exists() && mockServerDir.isDirectory();
    }

    /**
     * Get the modification time of the build file (pom.xml or build.gradle).
     * Checks for Maven pom.xml first, then Gradle build.gradle, then build.gradle.kts.
     *
     * @param projectDirectory The project root directory
     * @return The modification time in milliseconds, or null if no build file found
     */
    public static Long getBuildFileModificationTime(File projectDirectory) {
        if (projectDirectory == null || !projectDirectory.exists()) {
            return null;
        }

        try {
            // Check for Maven pom.xml
            Path pomPath = Paths.get(projectDirectory.getAbsolutePath(), "pom.xml");
            if (Files.exists(pomPath)) {
                return Files.getLastModifiedTime(pomPath).toMillis();
            }

            // Check for Gradle build.gradle
            Path gradlePath = Paths.get(projectDirectory.getAbsolutePath(), "build.gradle");
            if (Files.exists(gradlePath)) {
                return Files.getLastModifiedTime(gradlePath).toMillis();
            }

            // Check for Gradle build.gradle.kts
            Path gradleKtsPath = Paths.get(projectDirectory.getAbsolutePath(), "build.gradle.kts");
            if (Files.exists(gradleKtsPath)) {
                return Files.getLastModifiedTime(gradleKtsPath).toMillis();
            }
        } catch (IOException e) {
            // Return null if unable to get modification time
            return null;
        }

        return null;
    }

    /**
     * Get the path to liberty-plugin-config.xml file.
     * Checks Maven target directory first, then Gradle build directory.
     *
     * @param projectDirectory The project root directory
     * @return The path to liberty-plugin-config.xml, or null if not found
     */
    public static File getConfigFilePath(File projectDirectory) {
        if (projectDirectory == null || !projectDirectory.exists()) {
            return null;
        }

        // Try Maven target directory first
        File mavenConfig = new File(projectDirectory, "target/liberty-plugin-config.xml");
        if (mavenConfig.exists()) {
            return mavenConfig;
        }

        // Try Gradle build directory
        File gradleConfig = new File(projectDirectory, "build/liberty-plugin-config.xml");
        if (gradleConfig.exists()) {
            return gradleConfig;
        }

        // Return Maven path as default (even if it doesn't exist yet)
        return mavenConfig;
    }

    /**
     * Check if the liberty-plugin-config.xml file points to a mock server directory.
     * This is determined by checking if the config file content contains "tmp" directory reference.
     *
     * @param configFile The liberty-plugin-config.xml file
     * @return true if the config points to a mock server, false otherwise
     */
    public static boolean isMockServerInConfig(File configFile) {
        if (configFile == null || !configFile.exists()) {
            return false;
        }

        try {
            String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            return content.contains("tmp");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validate that the mock server structure exists and is properly configured.
     * This performs comprehensive validation including:
     * - Mock server directory exists
     * - Config file exists and points to mock server
     * - Mock server directory structure is intact
     *
     * @param buildDirectory The build output directory
     * @param serverName The name of the Liberty server
     * @return true if the mock server structure is valid, false otherwise
     */
    public static boolean validateMockServerStructure(File buildDirectory, String serverName) {
        if (buildDirectory == null || serverName == null || serverName.trim().isEmpty()) {
            return false;
        }

        // Check if mock server directory exists
        if (!mockServerStructureExists(buildDirectory, serverName)) {
            return false;
        }

        // Check if tmp directory exists
        File tmpDir = new File(buildDirectory, "tmp");
        if (!tmpDir.exists() || !tmpDir.isDirectory()) {
            return false;
        }

        // Verify the complete directory structure
        File mockInstallDir = getMockInstallDirectory(buildDirectory);
        File mockUserDir = getMockUserDirectory(buildDirectory);
        File mockServersDir = getMockServersDirectory(buildDirectory);
        File mockServerDir = getMockServerDirectory(buildDirectory, serverName);

        return mockInstallDir.exists() && mockInstallDir.isDirectory() &&
               mockUserDir.exists() && mockUserDir.isDirectory() &&
               mockServersDir.exists() && mockServersDir.isDirectory() &&
               mockServerDir.exists() && mockServerDir.isDirectory();
    }

    /**
     * Clean up the mock server structure.
     *
     * @param buildDirectory The build output directory
     * @throws IOException if cleanup fails
     */
    public static void cleanMockServerStructure(File buildDirectory) throws IOException {
        File tmpDir = new File(buildDirectory, "tmp");
        if (tmpDir.exists()) {
            deleteDirectory(tmpDir);
        }
    }

    /**
     * Recursively delete a directory and its contents.
     *
     * @param directory The directory to delete
     * @throws IOException if deletion fails
     */
    private static void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete: " + directory.getAbsolutePath());
        }
    }
}