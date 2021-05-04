package io.openliberty.tools.common.plugins.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import io.openliberty.tools.common.plugins.util.PluginExecutionException;

public abstract class LooseApplication {
    
    protected final String buildDirectory;
    protected final LooseConfigData config;
    
    private static final String MANIFEST_TARGET = "/META-INF/MANIFEST.MF";
    
    public LooseApplication(String buildDirectory, LooseConfigData config) {
        this.buildDirectory = buildDirectory;
        this.config = config;
    }
    
    public LooseConfigData getConfig() {
        return config;
    }
    
    public Element getDocumentRoot() {
        return config.getDocumentRoot();
    }
    
    public Element addArchive(Element parent, String target) {
        return config.addArchive(parent, target);
    }
    
    public void addOutputDir(Element parent, Path outputDirectory, String target) throws DOMException, IOException {
        addOutputDir(parent, outputDirectory.toFile(), target);

    }

    public void addOutputDir(Element parent, File outputDirectory, String target) throws DOMException, IOException {
        config.addDir(parent, outputDirectory, target);
    }
    
    public void addMetaInfFiles(Element parent, File outputDirectory) throws Exception {
        File metaInfFolder = new File(outputDirectory + "/" + "META-INF");
        boolean existsAndHasAdditionalFiles = metaInfFolder.exists() && metaInfFolder.list().length > 0;
        if (existsAndHasAdditionalFiles) {
            // then we should add each file from this folder
            addFiles(parent, metaInfFolder, "/META-INF");
        }
    }

    private void addFiles(Element parent, File file, String targetPrefix) throws DOMException, IOException {
        for (File subFile : file.listFiles()) {
            if (subFile.isDirectory()) {
                addFiles(parent, subFile, targetPrefix + "/" + subFile.getName());
            } else {
                config.addFile(parent, subFile, targetPrefix + "/" + subFile.getName());
            }
        }
    }
    
    public void addManifestFileWithParent(Element parent, File manifestFile) throws Exception {
        config.addFile(parent, getManifestOrDefault(manifestFile), MANIFEST_TARGET);
    }
    
    public void addManifestFile(File manifestFile) throws Exception {
        config.addFile(getManifestOrDefault(manifestFile), MANIFEST_TARGET);
    } 
    
    private File getManifestOrDefault(File manifestFile) throws IOException, PluginExecutionException {
        File defaultManifestFileLocation = new File(buildDirectory + "/tmp" + MANIFEST_TARGET);
        defaultManifestFileLocation.getParentFile().mkdirs();
        if(manifestFile != null && manifestFile.exists() && manifestFile.isFile()) {
            // Copy the file to a good location (guaranteed inside META-INF folder)
            // See https://github.com/WASdev/ci.gradle/issues/286
            FileUtils.copyFile(manifestFile, defaultManifestFileLocation);
        }
        else {
            // Generate the manifest file at the good location
            FileOutputStream fos = new FileOutputStream(defaultManifestFileLocation);
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.write(fos);
            fos.close();
        }
        return defaultManifestFileLocation;
    }
    
}
