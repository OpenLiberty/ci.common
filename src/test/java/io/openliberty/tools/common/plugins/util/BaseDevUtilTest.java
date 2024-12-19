/**
 * (C) Copyright IBM Corporation 2019, 2023.
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
import java.util.Collection;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import io.openliberty.tools.ant.ServerTask;

public class BaseDevUtilTest {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    public class DevTestUtil extends DevUtil {

        public DevTestUtil(File serverDirectory, File sourceDirectory, File testSourceDirectory, File configDirectory,
                List<File> resourceDirs, List<Path> webResourceDirs, boolean hotTests, boolean skipTests) throws IOException {
            super(temp.newFolder(), serverDirectory, sourceDirectory, testSourceDirectory, configDirectory, null, null,
                    resourceDirs, false, hotTests, skipTests, false, false, false, null, 30, 30, 5, 500, true, false, false, false,
                    false, null, null, null, 0, false, null, false, null, null, false, null, null, null, false, null, null, webResourceDirs,true);
        }

        public DevTestUtil(File serverDirectory, File buildDir) {
            super(buildDir, serverDirectory, null, null, null, null, null,
                    null, false, false, false, false, false, false, null, 30, 30, 5, 500, true, false, false, false,
                    false, null, null, null, 0, false, null, false, null, null, false, null, null, null, false, null, null, null,true);
        }

        @Override
        public void debug(String msg) {
            // not needed for tests
            
        }

        @Override
        public void debug(String msg, Throwable e) {
            // not needed for tests
            
        }

        @Override
        public void debug(Throwable e) {
            // not needed for tests
            
        }

        @Override
        public void warn(String msg) {
            // not needed for tests
            
        }

        @Override
        public void info(String msg) {
            // not needed for tests
            
        }

        @Override
        public void error(String msg) {
            // not needed for tests
            
        }

        @Override
        public void error(String msg, Throwable e) {
            // not needed for tests
            
        }

        @Override
        public boolean isDebugEnabled() {
            // not needed for tests
            return false;
        }

        @Override
        public void stopServer() {
            // not needed for tests
            
        }

        @Override
        public ServerTask getServerTask() throws IOException {
            // not needed for tests
            return null;
        }

        @Override
        public boolean recompileBuildFile(File buildFile, Set<String> compileArtifactPaths,
                Set<String> testArtifactPaths, boolean generateFeatures, ThreadPoolExecutor executor) {
            // not needed for tests
            return false;
        }

        @Override
        public boolean updateArtifactPaths(ProjectModule projectModule, boolean redeployCheck,
                boolean generateFeatures, ThreadPoolExecutor executor) throws PluginExecutionException {
            // not needed for tests
            return false;
        }

        @Override
        public boolean updateArtifactPaths(File parentBuildFile) {
            // not needed for tests
            return false;
        }

        @Override
        public int countApplicationUpdatedMessages() {
            // not needed for tests
            return 0;
        }

        @Override
        public void runTests(boolean waitForApplicationUpdate, int messageOccurrences, ThreadPoolExecutor executor,
                boolean forceSkipTests, boolean forceSkipUTs, boolean forceSkipITs, File buildFile, String projectName) {
            // not needed for tests

        }

        @Override
        public void installFeatures(File configFile, File serverDir, boolean generateFeatures) {
            // not needed for tests
        }

        @Override
        public ServerFeatureUtil getServerFeatureUtilObj() {
            // not needed for tests
            return null;
        }

        @Override
        public Set<String> getExistingFeatures() {
            // not needed for tests
            return null;
        }

        @Override
        public void updateExistingFeatures() {
            // not needed for tests
        }

        @Override
        public boolean compile(File dir) {
            // not needed for tests
            return false;
        }

        @Override
        public void runUnitTests(File buildFile) throws PluginScenarioException, PluginExecutionException {
            // not needed for tests
        }

        @Override
        public void runIntegrationTests(File buildFile) throws PluginScenarioException, PluginExecutionException {
            // not needed for tests
        }

        @Override
        public void libertyCreate() {
            // not needed for tests
        }

        @Override
        public void libertyDeploy() {
            // not needed for tests
        }

        @Override
        public void libertyInstallFeature() {
            // not needed for tests
        }

        @Override
        public boolean libertyGenerateFeatures(Collection<String> classes, boolean optimize) {
            // not needed for tests
            return true;
        }

        @Override
        public void redeployApp() throws PluginExecutionException {
            // not needed for tests
        }

        @Override
        public String getServerStartTimeoutExample() {
            // not needed for tests
            return null;
        }

        @Override
        public String getProjectName() {
            // not needed for tests
            return null;
        }

        @Override
        public boolean isLooseApplication() {
            // not needed for tests
            return true;
        }

        @Override
        public File getLooseApplicationFile() {
            // not needed for tests
            return null;
        }

        @Override
        public boolean compile(File dir, ProjectModule project) {
            // not needed for tests
            return false;
        }
        
		@Override
		protected void updateLooseApp() throws PluginExecutionException {
			// not needed for tests
		}

		@Override
		protected void resourceDirectoryCreated() throws IOException {
			// not needed for tests
		}

		@Override
		protected void resourceModifiedOrCreated(File fileChanged, File resourceParent, File outputDirectory)
				throws IOException {
			// not needed for tests
		}

		@Override
		protected void resourceDeleted(File fileChanged, File resourceParent, File outputDirectory) throws IOException {
			// not needed for tests
		}
    }
    
    public DevUtil getNewDevUtil(File serverDirectory) throws IOException  {
        return new DevTestUtil(serverDirectory, null, null, null, null, null, false, false);
    }

    public DevUtil getNewDevUtil(File serverDirectory, File buildDir) {
        return new DevTestUtil(serverDirectory, buildDir);
    }
}
