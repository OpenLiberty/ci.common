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