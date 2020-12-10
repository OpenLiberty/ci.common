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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import io.openliberty.tools.ant.ServerTask;

public class BaseDevUtilTest {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    public class DevTestUtil extends DevUtil {

        public DevTestUtil(File serverDirectory, File sourceDirectory,
                File testSourceDirectory, File configDirectory, List<File> resourceDirs, boolean hotTests, boolean skipTests) {
            super(serverDirectory, sourceDirectory, testSourceDirectory, configDirectory, null, resourceDirs, hotTests, skipTests, 
                  false, false, null, 30, 30, 5, 500, true, false, false, false, false, null, null, 0, false, null, false, null);
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
        public boolean recompileBuildFile(File buildFile, List<String> artifactPaths, ThreadPoolExecutor executor) {
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
                boolean forceSkipUTs) {
            // not needed for tests
            
        }

        @Override
        public void checkConfigFile(File configFile, File serverDir) {
            // not needed for tests
            
        }

        @Override
        public boolean compile(File dir) {
            // not needed for tests
            return false;
        }

        @Override
        public List<String> getArtifacts() {
            // not needed for tests
            return null;
        }

        @Override
        public void runUnitTests() throws PluginScenarioException, PluginExecutionException {
            // not needed for tests
        }

        @Override
        public void runIntegrationTests() throws PluginScenarioException, PluginExecutionException {
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
        
    }
    
    public DevUtil getNewDevUtil(File serverDirectory)  {
        return new DevTestUtil(serverDirectory, null, null, null, null, false, false);
    }
}
