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
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.StreamSupport;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import io.openliberty.tools.common.CommonLoggerI;

/**
 * Utility class to determine server features
 */
public abstract class ServerFeatureUtil extends AbstractContainerSupportUtil implements CommonLoggerI {
    
    public static final String OPEN_LIBERTY_GROUP_ID = "io.openliberty.features";
    public static final String REPOSITORY_RESOLVER_ARTIFACT_ID = "repository-resolver";
    public static final String INSTALL_MAP_ARTIFACT_ID = "install-map";
    private static final int COPY_FILE_TIMEOUT_MILLIS = 5 * 60 * 1000;

    public static final String WLP_INSTALL_DIR = "wlp.install.dir";
    public static final String WLP_USER_DIR = "wlp.user.dir";
    public static final String USR_EXTENSION_DIR = "usr.extension.dir";
    public static final String SHARED_APP_DIR = "shared.app.dir";
    public static final String SHARED_CONFIG_DIR = "shared.config.dir";
    public static final String SHARED_RESOURCES_DIR = "shared.resource.dir";
    public static final String SHARED_STACKGROUP_DIR = "shared.stackgroup.dir";
    public static final String SERVER_CONFIG_DIR = "server.config.dir";

    private Map<String, File> libertyDirectoryPropertyToFile = null;
    private boolean lowerCaseFeatures = true;
    protected boolean suppressLogs = false; // set to true when info and warning messages should not be displayed to
                                            // users, messages are logged as debug instead
  
    protected File installJarFile;
    private URLClassLoader installMapLoader = null;
    private Class<Map<String, Object>> installMapClass = null;
    protected Map<String, Object> mapBasedInstallKernel= null;
    
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
     * Log error
     * @param msg
     */
    public abstract void error(String msg);

    /**
     * Log error
     * @param msg
     * @param e
     */
    public abstract void error(String msg, Throwable e);

    /**
     * Returns whether debug is enabled by the current logger
     * 
     * @return whether debug is enabled
     */
    public abstract boolean isDebugEnabled();

    public void setLibertyDirectoryPropertyFiles(Map<String,File> libertyDirPropFiles) {
        if (libertyDirPropFiles != null) {
            libertyDirectoryPropertyToFile = new HashMap<String, File> (libertyDirPropFiles);
        }
    }

    public Map<String, File> getLibertyDirectoryPropertyFiles() {
        if (libertyDirectoryPropertyToFile == null) {
            return new HashMap<String,File>();
        }
        return libertyDirectoryPropertyToFile;
    }

    /**
     * Get the set of features defined in the server.xml
     * @param serverDirectory The server directory containing the server.xml
     * @param serverXmlFile The server.xml file
     * @param libertyDirPropFiles Map of Liberty directory properties to the actual File for each directory
     * @param dropinsFilesToIgnore A set of file names under configDropins/overrides or configDropins/defaults to ignore
     * @return the set of features that should be installed from server.xml, or empty set if nothing should be installed
     *         or null if there are no valid xml files or they have no featureManager section
     */
     public Set<String> getServerFeatures(File serverDirectory, File serverXmlFile, Map<String,File> libertyDirPropFiles, Set<String> dropinsFilesToIgnore) {
        if (libertyDirPropFiles != null) {
            setLibertyDirectoryPropertyFiles(libertyDirPropFiles);
        } else if (libertyDirectoryPropertyToFile == null) {
            warn("The properties for directories are null and could lead to server include files not being processed for server features.");
        }
        Properties bootstrapProperties = getBootstrapProperties(new File(serverDirectory, "bootstrap.properties"));
        Set<String> result = getConfigDropinsFeatures(null, serverDirectory, bootstrapProperties, "defaults", dropinsFilesToIgnore);
        if (serverXmlFile == null) {
            serverXmlFile = new File(serverDirectory, "server.xml");
        }

        // CLK999 Need to also handle server.env and variables in server.xml with default values in addition to bootstrap.properties.
        result = getServerXmlFeatures(result, serverDirectory, serverXmlFile, bootstrapProperties, null);
        // add the overrides at the end since they should not be replaced by any previous content
        return getConfigDropinsFeatures(result, serverDirectory, bootstrapProperties, "overrides", dropinsFilesToIgnore);
    }

