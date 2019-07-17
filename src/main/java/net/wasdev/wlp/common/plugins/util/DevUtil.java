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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;

import net.wasdev.wlp.ant.ServerTask;

import com.sun.nio.file.SensitivityWatchEventModifier;

/**
 * Utility class for dev mode.
 */
public abstract class DevUtil {

    protected static final String START_APP_MESSAGE_REGEXP = "CWWKZ0001I.*";

    /**
     * Log debug
     * 
     * @param msg
     */
    public abstract void debug(String msg);

    /**
     * Log debug
     * 
     * @param msg
     * @param e
     */
    public abstract void debug(String msg, Throwable e);

    /**
     * Log debug
     * 
     * @param e
     */
    public abstract void debug(Throwable e);

    /**
     * Log warning
     * 
     * @param msg
     */
    public abstract void warn(String msg);

    /**
     * Log info
     * 
     * @param msg
     */
    public abstract void info(String msg);

    /**
     * Log error
     * 
     * @param msg
     */
    public abstract void error(String msg);

    /**
     * Returns whether debug is enabled by the current logger
     * 
     * @return whether debug is enabled
     */
    public abstract boolean isDebugEnabled();

    /**
     * Updates artifacts of current project
     */
    public abstract List<String> getArtifacts();

    /**
     * Recompile the build file
     * 
     * @param buildFile
     * @param artifactPaths
     * @param executor The thread pool executor
     * @return true if the build file was recompiled with changes
     */
    public abstract boolean recompileBuildFile(File buildFile, List<String> artifactPaths, ThreadPoolExecutor executor);

    /**
     * Get the number of times the application updated message has appeared in the application log
     * 
     * @return 
     */
    public abstract int countApplicationUpdatedMessages();

    /**
     * Run unit and/or integration tests
     * 
     * @param waitForApplicationUpdate Whether to wait for the application to update before running integration tests
     * @param messageOccurrences The previous number of times the application updated message has appeared.
     * @param executor The thread pool executor
     * @param forceSkipUTs Whether to force skip the unit tests
     */
    public abstract void runTests(boolean waitForApplicationUpdate, int messageOccurrences, ThreadPoolExecutor executor, boolean forceSkipUTs);


    /**
     * Check the configuration file for new features
     * 
     * @param configFile
     * @param serverDir
     */
    public abstract void checkConfigFile(File configFile, File serverDir);

    /**
     * Compile the specified directory
     * @param dir
     * @return
     */
    public abstract boolean compile(File dir);

    /**
     * Stop the server
     */
    public abstract void stopServer();

    /**
     * Get the ServerTask for debug mode
     * 
     * @return ServerTask for debug mode
     * @throws IOException if there was an error copying config files
     */
    public abstract ServerTask getDebugServerTask() throws IOException;

    private File serverDirectory;
    private File sourceDirectory;
    private File testSourceDirectory;
    private File configDirectory;
    private List<File> resourceDirs;
    private boolean hotTests;
    private Path tempConfigPath;
    private boolean skipTests;

    public DevUtil(File serverDirectory, File sourceDirectory, File testSourceDirectory,
            File configDirectory, List<File> resourceDirs, boolean hotTests, boolean skipTests) {
        this.serverDirectory = serverDirectory;
        this.sourceDirectory = sourceDirectory;
        this.testSourceDirectory = testSourceDirectory;
        this.configDirectory = configDirectory;
        this.resourceDirs = resourceDirs;
        this.hotTests = hotTests;
        this.skipTests = skipTests;
    }

