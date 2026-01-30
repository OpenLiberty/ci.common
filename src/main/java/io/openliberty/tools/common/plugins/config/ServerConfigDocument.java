/**
 * (C) Copyright IBM Corporation 2017, 2026.
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import io.openliberty.tools.common.plugins.util.LibertyPropFilesUtility;
import io.openliberty.tools.common.plugins.util.OSUtil;
import io.openliberty.tools.common.plugins.util.PluginExecutionException;
import org.apache.commons.io.comparator.NameFileComparator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.openliberty.tools.common.CommonLoggerI;
import io.openliberty.tools.common.plugins.util.ServerFeatureUtil;
import io.openliberty.tools.common.plugins.util.VariableUtility;

import static io.openliberty.tools.common.plugins.util.VariableUtility.parseVariables;

// Moved from ci.maven/liberty-maven-plugin/src/main/java/net/wasdev/wlp/maven/plugins/ServerConfigDocument.java
public class ServerConfigDocument {

    private CommonLoggerI log;

    private File configDirectory;
    private File serverXMLFile;
    private File originalServerXMLFile;

    private Set<String> names;
    private Set<String> namelessLocations;
    private Set<String> locations;
    private HashMap<String, String> locationsAndNames;
    private Properties props;
    private Properties defaultProps;
    private Map<String, File> libertyDirectoryPropertyToFile = null;

    Optional<String> springBootAppNodeLocation = Optional.empty();
    Optional<String> springBootAppNodeDocumentURI = Optional.empty();

    private static final XPathExpression XPATH_SERVER_APPLICATION;
    private static final XPathExpression XPATH_SERVER_WEB_APPLICATION;
    private static final XPathExpression XPATH_SERVER_SPRINGBOOT_APPLICATION;
    private static final XPathExpression XPATH_SERVER_ENTERPRISE_APPLICATION;
    private static final XPathExpression XPATH_SERVER_INCLUDE;
    public static final XPathExpression XPATH_SERVER_VARIABLE;
    private static final XPathExpression XPATH_ALL_SERVER_APPLICATIONS;
    // Windows style: !VAR!
    private static final Pattern WINDOWS_EXPANSION_VAR_PATTERN;
    // Linux style: ${VAR}
    private static final Pattern LINUX_EXPANSION_VAR_PATTERN;
    private static final int MAX_SUBSTITUTION_DEPTH = 5;


    static {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            XPATH_SERVER_APPLICATION = xPath.compile("/server/application");
            XPATH_SERVER_WEB_APPLICATION = xPath.compile("/server/webApplication");
            XPATH_SERVER_SPRINGBOOT_APPLICATION = xPath.compile("/server/springBootApplication");
            XPATH_SERVER_ENTERPRISE_APPLICATION = xPath.compile("/server/enterpriseApplication");
            XPATH_SERVER_INCLUDE = xPath.compile("/server/include");
            XPATH_SERVER_VARIABLE = xPath.compile("/server/variable");
            XPATH_ALL_SERVER_APPLICATIONS = xPath.compile("/server/application | /server/webApplication | /server/enterpriseApplication | /server/springBootApplication");
        } catch (XPathExpressionException ex) {
            // These XPath expressions should all compile statically.
            // Compilation failures mean the expressions are not syntactically
            // correct
            throw new RuntimeException(ex);
        }
        WINDOWS_EXPANSION_VAR_PATTERN = Pattern.compile("!(\\w+)!");
        LINUX_EXPANSION_VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");
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
     * Adapt when ready. Expects the libertyDirPropertyFiles to be populated
     *
     * @param log
     * @param originalServerXMLFile
     * @param libertyDirPropertyFiles
     */
    public ServerConfigDocument(CommonLoggerI log, File originalServerXMLFile, Map<String, File> libertyDirPropertyFiles) throws PluginExecutionException {
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
        this.originalServerXMLFile = originalServerXMLFile;
        initializeAppsLocation();
    }


    /**
     * Constructor for LCLS usage
     * @param log logger instance
     * @param originalServerXMLFile  original xml file
     * @param installDirectory install directory file
     * @param userDirectory user directory file
     * @param serverDirectory server directory file
     * @throws PluginExecutionException
     * @throws IOException
     */
    public ServerConfigDocument(CommonLoggerI log, File originalServerXMLFile, File installDirectory, File userDirectory, File serverDirectory, File serverOutputDirectory) throws PluginExecutionException, IOException {
        this(log, originalServerXMLFile, LibertyPropFilesUtility.getLibertyDirectoryPropertyFiles(log, installDirectory, userDirectory, serverDirectory, serverOutputDirectory));
    }

    // test constructor that takes in initial properties to be called modularly
    public ServerConfigDocument(CommonLoggerI log, File originalServerXMLFile, Map<String, File> libertyDirPropertyFiles, Properties initProperties) {
        this.log = log;
        libertyDirectoryPropertyToFile = new HashMap<String, File>(libertyDirPropertyFiles);
        configDirectory = libertyDirectoryPropertyToFile.get(ServerFeatureUtil.SERVER_CONFIG_DIR);
        serverXMLFile = getFileFromConfigDirectory("server.xml");
        locations = new HashSet<String>();
        names = new HashSet<String>();
        namelessLocations = new HashSet<String>();
        locationsAndNames = new HashMap<String, String>();
        props = new Properties();
        if (initProperties != null) props.putAll(initProperties);
        defaultProps = new Properties();
        this.originalServerXMLFile = originalServerXMLFile;
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
            docBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            docBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            docBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            docBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            docBuilderFactory.setXIncludeAware(false);
            docBuilderFactory.setExpandEntityReferences(false);
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
     //  8. add liberty predefined variables to variable map
     */
    public void initializeAppsLocation() throws PluginExecutionException {
        try {
            // 1. Need to parse variables in the server.xml for default values before trying to
            //    find the include files in case one of the variables is used in the location.
            Document doc = parseDocument(serverXMLFile);
            parseVariablesForDefaultValues(doc);

            // 2. get variables from server.env
            processServerEnv();

            // 3. get variables from jvm.options. Incomplete uncommon usecase. Uncomment when ready.
            // processJvmOptions();

            // 3. get variables from bootstrap.properties
            processBootstrapProperties();

            // 4. Java system properties
            processSystemProperties();

            // 5. Variables loaded from 'variables' directory
            processVariablesDirectory();

            // 6. variable values declared in server.xml(s)
            processServerXml(doc);

            // 7. variables declared on the command line
            // Maven: https://github.com/OpenLiberty/ci.maven/blob/main/docs/common-server-parameters.md#setting-liberty-configuration-with-maven-project-properties
            // Gradle: https://github.com/dshimo/ci.gradle/blob/main/docs/libertyExtensions.md

            // 8. liberty pre defined variables
            processPredefinedVariables();

            parseApplication(doc, XPATH_SERVER_APPLICATION);
            parseApplication(doc, XPATH_SERVER_WEB_APPLICATION);
            parseApplication(doc, XPATH_SERVER_ENTERPRISE_APPLICATION);
            parseApplication(doc, XPATH_SERVER_SPRINGBOOT_APPLICATION);
            parseNames(doc, XPATH_ALL_SERVER_APPLICATIONS);
            parseInclude(doc);
            parseConfigDropinsDir();

        } catch (Exception e) {
            if(e instanceof PluginExecutionException){
                throw (PluginExecutionException)e;
            }
            e.printStackTrace();
        }
    }

    /**
     * Add predefined variables into variable map
     * uses liberty property files map and take all predefined properties
     * LibertyPropFilesUtility.getLibertyDirectoryPropertyFiles() takes care of adding all predefined properties into libertyDirPropertyFiles
     * @throws IOException
     */
    private void processPredefinedVariables() throws IOException{
        for (Map.Entry<String, File> stringFileEntry : getLibertyDirPropertyFiles().entrySet()) {
            props.put(stringFileEntry.getKey(), stringFileEntry.getValue().getCanonicalPath());
        }
    }

    /**
     * server.env file read order
     *   1. {wlp.install.dir}/etc/
     *   2. {wlp.user.dir}/shared/
     *   3. {server.config.dir}/
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
        Map<String, String> resolvedMap = new HashMap<>();

        props.forEach((k, v) -> {
            String key = (String) k;
            String value = (String) v;
            Set<String> resolveInProgressProps = new HashSet<>();
            resolveInProgressProps.add(key);
            resolvedMap.put(key, resolveExpansionProperties(props, value,key, resolveInProgressProps, MAX_SUBSTITUTION_DEPTH));
        });

        // After all resolutions are calculated, update the original props
        props.putAll(resolvedMap);
    }

    /**
     * Resolves property placeholders recursively with safety guards.
     * Uses appendReplacement to ensure a single-pass scan and strict depth control.
     *
     * @param props                  The properties source.
     * @param value                  The string currently being processed.
     * @param resolveInProgressProps The set of variables in the current stack to detect loops.
     * @param remainingDepth         Remaining levels of recursion allowed.
     * @return The resolved string or raw text if depth/circularity limits are hit.
     */
    private String resolveExpansionProperties(Properties props, String value, String key, Set<String> resolveInProgressProps, int remainingDepth) {
        if (value == null) return null;

        // 1. Initial Depth Check
        if (remainingDepth <= 0) {
            log.warn("Max substitution depth reached for key: " + key + ". Returning raw value: " + value);
            return value;
        }
        Pattern pattern = OSUtil.isWindows() ? WINDOWS_EXPANSION_VAR_PATTERN : LINUX_EXPANSION_VAR_PATTERN;
        Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);

            // 2. Circular Reference Guard
            if (resolveInProgressProps.contains(varName)) {
                log.warn("Circular reference detected: " + varName + " depends on itself in key " + key + ". Skipping expansion.");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String replacement = props.getProperty(varName);
            if (replacement != null) {
                // 3. Recursive Logic with Depth Guard
                if (remainingDepth <= 1) {
                    log.warn("Depth limit hit at '" + varName + "'. Appending raw value without further expansion.");
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                } else {
                    resolveInProgressProps.add(varName);
                    try {
                        String resolved = resolveExpansionProperties(props, replacement, key, resolveInProgressProps, remainingDepth - 1);
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
                    } finally {
                        resolveInProgressProps.remove(varName);
                    }
                }
            } else {
                // Variable not found in Properties; leave the original ${VAR} or !VAR!
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
            log.debug("Resolving Property " + varName + "value with " + sb);
        }
        // 4. Finalize the string
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Likely not needed to be processed by the LMP/LGP tools. These properties benefit the JVM
     * System properties would need to process out -D. jvm.options do not support variable substitution
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
        parsePropertiesFromFile(getFileFromConfigDirectory("configDropins/default/" + jvmOptionsString));
        parsePropertiesFromFile(getFileFromConfigDirectory(jvmOptionsString));
        parsePropertiesFromFile(getFileFromConfigDirectory("configDropins/overrides/" + jvmOptionsString));
    }

    /**
     * Process bootstrap.properties and boostrap.include
     * @throws Exception
     * @throws FileNotFoundException
     */
    public void processBootstrapProperties() throws Exception, FileNotFoundException {
        File bootstrapFile = getFileFromConfigDirectory("bootstrap.properties");
        if (bootstrapFile == null) {
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
            parsePropertiesFromFile(bootstrapIncludeFile);
            processedBootstrapIncludes.add(bootstrapIncludeFile.getAbsolutePath());
            processBootstrapInclude(processedBootstrapIncludes);
        }
    }

    private void processSystemProperties() {
        props.putAll(System.getProperties());
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
    private void parseNames(Document doc, XPathExpression expression) throws XPathExpressionException, IOException, SAXException {
        // parse input document
        NodeList nodeList = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);

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

    private void parseApplication(Document doc, XPathExpression expression) throws XPathExpressionException, PluginExecutionException {

        NodeList nodeList = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);
        if(expression.equals(XPATH_SERVER_SPRINGBOOT_APPLICATION) && nodeList.getLength()>1){
            throw new PluginExecutionException(String.format("Found multiple springBootApplication elements specified in the server configuration file %s. Only one springBootApplication can be configured per Liberty server.", doc.getDocumentURI()));
        }
        for (int i = 0; i < nodeList.getLength(); i++) {
            String nodeValue = nodeList.item(i).getAttributes().getNamedItem("location").getNodeValue();
            // add unique values only
            if (!nodeValue.isEmpty()) {
                checkForSpringBootApplicationNode(doc, expression, nodeValue);
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

    private void checkForSpringBootApplicationNode(Document doc, XPathExpression expression, String nodeValue) throws PluginExecutionException {
        if(expression.equals(XPATH_SERVER_SPRINGBOOT_APPLICATION)){
            // checking whether any springBootAppNodeLocation already configured from other server configuration files
            if(springBootAppNodeLocation.isPresent() && springBootAppNodeDocumentURI.isPresent()){
                throw new PluginExecutionException(String.format("Found multiple springBootApplication elements specified in the server configuration in files [%s, %s]. Only one springBootApplication can be configured per Liberty server.", springBootAppNodeDocumentURI.get(), doc.getDocumentURI()));
            }
            else {
                log.debug("Setting springBootApplication location as "+ nodeValue);
                springBootAppNodeLocation = Optional.of(nodeValue);
                springBootAppNodeDocumentURI = Optional.of(doc.getDocumentURI());
            }
        }
    }

    private void parseInclude(Document doc) throws XPathExpressionException, IOException, SAXException, PluginExecutionException {
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
                    parseApplication(inclDoc, XPATH_SERVER_SPRINGBOOT_APPLICATION);
                    parseApplication(inclDoc, XPATH_SERVER_ENTERPRISE_APPLICATION);
                    parseNames(inclDoc, XPATH_ALL_SERVER_APPLICATIONS);
                    // handle nested include elements
                    parseInclude(inclDoc);
                }
            }
        }
    }

    private void parseConfigDropinsDir() throws XPathExpressionException, IOException, SAXException, PluginExecutionException {
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

    private void parseDropinsFiles(File[] files) throws XPathExpressionException, IOException, SAXException, PluginExecutionException {
        Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
        for (File file : files) {
            if (file.isFile()) {
                parseDropinsFile(file);
            }
        }
    }

    private void parseDropinsFile(File file) throws IOException, XPathExpressionException, SAXException, PluginExecutionException {
        // get input XML Document
        Document doc = parseDocument(file);
        if (doc != null) {
            parseApplication(doc, XPATH_SERVER_APPLICATION);
            parseApplication(doc, XPATH_SERVER_WEB_APPLICATION);
            parseApplication(doc, XPATH_SERVER_SPRINGBOOT_APPLICATION);
            parseApplication(doc, XPATH_SERVER_ENTERPRISE_APPLICATION);
            parseNames(doc, XPATH_ALL_SERVER_APPLICATIONS);
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
            Document document= parseDocument(is);
            document.setDocumentURI(file.getCanonicalPath());
            return document;
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
            log.debug("Processed properties from file: " + propertiesFile.getAbsolutePath());
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
        List<Properties> propsList = parseVariables(doc, true, false, false);
        defaultProps.putAll(propsList.get(1));
    }

    private void parseVariablesForValues(Document doc) throws XPathExpressionException {
        List<Properties> propsList = parseVariables(doc, false, true, false);
        props.putAll(propsList.get(0));
    }

    public void parseVariablesForBothValues(Document doc) throws XPathExpressionException {
        List<Properties> propsList = parseVariables(doc, false, false, true);
        props.putAll(propsList.get(0));
        defaultProps.putAll(propsList.get(1));
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
     * If giveConfigDirPrecedence is set to true, return the file from the configDirectory if it exists;
     * otherwise return specificFile if it exists, or null if not.
     * If giveConfigDirPrecedence is set to false, return specificFile if it exists;
     * otherwise return the file from the configDirectory if it exists, or null if not.
     */
    private File findConfigFile(String fileName, File specificFile, boolean giveConfigDirPrecedence) {
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
    private File getFileFromConfigDirectory(String filename) {
        File f = new File(configDirectory, filename);
        if (configDirectory != null && f.exists()) {
            return f;
        }
        log.debug(filename + " was not found in: " + configDirectory.getAbsolutePath());
        return null;
    }

    public File getOriginalServerXMLFile() {
        return originalServerXMLFile;
    }

    public void setOriginalServerXMLFile(File originalServerXMLFile) {
        this.originalServerXMLFile = originalServerXMLFile;
    }

    public Optional<String> getSpringBootAppNodeLocation() {
        return springBootAppNodeLocation;
    }

    public void setSpringBootAppNodeLocation(Optional<String> springBootAppNodeLocation) {
        this.springBootAppNodeLocation = springBootAppNodeLocation;
    }

}