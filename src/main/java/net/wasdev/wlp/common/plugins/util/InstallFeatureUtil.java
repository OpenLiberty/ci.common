/**
 * (C) Copyright IBM Corporation 2018.
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
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Utility class to install features from Maven repositories.
 */
public abstract class InstallFeatureUtil {
    
    private final File installDirectory;
    
    private final File installJarFile;
    
    private final String to;
    
    private final Set<File> downloadedJsons;
    
    private static final String INSTALL_MAP_PREFIX = "com.ibm.ws.install.map";
    private static final String INSTALL_MAP_SUFFIX = ".jar";
    
    /**
     * Initialize the utility and check for unsupported scenarios.
     * 
     * @param installDirectory The install directory
     * @param from The "from" parameter specified in the plugin configuration, or null if not specified
     * @param to The "to" parameter specified in the plugin configuration, or null if not specified
     * @param pluginListedEsas The list of ESAs specified in the plugin configuration, or null if not specified
     * @throws PluginScenarioException If the current scenario is not supported
     * @throws PluginExecutionException If properties files cannot be found in the installDirectory/lib/versions
     */
    public InstallFeatureUtil(File installDirectory, String from, String to, Set<String> pluginListedEsas) throws PluginScenarioException, PluginExecutionException {
        this.installDirectory = installDirectory;
        this.to = to;
        installJarFile = getMapBasedInstallKernelJar(new File(installDirectory, "lib"));
        if (installJarFile == null) {
            throw new PluginScenarioException("Install map jar not found.");
        }
        downloadedJsons = downloadProductJsons(installDirectory);
        if (downloadedJsons.isEmpty()) {
            throw new PluginScenarioException("Cannot find JSONs for to the installed runtime from the Maven repository.");
        }
        if (hasUnsupportedParameters(from, pluginListedEsas)) {
            throw new PluginScenarioException("Cannot install features from a Maven repository when using the 'to' or 'from' parameters or when specifying ESA files.");
        }
    }
    
    /**
     * Log debug
     * @param msg
     */
    public abstract void debug(String msg);

    /**
     * Log debug
     * @param msg
     * @param e
     */
    public abstract void debug(String msg, Throwable e);

    /**
     * Log debug
     * @param e
     */
    public abstract void debug(Throwable e);

    /**
     * Log warning
     * @param msg
     */
    public abstract void warn(String msg);

    /**
     * Log info
     * @param msg
     */
    public abstract void info(String msg);

    /**
     * Returns whether debug is enabled by the current logger
     * @return whether debug is enabled
     */
    public abstract boolean isDebugEnabled();

    /**
     * Download the artifact from the specified Maven coordinates, or retrieve it from the cache if it already exists.
     * 
     * @param groupId The group ID
     * @param artifactId The artifact ID
     * @param type The type e.g. esa
     * @param version The version
     * @return The file corresponding to the downloaded artifact
     * @throws PluginExecutionException If the artifact could not be downloaded
     */
    public abstract File downloadArtifact(String groupId, String artifactId, String type, String version)
            throws PluginExecutionException;
    
    /**
     * Combine the given String collections into a set
     * 
     * @param collections a collection of strings
     * @return the combined set of strings
     */
    @SafeVarargs
    public static Set<String> combineToSet(Collection<String>... collections) {
        Set<String> result = new HashSet<String>();
        for (Collection<String> collection : collections) {
            if (collection != null) {
                result.addAll(collection);
            }
        }
        return result;
    }
    
    /**
     * Get the set of features defined in the server.xml if there were no features listed in the plugin configuration
     * @param serverDirectory The server directory containing the server.xml
     * @param noPluginListedFeatures true if there were no features listed in the plugin configuration
     * @return the set of features that should be installed from server.xml
     */
    public Set<String> getServerFeatures(File serverDirectory, boolean noPluginListedFeatures) {
        // parse server.xml features only if there are no configured features in the pom
        if (noPluginListedFeatures) {
            debug("No features were listed for the plugin. Using server.xml.");
            return getServerFeatures(serverDirectory);
        } else {
            debug("Features were listed for the plugin. Skipping server.xml.");
            return new HashSet<String>();
        }
    }

