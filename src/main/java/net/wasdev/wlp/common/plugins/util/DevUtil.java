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
import java.util.HashSet;
import java.util.List;
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

import com.sun.nio.file.SensitivityWatchEventModifier;

/**
 * Utility class for dev mode.
 */
public abstract class DevUtil {

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
     * Stop the server
     */
    public abstract void stopServer();

    /**
     * Starts the default server
     */
    public abstract void startServer();

    /**
     * Updates artifacts of current project
     */
    public abstract void getArtifacts(List<String> artifactPaths);

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
     */
    public abstract void checkConfigFile(File configFile);

    /**
     * Compile the specified directory
     * @param dir
     * @return
     */
    public abstract boolean compile(File dir);
    
    /**
     * Restart dev mode
     * @param executor
     */
    public abstract void restartDevMode(ThreadPoolExecutor executor);

    private List<String> jvmOptions;

    private File serverDirectory;
    private File sourceDirectory;
    private File testSourceDirectory;
    private File configDirectory;
    private File defaultConfigDirectory;
    private List<File> resourceDirs;

    public DevUtil(List<String> jvmOptions, File serverDirectory, File sourceDirectory, File testSourceDirectory,
            File configDirectory, File defaultConfigDirectory, List<File> resourceDirs) {
        this.jvmOptions = jvmOptions;
        this.serverDirectory = serverDirectory;
        this.sourceDirectory = sourceDirectory;
        this.testSourceDirectory = testSourceDirectory;
        this.configDirectory = configDirectory;
        this.defaultConfigDirectory = defaultConfigDirectory;
        this.resourceDirs = resourceDirs;
    }
    
