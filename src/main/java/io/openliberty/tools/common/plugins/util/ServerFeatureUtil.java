/**
 * (C) Copyright IBM Corporation 2019, 2020.
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
import java.net.MalformedURLException;
import java.net.URL;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/**
 * Utility class to determine server features
 */
public abstract class ServerFeatureUtil extends AbstractContainerSupportUtil {
    
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

    private Map<String,File> libertyDirectoryPropertyToFile = null;
    
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
     * Get the set of features defined in the server.xml
     * @param serverDirectory The server directory containing the server.xml
     * @param libertyDirPropFiles Map of Liberty directory properties to the actual File for each directory
     * @return the set of features that should be installed from server.xml, or empty set if nothing should be installed
     */
    public Set<String> getServerFeatures(File serverDirectory, Map<String,File> libertyDirPropFiles) {
        if (libertyDirPropFiles != null) {
            libertyDirectoryPropertyToFile = new HashMap(libertyDirPropFiles);
        } else {
            warn("The properties for directories are null and could lead to server include files not being processed for server features.");
            libertyDirectoryPropertyToFile = new HashMap<String,File>();
        }
        Properties bootstrapProperties = getBootstrapProperties(new File(serverDirectory, "bootstrap.properties"));
        Set<String> result = getConfigDropinsFeatures(null, serverDirectory, bootstrapProperties, "defaults");
        result = getServerXmlFeatures(result, new File(serverDirectory, "server.xml"), bootstrapProperties, null);
        // add the overrides at the end since they should not be replaced by any previous content
        return getConfigDropinsFeatures(result, serverDirectory, bootstrapProperties, "overrides");
    }

    /**
     * Initializes the pre-defined Liberty directory properties which will be used when resolving variable references in 
     * the include element location attribute, such as <include location="${server.config.dir}/xyz.xml"/>. 
     * Note that we are intentionally not including the wlp.output.dir property, as that location can be specified by the
     * user outside of the Liberty installation and does not make much sense as a location for server include files.
     * All other Liberty directory properties can be determined relative to the passed in serverDirectory, which is the 
     * server.config.dir.
     *
     * @param serverDirectory The server directory containing the server.xml
     */
    private void initializeLibertyDirectoryPropertyFiles(File serverDirectory) {
        libertyDirectoryPropertyToFile = new HashMap<String,File>();
        if (serverDirectory.exists()) {
            try {
                libertyDirectoryPropertyToFile.put(SERVER_CONFIG_DIR, serverDirectory.getCanonicalFile());

                File wlpUserDir = serverDirectory.getParentFile().getParentFile();
                libertyDirectoryPropertyToFile.put(WLP_USER_DIR, wlpUserDir.getCanonicalFile());

                File wlpInstallDir = wlpUserDir.getParentFile();
                libertyDirectoryPropertyToFile.put(WLP_INSTALL_DIR, wlpInstallDir.getCanonicalFile());
 
                File userExtDir = new File(wlpUserDir, "extension");
                libertyDirectoryPropertyToFile.put(USR_EXTENSION_DIR, userExtDir.getCanonicalFile());

                File userSharedDir = new File(wlpUserDir, "shared");
                File userSharedAppDir = new File(userSharedDir, "app");
                File userSharedConfigDir = new File(userSharedDir, "config");
                File userSharedResourcesDir = new File(userSharedDir, "resources");
                File userSharedStackGroupsDir = new File(userSharedDir, "stackGroups");

                libertyDirectoryPropertyToFile.put(SHARED_APP_DIR, userSharedAppDir.getCanonicalFile());
                libertyDirectoryPropertyToFile.put(SHARED_CONFIG_DIR, userSharedConfigDir.getCanonicalFile());
                libertyDirectoryPropertyToFile.put(SHARED_RESOURCES_DIR, userSharedResourcesDir.getCanonicalFile());
                libertyDirectoryPropertyToFile.put(SHARED_STACKGROUP_DIR, userSharedStackGroupsDir.getCanonicalFile());
            } catch (Exception e) {
                warn("The properties for directories could not be initialized because an error occurred when accessing them.");
                debug("Exception received: "+e.getMessage(), e);
            }
        } else {
            warn("The " + serverDirectory + " directory cannot be accessed. Skipping its server features.");
        }
    }
    
