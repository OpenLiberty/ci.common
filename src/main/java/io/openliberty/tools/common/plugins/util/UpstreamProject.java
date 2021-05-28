package io.openliberty.tools.common.plugins.util;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class UpstreamProject {

    private File buildFile;
    private List<String> compileArtifacts;
    private List<String> testArtifacts;
    private File sourceDirectory;
    private File outputDirectory;
    private File testSourceDirectory;
    private File testOutputDirectory;
    private String projectName;
    private List<File> resourceDirs;
    private HashMap<File, Boolean> resourceMap;
    private boolean skipUTs;

    // src/main/java file changes
    public Collection<File> recompileJavaSources;
    public Collection<File> deleteJavaSources;
    public Collection<File> failedCompilationJavaSources;

    // src/test/java file changes
    public Collection<File> recompileJavaTests;
    public Collection<File> deleteJavaTests;
    public Collection<File> failedCompilationJavaTests;

    /**
     * Defines an upstream project for supporting multi-module projects
     * 
     * @param buildFile           pom.xml
     * @param projectName         project name (artifactId)
     * @param compileArtifacts    compileArtifacts of project
     * @param testArtifacts       testArtifacts of project
     * @param sourceDirectory     src/main/java dir
     * @param outputDirectory     output dir
     * @param testSourceDirectory src/test/java dir
     * @param testOutputDirectory test output dir
     * @param resourceDirs        resource directories
     * @param skipUTs
     */
    public UpstreamProject(File buildFile, String projectName, List<String> compileArtifacts,
            List<String> testArtifacts, File sourceDirectory, File outputDirectory, File testSourceDirectory,
            File testOutputDirectory, List<File> resourceDirs, boolean skipUTs) {
        this.buildFile = buildFile;
        this.projectName = projectName;
        this.compileArtifacts = compileArtifacts;
        this.testArtifacts = testArtifacts;
        this.sourceDirectory = sourceDirectory;
        this.outputDirectory = outputDirectory;
        this.testSourceDirectory = testSourceDirectory;
        this.testOutputDirectory = testOutputDirectory;
        this.resourceDirs = resourceDirs;
        this.skipUTs = skipUTs;

        // init src/main/java file tracking collections
        this.recompileJavaSources = new HashSet<File>();
        this.deleteJavaSources = new HashSet<File>();
        this.failedCompilationJavaSources = new HashSet<File>();

        // init src/test/java file tracking collections
        this.recompileJavaTests = new HashSet<File>();
        this.deleteJavaTests = new HashSet<File>();
        this.failedCompilationJavaTests = new HashSet<File>();

        // resource map
        this.resourceMap = new HashMap<File, Boolean>();
    }

    public HashMap<File, Boolean> getResourceMap() {
        return this.resourceMap;
    }

    public void setResourceMap(HashMap<File, Boolean> resourceMap) {
        this.resourceMap = resourceMap;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public File getBuildFile() {
        return this.buildFile;
    }

    public List<String> getCompileArtifacts() {
        return this.compileArtifacts;
    }

    public List<String> getTestArtifacts() {
        return this.testArtifacts;
    }

    public File getSourceDirectory() {
        return this.sourceDirectory;
    }

    public File getOutputDirectory() {
        return this.outputDirectory;
    }

    public File getTestSourceDirectory() {
        return this.testSourceDirectory;
    }

    public File getTestOutputDirectory() {
        return this.testOutputDirectory;
    }

    public List<File> getResourceDirs() {
        return this.resourceDirs;
    }

    public boolean skipUTs() {
        return this.skipUTs;
    }
}