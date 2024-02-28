package io.openliberty.tools.common.config;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import io.openliberty.tools.common.TestLogger;
import io.openliberty.tools.common.plugins.config.ServerConfigDocument;

/*
 Docs: https://openliberty.io/docs/latest/reference/config/server-configuration-overview.html
 # Variable substitution in increasing order of precedence (7 overrides 1)
 1. variable default values in the server.xml file
 2. environment variables
 3. bootstrap.properties
 4. Java system properties
 5. Variables loaded from files in the ${server.config.dir}/variables directory or other directories as specified by the VARIABLE_SOURCE_DIRS environment variable
 6. variable values declared in the server.xml file
 7. variables declared on the command line
 */

public class ServerConfigDocumentTest {
    private final static Path RESOURCES_DIR = Paths.get("src/test/resources/");
    private final static Path WLP_DIR = RESOURCES_DIR.resolve("serverConfig/liberty/wlp/");
    private final static Path WLP_USER_DIR = RESOURCES_DIR.resolve("serverConfig/liberty/wlp/usr/");
    private final static Path DEFAULTSERVER_CONFIG_DIR = WLP_USER_DIR.resolve("servers/defaultServer");
    private final static Path MOCK_SERVER_DIR = RESOURCES_DIR.resolve("servers/");
    
    // 1. variable default values in server.xml file
    // 6. variable values declared in the server.xml file
    @Test
    public void processServerXml() throws FileNotFoundException, IOException, XPathExpressionException, SAXException {
        File serversDir = MOCK_SERVER_DIR.toFile();
        Document doc;

        // no variables defined
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger());
        File empty = new File(serversDir, "emptyList.xml");
        doc = configDocument.parseDocument(empty);
        configDocument.parseVariablesForBothValues(doc);
        assertTrue(configDocument.getDefaultProperties().isEmpty() && configDocument.getProperties().isEmpty());

        // variables defined
        configDocument = new ServerConfigDocument(new TestLogger());
        File defined = new File(serversDir, "definedVariables.xml");
        doc = configDocument.parseDocument(defined);
        configDocument.parseVariablesForBothValues(doc);
        assertEquals(1, configDocument.getDefaultProperties().size());
        assertEquals("9080", configDocument.getDefaultProperties().getProperty("default.http.port"));
        assertEquals(1, configDocument.getProperties().size());
        assertEquals("9081", configDocument.getProperties().getProperty("http.port"));

