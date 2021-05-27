package io.openliberty.tools.common.plugins.util;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class UpstreamProject {

    private File buildFile;
    private List<String> compileArtifacts;
    private File sourceDirectory;
    private File outputDirectory;
    private String projectName;
    private List<File> resourceDirs;
    private HashMap<File, Boolean> resourceMap;

    // src/main/java file changes
    public Collection<File> recompileJavaSources;
    public Collection<File> deleteJavaSources;
    public Collection<File> failedCompilationJavaSources;

    /**
     * Defines an upstream project for supporting multi-module projects
     * 
     * @param buildFile        pom.xml
     * @param projectName      project name (artifactId)
     * @param compileArtifacts compileArtifacts of project
     * @param sourceDirectory  src/main/java dir
     * @param outputDirectory  output dir
     * @param resourceDirs     resource directories
     */
    public UpstreamProject(File buildFile, String projectName, List<String> compileArtifacts, File sourceDirectory,
            File outputDirectory, List<File> resourceDirs) {
        this.buildFile = buildFile;
        this.projectName = projectName;
        this.compileArtifacts = compileArtifacts;
        this.sourceDirectory = sourceDirectory;
        this.outputDirectory = outputDirectory;
        this.resourceDirs = resourceDirs;

        // init src/main/java file tracking collections
        this.recompileJavaSources = new HashSet<File>();
        this.deleteJavaSources = new HashSet<File>();
        this.failedCompilationJavaSources = new HashSet<File>();

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

    public void setCompileArtifacts(List<String> artifacts) {
        this.compileArtifacts = artifacts;
    }

    public List<String> getCompileArtifacts() {
        return this.compileArtifacts;
    }

    public File getSourceDirectory() {
        return this.sourceDirectory;
    }

    public File getOutputDirectory() {
        return this.outputDirectory;
    }

    public List<File> getResourceDirs() {
        return this.resourceDirs;
    }
}