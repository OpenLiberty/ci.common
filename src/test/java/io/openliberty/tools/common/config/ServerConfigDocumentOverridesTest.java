package io.openliberty.tools.common.config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import io.openliberty.tools.common.plugins.util.PluginExecutionException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import io.openliberty.tools.common.TestLogger;
import io.openliberty.tools.common.plugins.config.ServerConfigDocument;
import io.openliberty.tools.common.plugins.util.ServerFeatureUtil;
import io.openliberty.tools.common.plugins.util.VariableUtility;


// Docs: https://openliberty.io/docs/latest/reference/config/server-configuration-overview.html
public class ServerConfigDocumentOverridesTest {
    private final static Path RESOURCES_DIR = Paths.get("src/test/resources/");
    private final static Path WLP_DIR = RESOURCES_DIR.resolve("serverConfig/liberty/wlp/");
    private final static Path WLP_USER_DIR = RESOURCES_DIR.resolve("serverConfig/liberty/wlp/usr/");
    private final static Path SERVER_CONFIG_DIR = WLP_USER_DIR.resolve("servers/defaultServer");
    private final static Path SHARED_CONFIG_DIR = WLP_USER_DIR.resolve("shared");
    private final static Path SERVERS_RESOURCES_DIR = RESOURCES_DIR.resolve("servers/");
    private final static Path SERVER_CONFIG_INCLUDE_DIR = SERVER_CONFIG_DIR.resolve("includes");

    // 1. variable default values in server.xml file
    // 6. variable values declared in the server.xml file
    @Test
    public void processServerXml() throws IOException, XPathExpressionException, SAXException {
        File serversResourceDir = SERVERS_RESOURCES_DIR.toFile();
        Document doc;
        Map<String, File> libertyDirPropMap = new HashMap<String, File>();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serversResourceDir);