    /**
     * Get the set of features defined in the server.xml
     * @param serverDirectory The server directory containing the server.xml
     * @param libertyDirPropFiles Map of Liberty directory properties to the actual File for each directory
     * @param dropinsFilesToIgnore A set of file names under configDropins/overrides or configDropins/defaults to ignore
     * @return the set of features that should be installed from server.xml, or empty set if nothing should be installed
     *         or null if there are no valid xml files or they have no featureManager section
     */
    public Set<String> getServerFeatures(File serverDirectory, Map<String,File> libertyDirPropFiles, Set<String> dropinsFilesToIgnore) {
        return getServerFeatures(serverDirectory, new File(serverDirectory, "server.xml"), libertyDirPropFiles, dropinsFilesToIgnore);
    }

    /**
     * Get the set of features defined in the server.xml
     * @param serverDirectory The server directory containing the server.xml
     * @param libertyDirPropFiles Map of Liberty directory properties to the actual File for each directory
     * @return the set of features that should be installed from server.xml, or empty set if nothing should be installed
     *         or null if there are no valid xml files or they have no featureManager section
     */
    public Set<String> getServerFeatures(File serverDirectory, Map<String,File> libertyDirPropFiles) {
        return getServerFeatures(serverDirectory, libertyDirPropFiles, null);
    }

    /**
     * Indicate whether the feature names should be converted to lower case. The default is to make all the names lower case.
     * @param val boolean false to indicate names should remain mixed case as defined in Liberty.
     *            True indicates the names will be folded to lower case.
     */
    public void setLowerCaseFeatures(boolean val) {
        lowerCaseFeatures = val;
    }

    /**
     * Indicate whether the info and warning messages should not be displayed. Used
     * as part of the dev mode flow to avoid flooding the console.
     * 
     * @param val boolean true to indicate that info and warning log messages should be not displayed.
     */
    public void setSuppressLogs(boolean val) {
        suppressLogs = val;
    }
    