    /**
     * Get the set of features defined in the server.xml
     * @param serverDirectory The server directory containing the server.xml
     * @return the set of features that should be installed from server.xml, or empty set if nothing should be installed
     */
    public static Set<String> getServerFeatures(File serverDirectory) {
        Set<String> defaults = getConfigDropinsFeatures(serverDirectory, "defaults");
        Set<String> defaultsAndServerXmlFeatures = getServerXmlFeatures(defaults, new File(serverDirectory, "server.xml"), null);
        // add the overrides at the end since they should not be replaced by any other content
        Set<String> overrides = getConfigDropinsFeatures(serverDirectory, "overrides");
        return combineToSet(defaultsAndServerXmlFeatures, overrides);
    }
    
    /**
     * Gets features from the configDropins's defaults or overrides directory
     * 
     * @param serverDirectory
     *            The server directory
     * @param folderName
     *            The folder under configDropins: either "defaults" or
     *            "overrides"
     * @return The set of features to install, or empty set if the folder has xml
     *         files with featureManager sections but no features to install, or
     *         null if there are no xml files or they have no featureManager
     *         section
     */
    private static Set<String> getConfigDropinsFeatures(File serverDirectory, String folderName) {
        File configDropinsFolder;
        try {
            configDropinsFolder = new File(new File(serverDirectory, "configDropins"), folderName).getCanonicalFile();
        } catch (IOException e) {
            // skip this directory if its path cannot be queried
            return null;
        }
        File[] configDropinsXmls = configDropinsFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        });
        if (configDropinsXmls == null || configDropinsXmls.length == 0) {
            return null;
        }
        Set<String> result = null;
        for (File xml : configDropinsXmls) {
            Set<String> features = getServerXmlFeatures(null, xml, null);
            if (features != null) {
                if (result == null) {
                    result = new HashSet<String>();
                }
                result.addAll(features);
            }
        }
        return result;
    }

    /**
     * Adds features from the given server file into the origResult or a new set
     * if origResult is null.
     * 
     * @param origResult
     *            The features that have been parsed so far.
     * @param serverFile
     *            The server XML file.
     * @param parsedXmls
     *            The list of XML files that have been parsed so far.
     * @return list of features that should be installed according to the
     *         origResult and the current serverFile, or empty set if the file
     *         (or its children) only has a featureManager section with no
     *         features, or null if the file (and all of its children) has no
     *         featureManager section
     */
    private static Set<String> getServerXmlFeatures(Set<String> origResult, File serverFile, List<File> parsedXmls) {
        Set<String> result = origResult;
        List<File> updatedParsedXmls = new ArrayList<File>();
        File canonicalServerFile;
        try {
            canonicalServerFile = serverFile.getCanonicalFile();
        } catch (IOException e) {
            // skip this server.xml if its path cannot be queried
            return result;
        }
        updatedParsedXmls.add(canonicalServerFile);
        if (parsedXmls != null) {
            updatedParsedXmls.addAll(parsedXmls);
        }
        if (canonicalServerFile.exists()) {
            try {
                Document doc = new XmlDocument() {
                    public Document getDocument(File file) throws IOException, ParserConfigurationException, SAXException {
                        createDocument(file);
                        return doc;
                    }
                }.getDocument(canonicalServerFile);
                Element root = doc.getDocumentElement();
                NodeList nodes = root.getChildNodes();

                for (int i = 0; i < nodes.getLength(); i++) {
                    if (nodes.item(i) instanceof Element) {
                        Element child = (Element) nodes.item(i);
                        if ("featureManager".equals(child.getNodeName())) {
                            if (result == null) {
                                result = new HashSet<String>();
                            }
                            result.addAll(parseFeatureManagerNode(child));
                        } else if ("include".equals(child.getNodeName())){
                            result = parseIncludeNode(result, canonicalServerFile, child, updatedParsedXmls);
                        }
                    }
                }
            } catch (IOException | ParserConfigurationException | SAXException e) {
                // just skip this server.xml if it cannot be parsed
                return result;
            }
        }
        return result;
    }
    
    /**
     * Parse features from an include node.
     * 
     * @param origResult
     *            The features that have been parsed so far.
     * @param serverFile
     *            The parent server XML file containing the include node.
     * @param node
     *            The include node.
     * @param updatedParsedXmls
     *            The list of XML files that have been parsed so far.
     * @return updated list of features that should be installed, or null if no
     *         featureManager section had been found so far.
     */
    private static Set<String> parseIncludeNode(Set<String> origResult, File serverFile, Element node,
            List<File> updatedParsedXmls) {
        Set<String> result = origResult;
        String includeFileName = node.getAttribute("location");
        File includeFile = new File(includeFileName);
        try {
            if (!includeFile.isAbsolute()) {
                includeFile = new File(serverFile.getParentFile().getAbsolutePath(), includeFileName)
                        .getCanonicalFile();
            } else {
                includeFile = includeFile.getCanonicalFile();
            }
        } catch (IOException e) {
            // skip this xml if its path cannot be queried
            return result;
        }
        if (!updatedParsedXmls.contains(includeFile)) {
            String onConflict = node.getAttribute("onConflict");
            if ("".equals(onConflict) || "merge".equalsIgnoreCase(onConflict)) {
                Set<String> features = getServerXmlFeatures(null, includeFile, updatedParsedXmls);
                if (features != null) {
                    if (result == null) {
                        result = features;
                    } else {
                        result.addAll(features);
                    }
                }                
            } else if ("replace".equalsIgnoreCase(onConflict)) {
                Set<String> features = getServerXmlFeatures(null, includeFile, updatedParsedXmls);
                if (features != null && !features.isEmpty()) {
                    // only replace if the child has features
                    result = features;
                }
            } else if ("ignore".equalsIgnoreCase(onConflict)) {
                Set<String> features = getServerXmlFeatures(null, includeFile, updatedParsedXmls);
                if (result == null) {
                    // parent has no results (i.e. no featureManager section), so use the child's results
                    result = features;
                } // else the parent already has some results (even if it's empty), so ignore the child
            }
        }
        return result;
    }

    private static List<String> parseFeatureManagerNode(Element node) {
        List<String> result = new ArrayList<String>();
        NodeList features = node.getElementsByTagName("feature");
        if (features != null) {
            for (int j = 0; j < features.getLength(); j++) {
                result.add(features.item(j).getTextContent());
            }
        }
        return result;
    }
    
    /**
     * Get the JSON files corresponding to the product properties from the lib/versions/*.properties files
     * @param installDirectory The install directory
     * @return the set of JSON files for the product
     * @throws PluginExecutionException if properties files could not be found from lib/versions
     */
    private Set<File> downloadProductJsons(File installDirectory) throws PluginExecutionException {
        // get productId and version for all properties
        File versionsDir = new File(installDirectory, "lib/versions");
        List<ProductProperties> propertiesList = loadProperties(versionsDir);

        if (propertiesList.isEmpty()) {
            throw new PluginExecutionException("Could not find any properties file in the " + versionsDir
                    + " directory. Ensure the directory " + installDirectory + " contains a Liberty installation.");
        }
        
        // download JSONs
        Set<File> downloadedJsons = new HashSet<File>();
        for (ProductProperties properties : propertiesList) {
            File json = downloadJsons(properties.getId(), properties.getVersion());
            if (json != null) {
                downloadedJsons.add(json);
            }
        }
        return downloadedJsons;
    }
    
    /**
     * Download the JSON file for the given product.
     * 
     * @param productId The product ID from the runtime's properties file
     * @param productVersion The product version from the runtime's properties file
     * @return The JSON file, or null if not found
     */
    private File downloadJsons(String productId, String productVersion) {
        String jsonGroupId = productId + ".features";        
        try {
            return downloadArtifact(jsonGroupId, "features", "json", productVersion);
        } catch (PluginExecutionException e) {
            debug("Cannot find json for productId " + productId + ", productVersion " + productVersion, e);
            return null;
        }
    }
    
    private List<ProductProperties> loadProperties(File dir) throws PluginExecutionException {
        List<ProductProperties> list = new ArrayList<ProductProperties>();

        File[] propertiesFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".properties");
            }
        });

        if (propertiesFiles != null) {
            for (File propertiesFile : propertiesFiles) {
                Properties properties = new Properties();
                InputStream input = null;
                try {
                    input = new FileInputStream(propertiesFile);
                    properties.load(input);
                    String productId = properties.getProperty("com.ibm.websphere.productId");
                    String productVersion = properties.getProperty("com.ibm.websphere.productVersion");
                    if (productId == null) {
                        throw new PluginExecutionException(
                                "Cannot find the \"com.ibm.websphere.productId\" property in the file "
                                        + propertiesFile.getAbsolutePath()
                                        + ". Ensure the file is valid properties file for the Liberty product or extension.");
                    }
                    if (productVersion == null) {
                        throw new PluginExecutionException(
                                "Cannot find the \"com.ibm.websphere.productVersion\" property in the file "
                                        + propertiesFile.getAbsolutePath()
                                        + ". Ensure the file is valid properties file for the Liberty product or extension.");
                    }
                    list.add(new ProductProperties(productId, productVersion));
                } catch (IOException e) {
                    throw new PluginExecutionException("Cannot read the product properties file " + propertiesFile.getAbsolutePath(), e);
                } finally {
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }

        return list;
    }
    
    private class ProductProperties {
        private String id;
        private String version;
        
        public ProductProperties(String id, String version) {
            this.id = id;
            this.version = version;
        }
        
        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }
    }
    
    /**
     * Returns true if this scenario is not supported for installing from Maven
     * repository, which is one of the following conditions: "from" parameter is
     * specified (don't need Maven repositories), or esa files are specified in
     * the configuration (not supported with Maven for now)
     * 
     * @param from
     *            the "from" parameter specified in the plugin
     * @param pluginListedEsas
     *            the ESA files specified in the plugin configuration
     * @return true if the fallback scenario occurred, false otherwise
     */
    private boolean hasUnsupportedParameters(String from, Set<String> pluginListedEsas) {
        boolean hasFrom = from != null;
        boolean hasPluginListedEsas = !pluginListedEsas.isEmpty();
        debug("hasFrom: " + hasFrom);
        debug("hasPluginListedEsas: " + hasPluginListedEsas);
        return hasFrom || hasPluginListedEsas;
    }
    
    private File downloadEsaArtifact(String mavenCoordinates) throws PluginExecutionException {
        String[] mavenCoordinateArray = mavenCoordinates.split(":");
        String groupId = mavenCoordinateArray[0];
        String artifactId = mavenCoordinateArray[1];
        String version = mavenCoordinateArray[2];
        return downloadArtifact(groupId, artifactId, "esa", version);
    }
    
    private List<File> downloadEsas(Collection<?> mavenCoordsList) throws PluginExecutionException{
        List<File> repoPaths = new ArrayList<File>();
        for (Object coordinate : mavenCoordsList) {
            repoPaths.add(downloadEsaArtifact((String) coordinate));
        }
        return repoPaths;
    }
    
    /**
     * Resolve, download, and install features from a Maven repository. This
     * method calls the resolver with the given JSONs and feature list,
     * downloads the ESAs corresponding to the resolved features, then installs
     * those features.
     * 
     * @param jsonRepos
     *            JSON files, each containing an array of metadata for all
     *            features in a Liberty release.
     * @param featuresToInstall
     *            The list of features to install.
     * @throws PluginExecutionException
     *             if any of the features could not be installed
     */
    @SuppressWarnings("unchecked")
    public void installFeatures(boolean isAcceptLicense, List<String> featuresToInstall) throws PluginExecutionException {
        List<File> jsonRepos = new ArrayList<File>(downloadedJsons);
        debug("JSON repos: " + jsonRepos);
        info("Installing features: " + featuresToInstall);

        try {
            Map<String, Object> mapBasedInstallKernel = createMapBasedInstallKernelInstance(installDirectory);
            mapBasedInstallKernel.put("install.local.esa", true);
            mapBasedInstallKernel.put("single.json.file", jsonRepos);
            mapBasedInstallKernel.put("features.to.resolve", featuresToInstall);
            mapBasedInstallKernel.put("license.accept", isAcceptLicense);

            if (isDebugEnabled()) {
                mapBasedInstallKernel.put("debug", Level.FINEST);
            }

            Collection<?> resolvedFeatures = (Collection<?>) mapBasedInstallKernel.get("action.result");
            if (resolvedFeatures == null) {
                debug("action.exception.stacktrace: "+mapBasedInstallKernel.get("action.exception.stacktrace"));
                String exceptionMessage = (String) mapBasedInstallKernel.get("action.error.message");
                throw new PluginExecutionException(exceptionMessage);
            } else if (resolvedFeatures.isEmpty()) {
                debug("action.exception.stacktrace: "+mapBasedInstallKernel.get("action.exception.stacktrace"));
                String exceptionMessage = (String) mapBasedInstallKernel.get("action.error.message");
                if (exceptionMessage.contains("CWWKF1250I")){
                    info(exceptionMessage);
                    info("The features are already installed, so no action is needed.");
                    return;
                } else {
                    throw new PluginExecutionException(exceptionMessage);
                }
            }
            Collection<File> artifacts = downloadEsas(resolvedFeatures);

            StringBuilder installedFeaturesBuilder = new StringBuilder();
            Collection<String> actionReturnResult = new ArrayList<String>();
            for (File esaFile: artifacts ){
                mapBasedInstallKernel.put("license.accept", isAcceptLicense);
                mapBasedInstallKernel.put("action.install", esaFile);
                mapBasedInstallKernel.put("to.extension", to);
                debug("Installing to extension: " + to);
                Integer ac = (Integer) mapBasedInstallKernel.get("action.result");
                debug("action.result: "+ac);
                debug("action.error.message: "+mapBasedInstallKernel.get("action.error.message"));
                if (mapBasedInstallKernel.get("action.error.message") != null) {
                    debug("action.exception.stacktrace: "+mapBasedInstallKernel.get("action.exception.stacktrace"));
                    String exceptionMessage = (String) mapBasedInstallKernel.get("action.error.message");
                    debug(exceptionMessage);
                    throw new PluginExecutionException(exceptionMessage);
                } else if (mapBasedInstallKernel.get("action.install.result") != null) {
                    actionReturnResult.addAll((Collection<String>) mapBasedInstallKernel.get("action.install.result"));
                }
            }
            for (String installResult : actionReturnResult) {
                installedFeaturesBuilder.append(installResult).append(" ");
            }
            productInfoValidate();
            info("The following features have been installed: " + installedFeaturesBuilder.toString());
        } catch (PrivilegedActionException e) {
            throw new PluginExecutionException("Could not load the jar " + installJarFile.getAbsolutePath(), e);
        }
    }
    
    private Map<String, Object> createMapBasedInstallKernelInstance(File installDirectory) throws PrivilegedActionException, PluginExecutionException {
        String installJarFileSubpath = installJarFile.getParentFile().getName() + File.separator + installJarFile.getName();
        Map<String, Object> mapBasedInstallKernel = AccessController.doPrivileged(new PrivilegedExceptionAction<Map<String, Object>>() {
            @SuppressWarnings({ "unchecked", "resource" })
            @Override
            public Map<String, Object> run() throws Exception {
                ClassLoader loader = new URLClassLoader(new URL[] { installJarFile.toURI().toURL() }, null);
                Class<Map<String, Object>> clazz;
                clazz = (Class<Map<String, Object>>) loader.loadClass("com.ibm.ws.install.map.InstallMap");
                return clazz.newInstance();
            }
        });
        if (mapBasedInstallKernel == null){
            throw new PluginExecutionException("Cannot run install jar file " + installJarFile);
        }

        // Init
        mapBasedInstallKernel.put("runtime.install.dir", installDirectory);
        mapBasedInstallKernel.put("install.map.jar", installJarFileSubpath);
        debug("install.map.jar: " + installJarFileSubpath);
        debug("install.kernel.init.code: " + mapBasedInstallKernel.get("install.kernel.init.code"));
        debug("install.kernel.init.error.message: " + mapBasedInstallKernel.get("install.kernel.init.error.message"));
        File usrDir = new File(installDirectory, "usr/tmp");
        mapBasedInstallKernel.put("target.user.directory", usrDir);
        return mapBasedInstallKernel;
    }
    
    /**
     * Find latest install map jar from specified directory
     * 
     * @return the install map jar file
     */
    public static File getMapBasedInstallKernelJar(File dir) {

        File[] installMapJars = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(INSTALL_MAP_PREFIX) && name.endsWith(INSTALL_MAP_SUFFIX);
            }
        });

        File result = null;
        if (installMapJars != null) {
            for (File jar : installMapJars) {
                if (isReplacementJar(result, jar)) {
                    result = jar;
                }
            }
        }

        return result;
    }
    
    /**
     * Returns whether file2 can replace file1 as the install map jar.
     */
    private static boolean isReplacementJar(File file1, File file2) {
        if (file1 == null) {
            return true;
        } else if (file2 == null) {
            return false;
        } else {
            String version1 = extractVersion(file1.getName());
            String version2 = extractVersion(file2.getName());
            return compare(version1, version2) < 0;
        }
    }
    
    private static String extractVersion(String fileName) {
        int startIndex = INSTALL_MAP_PREFIX.length() + 1; // skip the underscore after the prefix
        int endIndex = fileName.lastIndexOf(INSTALL_MAP_SUFFIX);
        if (startIndex < endIndex) {
            return fileName.substring(startIndex, endIndex);
        } else {
            return null;
        }
    }

    /**
     * Performs pairwise comparison of version strings, including nulls and non-integer components.
     * @param version1
     * @param version2
     * @return positive if version2 is greater, negative if version1 is greater, otherwise 0
     */
    private static int compare(String version1, String version2) {
        if (version1 == null && version2 == null) {
            return 0;
        } else if (version1 == null && version2 != null) {
            return -1;
        } else if (version1 != null && version2 == null) {
            return 1;
        }
        String[] components1 = version1.split("\\.");
        String[] components2 = version2.split("\\.");
        for (int i = 0; i < components1.length && i < components2.length; i++) {
            int comparison;
            try {
                comparison = new Integer(components1[i]).compareTo(new Integer(components2[i]));
            } catch (NumberFormatException e) {
                comparison = components1[i].compareTo(components2[i]);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return components1.length - components2.length;
    }
    
    /**
     * Performs product validation by running bin/productInfo validate
     * 
     * @throws PluginExecutionException
     *             if product validation failed or could not be run
     */
    public void productInfoValidate() throws PluginExecutionException {
        Process pr = null;
        InputStream is = null;
        Scanner s = null;
        Worker worker = null;
        try {
            String command;
            if (OSUtil.isWindows()) {
                command = installDirectory + "\\bin\\productInfo.bat validate";
            } else {
                command = installDirectory + "/bin/productInfo validate";
            }
            pr = Runtime.getRuntime().exec(command);
            worker = new Worker(pr);
            worker.start();
            worker.join(300000);
            if (worker.exit == null) {
                throw new PluginExecutionException("Product validation error: timeout");
            }
            int exitValue = pr.exitValue();
            if (exitValue != 0) {
                is = pr.getInputStream();
                s = new Scanner(is);
                // use regex to match the beginning of the input
                s.useDelimiter("\\A");
                if (s.hasNext()) {
                    throw new PluginExecutionException(s.next());
                } else {
                    throw new PluginExecutionException("Product validation exited with return code " + exitValue);
                }
            } else {
                info("Product validation completed successfully.");
            }
        } catch (IOException ex) {
            throw new PluginExecutionException("Product validation error: " + ex);
        } catch (InterruptedException ex) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new PluginExecutionException("Product validation error: " + ex);
        } finally {
            if (s != null) {
                s.close();
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
            if (pr != null) {
                pr.destroy();
            }
        }
    }

    private static class Worker extends Thread {
        private final Process process;
        private Integer exit;

        private Worker(Process process) {
            this.process = process;
        }

        public void run() {
            try {
                exit = process.waitFor();
            } catch (InterruptedException ignore) {
                return;
            }
        }
    }

}