    public void cleanUpJVMOptions() {
        // cleaning up jvm.options files
        if (jvmOptions == null || jvmOptions.isEmpty()) {
            File jvmOptionsFile = new File(serverDirectory.getAbsolutePath() + "/jvm.options");
            File jvmOptionsBackup = new File(serverDirectory.getAbsolutePath() + "/jvmBackup.options");
            if (jvmOptionsFile.exists()) {
                debug("Deleting liberty:dev jvm.options file");
                if (jvmOptionsBackup.exists()) {
                    try {
                        Files.copy(jvmOptionsBackup.toPath(), jvmOptionsFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        boolean deleted = jvmOptionsBackup.delete();
                    } catch (IOException e) {
                        error("Could not restore jvm.options: " + e.getMessage());
                    }
                } else {
                    boolean deleted = jvmOptionsFile.delete();
                    if (deleted) {
                        info("Sucessfully deleted liberty:dev jvm.options file");
                    } else {
                        error("Could not delete liberty:dev jvm.options file");
                    }
                }
            }
        }
    }
    
    public void cleanUpServerEnv() {
    	// clean up server.env file
        File serverEnvFile = new File(serverDirectory.getAbsolutePath() + "/server.env");
        File serverEnvBackup = new File(serverDirectory.getAbsolutePath() + "/server.env.bak");
        
        if (serverEnvBackup.exists()) {
        	// Restore original server.env file
        	try {
				Files.copy(serverEnvBackup.toPath(), serverEnvFile.toPath(),
							StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				error("Could not restore server.env: " + e.getMessage());
			}
        	
        	serverEnvBackup.delete();
        } else {
        	// Delete server.env file
        	serverEnvFile.delete();
        }
    }

    public void addShutdownHook(final ThreadPoolExecutor executor) {
        // shutdown hook to stop server when x mode is terminated
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                debug("Inside Shutdown Hook, shutting down server");
                
                cleanUpJVMOptions();
                cleanUpServerEnv();

                // shutdown tests
                executor.shutdown();

                // stopping server
                stopServer();
            }
        });
    }
    
    public void enableServerDebug(int libertyDebugPort) throws IOException {
    	String serverEnvPath = serverDirectory.getAbsolutePath() + "/server.env";
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
    		String line;
    		while ((line = reader.readLine()) != null) {
    			sb.append(line);
    			sb.append("\n");
    		}
    		reader.close();
    	}
    	
    	debug("Creating server.env file: " + serverEnvFile.getAbsolutePath());
    	sb.append("WLP_DEBUG_SUSPEND=n\n");
    	sb.append("WLP_DEBUG_ADDRESS=");
    	sb.append(libertyDebugPort);
    	sb.append("\n");
    	
    	BufferedWriter writer = new BufferedWriter(new FileWriter(serverEnvFile));
    	writer.write(sb.toString());
    	writer.close();
    	
    	if (serverEnvFile.exists()) {
    		info("Successfully created liberty:dev server.env file");
    	}
    }

    public void watchFiles(File buildFile, File outputDirectory, File testOutputDirectory,
            final ThreadPoolExecutor executor, List<String> artifactPaths, boolean noConfigDir, File configFile)
            throws Exception {

        try (WatchService watcher = FileSystems.getDefault().newWatchService();) {
            Path srcPath = sourceDirectory.getAbsoluteFile().toPath();
            Path testSrcPath = testSourceDirectory.getAbsoluteFile().toPath();
            Path configPath = configDirectory.getAbsoluteFile().toPath();

            boolean sourceDirRegistered = false;
            boolean testSourceDirRegistered = false;
            boolean configDirRegistered = false;

            if (this.sourceDirectory.exists()) {
                registerAll(this.sourceDirectory.toPath(), srcPath, watcher);
                sourceDirRegistered = true;
            }

            if (this.testSourceDirectory.exists()) {
                registerAll(this.testSourceDirectory.toPath(), testSrcPath, watcher);
                testSourceDirRegistered = true;
            }

            if (this.configDirectory.exists()) {
                registerAll(this.configDirectory.toPath(), configPath, watcher);
                configDirRegistered = true;
            }
            
            for (File resourceDir : resourceDirs) {
                if (resourceDir.exists()) {
                    registerAll(resourceDir.toPath(), resourceDir.getAbsoluteFile().toPath(), watcher);
                }
            }

            buildFile.getParentFile().toPath().register(
                    watcher, new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE },
                    SensitivityWatchEventModifier.HIGH);
            debug("Watching build file directory: " + buildFile.getParentFile().toPath());

            while (true) {
                // check if javaSourceDirectory has been added
                if (!sourceDirRegistered && this.sourceDirectory.exists()) {
                    compile(this.sourceDirectory);
                    registerAll(this.sourceDirectory.toPath(), srcPath, watcher);
                    debug("Registering Java source directory: " + this.sourceDirectory);
                    sourceDirRegistered = true;
                } else if (sourceDirRegistered && !this.sourceDirectory.exists()) {
                    cleanTargetDir(outputDirectory);
                    sourceDirRegistered = false;
                }

                // check if testSourceDirectory has been added
                if (!testSourceDirRegistered && this.testSourceDirectory.exists()) {
                    compile(this.testSourceDirectory);
                    registerAll(this.testSourceDirectory.toPath(), testSrcPath, watcher);
                    debug("Registering Java test directory: " + this.testSourceDirectory);
                    runTestThread(false, executor, -1, false);
                    testSourceDirRegistered = true;
                } else if (testSourceDirRegistered && !this.testSourceDirectory.exists()) {
                    cleanTargetDir(testOutputDirectory);
                    testSourceDirRegistered = false;
                }
                
                // check if defaultConfigDirectory has been added and restart dev mode if it has
                if (noConfigDir && this.defaultConfigDirectory.exists()){
                    restartDevMode(executor);
                }
                
                // check if configDirectory has been added
                if (!configDirRegistered && this.configDirectory.exists()){
                    restartDevMode(executor);
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
                            if (directory.startsWith(resourceDir.toPath())) {
                                resourceParent = resourceDir;
                            }
                        }
                        
                        int numApplicationUpdatedMessages = countApplicationUpdatedMessages();

                        // src/main/java directory
                        if (directory.startsWith(this.sourceDirectory.toPath())) {
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
                                runTestThread(true, executor, numApplicationUpdatedMessages, false);
                            }
                        } else if (directory.startsWith(this.testSourceDirectory.toPath())) { // src/main/test
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
                                runTestThread(false, executor, -1, false);
                            }
                        } else if (directory.startsWith(this.configDirectory.toPath())) { // config
                                                                                          // files
                            if (fileChanged.exists() && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                    || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                                if (!noConfigDir || fileChanged.getAbsolutePath().endsWith(configFile.getName())) {
                                    copyFile(fileChanged, this.configDirectory, serverDirectory);
                                    checkConfigFile(fileChanged);

                                    // run integration tests only since config files changed
                                    runTestThread(true, executor, numApplicationUpdatedMessages, true);
                                }
                            } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                if (!noConfigDir || fileChanged.getAbsolutePath().endsWith(configFile.getName())) {
                                    info("Config file deleted: " + fileChanged.getName());
                                    deleteFile(fileChanged, this.configDirectory, serverDirectory);

                                    // run integration tests only since config file changed
                                    runTestThread(true, executor, numApplicationUpdatedMessages, true);
                                }
                            }
                        } else if (resourceParent != null && directory.startsWith(resourceParent.toPath())) { // resources
                            debug("Resource dir: " + resourceParent.toString());
                            debug("File within resource directory");
                            if (fileChanged.exists() && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                    || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                                copyFile(fileChanged, resourceParent, outputDirectory);

                                // run all tests on resource change
                                runTestThread(true, executor, numApplicationUpdatedMessages, false);
                            } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                debug("Resource file deleted: " + fileChanged.getName());
                                deleteFile(fileChanged, resourceParent, outputDirectory);
                                // run all tests on resource change
                                runTestThread(true, executor, numApplicationUpdatedMessages, false);
                            }
                        } else if (fileChanged.equals(buildFile)
                                && directory.startsWith(buildFile.getParentFile().toPath())
                                && event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) { // pom.xml

                                    boolean recompiledBuild = recompileBuildFile(buildFile, artifactPaths, executor);
                                    // run all tests on build file change
                                    if (recompiledBuild) {
                                        runTestThread(true, executor, numApplicationUpdatedMessages, false);
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

    public String readFile(File file) throws IOException {
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    public void copyFile(File fileChanged, File srcDir, File targetDir) throws IOException {
        String relPath = fileChanged.getAbsolutePath().substring(
                fileChanged.getAbsolutePath().indexOf(srcDir.getAbsolutePath()) + srcDir.getAbsolutePath().length());

        File targetResource = new File(targetDir.getAbsolutePath() + relPath);
        try {
            FileUtils.copyFile(fileChanged, targetResource);
            info("Copied file: " + fileChanged.getAbsolutePath() + " to: " + targetResource.getAbsolutePath());
        } catch (FileNotFoundException ex) {
            debug("Failed to copy file: " + fileChanged.getAbsolutePath());
        } catch (Exception ex) {
            error("Error occured while copying file: " + ex.getMessage());
        }
    }

    protected void deleteFile(File deletedFile, File dir, File targetDir) {
        debug("File that was deleted: " + deletedFile.getAbsolutePath());
        String relPath = deletedFile.getAbsolutePath().substring(
                deletedFile.getAbsolutePath().indexOf(dir.getAbsolutePath()) + dir.getAbsolutePath().length());
        File targetFile = new File(targetDir.getAbsolutePath() + relPath);
        debug("Target file exists: " + targetFile.exists());
        if (targetFile.exists()) {
            targetFile.delete();
            info("Deleted file: " + targetFile.getAbsolutePath());
        }
    }
    
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
        if (outputDirectory.listFiles().length > 0){
            outputDirectory.delete();
        }
    }

    protected void registerAll(final Path start, final Path dir, final WatchService watcher) throws IOException {
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

    protected void deleteJavaFile(File fileChanged, File classesDir, File compileSourceRoot) {
        if (fileChanged.getName().endsWith(".java")) {
            String fileName = fileChanged.getName().substring(0, fileChanged.getName().indexOf(".java"));
            File parentFile = fileChanged.getParentFile();
            String relPath = parentFile.getAbsolutePath()
                    .substring(parentFile.getAbsolutePath().indexOf(compileSourceRoot.getAbsolutePath())
                            + compileSourceRoot.getAbsolutePath().length())
                    + "/" + fileName + ".class";
            File targetFile = new File(classesDir.getAbsolutePath() + relPath);

            if (targetFile.exists()) {
                targetFile.delete();
                info("Java class deleted: " + targetFile.getAbsolutePath());
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
                    runTestThread(false, executor, -1, false);
                } else {
                    runTestThread(true, executor, messageOccurrences, false);
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

    protected Set<File> getClassPath(List<String> artifactPaths, List<File> outputDirs) {
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
            toParse.add(new File(url.getPath()).getAbsolutePath());
        }

        for (String artifactPath : artifactPaths) {
            toParse.add(new File(artifactPath).getAbsolutePath());
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
                    if (!file.isDirectory() && file.getName().endsWith(".jar")) {
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
                                        toParse.add(f.getAbsolutePath());
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
     * Runt tests in a new thread.
     * 
     * @param waitForApplicationUpdate whether it should wait for the application to update before running integration tests
     * @param executor the thread pool executor
     * @param messageOccurrences how many times the application updated message has occurred in the log
     * @param forceSkipUTs whether to force skip the unit tests
     */
    public void runTestThread(boolean waitForApplicationUpdate, ThreadPoolExecutor executor, int messageOccurrences, boolean forceSkipUTs) {
        try {
            executor.execute(new TestJob(waitForApplicationUpdate, messageOccurrences, executor, forceSkipUTs));
        } catch (RejectedExecutionException e) {
            debug("Cannot add thread since max threads reached", e);
        }
    }

    private class TestJob implements Runnable {
        private boolean waitForApplicationUpdate;
        private int messageOccurrences;
        private ThreadPoolExecutor executor;
        private boolean forceSkipUTs;

        public TestJob(boolean waitForApplicationUpdate, int messageOccurrences, ThreadPoolExecutor executor, boolean forceSkipUTs) {
            this.waitForApplicationUpdate = waitForApplicationUpdate;
            this.messageOccurrences = messageOccurrences;
            this.executor = executor;
            this.forceSkipUTs = forceSkipUTs;
        }

        @Override
        public void run() {
            runTests(waitForApplicationUpdate, messageOccurrences, executor, forceSkipUTs);
        }
    }

}