    /**
     * Gets features from the configDropins's defaults or overrides directory
     * 
     * @param origResult           The features that have been parsed so far.
     * @param serverDirectory      The server directory
     * @param bootstrapProperties  Bootstrap proeprties
     * @param folderName           The folder under configDropins: either "defaults"
     *                             or "overrides"
     * @param dropinsFilesToIgnore A set of file names under the given folderName
     *                             that should be ignored
     * @return The set of features to install, or empty set if the cumulatively
     *         parsed xml files only have featureManager sections but no features to
     *         install, or null if there are no valid xml files or they have no
     *         featureManager section
     */
    private Set<String> getConfigDropinsFeatures(Set<String> origResult, File serverDirectory, Properties bootstrapProperties, String folderName, final Set<String> dropinsFilesToIgnore) {
        Set<String> result = origResult;
        File configDropinsFolder;
        try {
            configDropinsFolder = new File(new File(serverDirectory, "configDropins"), folderName).getCanonicalFile();
        } catch (IOException e) {
            // skip this directory if its path cannot be queried
            warn("The " + serverDirectory + "/configDropins/" + folderName + " directory cannot be accessed. Skipping its server features.");
            debug("Exception received: "+e.getMessage(), e);
            return result;
        }
        File[] configDropinsXmls = configDropinsFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml") && (dropinsFilesToIgnore == null || !dropinsFilesToIgnore.contains(name));
            }
        });
        if (configDropinsXmls == null || configDropinsXmls.length == 0) {
            return result;
        }
        // sort the files in alphabetical order so that overrides will happen in the proper order
        Comparator<File> comparator = new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getAbsolutePath().toLowerCase().compareTo(right.getAbsolutePath().toLowerCase());
            }
        };
        Collections.sort(Arrays.asList(configDropinsXmls), comparator);

        for (File xml : configDropinsXmls) {
            Set<String> features = getServerXmlFeatures(result, serverDirectory, xml, bootstrapProperties,null);
            if (features != null) {
                result = features;
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
     * @param serverDirectory 
     *            The server directory containing the server.xml.
     * @param serverFile
     *            The server XML file.
     * @param bootstrapProperties
     *            The properties defined in bootstrap.properties.
     * @param parsedXmls
     *            The list of XML files that have been parsed so far.
     * @return The set of features to install, or empty set if the cumulatively
     *         parsed xml files only have featureManager sections but no
     *         features to install, or null if there are no valid xml files or
     *         they have no featureManager section
     */
    public Set<String> getServerXmlFeatures(Set<String> origResult, File serverDirectory, File serverFile, Properties bootstrapProperties, List<File> parsedXmls) {
        Set<String> result = origResult;
        List<File> updatedParsedXmls = parsedXmls != null ? parsedXmls : new ArrayList<File>();
        File canonicalServerFile;
        try {
            canonicalServerFile = serverFile.getCanonicalFile();
        } catch (IOException e) {
            // skip this server.xml if its path cannot be queried
            warn("The server file " + serverFile + " cannot be accessed. Skipping its features.");
            debug("Exception received: "+e.getMessage(), e);
            return result;
        }
        
        info("Parsing the server file for features and includes: " + getRelativeServerFilePath(serverDirectory, serverFile));
        updatedParsedXmls.add(canonicalServerFile);
        if (!canonicalServerFile.exists()) {
            warn("The server file " + canonicalServerFile + " does not exist. Skipping its features.");
        } else if (canonicalServerFile.length() == 0) {
            debug("The server file " + canonicalServerFile + " is empty.");
        } else {
            try {
                DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                db.setErrorHandler(new ErrorHandler() {
                    @Override
                    public void warning(SAXParseException e) throws SAXException {
                        debug("Exception received: "+e.getMessage(), e);
                    }
                
                    @Override
                    public void fatalError(SAXParseException e) throws SAXException {
                        throw e;
                    }
                
                    @Override
                    public void error(SAXParseException e) throws SAXException {
                        throw e;
                    }
                });
                Document doc = db.parse(canonicalServerFile);
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
                            result = parseIncludeNode(result, serverDirectory, canonicalServerFile, bootstrapProperties, child, updatedParsedXmls);
                        }
                    }
                }
            } catch (IOException | ParserConfigurationException | SAXException e) {
                // just skip this server.xml if it cannot be parsed
                warn("The server file " + canonicalServerFile + " cannot be parsed. Skipping its features.");
                debug("Exception received: "+e.getMessage(), e);
                return result;
            }
        }
        return result;
    }

    /**
     * Parse feature elements from a featureManager node, trimming whitespace
     * and treating everything as lowercase.
     * 
     * @param node
     *            The featureManager node
     * @return Set of trimmed lowercase feature names
     */
    private Set<String> parseFeatureManagerNode(Element node) {
        Set<String> result = new HashSet<String>();
        NodeList features = node.getElementsByTagName("feature");
        if (features != null) {
            for (int j = 0; j < features.getLength(); j++) {
                String content = features.item(j).getTextContent();
                if (content != null) {
                	content = content.trim();
                	if (content.contains(":")) {
                		String[] contentsplit = content.split(":");
                		if (contentsplit.length > 2) {
                			debug("The format of feature " + content + " in the server.xml is not valid and its installation will be skipped.");
                		} else {
                			result.add(contentsplit[0] + ":" + contentsplit[1].trim().toLowerCase());
                		}
                	} else {
                        if (lowerCaseFeatures) {
                            content = content.trim().toLowerCase();
                        } else {
                            content = content.trim();
                        }
                        // Check for empty feature element, skip it and log warning.
                        if (content.isEmpty()) {
                            warn("An empty feature was specified in a server configuration file. Ensure that the features are valid.");
                        } else {
                            result.add(content);
                        }
                	}
                }
            }
        }
        return result;
    }
    
    /**
     * Parse features from an include node.
     * 
     * @param origResult
     *            The features that have been parsed so far.
     * @param serverDirectory
     *            The server directory containing the server.xml.
     * @param serverFile
     *            The parent server XML file containing the include node.
     * @param node
     *            The include node.
     * @param updatedParsedXmls
     *            The list of XML files that have been parsed so far.
     * @return The set of features to install, or empty set if the cumulatively
     *         parsed xml files only have featureManager sections but no
     *         features to install, or null if there are no valid xml files or
     *         they have no featureManager section
     * @throws IOException
     */
    private Set<String> parseIncludeNode(Set<String> origResult, File serverDirectory, File serverFile, Properties bootstrapProperties, Element node,
            List<File> updatedParsedXmls) {
        Set<String> result = origResult;
        // Need to handle more variable substitution for include location.
        String nodeValue = node.getAttribute("location");
        String includeFileName = VariableUtility.resolveVariables(this, nodeValue, null, bootstrapProperties, new Properties(), getLibertyDirectoryPropertyFiles());

        if (includeFileName == null || includeFileName.trim().isEmpty()) {
            warn("Unable to parse include file "+nodeValue+". Skipping the included features.");
            return result;
        }

        File includeFile = null;
        if (isURL(includeFileName)) {
            try {
                File tempFile = File.createTempFile("serverFromURL", ".xml");
                FileUtils.copyURLToFile(new URL(includeFileName), tempFile, COPY_FILE_TIMEOUT_MILLIS, COPY_FILE_TIMEOUT_MILLIS);
                includeFile = tempFile;
            } catch (IOException e) {
                // skip this xml if it cannot be accessed from URL
                warn("The server file " + serverFile + " includes a URL " + includeFileName + " that cannot be accessed. Skipping the included features.");
                debug("Exception received: "+e.getMessage(), e);
                return result;
            }
        } else {
            includeFile = new File(includeFileName);
        }
        try {
            if (!includeFile.isAbsolute()) {
                includeFile = new File(serverFile.getParentFile().getAbsolutePath(), includeFileName)
                        .getCanonicalFile();
            } else {
                includeFile = includeFile.getCanonicalFile();
            }
        } catch (IOException e) {
            // skip this xml if its path cannot be queried
            warn("The server file " + serverFile + " includes a file " + includeFileName + " that cannot be accessed. Skipping the included features.");
            debug("Exception received: "+e.getMessage(), e);
            return result;
        }

        ArrayList<File> includeFiles = new ArrayList<File>();
        if (includeFile.isDirectory()) {
            try (DirectoryStream<Path> dstream = Files.newDirectoryStream(includeFile.toPath(), "*.xml")) {
                StreamSupport.stream(dstream.spliterator(), false)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> { 
                        try {
                            includeFiles.add(p.toFile());
                        } catch (Exception e) {
                            debug("Failed to resolve file from path: " + p);
                        }
                    });
            } catch (IOException e) {
                debug("Unable to open include directory: " + includeFileName);
            }
        } else {
            includeFiles.add(includeFile);
        }

        for (File file : includeFiles) {
            if (!updatedParsedXmls.contains(file)) {
                String onConflict = node.getAttribute("onConflict");
                Set<String> features = getServerXmlFeatures(null, serverDirectory, file, bootstrapProperties, updatedParsedXmls);
                if (features != null && !features.isEmpty()) {
                    info("Features were included for file "+ file.toString());
                }
                result = handleOnConflict(result, onConflict, features);
            }
        }
        return result;
    }
    
    private static boolean isURL(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException ex) {
            return false;
        }
    }
    
    private Set<String> handleOnConflict(Set<String> origResult, String onConflict, Set<String> features) {
        Set<String> result = origResult;
        if ("replace".equalsIgnoreCase(onConflict)) {
            if (features != null && !features.isEmpty()) {
                // only replace if the child has features
                result = features;
            }
        } else if ("ignore".equalsIgnoreCase(onConflict)) {
            if (result == null) {
                // parent has no results (i.e. no featureManager section), so use the child's results
                result = features;
            } // else the parent already has some results (even if it's empty), so ignore the child
        } else {
            // anything else counts as "merge", even if the onConflict value is invalid
            if (features != null) {
                if (result == null) {
                    result = features;
                } else {
                    result.addAll(features);
                }
            }
        }
        return result;
    }

    private Properties getBootstrapProperties(File bootstrapProperties) {
        Properties prop = new Properties();
        if (bootstrapProperties != null && bootstrapProperties.exists()) {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(bootstrapProperties);
                prop.load(stream);
            } catch (IOException e) {
                warn("The bootstrap.properties file " + bootstrapProperties.getAbsolutePath()
                        + " could not be loaded. Skipping the bootstrap.properties file.");
                debug("Exception received: "+e.getMessage(), e);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        debug("Could not close input stream for file " + bootstrapProperties.getAbsolutePath(), e);
                    }
                }
            }
        }
        return prop;
    }



    // return the server file path relative to the server directory
    private String getRelativeServerFilePath(File serverDirectory, File serverFile) {
        try {
            File canonicalServerDirectory = serverDirectory.getCanonicalFile();
            URI serverDirectoryUri = canonicalServerDirectory.toURI();
            URI serverFileUri = serverFile.toURI();
            return serverDirectory.getName() + File.separator + serverDirectoryUri.relativize(serverFileUri).getPath();
        } catch (IOException e1) {
            debug("Unable to determine the file path of " + serverFile + " relative to the server directory "
                    + serverDirectory);
            return serverFile.toString();
        }
    }
    
    //return user directory
    protected File getUserExtensionPath() {
    	if(libertyDirectoryPropertyToFile == null || libertyDirectoryPropertyToFile.get(USR_EXTENSION_DIR) == null ) {
    		return null;
    	}
    	return libertyDirectoryPropertyToFile.get(USR_EXTENSION_DIR);
    }
    
    /**
     * @return ClassLoader of com.ibm.ws.install.map.jar
     * @throws MalformedURLException
     * @throws PluginExecutionException
     */
    private ClassLoader getInstallMapClassLoader() throws MalformedURLException, PluginExecutionException {
        if (installJarFile == null) {
            throw new PluginExecutionException("Install map jar not found.");
        }
        
        if (installMapLoader == null) {
            ClassLoader cl = this.getClass().getClassLoader();
            installMapLoader = new URLClassLoader(new URL[] { installJarFile.toURI().toURL() }, cl);
        }
        return installMapLoader;
    }

    /**
     * @return com.ibm.ws.install.map.InstallMap.class
     * @throws MalformedURLException
     * @throws PrivilegedActionException
     * @throws PluginExecutionException
     */
    private Class<Map<String, Object>> getInstallMapClass() throws MalformedURLException, PrivilegedActionException, PluginExecutionException {
        if (installMapClass == null) {
            final ClassLoader cl = getInstallMapClassLoader();
            installMapClass = AccessController.doPrivileged(new PrivilegedExceptionAction <Class<Map<String, Object>>>() {
                @SuppressWarnings({ "unchecked" })
                @Override
                public Class<Map<String, Object>> run() throws Exception {
                    installMapClass = (Class<Map<String, Object>>) cl.loadClass("com.ibm.ws.install.map.InstallMap");
                    return installMapClass;
                }
            });
        }
        
        if (installMapClass == null){
        	throw new PluginExecutionException("Cannot run install jar file " + installJarFile);
        }
        
        return installMapClass;
    }

    /**
     * @return creates a new instance of com.ibm.ws.install.map.InstallMap.class
     * @throws MalformedURLException
     * @throws PluginExecutionException
     * @throws SecurityException
     * @throws PrivilegedActionException
     */
    private Map<String, Object> getInstallMapObject()
	    throws MalformedURLException, PluginExecutionException, SecurityException, PrivilegedActionException {
	if (mapBasedInstallKernel == null) {
	    Class<Map<String, Object>> clazz = getInstallMapClass();
	    try {
		mapBasedInstallKernel = (Map<String, Object>) clazz.getDeclaredConstructor().newInstance();
	    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
		    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
		// TODO Auto-generated catch block
		throw new PluginExecutionException("Error finding install kernel map using reflection", e);
	    }

	    if (mapBasedInstallKernel == null) {
		throw new PluginExecutionException("Error finding install kernel map using reflection");
	    }
	}
	return mapBasedInstallKernel;
    }
    
    /**
     * This creates a Install map and initializes basic information such as installDir, usrDir, logging level, etc.   
     * @param jars to override
     * @param installDirectory
     * @return Map<String, Object> of Install map
     * @throws PrivilegedActionException
     * @throws PluginExecutionException
     * @throws MalformedURLException
     */
    protected Map<String, Object> createMapBasedInstallKernelInstance(String bundle, File installDirectory)
            throws PrivilegedActionException, PluginExecutionException, MalformedURLException {
	mapBasedInstallKernel = getInstallMapObject();

        // Init
        if (bundle != null) {
            List<String> bundles = new ArrayList<String>();
            bundles.add(bundle);
            debug("Overriding jar using: " + bundle);
            mapBasedInstallKernel.put("override.jar.bundles", bundles);
        }
        mapBasedInstallKernel.put("runtime.install.dir", installDirectory);
        try {
            mapBasedInstallKernel.put("install.map.jar.file", installJarFile);
            debug("install.map.jar.file: " + installJarFile);
        } catch (RuntimeException e) {
            debug("This version of the install map does not support the key \"install.map.jar.file\"", e);
            String installJarFileSubpath = installJarFile.getParentFile().getName() + File.separator + installJarFile.getName();
            mapBasedInstallKernel.put("install.map.jar", installJarFileSubpath);
            debug("install.map.jar: " + installJarFileSubpath);
        }
        debug("install.kernel.init.code: " + mapBasedInstallKernel.get("install.kernel.init.code"));
        debug("install.kernel.init.error.message: " + mapBasedInstallKernel.get("install.kernel.init.error.message"));
        File usrDir = new File(installDirectory, "usr");
        mapBasedInstallKernel.put("target.user.directory", usrDir);
	if (isDebugEnabled()) {
	    mapBasedInstallKernel.put("debug", Level.FINEST);
        }else {
            mapBasedInstallKernel.put("debug", Level.INFO);
        }
        return mapBasedInstallKernel;
    }

}