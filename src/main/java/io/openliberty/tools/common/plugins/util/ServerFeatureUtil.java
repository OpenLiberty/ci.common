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
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.openliberty.tools.common.plugins.config.XmlDocument;

/**
 * Utility class to determine server features
 */
public abstract class ServerFeatureUtil {
    
    public static final String OPEN_LIBERTY_GROUP_ID = "io.openliberty.features";
    public static final String REPOSITORY_RESOLVER_ARTIFACT_ID = "repository-resolver";
    public static final String INSTALL_MAP_ARTIFACT_ID = "install-map";
    private static final int COPY_FILE_TIMEOUT_MILLIS = 5 * 60 * 1000;
    
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
     * @return the set of features that should be installed from server.xml, or empty set if nothing should be installed
     */
    public Set<String> getServerFeatures(File serverDirectory) {
        Properties bootstrapProperties = getBootstrapProperties(new File(serverDirectory, "bootstrap.properties"));
        Set<String> result = getConfigDropinsFeatures(null, serverDirectory, bootstrapProperties, "defaults");
        result = getServerXmlFeatures(result, new File(serverDirectory, "server.xml"), bootstrapProperties, null);
        // add the overrides at the end since they should not be replaced by any previous content
        return getConfigDropinsFeatures(result, serverDirectory, bootstrapProperties, "overrides");
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
            debug(e);
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
            debug(e);
            return result;
        }
        updatedParsedXmls.add(canonicalServerFile);
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
                            result = parseIncludeNode(result, canonicalServerFile, bootstrapProperties, child, updatedParsedXmls);
                        }
                    }
                }
            } catch (IOException | ParserConfigurationException | SAXException e) {
                // just skip this server.xml if it cannot be parsed
                warn("The server file " + serverFile + " cannot be parsed. Skipping its features.");
                debug(e);
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
                debug(e);
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
            debug(e);
            return result;
        }
        if (!updatedParsedXmls.contains(includeFile)) {
            String onConflict = node.getAttribute("onConflict");
            Set<String> features = getServerXmlFeatures(null, includeFile, bootstrapProperties, updatedParsedXmls);
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
            try {
                prop.load(new FileInputStream(bootstrapProperties));
            } catch (IOException e) {
                warn("The bootstrap.properties file " + bootstrapProperties + " could not be loaded. Skipping the bootstrap.properties file.");
                debug(e);
            }
        }
        return prop;
    }

    private String evaluateExpression(Properties properties, String expression) {
        String value = expression;
        if (expression != null) {
            Pattern p = Pattern.compile("\\$\\{([^\\}]*)\\}");
            Matcher m = p.matcher(expression);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String variable = m.group(1);
                String propertyValue = properties.getProperty(variable, "\\$\\{" + variable + "\\}");
                m.appendReplacement(sb, propertyValue);
            }
            m.appendTail(sb);
            value = sb.toString();
        }
        return value;
    }
    
}