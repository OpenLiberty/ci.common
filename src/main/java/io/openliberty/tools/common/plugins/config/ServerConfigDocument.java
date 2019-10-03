/**
 * (C) Copyright IBM Corporation 2017, 2019.
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
package io.openliberty.tools.common.plugins.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Properties;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

import io.openliberty.tools.common.CommonLoggerI;

// Moved from ci.maven/liberty-maven-plugin/src/main/java/net/wasdev/wlp/maven/plugins/ServerConfigDocument.java
public class ServerConfigDocument {

    private static ServerConfigDocument instance;

    private static CommonLoggerI log;

    private static DocumentBuilder docBuilder;

    private static File configDirectory;
    private static File serverXMLFile;

    private static Set<String> names;
    private static Set<String> namelessLocations;
    private static Set<String> locations;
    private static HashMap<String, String> locationsAndNames;
    private static Properties props;
    private static Properties defaultProps;

    private static final String VARIABLE_NAME_PATTERN = "\\$\\{(.*?)\\}";
    private static final Pattern varNamePattern = Pattern.compile(VARIABLE_NAME_PATTERN);

    private static final XPathExpression XPATH_SERVER_APPLICATION;
    private static final XPathExpression XPATH_SERVER_WEB_APPLICATION;
    private static final XPathExpression XPATH_SERVER_ENTERPRISE_APPLICATION;
    private static final XPathExpression XPATH_SERVER_INCLUDE;
    private static final XPathExpression XPATH_SERVER_VARIABLE;

    static {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            XPATH_SERVER_APPLICATION = xPath.compile("/server/application");
            XPATH_SERVER_WEB_APPLICATION = xPath.compile("/server/webApplication");
            XPATH_SERVER_ENTERPRISE_APPLICATION = xPath.compile("/server/enterpriseApplication");
            XPATH_SERVER_INCLUDE = xPath.compile("/server/include");
            XPATH_SERVER_VARIABLE = xPath.compile("/server/variable");
        } catch (XPathExpressionException ex) {
            // These XPath expressions should all compile statically.
            // Compilation failures mean the expressions are not syntactically
            // correct
            throw new RuntimeException(ex);
        }
    }

    public Set<String> getLocations() {
        return locations;
    }

    public  Set<String> getNames() {
        return names;
    }

    public Set<String> getNamelessLocations() {
        return namelessLocations;
    }

    public static Properties getProperties() {
        return props;
    }

    public static Properties getDefaultProperties() {
        return defaultProps;
    }

    private static File getServerXML() {
        return serverXMLFile;
    }

    public ServerConfigDocument(CommonLoggerI log, File serverXML, File configDir, File bootstrapFile,
            Map<String, String> bootstrapProp, File serverEnvFile) {
        this(log, serverXML, configDir, bootstrapFile, bootstrapProp, serverEnvFile, true);
    }

    public ServerConfigDocument(CommonLoggerI log, File serverXML, File configDir, File bootstrapFile,
            Map<String, String> bootstrapProp, File serverEnvFile, boolean giveConfigDirPrecedence) {
        initializeAppsLocation(log, serverXML, configDir, bootstrapFile, bootstrapProp, serverEnvFile, giveConfigDirPrecedence);
    }

    private static DocumentBuilder getDocumentBuilder() {
        if (docBuilder == null) {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setIgnoringComments(true);
            docBuilderFactory.setCoalescing(true);
            docBuilderFactory.setIgnoringElementContentWhitespace(true);
            docBuilderFactory.setValidating(false);
            try {
                docBuilder = docBuilderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                // fail catastrophically if we can't create a document builder
                throw new RuntimeException(e);
            }
        }
        return docBuilder;
    }

    /**
     * Nulls out cached instance so a new one will be created next time a getInstance() is done.
     * Not thread-safe.
     */
    public static void markInstanceStale() {
        instance = null;
    }
    
    public static ServerConfigDocument getInstance(CommonLoggerI log, File serverXML, File configDir, File bootstrapFile,
            Map<String, String> bootstrapProp, File serverEnvFile) throws IOException {
        return getInstance(log, serverXML, configDir, bootstrapFile, bootstrapProp, serverEnvFile, true);
    }

    public static ServerConfigDocument getInstance(CommonLoggerI log, File serverXML, File configDir, File bootstrapFile,
            Map<String, String> bootstrapProp, File serverEnvFile, boolean giveConfigDirPrecedence) throws IOException {
        // Initialize if instance is not created yet, or source server xml file
        // location has been changed.
        if (instance == null || !serverXML.getCanonicalPath().equals(getServerXML().getCanonicalPath())) {
            instance = new ServerConfigDocument(log, serverXML, configDir, bootstrapFile, bootstrapProp, serverEnvFile, giveConfigDirPrecedence);
        }
        return instance;
    }

    private static void initializeAppsLocation(CommonLoggerI log, File serverXML, File configDir, File bootstrapFile,
            Map<String, String> bootstrapProp, File serverEnvFile, boolean giveConfigDirPrecedence) {
        try {
            ServerConfigDocument.log = log;
            serverXMLFile = serverXML;
            configDirectory = configDir;

            locations = new HashSet<String>();
            names = new HashSet<String>();
            namelessLocations = new HashSet<String>();
            locationsAndNames = new HashMap<String, String>();
            props = new Properties();
            defaultProps = new Properties();

            Document doc = parseDocument(new FileInputStream(serverXMLFile));

            // Server variable precedence in ascending order if defined in
            // multiple locations.
            //
            // 1. defaultValue from variables defined in server.xml or defined in <include/> files
            // e.g. <variable name="myVarName" defaultValue="myVarValue" />
            // 2. variables from 'server.env'
            // 3. variables from 'bootstrap.properties'
            // 4. variables defined in <include/> files
            // 5. variables from configDropins/defaults/<file_name>
            // 6. variables defined in server.xml
            // e.g. <variable name="myVarName" value="myVarValue" />
            // 7. variables from configDropins/overrides/<file_name>

            // get variables from server.env
            File cfgFile = findConfigFile("server.env", serverEnvFile, giveConfigDirPrecedence);

            if (cfgFile != null) {
                parseProperties(new FileInputStream(cfgFile));
            }

            File cfgDirFile = getFileFromConfigDirectory("bootstrap.properties");

            if (giveConfigDirPrecedence && cfgDirFile != null) {
                parseProperties(new FileInputStream(cfgDirFile));
            } else if (bootstrapProp != null && !bootstrapProp.isEmpty()) {
                for (Map.Entry<String,String> entry : bootstrapProp.entrySet()) {
                    if (entry.getValue() != null) {
                        props.setProperty(entry.getKey(),entry.getValue());  
                    } 
                }
            } else if (bootstrapFile != null && bootstrapFile.exists()) {
                parseProperties(new FileInputStream(bootstrapFile));
            } else if (cfgDirFile != null) {
                parseProperties(new FileInputStream(cfgDirFile));
            }

            parseIncludeVariables(doc);
            parseConfigDropinsDirVariables("defaults");
            parseVariables(doc);
            parseConfigDropinsDirVariables("overrides");

            parseApplication(doc, XPATH_SERVER_APPLICATION);
            parseApplication(doc, XPATH_SERVER_WEB_APPLICATION);
            parseApplication(doc, XPATH_SERVER_ENTERPRISE_APPLICATION);
            parseNames(doc, "/server/application | /server/webApplication | /server/enterpriseApplication");
            parseInclude(doc);
            parseConfigDropinsDir();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Checks for application names in the document. Will add locations without names to a Set
    private static void parseNames(Document doc, String expression) throws XPathExpressionException, IOException, SAXException {
        // parse input document
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i).getAttributes().getNamedItem("name") != null) {
                String nameValue = nodeList.item(i).getAttributes().getNamedItem("name").getNodeValue();
                String locationValue = nodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();

                // add unique values only
                if (!nameValue.isEmpty()) {
                    String resolvedName = resolveVariables(nameValue, null);
                    String resolvedLocation = resolveVariables(locationValue, null);
                    if (resolvedName == null) {
                        if (!names.contains(nameValue)) {
                            names.add(nameValue);
                        }
                    } else if (!names.contains(resolvedName)) {
                        names.add(resolvedName);
                    }
                    if (resolvedLocation != null) {
                        if (resolvedName == null) {
                            locationsAndNames.put(resolvedLocation, nameValue);
                        } else {
                            locationsAndNames.put(resolvedLocation, resolvedName);
                        }
                    }
                }
            } else {
                String nodeValue = nodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();

                // add unique values only
                if (!nodeValue.isEmpty()) {
                    String resolved = resolveVariables(nodeValue, null);
                    if (resolved == null) {
                        if (! namelessLocations.contains(nodeValue)) {
                            namelessLocations.add(nodeValue);
                        }
                    } else if (! namelessLocations.contains(resolved)) {
                        namelessLocations.add(resolved);
                    }
                }
            }
        }
    }

    public static String findNameForLocation(String location) {
        String appName = locationsAndNames.get(location);

        if (appName == null || appName.isEmpty()) {
            appName = location.substring(0, location.lastIndexOf('.'));
        }

        return appName;
    }

    private static void parseApplication(Document doc, XPathExpression expression) throws XPathExpressionException {

        NodeList nodeList = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            String nodeValue = nodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();

            // add unique values only
            if (!nodeValue.isEmpty()) {
                String resolved = resolveVariables(nodeValue, null);
                if (resolved == null) {
                    // location could not be resolved, log message and add location as is
                    log.info("The variables referenced by location " + nodeValue + " cannot be resolved.");
                    if (!locations.contains(nodeValue)) {
                        locations.add(nodeValue);
                    }
                } else if (!locations.contains(resolved)) {
                    locations.add(resolved);
                }
            }
        }
    }

    private static void parseInclude(Document doc) throws XPathExpressionException, IOException, SAXException {
        // parse include document in source server xml
        NodeList nodeList = (NodeList) XPATH_SERVER_INCLUDE.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            String nodeValue = nodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();

            if (!nodeValue.isEmpty()) {
                Document docIncl = getIncludeDoc(nodeValue);

                if (docIncl != null) {
                    parseApplication(docIncl, XPATH_SERVER_APPLICATION);
                    parseApplication(docIncl, XPATH_SERVER_WEB_APPLICATION);
                    parseApplication(docIncl, XPATH_SERVER_ENTERPRISE_APPLICATION);
                    // handle nested include elements
                    parseInclude(docIncl);
                }
            }
        }
    }

    private static void parseConfigDropinsDir() throws XPathExpressionException, IOException, SAXException {
        File configDropins = getConfigDropinsDir();

        if (configDropins != null && configDropins.exists()) {
            File overrides = new File(configDropins, "overrides");
            if (overrides.exists()) {
                parseDropinsFiles(overrides.listFiles());
            }

            File defaults = new File(configDropins, "defaults");
            if (defaults.exists()) {
                parseDropinsFiles(defaults.listFiles());
            }
        }
    }

    private static void parseDropinsFiles(File[] files) throws XPathExpressionException, IOException, SAXException {
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                parseDropinsFile(files[i]);
            }
        }
    }

    private static Document parseDropinsXMLFile(File file) throws FileNotFoundException, IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return parseDocument(is);
        } catch (SAXException ex) {
            // If the file was not valid XML, assume it was some other non XML
            // file in dropins.
            log.info("Skipping parsing " + file.getAbsolutePath() + " because it was not recognized as XML.");
            return null;
        }
    }

    private static void parseDropinsFile(File file) throws IOException, XPathExpressionException, SAXException {
        // get input XML Document
        Document doc = parseDropinsXMLFile(file);
        if (doc != null) {
            parseApplication(doc, XPATH_SERVER_APPLICATION);
            parseApplication(doc, XPATH_SERVER_WEB_APPLICATION);
            parseApplication(doc, XPATH_SERVER_ENTERPRISE_APPLICATION);
            parseInclude(doc);
        }
    }

    private static Document getIncludeDoc(String loc) throws IOException, SAXException {

        Document doc = null;
        File locFile = null;

        if (loc.startsWith("http:") || loc.startsWith("https:")) {
            if (isValidURL(loc)) {
                URL url = new URL(loc);
                URLConnection connection = url.openConnection();
                doc = parseDocument(connection.getInputStream());
            }
        } else if (loc.startsWith("file:")) {
            if (isValidURL(loc)) {
                locFile = new File(loc);
                if (locFile.exists()) {
                    InputStream inputStream = new FileInputStream(locFile.getCanonicalPath());
                    doc = parseDocument(inputStream);
                }
            }
        } else if (loc.startsWith("ftp:")) {
            // TODO handle ftp protocol
        } else {
            locFile = new File(loc);

            // check if absolute file
            if (locFile.isAbsolute()) {
                if (locFile.exists()) {
                    InputStream inputStream = new FileInputStream(locFile.getCanonicalPath());
                    doc = parseDocument(inputStream);
                }
            } else {
                // check configDirectory first if exists
                if (configDirectory != null && configDirectory.exists()) {
                    locFile = new File(configDirectory, loc);
                }

                if (locFile == null || !locFile.exists()) {
                    locFile = new File(getServerXML().getParentFile(), loc);
                }

                if (locFile != null && locFile.exists()) {
                    InputStream inputStream = new FileInputStream(locFile.getCanonicalPath());
                    doc = parseDocument(inputStream);
                }
            }
        }
        return doc;
    }

    private static Document parseDocument(InputStream in) throws SAXException, IOException {
        try (InputStream ins = in) { // ins will be auto-closed
            return getDocumentBuilder().parse(ins);
        }
    }

    private static void parseProperties(InputStream ins) throws Exception {
        try {
            props.load(ins);
        } catch (Exception e) {
            throw e;
        } finally {
            if (ins != null) {
                ins.close();
            }
        }
    }

    private static boolean isValidURL(String url) {
        try {
            URL testURL = new URL(url);
            testURL.toURI();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /*
     * Attempts to resolve all variables in the passed in nodeValue. Variable value/defaultValue can reference other variables.
     * This method is called recursively to resolve the variables. The variableChain collection keeps track of the variable references
     * in a resolution chain in order to prevent an infinite loop. The variableChain collection should be passed as null on the initial call.
     */
    private static String resolveVariables(String nodeValue, Collection<String> variableChain) {

        String resolved = nodeValue;
        Matcher varNameMatcher = varNamePattern.matcher(nodeValue);

        Collection<String> variablesToResolve = new HashSet<String> ();

        while (varNameMatcher.find()) {
            String varName = varNameMatcher.group(1);
            if (variableChain != null && variableChain.contains(varName)) {
                // Found recursive reference when resolving variables. Log message and return null.
                log.debug("Found a recursive variable reference when resolving ${" + varName + "}");
                return null;
            } else {
                variablesToResolve.add(varName);
            }
        }

        for (String nextVariable : variablesToResolve) {
            String value = getProperties().getProperty(nextVariable);
            if (value == null) {
                // Check for default value since no other value found.
                value = getDefaultProperties().getProperty(nextVariable);
            }

            if (value != null && !value.isEmpty()) {
                Collection<String> thisVariableChain = new HashSet<String> ();
                thisVariableChain.add(nextVariable);

                if (variableChain != null && !variableChain.isEmpty()) {
                    thisVariableChain.addAll(variableChain);
                }

                String resolvedValue = resolveVariables(value, thisVariableChain);

                if (resolvedValue != null) {
                    resolved = resolved.replaceAll("\\$\\{" + nextVariable + "\\}", resolvedValue);
                } else {
                    // Variable value could not be resolved. Log message and return null.
                    log.debug("Could not resolve the value " + value + " for variable ${" + nextVariable + "}");
                    return null;
                }
            } else {
                // Variable could not be resolved. Log message and return null.
                log.debug("Variable " + nextVariable + " cannot be resolved.");
                return null;
            }
        }

        return resolved;
    }

    private static void parseVariables(Document doc) throws XPathExpressionException {
        // parse input document
        NodeList nodeList = (NodeList) XPATH_SERVER_VARIABLE.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            NamedNodeMap attr = nodeList.item(i).getAttributes();

            String varName = attr.getNamedItem("name").getNodeValue();

            if (!varName.isEmpty()) {
                // A variable can have either a value attribute OR a defaultValue attribute.
                String varValue = getValue(attr, "value");
                if (varValue != null && !varValue.isEmpty()) {
                    props.setProperty(varName, varValue);
                } else {
                    String varDefaultValue = getValue(attr, "defaultValue");
                    if (varDefaultValue != null && ! varDefaultValue.isEmpty()) {
                        defaultProps.setProperty(varName, varDefaultValue);
                    }
                }
            }
        }
    }

    private static String getValue(NamedNodeMap attr, String nodeName) {
        String value = null;
        Node valueNode = attr.getNamedItem(nodeName);
        if (valueNode != null) {
            value = valueNode.getNodeValue();
        }
        return value;
    }

    private static void parseIncludeVariables(Document doc) throws XPathExpressionException, IOException, SAXException {
        // parse include document in source server xml
        NodeList nodeList = (NodeList) XPATH_SERVER_INCLUDE.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            String nodeValue = nodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();

            if (!nodeValue.isEmpty()) {
                Document docIncl = getIncludeDoc(nodeValue);

                if (docIncl != null) {
                    parseVariables(docIncl);
                    // handle nested include elements
                    parseIncludeVariables(docIncl);
                }
            }
        }
    }

    private static File getConfigDropinsDir() {
        File configDropins = null;

        // if configDirectory exists and contains configDropins directory,
        // its configDropins has higher precedence.
        if (configDirectory != null && configDirectory.exists()) {
            configDropins = new File(configDirectory, "configDropins");
        }

        if (configDropins == null || !configDropins.exists()) {
            configDropins = new File(getServerXML().getParent(), "configDropins");
        }
        return configDropins;
    }

    private static void parseConfigDropinsDirVariables(String inDir)
            throws XPathExpressionException, SAXException, IOException {
        File configDropins = getConfigDropinsDir();

        if (configDropins != null && configDropins.exists()) {
            File dir = new File(configDropins, inDir);

            if (dir.exists()) {
                File[] cfgFiles = dir.listFiles();

                for (int i = 0; i < cfgFiles.length; i++) {
                    if (cfgFiles[i].isFile()) {
                        parseDropinsFilesVariables(cfgFiles[i]);
                    }
                }
            }
        }
    }

    private static void parseDropinsFilesVariables(File file)
            throws SAXException, IOException, XPathExpressionException {
        // get input XML Document
        Document doc = parseDropinsXMLFile(file);
        if (doc != null) {
            parseVariables(doc);
            parseIncludeVariables(doc);
        }
    }

    /*
     * If giveConfigDirPrecedence is set to true, return the file from the configDirectory if it exists;
     * otherwise return specificFile if it exists, or null if not.
     * If giveConfigDirPrecedence is set to false, return specificFile if it exists;
     * otherwise return the file from the configDirectory if it exists, or null if not.
     */
    private static File findConfigFile(String fileName, File specificFile, boolean giveConfigDirPrecedence) {
        File f = new File(configDirectory, fileName);

        if (giveConfigDirPrecedence) {
            if (configDirectory != null && f.exists()) {
                return f;
            }
            if (specificFile != null && specificFile.exists()) {
                return specificFile;
            }
        } else {
            if (specificFile != null && specificFile.exists()) {
                return specificFile;
            }
            if (configDirectory != null && f.exists()) {
                return f;
            }
        }

        return null;
    }

    /*
     * Get the file from configDrectory if it exists, or null if not
     */
    private static File getFileFromConfigDirectory(String file) {
        File f = new File(configDirectory, file);
        if (configDirectory != null && f.exists()) {
            return f;
        }
        return null;
    }
}
