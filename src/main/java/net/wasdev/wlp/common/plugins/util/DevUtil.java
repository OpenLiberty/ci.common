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

import java.io.BufferedWriter;
import java.io.File;
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
import java.util.concurrent.ThreadPoolExecutor;
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
     */
    public abstract void recompileBuildFile(File buildFile, List<String> artifactPaths);

    public abstract int getMessageOccurrences(String regexp, File logFile);

    public abstract void runTestThread(ThreadPoolExecutor executor, String regexp, File logFile,
            int messageOccurrences);

    private List<String> jvmOptions;

    private File serverDirectory;
    private File sourceDirectory;
    private File testSourceDirectory;
    private File configDirectory;
    private List<File> resourceDirs;
    boolean skipTests;
    boolean skipITs;

    public DevUtil(List<String> jvmOptions, File serverDirectory, File sourceDirectory, File testSourceDirectory,
            File configDirectory, List<File> resourceDirs, boolean skipTests, boolean skipITs) {
        this.jvmOptions = jvmOptions;
        this.serverDirectory = serverDirectory;
        this.sourceDirectory = sourceDirectory;
        this.testSourceDirectory = testSourceDirectory;
        this.configDirectory = configDirectory;
        this.resourceDirs = resourceDirs;
        this.skipTests = skipTests;
        this.skipITs = skipITs;
    }

    public void addShutdownHook(final ThreadPoolExecutor executor) {
        // shutdown hook to stop server when x mode is terminated
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                debug("Inside Shutdown Hook, shutting down server");

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
                                jvmOptionsBackup.delete();
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

                // shutdown tests
                executor.shutdown();

                // stopping server
                stopServer();
            }
        });
    }

    public void enableServerDebug(int libertyDebugPort) throws IOException {
        // creating jvm.options file to open a debug port
        debug("jvmOptions: " + jvmOptions);
        if (jvmOptions != null && !jvmOptions.isEmpty()) {
            warn("Cannot start liberty:dev in debug mode because jvmOptions are specified in the server configuration");
        } else {
            File jvmOptionsFile = new File(serverDirectory.getAbsolutePath() + "/jvm.options");
            if (jvmOptionsFile.exists()) {
                debug("jvm.options already exists");
                File jvmOptionsBackup = new File(serverDirectory.getAbsolutePath() + "/jvmBackup.options");
                Files.copy(jvmOptionsFile.toPath(), jvmOptionsBackup.toPath());
                boolean deleted = jvmOptionsFile.delete();
                if (!deleted) {
                    error("Could not move existing liberty:dev jvm.options file");
                }
            }
            debug("Creating jvm.options file: " + jvmOptionsFile.getAbsolutePath());
            StringBuilder sb = new StringBuilder("# Generated by liberty:dev \n");
            sb.append("-Dwas.debug.mode=true\n");
            sb.append("-Dcom.ibm.websphere.ras.inject.at.transform=true\n");
            sb.append("-Dsun.reflect.noInflation=true\n");
            sb.append("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + libertyDebugPort);
            BufferedWriter writer = new BufferedWriter(new FileWriter(jvmOptionsFile));
            writer.write(sb.toString());
            writer.close();
            if (jvmOptionsFile.exists()) {
                info("Successfully created liberty:dev jvm.options file");
            }
        }
    }

    public void watchFiles(Path srcPath, Path testSrcPath, Path configPath, File buildFile, File outputDirectory,
            File testOutputDirectory, final ThreadPoolExecutor executor, List<String> artifactPaths,
            boolean noConfigDir, File configFile) throws Exception {

        try (WatchService watcher = FileSystems.getDefault().newWatchService();) {
            registerAll(this.sourceDirectory.toPath(), srcPath, watcher);
            registerAll(this.testSourceDirectory.toPath(), testSrcPath, watcher);
            registerAll(this.configDirectory.toPath(), configPath, watcher);
            for (File resourceDir : resourceDirs) {
                registerAll(resourceDir.toPath(), resourceDir.getAbsoluteFile().toPath(), watcher);
            }

            buildFile.getParentFile().toPath().register(
                    watcher, new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE },
                    SensitivityWatchEventModifier.HIGH);
            debug("Registering watchservice directory: " + buildFile.getParentFile().toPath());

            while (true) {
                final WatchKey wk = watcher.take();
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

                    // src/main/java directory
                    if (directory.startsWith(this.sourceDirectory.toPath())) {
                        ArrayList<File> javaFilesChanged = new ArrayList<File>();
                        javaFilesChanged.add(fileChanged);
                        if (fileChanged.exists() && fileChanged.getName().endsWith(".java")
                                && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                        || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                            debug("Java source file modified: " + fileChanged.getName());
                            recompileJavaSource(javaFilesChanged, artifactPaths, executor, outputDirectory,
                                    testOutputDirectory);
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            debug("Java file deleted: " + fileChanged.getName());
                            deleteJavaFile(fileChanged, outputDirectory, this.sourceDirectory);
                        }
                    } else if (directory.startsWith(this.testSourceDirectory.toPath())) { // src/main/test
                        ArrayList<File> javaFilesChanged = new ArrayList<File>();
                        javaFilesChanged.add(fileChanged);
                        if (fileChanged.exists() && fileChanged.getName().endsWith(".java")
                                && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                        || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                            recompileJavaTest(javaFilesChanged, artifactPaths, executor, outputDirectory,
                                    testOutputDirectory);
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            debug("Java file deleted: " + fileChanged.getName());
                            deleteJavaFile(fileChanged, testOutputDirectory, this.testSourceDirectory);
                        }
                    } else if (directory.startsWith(this.configDirectory.toPath())) { // config
                                                                                      // files
                        if (fileChanged.exists() && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                            if (!noConfigDir || fileChanged.getAbsolutePath().endsWith(configFile.getName())) {
                                copyFile(fileChanged, this.configDirectory, serverDirectory);
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            if (!noConfigDir || fileChanged.getAbsolutePath().endsWith(configFile.getName())) {
                                info("Config file deleted: " + fileChanged.getName());
                                deleteFile(fileChanged, this.configDirectory, serverDirectory);
                            }
                        }
                    } else if (resourceParent != null && directory.startsWith(resourceParent.toPath())) { // resources
                        debug("Resource dir: " + resourceParent.toString());
                        debug("File within resource directory");
                        if (fileChanged.exists() && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                || event.kind() == StandardWatchEventKinds.ENTRY_CREATE)) {
                            copyFile(fileChanged, resourceParent, outputDirectory);
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            debug("Resource file deleted: " + fileChanged.getName());
                            deleteFile(fileChanged, resourceParent, outputDirectory);
                        }
                    } else if (fileChanged.equals(buildFile) && directory.startsWith(buildFile.getParentFile().toPath())
                            && event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) { // pom.xml
                        recompileBuildFile(buildFile, artifactPaths);
                    }
                }
                // reset the key
                boolean valid = wk.reset();
                if (!valid) {
                    info("WatchService key has been unregistered");
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
        info("Copying file: " + fileChanged.getAbsolutePath() + " to: " + targetResource.getAbsolutePath());
        FileUtils.copyFile(fileChanged, targetResource);
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

    protected void registerAll(final Path start, final Path dir, final WatchService watcher) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                debug("Registering watchservice directory: " + dir.toString());
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

    protected void recompileJavaSource(List<File> javaFilesChanged, List<String> artifactPaths,
            ThreadPoolExecutor executor, File outputDirectory, File testOutputDirectory) throws Exception {
        recompileJava(javaFilesChanged, artifactPaths, executor, false, outputDirectory, testOutputDirectory);
    }

    protected void recompileJavaTest(List<File> javaFilesChanged, List<String> artifactPaths,
            ThreadPoolExecutor executor, File outputDirectory, File testOutputDirectory) throws Exception {
        recompileJava(javaFilesChanged, artifactPaths, executor, true, outputDirectory, testOutputDirectory);
    }

    protected void recompileJava(List<File> javaFilesChanged, List<String> artifactPaths, ThreadPoolExecutor executor,
            boolean tests, File outputDirectory, File testOutputDirectory) {
        try {
            File logFile = null;
            String regexp = null;
            int messageOccurrences = -1;
            if (!(this.skipTests || this.skipITs)) {
                getMessageOccurrences(regexp, logFile);
            }

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
                    runTestThread(executor, null, null, -1);
                } else {
                    runTestThread(executor, regexp, logFile, messageOccurrences);
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

}
