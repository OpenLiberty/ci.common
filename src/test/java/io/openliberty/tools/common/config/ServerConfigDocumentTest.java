package io.openliberty.tools.common.config;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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
    public final static Logger LOGGER = Logger.getLogger(ServerConfigDocumentTest.class.getName());
    private final static String RESOURCES_DIR = "src/test/resources/";
    private final static String WLP_DIR = "serverConfig/liberty/wlp/";
    private final static String WLP_USER_DIR = "serverConfig/liberty/wlp/usr/";
    private final static String DEFAULT_USER_SERVER_DIR = "servers/defaultServer/";
    private final static String DEFAULT_SERVER_DIR = "servers/";
    
    // 1. variable default values in server.xml file
    // 6. variable values declared in the server.xml file
    @Test
    public void processServerXml() throws FileNotFoundException, IOException, XPathExpressionException, SAXException {
        File serversDir = new File(RESOURCES_DIR + DEFAULT_SERVER_DIR);
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

    // server.env files are read in increasing precedence
    //   1. {wlp.install.dir}/etc
    //   2. {wlp.user.dir}/shared
    //   3. {server.config.dir}
    // Table of directories: https://openliberty.io/docs/latest/reference/directory-locations-properties.html
    @Test
    public void processServerEnv() throws FileNotFoundException, Exception {
        File serverDir = new File(RESOURCES_DIR + WLP_USER_DIR + DEFAULT_USER_SERVER_DIR);
        File specificFile = new File(serverDir, "server2.env");
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), null, serverDir, null);
        configDocument.processServerEnv(specificFile, false);
        assertEquals("TEST", configDocument.getProperties().getProperty("keystore_password"));

        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), null, serverDir, null);
        configDocument.processServerEnv(specificFile, true);
        assertNotEquals("TEST", configDocument.getProperties().getProperty("keystore_password"));

        // TODO: multiple file locations
        // File wlpDir = new File(RESOURCES_DIR + WLP_DIR);
    }

    // 2. environment variables
    // TODO: test without using processServerEnv
    @Test
    public void environmentVariables() throws FileNotFoundException, Exception {
        // TODO: alt var lookups - https://github.com/OpenLiberty/ci.common/issues/126
        File serverConfigDir = new File(RESOURCES_DIR + WLP_USER_DIR + DEFAULT_USER_SERVER_DIR);
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), new File(serverConfigDir, "server.xml"), serverConfigDir, null);
        Document doc = configDocument.parseDocument(new File(serverConfigDir, "server.xml"));
        
        // env var not set
        configDocument.parseVariablesForBothValues(doc);
        assertEquals("${my.env.var}", configDocument.getProperties().getProperty("my.env.var.level"));
       
        // replace non-alphanumeric characters with '_' and to uppercase
        configDocument.processServerEnv(new File(serverConfigDir, "server2.env"), false);
        assertEquals("3", configDocument.getProperties().getProperty("MY_ENV_VAR"));
        configDocument.parseVariablesForBothValues(doc);
        // assertEquals("3", configDocument.getProperties().getProperty("my.env.var.level"));

        // replace non-alphanumeric characters with '_'
        configDocument.processServerEnv(null, true);
        assertEquals("2", configDocument.getProperties().getProperty("my_env_var"));
        configDocument.parseVariablesForBothValues(doc);
        // assertEquals("2", configDocument.getProperties().getProperty("my.env.var.level"));
    }


    // 3. bootstrap.properties
    @Test
    public void processBootstrapProperties() throws FileNotFoundException, Exception {
        File serversDir = new File(RESOURCES_DIR + DEFAULT_SERVER_DIR);
        File altBootstrapPropertiesFile = new File(serversDir, "bootstrap2.properties");
        Map<String, String> bootstrapProp = new HashMap<String, String>();
        bootstrapProp.put("http.port", "1000");
        ServerConfigDocument configDocument;

        // lowest to highest precedence
        // default bootstrap.properties in config dir
        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), null, serversDir, null);
        configDocument.processBootstrapProperties(new File("DOES_NOT_EXIST"), new HashMap<>(), false);
        assertEquals(1, configDocument.getProperties().size());

        // use bootstrapFile
        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), null, serversDir, null);
        configDocument.processBootstrapProperties(altBootstrapPropertiesFile, new HashMap<>(), false);
        assertEquals(2, configDocument.getProperties().size());
        assertEquals("9080", configDocument.getProperties().getProperty("http.port"));

        // test bootstrapProperty overrides
        File serverXml = new File(serversDir, "definedVariables.xml");
        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), null, serversDir, null);
        Document doc = configDocument.parseDocument(serverXml);
        configDocument.parseVariablesForBothValues(doc);
        assertEquals("9081", configDocument.getProperties().getProperty("http.port"));
        configDocument.processBootstrapProperties(altBootstrapPropertiesFile, bootstrapProp, false);
        assertEquals("1000", configDocument.getProperties().getProperty("http.port"));

        // use giveConfigDirPrecedence
        configDocument = new ServerConfigDocument(new TestLogger());
        configDocument.initializeFields(new TestLogger(), null, serversDir, null);
        configDocument.processBootstrapProperties(altBootstrapPropertiesFile, new HashMap<>(), true);
        assertEquals(1, configDocument.getProperties().size());

        // TODO: bootstraps.include
        // configDocument = new ServerConfigDocument(new TestLogger());
        // configDocument.initializeFields(new TestLogger(), null, serversDir, null);
        // configDocument.processBootstrapProperties(new File(serversDir, "bootstrapsInclude.properties"), new HashMap<>(), false);
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
        File serversDir = new File(RESOURCES_DIR + WLP_USER_DIR + DEFAULT_USER_SERVER_DIR);

        // server.xml overrides server.env
        ServerConfigDocument configDocument = new ServerConfigDocument(new TestLogger(), 
                new File(serversDir, "server.xml"), serversDir, new File(serversDir, "bootstrap.properties"), 
                new HashMap<>(), new File(serversDir, "server.env"), false, null);
        assertEquals("new_value", configDocument.getProperties().getProperty("overriden_value"));
    }
}