        // variables defined in <include/> files
        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), new File(serversDir, "server.xml"), null, null);
        File include = new File(serversDir, "testIncludeParseVariables.xml");
        doc = configDocument.parseDocument(include);
        assertTrue(configDocument.getDefaultProperties().isEmpty() && configDocument.getProperties().isEmpty());
        configDocument.parseIncludeVariables(doc);
        assertEquals(1, configDocument.getDefaultProperties().size());
        assertEquals("9080", configDocument.getDefaultProperties().getProperty("default.http.port"));
        assertEquals(1, configDocument.getProperties().size());
        assertEquals("9081", configDocument.getProperties().getProperty("http.port"));

        // server.xmls read in increasing precedence
        // TODO: refactor
        // configDropins/defaults
        // server.xml
        // configDropins/overrides
    }

    // when a server.xml references an environment variable that could not be resolved, additionally search for:
    //   1. replace all non-alphanumeric characters with underscore char '_'
    //   2. change all characters to uppercase
    @Test
    public void serverXmlEnvVarVariationLookup() throws FileNotFoundException, Exception {
        File serverXml = DEFAULTSERVER_CONFIG_DIR.resolve("server.xml").toFile();
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), serverXml, DEFAULTSERVER_CONFIG_DIR.toFile(), new HashMap<>());
        Document serverXmlDoc = configDocument.parseDocument(serverXml);
        configDocument.parseVariablesForBothValues(serverXmlDoc);
        assertEquals("${this.value}", configDocument.getDefaultProperties().getProperty("server.env.defined"));
        assertEquals("${this.value}", configDocument.getProperties().getProperty("server.env.defined"));
        configDocument.processBootstrapProperties(new HashMap<>(), null);
        configDocument.processServerEnv();

        configDocument.parseVariablesForBothValues(serverXmlDoc);
        // TODO: implement feature and uncomment these lines
        // assertEquals("DEFINED", configDocument.getProperties().getProperty("server.env.defined"));
        // assertEquals("DEFINED", configDocument.getProperties().getProperty("bootstrap.properties.defined"));
    }
    
    // server.env files are read in increasing precedence
    //   1. {wlp.install.dir}/etc
    //   2. {wlp.user.dir}/shared
    //   3. {server.config.dir}
    // Liberty Directory Properties: https://openliberty.io/docs/latest/reference/directory-locations-properties.html
    @Test
    public void processServerEnv() throws FileNotFoundException, Exception {
        File wlpInstallDir = WLP_DIR.toFile();
        File wlpUserDir = WLP_USER_DIR.toFile();
        File serverDir = DEFAULTSERVER_CONFIG_DIR.toFile();
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger());
        Map<String, File> libertyDirectoryPropertyToFileMap = new HashMap<String, File>();
        libertyDirectoryPropertyToFileMap.put("wlp.install.dir", wlpInstallDir);
        libertyDirectoryPropertyToFileMap.put("wlp.user.dir", wlpUserDir);
        libertyDirectoryPropertyToFileMap.put("server.config.dir", serverDir);

        configDocument.initializeFields(new TestLogger(), null, serverDir, libertyDirectoryPropertyToFileMap);
        configDocument.processServerEnv();
        Properties props = configDocument.getProperties();

        // {wlp.install.dir}/etc
        assertEquals("true", props.get("etc.unique"));

        // {wlp.user.dir}/shared
        assertEquals("true", props.get("shared.unique"));
        assertEquals("true", props.get("shared.overriden"));
        
        // {server.config.dir}
        assertEquals("old_value", props.get("overriden_value"));
        assertEquals("1111", props.get("http.port"));
    }

    // 2. environment variables
    @Test
    public void environmentVariables() throws FileNotFoundException, Exception {

    }


    // 3. bootstrap.properties
    @Test
    public void processBootstrapProperties() throws FileNotFoundException, Exception {
        File serversDir = MOCK_SERVER_DIR.toFile();
        ServerConfigDocument configDocument;

        // bootstrap.properties in config dir
        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), null, serversDir, null);
        configDocument.processBootstrapProperties(new HashMap<>(), new File("DOES_NOT_EXIST"));
        assertEquals(1, configDocument.getProperties().size());

        // use bootstrapFile, kept for flexibility
        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), null, serversDir, null);
        configDocument.processBootstrapProperties(new HashMap<>(), DEFAULTSERVER_CONFIG_DIR.resolve("bootstrap.properties").toFile());
        assertEquals(2, configDocument.getProperties().size());
        assertEquals("DEFINED", configDocument.getProperties().getProperty("THAT_VALUE"));

        // test bootstrapProperty map overrides
        Map<String, String> bootstrapPropertyMap = new HashMap<String, String>();
        bootstrapPropertyMap.put("http.port", "1000");
        File serverXml = new File(serversDir, "definedVariables.xml");
        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), serverXml, serversDir, null);
        configDocument.parseVariablesForBothValues(configDocument.parseDocument(serverXml));
        assertEquals("9081", configDocument.getProperties().getProperty("http.port"));
        configDocument.processBootstrapProperties(bootstrapPropertyMap, null);
        assertEquals("1000", configDocument.getProperties().getProperty("http.port"));

        // TODO: bootstraps.include
        // configDocument = new ServerConfigDocument(new TestLogger());
        // configDocument.initializeFields(new TestLogger(), null, serversDir, null);
        // configDocument.processBootstrapProperties(new HashMap<>(), MOCK_SERVER_DIR.resolve("bootstrapInclude.properties").toFile());
        // assertEquals("9080", configDocument.getProperties().getProperty("http.port"));
    }

    // 4. Java system properties
    @Test
    public void jvmOptions() {

    }

    // 5. Variables loaded from files in the ${server.config.dir}/variables directory or other 
    //    directories as specified by the VARIABLE_SOURCE_DIRS environment variable
    @Test
    public void variablesDir() {
        // TODO: not yet implemented. read copied dir instead of src for now
        // see https://github.com/OpenLiberty/ci.common/issues/126
    }
    

    // 7. variables declared on the command line
    @Test
    public void CLI() {

    }

    // TODO: test each overrides layer
    // jvm.options override server.env
    // bootstrap.properties override jvm.options and server.env
    // server.xml override bootstrap.properties, jvm.options, and server.env
    @Test
    public void overrides() {
        File serversDir = DEFAULTSERVER_CONFIG_DIR.toFile();
        // server.xml overrides server.env
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), 
                new File(serversDir, "server.xml"), serversDir, new File(serversDir, "bootstrap.properties"), 
                new HashMap<>(), new File(serversDir, "server.env"), false, null);
        assertEquals("new_value", configDocument.getProperties().getProperty("overriden_value"));
    }
}