    /**
     * Gets features from the configDropins's defaults or overrides directory
     * 
     * @param origResult
     *            The features that have been parsed so far.
     * @param serverDirectory
     *            The server directory
     * @param folderName
     *            The folder under configDropins: either "defaults" or
     *            "overrides"
     * @return The set of features to install, or empty set if the cumulatively
     *         parsed xml files only have featureManager sections but no
     *         features to install, or null if there are no valid xml files or
     *         they have no featureManager section
     */
    private Set<String> getConfigDropinsFeatures(Set<String> origResult, File serverDirectory, Properties bootstrapProperties, String folderName) {
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
                return name.endsWith(".xml");
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
            Set<String> features = getServerXmlFeatures(result, xml, bootstrapProperties,null);
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
     * @param serverFile
     *            The server XML file.
     * @param parsedXmls
     *            The list of XML files that have been parsed so far.
     * @return The set of features to install, or empty set if the cumulatively
     *         parsed xml files only have featureManager sections but no
     *         features to install, or null if there are no valid xml files or
     *         they have no featureManager section
     */
    private Set<String> getServerXmlFeatures(Set<String> origResult, File serverFile, Properties bootstrapProperties, List<File> parsedXmls) {
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
        info("Parsing the server file " + canonicalServerFile + " for features and includes.");
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
                            result = parseIncludeNode(result, canonicalServerFile, bootstrapProperties, child, updatedParsedXmls);
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
                    if (content.contains(":")) {
                        debug("The feature " + content + " in the server.xml file is a user feature and its installation will be skipped.");
                    } else {
                        result.add(content.trim().toLowerCase());
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
     */
    private Set<String> parseIncludeNode(Set<String> origResult, File serverFile, Properties bootstrapProperties, Element node,
            List<File> updatedParsedXmls) {
        Set<String> result = origResult;
        String includeFileName = evaluateExpression(bootstrapProperties, node.getAttribute("location"));

        if (includeFileName == null || includeFileName.trim().isEmpty()) {
            warn("Unable to parse include file "+node.getAttribute("location")+". Skipping the included features.");
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
        if (!updatedParsedXmls.contains(includeFile)) {
            String onConflict = node.getAttribute("onConflict");
            Set<String> features = getServerXmlFeatures(null, includeFile, bootstrapProperties, updatedParsedXmls);
            if (features != null && !features.isEmpty()) {
                info("Features were included for file "+ includeFileName);
            }
            result = handleOnConflict(result, onConflict, features);
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

    private String evaluateExpression(Properties properties, String expression) {
        String value = expression;
        if (expression != null) {
            Pattern p = Pattern.compile("\\$\\{(.*?)\\}");
            Matcher m = p.matcher(expression);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String variable = m.group(1);
                
                String propertyValue = properties.getProperty(variable, "${" + variable + "}");
                
                // Remove encapsulating ${} characters and validate that a valid liberty directory property was configured
                propertyValue = removeEncapsulatingEnvVarSyntax(propertyValue, properties); 
                
                if (propertyValue == null) {
                    return null;
                }

                m.appendReplacement(sb, propertyValue);
            }
            m.appendTail(sb);
            value = sb.toString();
        }
        // For Windows, avoid escaping the backslashes by changing to forward slashes
        value = value.replace("\\","/");
        debug("Include location attribute "+ expression +" evaluated and replaced with "+value);
        return value;
    }

    private String removeEncapsulatingEnvVarSyntax(String propertyValue, Properties properties){
        Pattern p = Pattern.compile("\\$\\{(.*?)\\}");
        Matcher m = p.matcher(propertyValue);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String envDirectoryProperty = m.group(1);
            if(!libertyDirectoryPropertyToFile.containsKey(envDirectoryProperty)) {
                // Check if property is a reference to a configured bootstrap property
                String bootStrapValue = properties.getProperty(envDirectoryProperty);
                if(bootStrapValue != null) {
                    // For Windows, avoid escaping the backslashes by changing to forward slashes
                    bootStrapValue = bootStrapValue.replace("\\","/");
                    m.appendReplacement(sb, removeEncapsulatingEnvVarSyntax(bootStrapValue, properties));
                } else {
                    warn("The referenced property " + envDirectoryProperty + " is not a predefined Liberty directory property or a configured bootstrap property.");
                    return null;
                }
            } else {
                File envDirectory = libertyDirectoryPropertyToFile.get(envDirectoryProperty);
                String path = envDirectory.toString();
                // For Windows, avoid escaping the backslashes by changing to forward slashes
                path = path.replace("\\","/");
                m.appendReplacement(sb, path);
            }
        }
        m.appendTail(sb);
        String returnValue = sb.toString();
        if (sb.charAt(0) == '"' && sb.charAt(sb.length()-1) == '"') {
            if (sb.length() > 2) {
                returnValue = sb.substring(1,sb.length()-1);
            } else {
                // The sb variable just contains a beginning and ending quote. Return an empty String.
                returnValue = "";
            }
        }
        // For Windows, avoid escaping the backslashes by changing to forward slashes
        returnValue = returnValue.replace("\\","/");
        debug("Include location attribute property value "+ propertyValue +" replaced with "+ returnValue);
        return returnValue;
    }
}