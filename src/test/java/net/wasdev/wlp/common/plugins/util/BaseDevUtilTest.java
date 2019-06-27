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

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class BaseDevUtilTest {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    public class DevTestUtil extends DevUtil {

        public DevTestUtil(File serverDirectory, File sourceDirectory,
                File testSourceDirectory, File configDirectory, List<File> resourceDirs, boolean hotTests, boolean skipTests) {
            super(serverDirectory, sourceDirectory, testSourceDirectory, configDirectory, resourceDirs, hotTests, skipTests);
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
        public boolean isDebugEnabled() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void stopServer() {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void startServer() {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void getArtifacts(List<String> artifactPaths) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public boolean recompileBuildFile(File buildFile, List<String> artifactPaths, ThreadPoolExecutor executor) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public int countApplicationUpdatedMessages() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public void runTests(boolean waitForApplicationUpdate, int messageOccurrences, ThreadPoolExecutor executor,
                boolean forceSkipUTs) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void checkConfigFile(File configFile, File serverDir) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public boolean compile(File dir) {
            // TODO Auto-generated method stub
            return false;
        }
        
    }
    
    public DevUtil getNewDevUtil(File serverDirectory)  {
        return new DevTestUtil(serverDirectory, null, null, null, null, false, false);
    }
}
