/**
 * (C) Copyright IBM Corporation 2017, 2024.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.comparator.NameFileComparator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

import io.openliberty.tools.common.CommonLoggerI;
import io.openliberty.tools.common.plugins.util.ServerFeatureUtil;
import io.openliberty.tools.common.plugins.util.VariableUtility;

// Moved from ci.maven/liberty-maven-plugin/src/main/java/net/wasdev/wlp/maven/plugins/ServerConfigDocument.java
public class ServerConfigDocument {

    private CommonLoggerI log;

    private File configDirectory;
    private File serverXMLFile;
    private static final String CONFIGDROPINS_DEFAULT = Paths.get("configDropins/default/").toString();
    private static final String CONFIGDROPINS_OVERRIDES = Paths.get("configDropins/overrides/").toString();

    private Set<String> names;
    private Set<String> namelessLocations;
    private Set<String> locations;
    private HashMap<String, String> locationsAndNames;
    private Properties props;
    private Properties defaultProps;
    private Map<String, File> libertyDirectoryPropertyToFile = null;

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

    public Properties getProperties() {
        return props;
    }

    public Map<String, File> getLibertyDirPropertyFiles() {
        return libertyDirectoryPropertyToFile;
    }

    public Properties getDefaultProperties() {
        return defaultProps;
    }

    public File getServerXML() {
        return serverXMLFile;
    }

    /**
     * Deprecated. Migrate to the simpler constructor.
     * @param log
     * @param serverXML
     * @param configDir
     * @param bootstrapFile
     * @param bootstrapProp
     * @param serverEnvFile
     * @param giveConfigDirPrecedence
     * @param libertyDirPropertyFiles - Contains a property to file mapping of directory locations
     */
    public ServerConfigDocument(CommonLoggerI log, File serverXML, File configDir, File bootstrapFile,
            Map<String, String> bootstrapProp, File serverEnvFile, boolean giveConfigDirPrecedence, Map<String, File> libertyDirPropertyFiles) {
                this.log = log;
        serverXMLFile = serverXML;
        configDirectory = configDir;
        if (libertyDirPropertyFiles != null) {
            libertyDirectoryPropertyToFile = new HashMap<String, File>(libertyDirPropertyFiles);
            if (libertyDirPropertyFiles.containsKey(ServerFeatureUtil.SERVER_CONFIG_DIR)) {
                configDirectory = libertyDirPropertyFiles.get(ServerFeatureUtil.SERVER_CONFIG_DIR);
            }
        } else {
            log.warn("The properties for directories are null and could lead to application locations not being resolved correctly.");
            libertyDirectoryPropertyToFile = new HashMap<String,File>();
        }
        locations = new HashSet<String>();
        names = new HashSet<String>();
        namelessLocations = new HashSet<String>();
        locationsAndNames = new HashMap<String, String>();
        props = new Properties();
        defaultProps = new Properties();

        initializeAppsLocation();
    }

    /**
     * Adapt when ready. Expects the libertyDirPropertyFiles to be populated
     * @param log
     * @param libertyDirPropertyFiles
     */
    public ServerConfigDocument(CommonLoggerI log, Map<String, File> libertyDirPropertyFiles) {
        this.log = log;
        if (libertyDirPropertyFiles != null) {
            libertyDirectoryPropertyToFile = new HashMap<String, File>(libertyDirPropertyFiles);
            configDirectory = libertyDirectoryPropertyToFile.get(ServerFeatureUtil.SERVER_CONFIG_DIR);
            serverXMLFile = getFileFromConfigDirectory("server.xml");
        } else {
            log.warn("The properties for directories are null and could lead to application locations not being resolved correctly.");
            libertyDirectoryPropertyToFile = new HashMap<String,File>();
        }
        locations = new HashSet<String>();
        names = new HashSet<String>();
        namelessLocations = new HashSet<String>();
        locationsAndNames = new HashMap<String, String>();
        props = new Properties();
        defaultProps = new Properties();

        // initializeAppsLocation();
    }

    // LCLS constructor
    // TODO: populate libertyDirectoryPropertyToFile with workspace information
    public ServerConfigDocument(CommonLoggerI log) {
        this(log, null);
    }

    private DocumentBuilder getDocumentBuilder() {
        DocumentBuilder docBuilder;

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setIgnoringComments(true);
        docBuilderFactory.setCoalescing(true);
        docBuilderFactory.setIgnoringElementContentWhitespace(true);
        docBuilderFactory.setValidating(false);
        try {
            docBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false); 
            docBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);    
            docBuilder = docBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            // fail catastrophically if we can't create a document builder
            throw new RuntimeException(e);
        }

        return docBuilder;
    }

    /** 
    //  Server variable precedence in ascending order if defined in multiple locations.
    //  1. variable default values in the server.xml file
    //  2. environment variables
    //     server.env
    //       a. ${wlp.install.dir}/etc/
    //       b. ${wlp.user.dir}/shared/
    //       c. ${server.config.dir}/
    //     jvm.options
    //       a. ${wlp.user.dir}/shared/jvm.options
    //       b. ${server.config.dir}/configDropins/defaults/
    //       c. ${server.config.dir}/
    //       d. ${server.config.dir}/configDropins/overrides/ 
    //  3. bootstrap.properties
    //       a. additional references by bootstrap.include
    //  4. Java system properties
    //  5. Variables loaded from files in the ${server.config.dir}/variables directory or 
    //     other directories as specified by the VARIABLE_SOURCE_DIRS environment variable
    //  6. variable values declared in the server.xml file
    //       a. ${server.config.dir}/configDropins/defaults/
    //       b. ${server.config.dir}/server.xml
    //       c. ${server.config.dir}/configDropins/overrides/
    //  7. variables declared on the command line
    */
    public void initializeAppsLocation() {
        try {
            // 1. Need to parse variables in the server.xml for default values before trying to 
            //    find the include files in case one of the variables is used in the location.
            Document doc = parseDocument(serverXMLFile);
            parseVariablesForDefaultValues(doc);

            // 2. get variables from server.env
            processServerEnv();

            // 3. get variables from jvm.options
            // processJvmOptions();

            // 3. get variables from bootstrap.properties
            processBootstrapProperties();

            // 4. Java system properties
            // configured in Maven/Gradle

            // 5. Variables loaded from 'variables' directory
            processVariablesDirectory();

            // 6. variable values declared in server.xml(s)
            processServerXml(doc);

            // 7. variables delcared on the command line
            // configured in Maven/Gradle

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

    /**
     * server.env file read order
     *   1. {wlp.install.dir}/etc/
     *   2. {wlp.user.dir}/shared/
     *   3. {server.config.dir}/
     * @param serverEnvFile
     * @throws Exception
     * @throws FileNotFoundException
     */
    public void processServerEnv() throws Exception, FileNotFoundException {
        final String serverEnvString = "server.env";
        parsePropertiesFromFile(new File(libertyDirectoryPropertyToFile.get(ServerFeatureUtil.WLP_INSTALL_DIR), 
                "etc" + File.separator + serverEnvString));
        parsePropertiesFromFile(new File(libertyDirectoryPropertyToFile.get(ServerFeatureUtil.WLP_USER_DIR),
                "shared" + File.separator + serverEnvString));
        parsePropertiesFromFile(getFileFromConfigDirectory(serverEnvString));
    }

    /**
     * Likely not needed to be processed by the LMP/LGP tools. These properties benefit the JVM
     *   1. ${wlp.user.dir}/shared/jvm.options
     *   2. ${server.config.dir}/configDropins/defaults/
     *   3. ${server.config.dir}/
     *   4. ${server.config.dir}/configDropins/overrides/
     * @throws FileNotFoundException
     * @throws Exception
     */
    public void processJvmOptions() throws FileNotFoundException, Exception {
        final String jvmOptionsString = "jvm.options";
        parsePropertiesFromFile(new File(libertyDirectoryPropertyToFile.get(ServerFeatureUtil.WLP_USER_DIR),
                "shared" + File.separator + jvmOptionsString));
        parsePropertiesFromFile(getFileFromConfigDirectory(CONFIGDROPINS_DEFAULT + File.separator + jvmOptionsString));
        parsePropertiesFromFile(getFileFromConfigDirectory(jvmOptionsString));
        parsePropertiesFromFile(getFileFromConfigDirectory(CONFIGDROPINS_OVERRIDES + File.separator + jvmOptionsString));
    }

    /**
     * Process bootstrap.properties and boostrap.include 
     * @param bootstrapProp - Populated in Maven/Gradle
     * @param bootstrapFile - Optional specific file which will take precedence over config dir file
     * @throws Exception
     * @throws FileNotFoundException
     */
    public void processBootstrapProperties() throws Exception, FileNotFoundException {
        File bootstrapFile = getFileFromConfigDirectory("bootstrap.properties");
        if (bootstrapFile == null) {
            log.debug("bootstrap.properties not found in: " + configDirectory.getAbsolutePath());
            return;
        }

        parsePropertiesFromFile(bootstrapFile);
        if (props.containsKey("bootstrap.include")) {
            Set<String> visited = new HashSet<String>();
            visited.add(bootstrapFile.getAbsolutePath());
            processBootstrapInclude(visited);
        }
    }

    /**
     * Recursive processing for a series of bootstrap.include that terminates upon revisit
     * @param bootstrapIncludeLocation
     * @param processedBootstrapIncludes
     * @throws Exception 
     * @throws FileNotFoundException 
     */
    private void processBootstrapInclude(Set<String> processedBootstrapIncludes) throws FileNotFoundException, Exception {
        String bootstrapIncludeLocationString = props.getProperty("bootstrap.include");
        Path bootstrapIncludePath = Paths.get(bootstrapIncludeLocationString);
        File bootstrapIncludeFile = bootstrapIncludePath.isAbsolute() ?
                new File(bootstrapIncludePath.toString()) : new File(configDirectory, bootstrapIncludePath.toString());

        if (processedBootstrapIncludes.contains(bootstrapIncludeFile.getAbsolutePath())) {
            return;
        }
 
        if (bootstrapIncludeFile.exists()) {
            parseProperties(new FileInputStream(bootstrapIncludeFile));
            processedBootstrapIncludes.add(bootstrapIncludeFile.getAbsolutePath());
            processBootstrapInclude(processedBootstrapIncludes);
        }
    }

    /**
     * By default, ${server.config.directory}/variables is processed.
     * If VARIABLE_SOURCE_DIRS is defined, those directories are processed instead.
     * A list of directories are delimited by ';' on Windows, and ':' on Unix
     * @throws Exception 
     * @throws FileNotFoundException 
     */
    public void processVariablesDirectory() throws FileNotFoundException, Exception {
        final String variableDirectoryProperty = "VARIABLE_SOURCE_DIRS";

        ArrayList<File> toProcess = new ArrayList<File>();
        if (!props.containsKey(variableDirectoryProperty)) {
            toProcess.add(getFileFromConfigDirectory("variables"));
        } else {
            String delimiter = (File.separator.equals("/")) ? ":" : ";";    // OS heuristic
            String[] directories = props.get(variableDirectoryProperty).toString().split(delimiter);
            for (String directory : directories) {
                Path directoryPath = Paths.get(directory);
                File directoryFile = directoryPath.toFile();
                if (directoryFile.exists()) {
                    toProcess.add(directoryFile);
                }
            }
        }

        for (File directory : toProcess) {
            if (directory == null || !directory.isDirectory()) {
                continue;
            }
            processVariablesDirectory(directory, "");
        }  
    }

    /**
     * The file name defines the variable name and its contents define the value.
     * If a directory is nested within a directory, it is recurisvely processed. 
     * A nested file will have its parent dir prepended for the property name e.g. {parent directory}/{file name}
     * If the file name ends with *.properties, then it's processed as a properties file.
     * @param directory      - The directory being processed
     * @param propertyPrefix - Tracks the nested directories to prepend
     * @throws FileNotFoundException
     * @throws Exception
     */
    private void processVariablesDirectory(File directory, String propertyPrefix)
            throws FileNotFoundException, Exception {
        for (File child : directory.listFiles()) {
            if (child.isDirectory()) {
                processVariablesDirectory(child, child.getName() + File.separator);
                continue;
            }

            if (child.getName().endsWith(".properties")) {
                parsePropertiesFromFile(child);
                continue;
            }

            String propertyName = propertyPrefix + child.getName();
            String propertyValue = new String(Files.readAllBytes(child.toPath()));
            props.setProperty(propertyName, propertyValue);
        }
    }

    /**
     * 
     * @param doc
     * @throws XPathExpressionException
     * @throws IOException
     * @throws SAXException
     */
    public void processServerXml(Document doc) throws XPathExpressionException, IOException, SAXException {
        parseIncludeVariables(doc);
        parseConfigDropinsDirVariables("defaults");
        parseVariablesForValues(doc);
        parseConfigDropinsDirVariables("overrides");
    }

    //Checks for application names in the document. Will add locations without names to a Set
    private void parseNames(Document doc, String expression) throws XPathExpressionException, IOException, SAXException {
        // parse input document
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i).getAttributes().getNamedItem("name") != null) {
                String nameValue = nodeList.item(i).getAttributes().getNamedItem("name").getNodeValue();
                String locationValue = nodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();

                // add unique values only
                if (!nameValue.isEmpty()) {
                    String resolvedName = VariableUtility.resolveVariables(log, nameValue, null, getProperties(), getDefaultProperties(), getLibertyDirPropertyFiles());
                    String resolvedLocation = VariableUtility.resolveVariables(log, locationValue, null, getProperties(), getDefaultProperties(), getLibertyDirPropertyFiles());
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
                    String resolved = VariableUtility.resolveVariables(log, nodeValue, null, getProperties(), getDefaultProperties(), getLibertyDirPropertyFiles());
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

    public String findNameForLocation(String location) {
        String appName = locationsAndNames.get(location);

        if (appName == null || appName.isEmpty()) {
            appName = location.substring(0, location.lastIndexOf('.'));
        }

        return appName;
    }

    private void parseApplication(Document doc, XPathExpression expression) throws XPathExpressionException {

        NodeList nodeList = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            String nodeValue = nodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();

            // add unique values only
            if (!nodeValue.isEmpty()) {
                String resolved = VariableUtility.resolveVariables(log, nodeValue, null, getProperties(), getDefaultProperties(), getLibertyDirPropertyFiles());
                if (resolved == null) {
                    // location could not be resolved, log message and add location as is
                    log.info("The variables referenced by location " + nodeValue + " cannot be resolved.");
                    if (!locations.contains(nodeValue)) {
                        locations.add(nodeValue);
                    }
                } else if (!locations.contains(resolved)) {
                    log.debug("Adding resolved app location: "+resolved+" for specified location: "+nodeValue);
                    locations.add(resolved);
                }
            }
        }
    }

    private void parseInclude(Document doc) throws XPathExpressionException, IOException, SAXException {
        // parse include document in source server xml
        NodeList nodeList = (NodeList) XPATH_SERVER_INCLUDE.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i) instanceof Element) {
                Element child = (Element) nodeList.item(i);

                // Need to handle more variable substitution for include location.
                String nodeValue = child.getAttribute("location");
                String includeFileName = VariableUtility.resolveVariables(log, nodeValue, null, getProperties(), getDefaultProperties(), getLibertyDirPropertyFiles());

                if (includeFileName == null || includeFileName.trim().isEmpty()) {
                    log.warn("Unable to resolve include file location "+nodeValue+". Skipping the included file during application location processing.");
                    continue;
                }

                ArrayList<Document> inclDocs = getIncludeDocs(includeFileName);
                for (Document inclDoc : inclDocs) {
                    parseApplication(inclDoc, XPATH_SERVER_APPLICATION);
                    parseApplication(inclDoc, XPATH_SERVER_WEB_APPLICATION);
                    parseApplication(inclDoc, XPATH_SERVER_ENTERPRISE_APPLICATION);
                    // handle nested include elements
                    parseInclude(inclDoc);
                }
            }
        }
    }

    private void parseConfigDropinsDir() throws XPathExpressionException, IOException, SAXException {
        File configDropins = getConfigDropinsDir();

        if (configDropins == null || !configDropins.exists()) {
            return;
        }

        File overrides = new File(configDropins, "overrides");
        if (overrides.exists()) {
            parseDropinsFiles(overrides.listFiles());
        }

        File defaults = new File(configDropins, "defaults");
        if (defaults.exists()) {
            parseDropinsFiles(defaults.listFiles());
        }
    }

    private void parseDropinsFiles(File[] files) throws XPathExpressionException, IOException, SAXException {
        Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
        for (File file : files) {
            if (file.isFile()) {
                parseDropinsFile(file);
            }
        }
    }

    private void parseDropinsFile(File file) throws IOException, XPathExpressionException, SAXException {
        // get input XML Document
        Document doc = parseDocument(file);
        if (doc != null) {
            parseApplication(doc, XPATH_SERVER_APPLICATION);
            parseApplication(doc, XPATH_SERVER_WEB_APPLICATION);
            parseApplication(doc, XPATH_SERVER_ENTERPRISE_APPLICATION);
            parseInclude(doc);
        }
    }

    private ArrayList<Document> getIncludeDocs(String loc) throws IOException, SAXException {
        ArrayList<Document> docs = new ArrayList<Document>();
        Document doc = null;
        File locFile = null;

        if (loc.startsWith("http:") || loc.startsWith("https:")) {
            if (isValidURL(loc)) {
                URL url = new URL(loc);
                doc = parseDocument(url);
                docs.add(doc);
            }
        } else if (loc.startsWith("file:")) {
            if (isValidURL(loc)) {
                locFile = new File(loc);
                // While URIs support directories, the Liberty include implementation does not support them yet.
                doc = parseDocument(locFile);
                docs.add(doc);
            }
        } else if (loc.startsWith("ftp:")) {
            // TODO handle ftp protocol
        } else {
            locFile = new File(loc);
            // check if absolute file
            if (!locFile.isAbsolute()) {
                // check configDirectory first if exists
                if (configDirectory != null && configDirectory.exists()) {
                    locFile = new File(configDirectory, loc);
                }

                if (locFile == null || !locFile.exists()) {
                    locFile = new File(getServerXML().getParentFile(), loc);
                }
            }
            parseDocumentFromFileOrDirectory(locFile, loc, docs);
        }

        if (docs.isEmpty()) {
            log.warn("Did not parse any file(s) from include location: " + loc);
        }
        return docs;
    }

    /**
     * Parses file or directory for all xml documents, and adds to ArrayList<Document>
     * @param f - file or directory to parse documents from
     * @param locationString - String representation of filepath for f
     * @param docs - ArrayList to store parsed Documents.
     * @throws FileNotFoundException
     * @throws IOException
     * @throws SAXException
     */
    private void parseDocumentFromFileOrDirectory(File f, String locationString, ArrayList<Document> docs) throws FileNotFoundException, IOException, SAXException {
        Document doc = null;
        // Earlier call to VariableUtility.resolveVariables() already converts all \ to /
        boolean isLibertyDirectory = locationString.endsWith("/");  // Liberty uses this to determine if directory. 

        if (f == null || !f.exists()) {
            log.warn("Unable to parse from file: " + f.getCanonicalPath());
            return;
        }
        // If file mismatches Liberty definition of directory
        if (f.isFile() && isLibertyDirectory) {
            log.error("Path specified a directory, but resource exists as a file (path=" + locationString + ")");
            return;
        } else if (f.isDirectory() && !isLibertyDirectory) {
            log.error("Path specified a file, but resource exists as a directory (path=" + locationString + ")");
            return;
        }

        if (f.isDirectory()) {
            parseDocumentsInDirectory(f, docs);
        } else {
            doc = parseDocument(f);
            docs.add(doc);
        }
    }

    /**
     * In a given directory, parse all direct children xml files in alphabetical order by filename, and adds to ArrayList<Document>
     * @param directory - directory to parse documents from
     * @param docs - ArrayList to store parsed Documents.
     * @throws IOException
     */
    private void parseDocumentsInDirectory(File directory, ArrayList<Document> docs) {
        // OpenLiberty reference code for behavior: https://github.com/OpenLiberty/open-liberty
        // ServerXMLConfiguration.java:parseDirectoryFiles() and XMLConfigParser.java:parseInclude()
        File[] files = directory.listFiles();
        Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
        for (File file : files) {
            try {
                docs.add(parseDocument(file));
            } catch (Exception e) {
                log.warn("Unable to parse from file " + file.getPath() + " from specified include directory: " + directory.getPath());
            }
        }
    }

    /**
     * Parse Document from XML file
     * @param file - XML file to parse for Document
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     * @throws SAXException
     */
    public Document parseDocument(File file) throws FileNotFoundException, IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return parseDocument(is);
        } catch (SAXException ex) {
            // If the file was not valid XML, assume it was some other non XML
            // file in dropins.
            log.info("Skipping parsing " + file.getAbsolutePath() + " because it was not recognized as XML.");
            return null;
        }
    }

    private Document parseDocument(URL url) throws IOException, SAXException {
        URLConnection connection = url.openConnection();
        try (InputStream is = connection.getInputStream()) {
            return parseDocument(is);
        }
    }

    private Document parseDocument(InputStream in) throws SAXException, IOException {
        try (InputStream ins = in) { // ins will be auto-closed
            return getDocumentBuilder().parse(ins);
        }
    }

    public void parsePropertiesFromFile(File propertiesFile) throws Exception, FileNotFoundException {
        if (propertiesFile != null && propertiesFile.exists()) {
            parseProperties(new FileInputStream(propertiesFile));
        }
    }

    private void parseProperties(InputStream ins) throws Exception {
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

    private boolean isValidURL(String url) {
        try {
            URL testURL = new URL(url);
            testURL.toURI();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }


    public void parseVariablesForDefaultValues(Document doc) throws XPathExpressionException {
        parseVariables(doc, true, false, false);
    }

    private void parseVariablesForValues(Document doc) throws XPathExpressionException {
        parseVariables(doc, false, true, false);
    }

    public void parseVariablesForBothValues(Document doc) throws XPathExpressionException {
        parseVariables(doc, false, false, true);
    }

    private void parseVariables(Document doc, boolean defaultValues, boolean values, boolean both) throws XPathExpressionException {
            // parse input document
        NodeList nodeList = (NodeList) XPATH_SERVER_VARIABLE.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            NamedNodeMap attr = nodeList.item(i).getAttributes();

            String varName = attr.getNamedItem("name").getNodeValue();

            if (!varName.isEmpty()) {
                // A variable can have either a value attribute OR a defaultValue attribute.
                String varValue = getValue(attr, "value");
                String varDefaultValue = getValue(attr, "defaultValue");

                if ((values || both) && (varValue != null && !varValue.isEmpty())) {
                    props.setProperty(varName, varValue);
                }

                if ((defaultValues || both) && (varDefaultValue != null && ! varDefaultValue.isEmpty())) {
                        defaultProps.setProperty(varName, varDefaultValue);
                }
            }
        }
    }

    private String getValue(NamedNodeMap attr, String nodeName) {
        String value = null;
        Node valueNode = attr.getNamedItem(nodeName);
        if (valueNode != null) {
            value = valueNode.getNodeValue();
        }
        return value;
    }

    public void parseIncludeVariables(Document doc) throws XPathExpressionException, IOException, SAXException {
        // parse include document in source server xml
        NodeList nodeList = (NodeList) XPATH_SERVER_INCLUDE.evaluate(doc, XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); i++) {
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }

            Element child = (Element) nodeList.item(i);
            // Need to handle more variable substitution for include location.
            String nodeValue = child.getAttribute("location");
            String includeFileName = VariableUtility.resolveVariables(log, nodeValue, null, getProperties(), getDefaultProperties(), getLibertyDirPropertyFiles());

            if (includeFileName == null || includeFileName.trim().isEmpty()) {
                log.warn("Unable to resolve include file location "+nodeValue+". Skipping the included file during application location processing.");
                continue;
            }

            ArrayList<Document> inclDocs = getIncludeDocs(includeFileName);

            for (Document inclDoc : inclDocs) {
                parseVariablesForBothValues(inclDoc);
                // handle nested include elements
                parseIncludeVariables(inclDoc);
            }
        }
    }

    private File getConfigDropinsDir() {
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

    private void parseConfigDropinsDirVariables(String inDir)
            throws XPathExpressionException, SAXException, IOException {
        File configDropins = getConfigDropinsDir();
        if (configDropins == null || !configDropins.exists()) {
            return;
        }

        File dir = new File(configDropins, inDir);
        if (!dir.exists()) {
            return;
        }

        File[] cfgFiles = dir.listFiles();
        Arrays.sort(cfgFiles, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
        for (File file : cfgFiles) {
            if (file.isFile()) {
                parseDropinsFilesVariables(file);
            }
        }
    }

    private void parseDropinsFilesVariables(File file)
            throws SAXException, IOException, XPathExpressionException {
        // get input XML Document
        Document doc = parseDocument(file);
        if (doc != null) {
            parseVariablesForBothValues(doc);
            parseIncludeVariables(doc);
        }
    }

    /*
     * Get the file from configDrectory if it exists, or null if not
     */
    private File getFileFromConfigDirectory(String filename) {
        File f = new File(configDirectory, filename);
        if (configDirectory != null && f.exists()) {
            return f;
        }
        return null;
    }
}
