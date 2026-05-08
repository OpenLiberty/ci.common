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
     * Default name for the temporary directory used for mock Liberty server structures.
     */
    public static final String DEFAULT_TEMP_DIR_NAME = ".libertyls-var-cache";

    /**
     * Create a mock Liberty server structure in the build output directory.
     * This mimics the actual Liberty server directory structure without installing Liberty.
     * Uses the default temporary directory name.
     *
     * <p>Structure created:</p>
     * <pre>
     * buildDir/.libertyls-var-cache/
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
     * @return The mock server directory (buildDir/.libertyls-var-cache/wlp/usr/servers/{serverName})
     * @throws IOException if directory creation fails
     */
    public static File createMockLibertyServerStructure(File buildDirectory, String serverName) throws IOException {
        return createMockLibertyServerStructure(buildDirectory, serverName, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Create a mock Liberty server structure in the build output directory with a custom temporary directory name.
     * This mimics the actual Liberty server directory structure without installing Liberty.
     *
     * <p>Structure created:</p>
     * <pre>
     * buildDir/{tempDirName}/
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
     * @param tempDirName The name of the temporary directory (e.g., ".libertyls-var-cache", "my-temp")
     * @return The mock server directory (buildDir/{tempDirName}/wlp/usr/servers/{serverName})
     * @throws IOException if directory creation fails
     */
    public static File createMockLibertyServerStructure(File buildDirectory, String serverName, String tempDirName) throws IOException {
        if (buildDirectory == null) {
            throw new IllegalArgumentException("Build directory cannot be null");
        }
        if (serverName == null || serverName.trim().isEmpty()) {
            throw new IllegalArgumentException("Server name cannot be null or empty");
        }
        if (tempDirName == null || tempDirName.trim().isEmpty()) {
            throw new IllegalArgumentException("Temporary directory name cannot be null or empty");
        }

        // Create temporary directory in build output
        File tmpDir = new File(buildDirectory, tempDirName);
        
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
     * Get the mock install directory path using the default temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @return The mock install directory (buildDir/tmp/liberty-var-cache/wlp)
     */
    public static File getMockInstallDirectory(File buildDirectory) {
        return getMockInstallDirectory(buildDirectory, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Get the mock install directory path with a custom temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @param tempDirName The name of the temporary directory
     * @return The mock install directory (buildDir/{tempDirName}/wlp)
     */
    public static File getMockInstallDirectory(File buildDirectory, String tempDirName) {
        File tmpDir = new File(buildDirectory, tempDirName);
        return new File(tmpDir, "wlp");
    }

    /**
     * Get the mock user directory path using the default temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @return The mock user directory (buildDir/tmp/liberty-var-cache/wlp/usr)
     */
    public static File getMockUserDirectory(File buildDirectory) {
        return getMockUserDirectory(buildDirectory, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Get the mock user directory path with a custom temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @param tempDirName The name of the temporary directory
     * @return The mock user directory (buildDir/{tempDirName}/wlp/usr)
     */
    public static File getMockUserDirectory(File buildDirectory, String tempDirName) {
        File mockInstallDir = getMockInstallDirectory(buildDirectory, tempDirName);
        return new File(mockInstallDir, "usr");
    }

    /**
     * Get the mock servers directory path using the default temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @return The mock servers directory (buildDir/tmp/liberty-var-cache/wlp/usr/servers)
     */
    public static File getMockServersDirectory(File buildDirectory) {
        return getMockServersDirectory(buildDirectory, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Get the mock servers directory path with a custom temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @param tempDirName The name of the temporary directory
     * @return The mock servers directory (buildDir/{tempDirName}/wlp/usr/servers)
     */
    public static File getMockServersDirectory(File buildDirectory, String tempDirName) {
        File mockUserDir = getMockUserDirectory(buildDirectory, tempDirName);
        return new File(mockUserDir, "servers");
    }

    /**
     * Get the mock server directory path for a specific server using the default temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @param serverName The name of the Liberty server
     * @return The mock server directory (buildDir/.libertyls-var-cache/wlp/usr/servers/{serverName})
     */
    public static File getMockServerDirectory(File buildDirectory, String serverName) {
        return getMockServerDirectory(buildDirectory, serverName, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Get the mock server directory path for a specific server with a custom temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @param serverName The name of the Liberty server
     * @param tempDirName The name of the temporary directory
     * @return The mock server directory (buildDir/{tempDirName}/wlp/usr/servers/{serverName})
     */
    public static File getMockServerDirectory(File buildDirectory, String serverName, String tempDirName) {
        File mockServersDir = getMockServersDirectory(buildDirectory, tempDirName);
        return new File(mockServersDir, serverName);
    }

    /**
     * Validate that the mock server structure exists using the default temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @param serverName The name of the Liberty server
     * @return true if the mock server structure exists, false otherwise
     */
    public static boolean mockServerStructureExists(File buildDirectory, String serverName) {
        return mockServerStructureExists(buildDirectory, serverName, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Validate that the mock server structure exists with a custom temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @param serverName The name of the Liberty server
     * @param tempDirName The name of the temporary directory
     * @return true if the mock server structure exists, false otherwise
     */
    public static boolean mockServerStructureExists(File buildDirectory, String serverName, String tempDirName) {
        File mockServerDir = getMockServerDirectory(buildDirectory, serverName, tempDirName);
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
     * This is determined by checking if the config file content contains the temporary directory reference.
     *
     * @param configFile The liberty-plugin-config.xml file
     * @return true if the config points to a mock server, false otherwise
     */
    public static boolean isMockServerInConfig(File configFile) {
        return isMockServerInConfig(configFile, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Check if the liberty-plugin-config.xml file points to a mock server directory with a custom temporary directory name.
     * This is determined by checking if the config file content contains the specified temporary directory reference.
     *
     * @param configFile The liberty-plugin-config.xml file
     * @param tempDirName The name of the temporary directory to check for
     * @return true if the config points to a mock server, false otherwise
     */
    public static boolean isMockServerInConfig(File configFile, String tempDirName) {
        if (configFile == null || !configFile.exists()) {
            return false;
        }

        try {
            String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            return content.contains(tempDirName);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validate that the mock server structure exists and is properly configured using the default temporary directory name.
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
        return validateMockServerStructure(buildDirectory, serverName, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Validate that the mock server structure exists and is properly configured with a custom temporary directory name.
     * This performs comprehensive validation including:
     * - Mock server directory exists
     * - Config file exists and points to mock server
     * - Mock server directory structure is intact
     *
     * @param buildDirectory The build output directory
     * @param serverName The name of the Liberty server
     * @param tempDirName The name of the temporary directory
     * @return true if the mock server structure is valid, false otherwise
     */
    public static boolean validateMockServerStructure(File buildDirectory, String serverName, String tempDirName) {
        if (buildDirectory == null || serverName == null || serverName.trim().isEmpty()) {
            return false;
        }

        // Check if mock server directory exists
        if (!mockServerStructureExists(buildDirectory, serverName, tempDirName)) {
            return false;
        }

        // Check if temporary directory exists
        File tmpDir = new File(buildDirectory, tempDirName);
        if (!tmpDir.exists() || !tmpDir.isDirectory()) {
            return false;
        }

        // Verify the complete directory structure
        File mockInstallDir = getMockInstallDirectory(buildDirectory, tempDirName);
        File mockUserDir = getMockUserDirectory(buildDirectory, tempDirName);
        File mockServersDir = getMockServersDirectory(buildDirectory, tempDirName);
        File mockServerDir = getMockServerDirectory(buildDirectory, serverName, tempDirName);

        return mockInstallDir.exists() && mockInstallDir.isDirectory() &&
               mockUserDir.exists() && mockUserDir.isDirectory() &&
               mockServersDir.exists() && mockServersDir.isDirectory() &&
               mockServerDir.exists() && mockServerDir.isDirectory();
    }

    /**
     * Clean up the mock server structure using the default temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @throws IOException if cleanup fails
     */
    public static void cleanMockServerStructure(File buildDirectory) throws IOException {
        cleanMockServerStructure(buildDirectory, DEFAULT_TEMP_DIR_NAME);
    }

    /**
     * Clean up the mock server structure with a custom temporary directory name.
     *
     * @param buildDirectory The build output directory
     * @param tempDirName The name of the temporary directory
     * @throws IOException if cleanup fails
     */
    public static void cleanMockServerStructure(File buildDirectory, String tempDirName) throws IOException {
        File tmpDir = new File(buildDirectory, tempDirName);
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