    /**
     * Start the server in debug mode and keep it running in a background thread.
     * 
     * @throws PluginExecutionException If the server startup could not be verified within the timeout
     */
    public void startServer(long serverStartTimeout, long verifyTimeout) throws PluginExecutionException {
        try {
            final ServerTask serverTask = getDebugServerTask();

            String logsDirectory = serverTask.getOutputDir() + "/" + serverTask.getServerName() + "/logs";
            File messagesLogFile = new File(logsDirectory + "/messages.log");

            // Set server start timeout
            if (serverStartTimeout < 0) {
                serverStartTimeout = 30;
            }
            serverTask.setTimeout(Long.toString(serverStartTimeout * 1000));

            // Watch logs directory if it already exists
            WatchService watchService = FileSystems.getDefault().newWatchService();
            boolean logsExist = new File(logsDirectory).isDirectory();

            if (logsExist) {
                // If the logs directory already exists, then
                // setup a watch service to monitor the directory.
                Paths.get(logsDirectory).register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
            }

            // Start server
            Thread serverThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        serverTask.execute();
                    } catch (Exception e) {
                        debug("Error starting server", e);
                    }
                }

            });

            serverThread.start();

            if (logsExist) {
                // If logs already exist, then watch the directory to ensure
                // messages.log is modified before continuing.
                boolean messagesModified = false;
                WatchKey key;
                while (!messagesModified && (key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals("messages.log")) {
                            messagesModified = true;
                            debug("messages.log has been changed");
                        }
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            }

            if (verifyTimeout < 0) {
                verifyTimeout = 30;
            }
            long timeout = verifyTimeout * 1000;
            long endTime = System.currentTimeMillis() + timeout;

            // Wait for the app started message in messages.log
            String startMessage = serverTask.waitForStringInLog(START_APP_MESSAGE_REGEXP, timeout, messagesLogFile);
            if (startMessage == null) {
                stopServer();
                throw new PluginExecutionException(
                        "Unable to verify if the server was started after " + verifyTimeout + " seconds.");
            }

            timeout = endTime - System.currentTimeMillis();
        } catch (Exception e) {
            debug("Error starting server", e);
        }
    }
    
    public void cleanUpServerEnv() {
        // clean up server.env file
        File serverEnvFile;
        File serverEnvBackup;
        try {
            serverEnvBackup = new File(serverDirectory.getCanonicalPath() + "/server.env.bak");
            serverEnvFile = new File(serverDirectory.getCanonicalPath() + "/server.env");
            if (serverEnvBackup.exists()) {
                // Restore original server.env file
                try {
                    Files.copy(serverEnvBackup.toPath(), serverEnvFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    error("Could not restore server.env: " + e.getMessage());
                }
                serverEnvBackup.delete();
            } else {
                // Delete server.env file
                serverEnvFile.delete();
            }
        } catch (IOException e) {
            error("Could not retrieve server.env: " + e.getMessage());
        }
    }
    
    public void cleanUpTempConfig() {
        if (this.tempConfigPath != null){
            File tempConfig = this.tempConfigPath.toFile();
            if (tempConfig.exists()){
                try {
                    FileUtils.deleteDirectory(tempConfig);
                    debug("Sucessfully deleted liberty:dev temporary configuration folder");
                } catch (IOException e) {
                    error("Could not delete liberty:dev temporary configuration folder");
                }
            }
        }
    }

    public void addShutdownHook(final ThreadPoolExecutor executor) {
        // shutdown hook to stop server when x mode is terminated
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                debug("Inside Shutdown Hook, shutting down server");
                
                cleanUpTempConfig();
                cleanUpServerEnv();

                if (hotkeyReader != null) {
                    hotkeyReader.shutdown();
                }

                // shutdown tests
                executor.shutdown();

                // stopping server
                stopServer();
            }
        });
    }

    public void enableServerDebug(int libertyDebugPort) throws IOException {
        String serverEnvPath = serverDirectory.getCanonicalPath() + "/server.env";
        File serverEnvFile = new File(serverEnvPath);
        StringBuilder sb = new StringBuilder();
        if (serverEnvFile.exists()) {
            debug("server.env already exists");
            File serverEnvBackup = new File(serverEnvPath + ".bak");
            Files.copy(serverEnvFile.toPath(), serverEnvBackup.toPath());
            boolean deleted = serverEnvFile.delete();

            if (!deleted) {
                error("Could not move existing liberty:dev server.env file");
            }

            BufferedReader reader = new BufferedReader(new FileReader(serverEnvBackup));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    sb.append("\n");
                }
            } finally {
                reader.close();
            }
        }

        debug("Creating server.env file: " + serverEnvFile.getCanonicalPath());
        sb.append("WLP_DEBUG_SUSPEND=n\n");
        sb.append("WLP_DEBUG_ADDRESS=");
        sb.append(libertyDebugPort);
        sb.append("\n");

        BufferedWriter writer = new BufferedWriter(new FileWriter(serverEnvFile));
        try {
            writer.write(sb.toString());
        } finally {
            writer.close();
        }

        if (serverEnvFile.exists()) {
            info("Successfully created liberty:dev server.env file");
        }
    }

    private HotkeyReader hotkeyReader = null;

    /**
     * Run a hotkey reader thread.
     * If the thread is already running, does nothing.
     * 
     * @param executor the test thread executor
     */
    public void runHotkeyReaderThread(ThreadPoolExecutor executor) {
        if (hotkeyReader == null) {
            hotkeyReader = new HotkeyReader(executor);
            new Thread(hotkeyReader).start();
            debug("Started hotkey reader.");

            if (!skipTests) {
                if (hotTests) {
                    info("Tests will run automatically when changes are detected. You can also press the Enter key to run tests on demand.");
                } else {
                    info("Press the Enter key to run tests on demand.");
                }
            }
        }
    }

    private class HotkeyReader implements Runnable {
        private Scanner scanner;
        private ThreadPoolExecutor executor;
        private boolean shutdown = false;

        public HotkeyReader(ThreadPoolExecutor executor) {
            this.executor = executor;
        }
    
        @Override
        public void run() {
            debug("Running hotkey reader thread");
            scanner = new Scanner(System.in);
            readInput();
        }

        public void shutdown() {
            shutdown = true;
        }
    
        private void readInput() {
            while (!shutdown) {
                debug("Waiting for Enter key to run tests");
                String line = scanner.nextLine();
                if (line != null && line.trim().equalsIgnoreCase("exit")) {
                    debug("Detected exit command");
                    System.exit(0);
                } else {
                    debug("Detected Enter key. Running tests...");
                    runTestThread(false, executor, -1, false, true);    
                }
            }
            debug("Hotkey reader thread was shut down");
        }
    }

    public void watchFiles(File buildFile, File outputDirectory, File testOutputDirectory,
            final ThreadPoolExecutor executor, List<String> artifactPaths, File configFile)
            throws Exception {
        try (WatchService watcher = FileSystems.getDefault().newWatchService();) {
            
            File serverXML = getFileFromConfigDirectory("server.xml"); // server.xml in the config directory
            File configFileParent = configFile.getParentFile();
            
            Path srcPath = this.sourceDirectory.getCanonicalFile().toPath();
            Path testSrcPath = this.testSourceDirectory.getCanonicalFile().toPath();
            Path configPath = this.configDirectory.getCanonicalFile().toPath();

            boolean sourceDirRegistered = false;
            boolean testSourceDirRegistered = false;
            boolean configDirRegistered = false;
            boolean configFileRegistered = false;

            if (this.sourceDirectory.exists()) {
                registerAll(srcPath, watcher);
                sourceDirRegistered = true;
            }

            if (this.testSourceDirectory.exists()) {
                registerAll(testSrcPath,watcher);
                testSourceDirRegistered = true;
            }

            if (this.configDirectory.exists()) {
                registerAll(configPath, watcher);
                configDirRegistered = true;
            }
            
            if (configFile.exists() && configFileParent.exists()){
                Path configFilePath = configFileParent.getCanonicalFile().toPath();
                registerAll(configFilePath, watcher);
                configFileRegistered = true;
            }
            
            HashMap<File, Boolean> resourceMap = new HashMap<File, Boolean>();
            for (File resourceDir : resourceDirs) {
                resourceMap.put(resourceDir, false);
                if (resourceDir.exists()) {
                    registerAll(resourceDir.getCanonicalFile().toPath(), watcher);
                    resourceMap.put(resourceDir, true);
                }
            }

            buildFile.getParentFile().toPath().register(
                    watcher, new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE },
                    SensitivityWatchEventModifier.HIGH);
            debug("Watching build file directory: " + buildFile.getParentFile().toPath());

            while (true) {
                // check if javaSourceDirectory has been added
                if (!sourceDirRegistered && this.sourceDirectory.exists()
                        && this.sourceDirectory.listFiles().length > 0) {
                    compile(this.sourceDirectory);
                    registerAll(srcPath, watcher);
                    debug("Registering Java source directory: " + this.sourceDirectory);
                    sourceDirRegistered = true;
                } else if (sourceDirRegistered && !this.sourceDirectory.exists()) {
                    cleanTargetDir(outputDirectory);
                    sourceDirRegistered = false;
                }

                // check if testSourceDirectory has been added
                if (!testSourceDirRegistered && this.testSourceDirectory.exists()
                        && this.testSourceDirectory.listFiles().length > 0) {
                    compile(this.testSourceDirectory);
                    registerAll(testSrcPath, watcher);
                    debug("Registering Java test directory: " + this.testSourceDirectory);
                    runTestThread(false, executor, -1, false, false);
                    testSourceDirRegistered = true;

                } else if (testSourceDirRegistered && !this.testSourceDirectory.exists()) {
                    cleanTargetDir(testOutputDirectory);
                    testSourceDirRegistered = false;
                }
                
                // check if configDirectory has been added
                if (!configDirRegistered && this.configDirectory.exists()){
                    configDirRegistered = true;
                    debug("Configuration directory has been added: " + this.configDirectory);
                    info("The server configuration directory " + configDirectory + " has been added. Restart liberty:dev mode for it to take effect.");
                }
                
                // check if configFile has been added
                if (!configFileRegistered && configFile.exists()){
                    configFileRegistered = true;
                    debug("Configuration file has been added: " + configFile);
                    info("The server configuration file " + configFile + " has been added. Restart liberty:dev mode for it to take effect.");
                }
                
                // check if resourceDirectory has been added
                for (File resourceDir : resourceDirs){
                    if (!resourceMap.get(resourceDir)) {
                        if (resourceDir.exists()) {
                            resourceMap.put(resourceDir, true);
                            debug("Resource directory has been added: " + resourceDir);
                            info("The resource directory " + resourceDir + "has been added. Restart liberty:dev mode for it to take effect.");
                        }
                    }
                }

                try {
                    final WatchKey wk = watcher.poll(1, TimeUnit.SECONDS);
                    for (WatchEvent<?> event : wk.pollEvents()) {
                        final Path changed = (Path) event.context();

                        final Watchable watchable = wk.watchable();
                        final Path directory = (Path) watchable;
                        debug("Processing events for watched directory: " + directory);

                        File fileChanged = new File(directory.toString(), changed.toString());
                        debug("Changed: " + changed + "; " + event.kind());

                        // resource file check
                        File resourceParent = null;
                        for (File resourceDir : resourceDirs) {
                            if (directory.startsWith(resourceDir.getCanonicalFile().toPath())) {
                                resourceParent = resourceDir;
                                break;
                            }
                        }
                        
                        int numApplicationUpdatedMessages = countApplicationUpdatedMessages();

                        // src/main/java directory
                        if (directory.startsWith(srcPath)) {
                            ArrayList<File> javaFilesChanged = new ArrayList<File>();
                            javaFilesChanged.add(fileChanged);
                            if (fileChanged.exists() && fileChanged.getName().endsWith(".java")
                                    && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                            || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                                debug("Java source file modified: " + fileChanged.getName());

                                // tests are run in recompileJavaSource
                                recompileJavaSource(javaFilesChanged, artifactPaths, executor, outputDirectory,
                                        testOutputDirectory);
                            } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                debug("Java file deleted: " + fileChanged.getName());
                                deleteJavaFile(fileChanged, outputDirectory, this.sourceDirectory);

                                // run all tests since Java files were changed
                                runTestThread(true, executor, numApplicationUpdatedMessages, false, false);
                            }
                        } else if (directory.startsWith(testSrcPath)) { // src/main/test
                            ArrayList<File> javaFilesChanged = new ArrayList<File>();
                            javaFilesChanged.add(fileChanged);
                            if (fileChanged.exists() && fileChanged.getName().endsWith(".java")
                                    && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                            || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                                
                                // tests are run in recompileJavaTest
                                recompileJavaTest(javaFilesChanged, artifactPaths, executor, outputDirectory,
                                        testOutputDirectory);
                            } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                debug("Java file deleted: " + fileChanged.getName());
                                deleteJavaFile(fileChanged, testOutputDirectory, this.testSourceDirectory);

                                // run all tests without waiting for app update since only unit test source changed
                                runTestThread(false, executor, -1, false, false);
                            }
                        } else if (directory.startsWith(configPath)) { // config files
                            if (fileChanged.exists() && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                    || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                                copyConfigFolder(fileChanged, this.configDirectory, "server.xml");
                                copyFile(fileChanged, this.configDirectory, serverDirectory, null);
                                runTestThread(true, executor, numApplicationUpdatedMessages, true, false);

                            } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                info("Config file deleted: " + fileChanged.getName());
                                deleteFile(fileChanged, this.configDirectory, serverDirectory, null);
                                runTestThread(true, executor, numApplicationUpdatedMessages, true, false);
                            }
                        } else if (directory.startsWith(configFileParent.getCanonicalFile().toPath())) {
                            if (serverXML == null || !serverXML.exists()) {
                                if (fileChanged.exists() && fileChanged.getCanonicalPath().endsWith(configFile.getName())
                                        && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                                || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                                    copyConfigFolder(fileChanged, configFileParent, "server.xml");
                                    copyFile(fileChanged, configFileParent, serverDirectory,
                                            "server.xml");

                                    runTestThread(true, executor, numApplicationUpdatedMessages, true, false);

                                } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE
                                        && fileChanged.getCanonicalPath().endsWith(configFile.getName())) {
                                    info("Config file deleted: " + fileChanged.getName());
                                    deleteFile(fileChanged, this.configDirectory, serverDirectory, "server.xml");
                                    runTestThread(true, executor, numApplicationUpdatedMessages, true, false);
                                }
                            }
                        } else if (resourceParent != null && directory.startsWith(resourceParent.getCanonicalFile().toPath())) { // resources
                            debug("Resource dir: " + resourceParent.toString());
                            debug("File within resource directory");
                            if (fileChanged.exists() && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                    || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                                copyFile(fileChanged, resourceParent, outputDirectory, null);

                                // run all tests on resource change
                                runTestThread(true, executor, numApplicationUpdatedMessages, false, false);
                            } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                debug("Resource file deleted: " + fileChanged.getName());
                                deleteFile(fileChanged, resourceParent, outputDirectory, null);
                                // run all tests on resource change
                                runTestThread(true, executor, numApplicationUpdatedMessages, false, false);
                            }
                        } else if (fileChanged.equals(buildFile)
                                && directory.startsWith(buildFile.getParentFile().getCanonicalFile().toPath())
                                && event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) { // pom.xml

                                    boolean recompiledBuild = recompileBuildFile(buildFile, artifactPaths, executor);
                                    // run all tests on build file change
                                    if (recompiledBuild) {
                                        runTestThread(true, executor, numApplicationUpdatedMessages, false, false);
                                    }
                        }
                    }
                    // reset the key
                    boolean valid = wk.reset();
                    if (!valid) {
                        debug("WatchService key has been unregistered");
                    }
                } catch (InterruptedException | NullPointerException e) {
                    // do nothing let loop continue
                }
            }
        }
    }

    /**
     * Reads the file to a String
     * 
     * @param file
     * @return String representation of the file
     * @throws IOException unable to read file to string
     */
    public String readFile(File file) throws IOException {
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    /**
     * Creates a temporary copy of the configuration file and checks the
     * configFile in the temporary directory to avoid install-feature timing
     * issues
     * 
     * @param fileChanged the file that was changed
     * @param srcDir the directory of the file changed
     * @param targetFileName if not null renames the fileChanged to targetFileName in the targetDir
     * @throws IOException creating and copying to tempConfig directory
     */
    public void copyConfigFolder(File fileChanged, File srcDir, String targetFileName)
            throws IOException {
        this.tempConfigPath = Files.createTempDirectory("tempConfig");
        File tempConfig = tempConfigPath.toFile();
        debug("Temporary configuration folder created: " + tempConfig);
        
        FileUtils.copyDirectory(serverDirectory, tempConfig);
        copyFile(fileChanged, srcDir, tempConfig, targetFileName);
        checkConfigFile(fileChanged, tempConfig);
        cleanUpTempConfig();
    }

    /**
     * Copies the fileChanged from the srcDir to the targetDir.
     * 
     * @param fileChanged the file that was changed
     * @param srcDir the directory of the file changed
     * @param targetDir the target directory
     * @param targetFileName if not null renames the fileChanged to targetFileName in the targetDir
     * @throws IOException unable to resolve canonical path
     */
    public void copyFile(File fileChanged, File srcDir, File targetDir, String targetFileName) throws IOException {
        String relPath = fileChanged.getCanonicalPath().substring(
                fileChanged.getCanonicalPath().indexOf(srcDir.getCanonicalPath()) + srcDir.getCanonicalPath().length());
        if (targetFileName != null) {
            relPath = relPath.substring(0, relPath.indexOf(fileChanged.getName())) + targetFileName;
        }
        File targetResource = new File(targetDir.getCanonicalPath() + relPath);

        try {
            FileUtils.copyFile(fileChanged, targetResource);
            info("Copied file: " + fileChanged.getCanonicalPath() + " to: " + targetResource.getCanonicalPath());
        } catch (FileNotFoundException ex) {
            debug("Failed to copy file: " + fileChanged.getCanonicalPath());
        } catch (Exception ex) {
            debug(ex);
        }
    }

    /**
     * Deletes the corresponding file in the targetDir.
     * 
     * @param deletedFile the file that was deleted
     * @param dir the directory of the deletedFile
     * @param targetDir the corresponding targetDir of the deletedFile
     * @param targetFileName if not null deletes the targetFile with this name
     * @throws IOException unable to resolve canonical path
     */
    protected void deleteFile(File deletedFile, File dir, File targetDir, String targetFileName) throws IOException {
        debug("File that was deleted: " + deletedFile.getCanonicalPath());
        String relPath = deletedFile.getCanonicalPath().substring(
                deletedFile.getCanonicalPath().indexOf(dir.getCanonicalPath()) + dir.getCanonicalPath().length());
        if (targetFileName != null) {
            relPath = relPath.substring(0, relPath.indexOf(deletedFile.getName())) + targetFileName;
        }
        File targetFile = new File(targetDir.getCanonicalPath() + relPath);
        debug("Target file exists: " + targetFile.exists());
        if (targetFile.exists()) {
            if (targetFile.delete()){
                info("Deleted file" + targetFile.getCanonicalPath());
            } else {
                error("Error deleting file " + targetFile.getCanonicalPath());
            }
        }
    }

    /**
     * Delete all the Java class files within the specified directory.
     * If the directory is empty, deletes the directory as well.
     *  
     * @param outputDirectory the directory for compiled classes
     */
    protected void cleanTargetDir(File outputDirectory){
        File[] fList = outputDirectory.listFiles();
        if (fList != null) {
            for (File file : fList) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".class")) {
                   file.delete();
                   info("Deleted Java class file: " + file);
                } else if (file.isDirectory()) {
                    cleanTargetDir(file);
                }
            }
        }
        if (outputDirectory.listFiles().length == 0){
            outputDirectory.delete();
        }
    }

    /**
     * Register the parent directory and all sub-directories with the WatchService
     * 
     * @param start parent directory
     * @param dir path of parent directory
     * @param watcher WatchService
     * @throws IOException unable to walk through file tree 
     */
    protected void registerAll(final Path start, final WatchService watcher) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                debug("Watching directory: " + dir.toString());
                dir.register(watcher,
                        new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE },
                        SensitivityWatchEventModifier.HIGH);

                return FileVisitResult.CONTINUE;
            }

        });
    }
    
    /**
     * Get the file from the configDirectory if it exists
     * 
     * @param file 
     * @return file or null if it does not exist
     */
    protected File getFileFromConfigDirectory(String file) {
        File f = new File(configDirectory, file);
        if (configDirectory != null && f.exists()) {
            return f;
        }
        return null;
    }

    /**
     * Given the fileChanged delete the corresponding Java class
     * 
     * @param fileChanged Java file changed
     * @param classesDir the directory for compiled classes
     * @param compileSourceRoot the source directory for the Java classes
     * @throws IOException unable to resolve canonical path
     */
    protected void deleteJavaFile(File fileChanged, File classesDir, File compileSourceRoot) throws IOException {
        if (fileChanged.getName().endsWith(".java")) {
            String fileName = fileChanged.getName().substring(0, fileChanged.getName().indexOf(".java"));
            File parentFile = fileChanged.getParentFile();
            String relPath = parentFile.getCanonicalPath()
                    .substring(parentFile.getCanonicalPath().indexOf(compileSourceRoot.getCanonicalPath())
                            + compileSourceRoot.getCanonicalPath().length())
                    + "/" + fileName + ".class";
            File targetFile = new File(classesDir.getCanonicalPath() + relPath);

            if (targetFile.exists()) {
                targetFile.delete();
                info("Java class deleted: " + targetFile.getCanonicalPath());
            }
        } else {
            debug("File deleted but was not a java file: " + fileChanged.getName());
        }
    }

    /**
     * Recompile Java source files and run tests after application update
     * 
     * @param javaFilesChanged list of Java files changed
     * @param artifactPaths list of project artifact paths for building the classpath
     * @param executor the test thread executor
     * @param outputDirectory the directory for compiled classes
     * @param testOutputDirectory the directory for compiled test classes
     * @throws Exception
     */
    protected void recompileJavaSource(List<File> javaFilesChanged, List<String> artifactPaths,
            ThreadPoolExecutor executor, File outputDirectory, File testOutputDirectory) throws Exception {
        recompileJava(javaFilesChanged, artifactPaths, executor, false, outputDirectory, testOutputDirectory);
    }

    /**
     * Recompile test source files and run tests immediately
     * 
     * @param javaFilesChanged list of Java files changed
     * @param artifactPaths list of project artifact paths for building the classpath
     * @param executor the test thread executor
     * @param outputDirectory the directory for compiled classes
     * @param testOutputDirectory the directory for compiled test classes
     * @throws Exception
     */
    protected void recompileJavaTest(List<File> javaFilesChanged, List<String> artifactPaths,
            ThreadPoolExecutor executor, File outputDirectory, File testOutputDirectory) throws Exception {
        recompileJava(javaFilesChanged, artifactPaths, executor, true, outputDirectory, testOutputDirectory);
    }

    /**
     * Recompile source files
     * 
     * @param javaFilesChanged list of Java files changed
     * @param artifactPaths list of project artifact paths for building the classpath
     * @param executor the test thread executor
     * @param tests indicates whether the files changed were test files
     * @param outputDirectory the directory for compiled classes
     * @param testOutputDirectory the directory for compiled test classes
     */
    protected void recompileJava(List<File> javaFilesChanged, List<String> artifactPaths, ThreadPoolExecutor executor,
            boolean tests, File outputDirectory, File testOutputDirectory) {
        try {
            int messageOccurrences = countApplicationUpdatedMessages();
            
            // source root is src/main/java or src/test/java
            File classesDir = tests ? testOutputDirectory : outputDirectory;

            List<String> optionList = new ArrayList<>();
            List<File> outputDirs = new ArrayList<File>();

            if (tests) {
                outputDirs.add(outputDirectory);
                outputDirs.add(testOutputDirectory);
            } else {
                outputDirs.add(outputDirectory);
            }
            Set<File> classPathElems = getClassPath(artifactPaths, outputDirs);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

            fileManager.setLocation(StandardLocation.CLASS_PATH, classPathElems);
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(classesDir));

            Iterable<? extends JavaFileObject> compilationUnits = fileManager
                    .getJavaFileObjectsFromFiles(javaFilesChanged);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, optionList, null,
                    compilationUnits);
            boolean didCompile = task.call();
            if (didCompile) {
                if (tests) {
                    info("Tests compilation was successful.");
                } else {
                    info("Source compilation was successful.");
                }

                // run tests after successful compile
                if (tests) {
                    // if only tests were compiled, don't need to wait for
                    // app to update
                    runTestThread(false, executor, -1, false, false);
                } else {
                    runTestThread(true, executor, messageOccurrences, false, false);
                }
            } else {
                if (tests) {
                    info("Tests compilation had errors.");
                } else {
                    info("Source compilation had errors.");
                }
            }
        } catch (Exception e) {
            debug("Error compiling java files", e);
        }
    }

    /**
     * Gets the class path for the specified artifactPaths and outputDirs.
     * 
     * @param artifactPaths list of artifacts for the current project
     * @param outputDirs list of output directories for the current project
     * @return set of classpath files
     * @throws IOException unable to resolve canonical path
     */
    protected Set<File> getClassPath(List<String> artifactPaths, List<File> outputDirs) throws IOException {
        List<URL> urls = new ArrayList<>();
        ClassLoader c = Thread.currentThread().getContextClassLoader();
        while (c != null) {
            if (c instanceof URLClassLoader) {
                urls.addAll(Arrays.asList(((URLClassLoader) c).getURLs()));
            }
            c = c.getParent();
        }

        Set<String> parsedFiles = new HashSet<>();
        Deque<String> toParse = new ArrayDeque<>();
        for (URL url : urls) {
            toParse.add(new File(url.getPath()).getCanonicalPath());
        }

        for (String artifactPath : artifactPaths) {
            toParse.add(new File(artifactPath).getCanonicalPath());
        }

        Set<File> classPathElements = new HashSet<>();
        classPathElements.addAll(outputDirs);
        while (!toParse.isEmpty()) {
            String s = toParse.poll();
            if (!parsedFiles.contains(s)) {
                parsedFiles.add(s);
                File file = new File(s);
                if (file.exists() && file.getName().endsWith(".jar")) {
                    classPathElements.add(file);
                    if (!file.isDirectory()) {
                        try (JarFile jar = new JarFile(file)) {
                            Manifest mf = jar.getManifest();
                            if (mf == null || mf.getMainAttributes() == null) {
                                continue;
                            }
                            Object classPath = mf.getMainAttributes().get(Attributes.Name.CLASS_PATH);
                            if (classPath != null) {
                                for (String i : classPath.toString().split(" ")) {
                                    File f;
                                    try {
                                        URL u = new URL(i);
                                        f = new File(u.getPath());
                                    } catch (MalformedURLException e) {
                                        f = new File(file.getParentFile(), i);
                                    }
                                    if (f.exists()) {
                                        toParse.add(f.getCanonicalPath());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to open class path file " + file, e);
                        }
                    }
                }
            }
        }
        return classPathElements;
    }

    /**
     * Run tests in a new thread.
     * 
     * @param waitForApplicationUpdate whether it should wait for the application to update before running integration tests
     * @param executor the thread pool executor
     * @param messageOccurrences how many times the application updated message has occurred in the log
     * @param forceSkipUTs whether to force skip the unit tests
     * @param manualInvocation whether the tests were manually invoked
     */
    public void runTestThread(boolean waitForApplicationUpdate, ThreadPoolExecutor executor, int messageOccurrences, boolean forceSkipUTs, boolean manualInvocation) {
        try {
            if (manualInvocation || hotTests) {
                executor.execute(new TestJob(waitForApplicationUpdate, messageOccurrences, executor, forceSkipUTs, manualInvocation));
            }
        } catch (RejectedExecutionException e) {
            debug("Cannot add thread since max threads reached", e);
        }
    }

    public class TestJob implements Runnable {
        private boolean waitForApplicationUpdate;
        private int messageOccurrences;
        private ThreadPoolExecutor executor;
        private boolean forceSkipUTs;
        private boolean manualInvocation;

        public TestJob(boolean waitForApplicationUpdate, int messageOccurrences, ThreadPoolExecutor executor, boolean forceSkipUTs, boolean manualInvocation) {
            this.waitForApplicationUpdate = waitForApplicationUpdate;
            this.messageOccurrences = messageOccurrences;
            this.executor = executor;
            this.forceSkipUTs = forceSkipUTs;
            this.manualInvocation = manualInvocation;
        }

        @Override
        public void run() {
            runTests(waitForApplicationUpdate, messageOccurrences, executor, forceSkipUTs);
        }

        public boolean isManualInvocation() {
            return manualInvocation;
        }
    }

}