        // no variables defined
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, null);
        File empty = new File(serversResourceDir, "emptyList.xml");
        doc = configDocument.parseDocument(empty);
        configDocument.parseVariablesForBothValues(doc);
        assertTrue(configDocument.getDefaultProperties().isEmpty() && configDocument.getProperties().isEmpty());

        // variables defined
        configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, null);
        File defined = new File(serversResourceDir, "definedVariables.xml");
        doc = configDocument.parseDocument(defined);
        configDocument.parseVariablesForBothValues(doc);
        assertEquals(1, configDocument.getDefaultProperties().size());
        assertEquals("9080", configDocument.getDefaultProperties().getProperty("default.http.port"));
        assertEquals(1, configDocument.getProperties().size());
        assertEquals("9081", configDocument.getProperties().getProperty("http.port"));

        // variables defined in <include/> files
        configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, null);
        File include = new File(serversResourceDir, "testIncludeParseVariables.xml");
        doc = configDocument.parseDocument(include);
        assertTrue(configDocument.getDefaultProperties().isEmpty() && configDocument.getProperties().isEmpty());
        configDocument.parseIncludeVariables(doc);
        assertEquals(1, configDocument.getDefaultProperties().size());
        assertEquals("9080", configDocument.getDefaultProperties().getProperty("default.http.port"));
        assertEquals(1, configDocument.getProperties().size());
        assertEquals("9081", configDocument.getProperties().getProperty("http.port"));

        // server.xml configDropins precedence
        File serverConfigDir = SERVER_CONFIG_DIR.toFile();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serverConfigDir);
        configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, null);
        doc = configDocument.parseDocument(new File(serverConfigDir, "server.xml"));
        configDocument.processServerXml(doc);   // Variable resolution warnings can be ignored here
        assertEquals("1", configDocument.getProperties().getProperty("config.dropins.defaults"));
        assertEquals("The value is expected to be overriden from 1 to 2", "2", configDocument.getProperties().getProperty("config.dropins.server"));
        assertEquals("The value is expected to be overriden from 1 to 3", "3", configDocument.getProperties().getProperty("config.dropins.overrides"));
    }

    // when a server.xml references an environment variable that could not be resolved, additionally search for:
    //   1. replace all non-alphanumeric characters with underscore char '_'
    //   2. change all characters to uppercase
    //   3. remove the env. prefix (<19.0.0.3 behavior)
    @Test
    public void serverXmlEnvVarVariationLookup() throws Exception {
        File serverConfigDir = SERVER_CONFIG_DIR.toFile();
        Map<String, File> libertyDirPropMap = new HashMap<String, File>();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serverConfigDir);

        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, null);
        Document serverXmlDoc = configDocument.parseDocument(new File(serverConfigDir, "server.xml"));
        configDocument.parseVariablesForBothValues(serverXmlDoc);
        assertEquals("${this.value}", configDocument.getDefaultProperties().getProperty("server.env.defined"));
        assertEquals("${this.value}", configDocument.getProperties().getProperty("server.env.defined"));
        assertEquals("${that.value}", configDocument.getProperties().getProperty("bootstrap.property.defined"));

        configDocument.processBootstrapProperties();
        assertFalse(configDocument.getProperties().containsKey("that.value"));
        assertTrue(configDocument.getProperties().containsKey("THAT_VALUE"));
        configDocument.processServerEnv();
        assertFalse(configDocument.getProperties().containsKey("this.value"));
        assertTrue(configDocument.getProperties().containsKey("this_value"));

        configDocument.parseVariablesForBothValues(serverXmlDoc);
        String resolveUnderscore = VariableUtility.resolveVariables(new TestLogger(), "${this.value}",
                null, configDocument.getProperties(), configDocument.getDefaultProperties(), libertyDirPropMap);
        assertEquals("DEFINED", resolveUnderscore);
        String resolveUnderscoreToUpper = VariableUtility.resolveVariables(new TestLogger(), "${that.value}",
                null, configDocument.getProperties(), configDocument.getDefaultProperties(), libertyDirPropMap);
        assertEquals("DEFINED", resolveUnderscoreToUpper);
        String resolveEnvPrefix = VariableUtility.resolveVariables(new TestLogger(), "${env.HOST}",
                null, configDocument.getProperties(), configDocument.getDefaultProperties(), libertyDirPropMap);
        assertEquals("localhost", resolveEnvPrefix);
    }

    @Test
    public void processServerEnv() throws Exception {
        File wlpInstallDir = WLP_DIR.toFile();
        File wlpUserDir = WLP_USER_DIR.toFile();
        File serverDir = SERVER_CONFIG_DIR.toFile();
        Map<String, File> libertyDirectoryPropertyToFileMap = new HashMap<String, File>();
        libertyDirectoryPropertyToFileMap.put(ServerFeatureUtil.WLP_INSTALL_DIR, wlpInstallDir);
        libertyDirectoryPropertyToFileMap.put(ServerFeatureUtil.WLP_USER_DIR, wlpUserDir);
        libertyDirectoryPropertyToFileMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serverDir);
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirectoryPropertyToFileMap);
        configDocument.processServerEnv();
        Properties props = configDocument.getProperties();

        // in increasing precedence
        // 1. {wlp.install.dir}/etc
        assertEquals("true", props.get("etc.unique"));

        // 2. {wlp.user.dir}/shared
        assertEquals("true", props.get("shared.unique"));
        assertEquals("This value is expected to be overriden from false to true","true", props.get("shared.overriden"));

        // 3. {server.config.dir}
        assertEquals("old_value", props.get("overriden_value"));
        assertEquals("This value is expected to be overriden from 9081 to 1111", "1111", props.get("http.port"));
    }

    // 3. bootstrap.properties
    @Test
    public void processBootstrapProperties() throws FileNotFoundException, Exception {
        File serversDir = SERVERS_RESOURCES_DIR.toFile();
        ServerConfigDocument configDocument;
        Map<String, File> libertyDirPropMap = new HashMap<String, File>();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serversDir);

        // bootstrap.properties
        configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, null);
        configDocument.processBootstrapProperties();
        assertEquals(1, configDocument.getProperties().size());
        assertEquals("extraFeatures.xml", configDocument.getProperties().getProperty("extras.filename"));

        // bootstrap.include
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, new File(serversDir, "bootstrapInclude"));
        configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, null);
        configDocument.processBootstrapProperties();
        assertEquals(2, configDocument.getProperties().size());
        assertTrue(configDocument.getProperties().containsKey("bootstrap.include"));
        assertEquals("extraFeatures.xml", configDocument.getProperties().getProperty("extras.filename"));

        // bootstrap.include termination check
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, new File(serversDir, "bootstrapOuroboros"));
        configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, null);
        configDocument.processBootstrapProperties();
    }

    // 5. Variables loaded from files in the ${server.config.dir}/variables directory or other
    //    directories as specified by the VARIABLE_SOURCE_DIRS environment variable
    @Test
    public void variablesDir() throws FileNotFoundException, Exception {
        File serversDir = SERVER_CONFIG_DIR.toFile();
        Map<String, File> libertyDirPropMap = new HashMap<String, File>();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serversDir);

        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap);
        configDocument.processVariablesDirectory();
        Properties props = configDocument.getProperties();
        assertEquals("9080", props.getProperty("httpPort"));
        assertEquals("1000", props.getProperty(String.join(File.separator, "nested", "httpPort")));
        assertEquals("1", props.getProperty("VALUE_1"));
        assertEquals("2", props.getProperty("VALUE_2"));

        // process VARIABLE_SOURCE_DIRS
        String delimiter = (File.separator.equals("/")) ? ":" : ";";
        String variableSourceDirsTestValue = String.join(delimiter,
                SERVERS_RESOURCES_DIR.resolve("variables").toString(),
                SERVER_CONFIG_DIR.toString(),
                "DOES_NOT_EXIST");
        Properties initProperties = new Properties();
        initProperties.put("VARIABLE_SOURCE_DIRS", variableSourceDirsTestValue);
        configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap, initProperties);
        configDocument.processVariablesDirectory();

        props = configDocument.getProperties();
        assertEquals("outer_space", props.getProperty("outer.source"));
        assertEquals("1", props.getProperty("VALUE_1"));
    }

    // Run the method
    @Test
    public void initializeAppsLocationTest() throws PluginExecutionException {
        File serverConfigDir = SERVER_CONFIG_DIR.toFile();
        Map<String, File> libertyDirPropMap = new HashMap<String, File>();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serverConfigDir);
        libertyDirPropMap.put(ServerFeatureUtil.WLP_INSTALL_DIR, WLP_DIR.toFile());
        libertyDirPropMap.put(ServerFeatureUtil.WLP_USER_DIR, WLP_USER_DIR.toFile());
        libertyDirPropMap.put(ServerFeatureUtil.SHARED_CONFIG_DIR, SHARED_CONFIG_DIR.toFile());
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap);

        Properties properties = configDocument.getProperties();
        Properties defaultProperties = configDocument.getDefaultProperties();

        // default properties in server.xml
        assertEquals(3, defaultProperties.size());

        // server.env
        // wlp/etc
        assertEquals("true", properties.get("etc.unique"));
        // wlp/shared > etc
        assertEquals("The value is expected to be overriden from false to true","true", properties.get("shared.overriden"));
        // serverConfig > shared
        assertEquals("The value is expected to be overriden from 9081 to 1111","1111", properties.get("http.port"));

        // bootstrap.properties, overrides server.env
        assertEquals("The value is expected to be overriden from false to true","true", properties.get("bootstrap.properties.override"));
        // variables dir, overrides bootstrap.prop
        assertEquals("The value is expected to be overriden from false to true","true", properties.get("variables.override"));
        // configDropins, overrides variable dir
        assertEquals("The value is expected to be overriden from 9080 to 7777","7777", properties.get("httpPort"));
    }

   @Test
    public void testProcessServerXmlWithSpringBootApplicationNode() throws IOException, PluginExecutionException {
        File serversResourceDir = SERVERS_RESOURCES_DIR.toFile();
        File springBootServerXmlDir = new File(serversResourceDir, "springBootApplicationTest");
        File serverXml = new File(springBootServerXmlDir, "valid_server.xml");
        File newServerXml=new File(springBootServerXmlDir, "server.xml");
        Files.copy(serverXml.toPath(),newServerXml.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Map<String, File> libertyDirPropMap = new HashMap<>();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, springBootServerXmlDir);
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap);
        assertTrue("ServerConfigDocument getSpringBootAppNodeLocation should not be empty  ",configDocument.getSpringBootAppNodeLocation().isPresent());
        assertEquals("ServerConfigDocument locations does not contain expected  ", "guide-spring-boot-0.1.0.jar", configDocument.getSpringBootAppNodeLocation().get());
    }

    @Test
    public void testProcessServerXmlWithMultipleSpringBootNodes() throws IOException {
        File serversResourceDir = SERVERS_RESOURCES_DIR.toFile();
        File springBootServerXmlDir = new File(serversResourceDir, "springBootApplicationTest");
        File serverXml = new File(springBootServerXmlDir, "invalid_server.xml");
        File newServerXml = new File(springBootServerXmlDir, "server.xml");
        Files.copy(serverXml.toPath(), newServerXml.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Map<String, File> libertyDirPropMap = new HashMap<>();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, springBootServerXmlDir);
        assertThrows("Found multiple springBootApplication elements specified in the server configuration. Only one springBootApplication can be configured per Liberty server.",
                PluginExecutionException.class, () -> new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap));
    }

    @Test
    public void testServerConfigDocumentForLCLS() throws IOException, PluginExecutionException {
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), null,
                WLP_DIR.toFile(),WLP_USER_DIR.toFile(),SERVER_CONFIG_DIR.toFile());
        assertEquals("ServerConfigDocument Liberty Property file map is not created properly ", 8, configDocument.getLibertyDirPropertyFiles().size());
        assertEquals("ServerConfigDocument http.port variable is not assigned properly ", "1111", configDocument.getProperties().getProperty("http.port"));
        assertEquals("ServerConfigDocument includeLocation default property is not assigned properly ", "includes", configDocument.getDefaultProperties().getProperty("includeLocation"));
    }

    @Test
    public void testProcessServerXmlWithMultipleSpringBootNodesInMultipleConfigFiles() throws IOException{
        // we have added <springbootapplication> in valid_server.xml and included_server.xml
        File serverConfigDir = SERVER_CONFIG_DIR.toFile();
        Map<String, File> libertyDirPropMap = new HashMap<String, File>();
        libertyDirPropMap.put(ServerFeatureUtil.SERVER_CONFIG_DIR, serverConfigDir);
        libertyDirPropMap.put(ServerFeatureUtil.WLP_INSTALL_DIR, WLP_DIR.toFile());
        libertyDirPropMap.put(ServerFeatureUtil.WLP_USER_DIR, WLP_USER_DIR.toFile());
        libertyDirPropMap.put(ServerFeatureUtil.SHARED_CONFIG_DIR, SHARED_CONFIG_DIR.toFile());
        File serversResourceDir = SERVERS_RESOURCES_DIR.toFile();
        File springBootServerXmlDir = new File(serversResourceDir, "springBootApplicationTest");

        File includedServerXml = new File(springBootServerXmlDir, "included_server.xml");
        File newIncludedServerXml = new File(SERVER_CONFIG_INCLUDE_DIR.toFile(), "included_server.xml");
        Files.copy(includedServerXml.toPath(), newIncludedServerXml.toPath(), StandardCopyOption.REPLACE_EXISTING);
        File validServerXml = new File(springBootServerXmlDir, "valid_server.xml");
        File newValidServerXml = new File(SERVER_CONFIG_INCLUDE_DIR.toFile(), "valid_server.xml");
        Files.copy(validServerXml.toPath(), newValidServerXml.toPath(), StandardCopyOption.REPLACE_EXISTING);
        assertThrows("Found multiple springBootApplication elements specified in the server configuration. Only one springBootApplication can be configured per Liberty server.",
                PluginExecutionException.class, () -> new ServerConfigDocument(new TestLogger(), null, libertyDirPropMap));
        Files.delete(newIncludedServerXml.toPath());
        Files.delete(newValidServerXml.toPath());
    }
}