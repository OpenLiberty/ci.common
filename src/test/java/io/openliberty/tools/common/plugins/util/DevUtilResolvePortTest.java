/**
 * (C) Copyright IBM Corporation 2026.
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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link DevUtil#resolveEffectiveContainerPort}.
 *
 * Each test creates a minimal on-disk Liberty server structure inside a
 * {@link TemporaryFolder} and calls the package-private method directly
 * through the {@link BaseDevUtilTest.DevTestUtil} subclass.
 */
public class DevUtilResolvePortTest extends BaseDevUtilTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Creates a server.xml with a literal httpEndpoint. */
    private File writeServerXml(File serverDir, String httpPort, String httpsPort) throws IOException {
        File serverXml = new File(serverDir, "server.xml");
        String port = httpPort  != null ? " httpPort=\""  + httpPort  + "\"" : "";
        String sport = httpsPort != null ? " httpsPort=\"" + httpsPort + "\"" : "";
        write(serverXml,
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\"" + port + sport + "/>\n" +
                "</server>");
        return serverXml;
    }

    /** Creates a server.xml with a variable reference in httpEndpoint. */
    private File writeServerXmlWithVar(File serverDir, String varName) throws IOException {
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <variable name=\"" + varName + "\" defaultValue=\"9090\"/>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${" + varName + "}\"/>\n" +
                "</server>");
        return serverXml;
    }

    private void write(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
    }

    private DevTestUtil util(File serverDir, File buildDir, File serverXmlFile) throws IOException {
        DevTestUtil u = new DevTestUtil(serverDir, buildDir);
        u.serverXmlFile = serverXmlFile;
        return u;
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    public void testLiteralHttpPort() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = writeServerXml(serverDir, "9090", null);
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9090, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testLiteralHttpsPort() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = writeServerXml(serverDir, null, "9453");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9453, u.resolveEffectiveContainerPort(9443, "httpsPort"));
    }

    @Test
    public void testDefaultPortReturnedWhenNoHttpEndpoint() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml, "<server><featureManager><feature>servlet-4.0</feature></featureManager></server>");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9080, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testDefaultPortReturnedWhenServerXmlMissing() throws Exception {
        File serverDir = tmp.newFolder("server");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), new File(serverDir, "nonexistent.xml"));

        assertEquals(9080, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testVariableResolvedFromDefaultValue() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = writeServerXmlWithVar(serverDir, "myHttpPort");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9090, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testVariableResolvedFromBootstrapProperties() throws Exception {
        File serverDir = tmp.newFolder("server");
        // server.xml references a variable with no defaultValue
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${http.port}\"/>\n" +
                "</server>");
        // bootstrap.properties defines the variable
        write(new File(serverDir, "bootstrap.properties"), "http.port=9091\n");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9091, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testVariableResolvedFromServerEnv() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${env.HTTP_PORT}\"/>\n" +
                "</server>");
        write(new File(serverDir, "server.env"), "HTTP_PORT=9092\n");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9092, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testVariableResolvedFromConfigDropinsOverrides() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${override.port}\"/>\n" +
                "</server>");
        // configDropins/overrides sets the variable at the highest precedence
        File overrides = new File(serverDir, "configDropins/overrides");
        overrides.mkdirs();
        write(new File(overrides, "port-override.xml"),
                "<server>\n" +
                "    <variable name=\"override.port\" value=\"9095\"/>\n" +
                "</server>");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9095, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testOverridePrecedenceOverDefault() throws Exception {
        File serverDir = tmp.newFolder("server");
        // server.xml has a defaultValue of 9090; bootstrap.properties overrides to 9093
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <variable name=\"http.port\" defaultValue=\"9090\"/>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${http.port}\"/>\n" +
                "</server>");
        write(new File(serverDir, "bootstrap.properties"), "http.port=9093\n");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        // bootstrap.properties (step 3) overrides defaultValue (step 1)
        assertEquals(9093, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testDefaultPortWhenPortIsNotANumber() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${unresolved.var}\"/>\n" +
                "</server>");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        // Variable is not defined anywhere — should fall back to default
        assertEquals(9080, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testFallsBackToConfigDirectoryWhenServerXmlFileIsNull() throws Exception {
        // serverXmlFile is null — resolveEffectiveContainerPort must fall back to configDirectory/server.xml.
        // Use separate dirs matching the real Maven layout:
        //   configDirectory = src/main/liberty/config  (source — this is where the fallback reads from)
        //   serverDirectory = target/.../defaultServer  (runtime — SCD looks for server.xml here)
        // The plugin copies server.xml from source to server dir at deploy time, so both dirs have it.
        File configDir = tmp.newFolder("src-config");
        File serverDir = tmp.newFolder("server");
        String content = "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"9097\"/>\n" +
                "</server>";
        write(new File(configDir, "server.xml"), content);
        write(new File(serverDir, "server.xml"), content);  // deployed copy for ServerConfigDocument
        DevTestUtil u = new DevTestUtil(serverDir, null, null, configDir,
                Collections.emptyList(), Collections.emptyList(), false, false);

        assertEquals(9097, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testPluginConfigXmlProvidesInstallAndUserDir() throws Exception {
        File serverDir  = tmp.newFolder("server");
        File installDir = tmp.newFolder("wlp");
        File userDir    = tmp.newFolder("usr");
        File buildDir   = tmp.newFolder("build");

        // Write server.env to the install dir (install/etc/server.env) — highest priority path
        File etcDir = new File(installDir, "etc");
        etcDir.mkdirs();
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${env.port}\"/>\n" +
                "</server>");
        write(new File(etcDir, "server.env"), "env.port=9096\n");

        // liberty-plugin-config.xml in buildDir points to installDir and userDir
        File pluginConfig = new File(buildDir, "liberty-plugin-config.xml");
        write(pluginConfig,
                "<liberty-plugin-config>\n" +
                "    <installDirectory>" + installDir.getAbsolutePath() + "</installDirectory>\n" +
                "    <userDirectory>" + userDir.getAbsolutePath() + "</userDirectory>\n" +
                "</liberty-plugin-config>");

        DevTestUtil u = util(serverDir, buildDir, serverXml);

        assertEquals(9096, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testVariableResolvedFromServerDirConfigDropinsOverrides() throws Exception {
        // Mirrors the real Maven/Gradle scenario:
        //   configDirectory = src/main/liberty/config  (source config — no configDropins here)
        //   serverDirectory = target/.../defaultServer  (runtime — plugin copies server.xml here
        //                                                and writes liberty-plugin-variable-config.xml
        //                                                into configDropins/overrides/)
        // ServerConfigDocument uses serverDirectory as SERVER_CONFIG_DIR, so it finds
        // both server.xml and configDropins/overrides/ under the same root.
        File srcConfigDir = tmp.newFolder("src-config");   // DevUtil.configDirectory (source)
        File serverDir    = tmp.newFolder("server");       // DevUtil.serverDirectory (runtime)

        // The plugin copies server.xml from srcConfigDir into serverDir at deploy time.
        // serverXmlFile points to the source copy; serverDir also has the deployed copy.
        String serverXmlContent =
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${default.http.port}\"/>\n" +
                "</server>";
        File srcServerXml    = new File(srcConfigDir, "server.xml");
        File deployedServerXml = new File(serverDir, "server.xml");
        write(srcServerXml,    serverXmlContent);
        write(deployedServerXml, serverXmlContent);  // deployed copy — SCD finds this via SERVER_CONFIG_DIR

        // liberty-plugin-variable-config.xml written by plugin into serverDir/configDropins/overrides/
        File overrides = new File(serverDir, "configDropins/overrides");
        overrides.mkdirs();
        write(new File(overrides, "liberty-plugin-variable-config.xml"),
                "<server>\n" +
                "    <variable name=\"default.http.port\" value=\"9090\"/>\n" +
                "</server>");

        // DevUtil: configDirectory = srcConfigDir, serverDirectory = serverDir
        // serverXmlFile points to source server.xml (set by watchFiles in real usage)
        DevTestUtil u = new DevTestUtil(serverDir, null, null, srcConfigDir,
                Collections.emptyList(), Collections.emptyList(), false, false);
        u.serverXmlFile = srcServerXml;

        assertEquals(9090, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testVariableResolvedForHttpsPort() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <variable name=\"my.https.port\" defaultValue=\"9453\"/>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpsPort=\"${my.https.port}\"/>\n" +
                "</server>");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9453, u.resolveEffectiveContainerPort(9443, "httpsPort"));
    }

    /**
     * A custom/external server.xml whose httpPort is defined in a sibling file
     * pulled in via a relative {@code <include>}.
     *
     * Layout:
     *   serverDir/server.xml  — contains {@code <include location="ports.xml"/>}
     *   serverDir/ports.xml   — contains the httpEndpoint with the literal port
     *
     * ServerConfigDocument resolves the relative include against configDirectory
     * (= serverDir), so it finds ports.xml and picks up the port value.
     */
    @Test
    public void testPortDefinedViaRelativeInclude() throws Exception {
        File serverDir = tmp.newFolder("server");

        // ports.xml — the included file that defines the actual port
        write(new File(serverDir, "ports.xml"),
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"9094\"/>\n" +
                "</server>");

        // server.xml delegates to ports.xml via a relative include
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <include location=\"ports.xml\"/>\n" +
                "</server>");

        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9094, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    /**
     * A custom/external server.xml with a sibling {@code configDropins} directory
     * that supplies the port variable.
     *
     * Layout:
     *   serverDir/server.xml                               — references ${ext.http.port}
     *   serverDir/configDropins/overrides/port.xml         — defines ext.http.port = 9098
     *
     * {@code ServerConfigDocument} uses {@code serverDirectory} as its config directory, so
     * it finds the sibling {@code configDropins} next to the deployed server.xml.
     * This test validates that variables from that sibling {@code configDropins} are
     * picked up and used to resolve the port, mirroring the real deployment layout.
     */
    @Test
    public void testPortFromSiblingConfigDropins() throws Exception {
        File serverDir = tmp.newFolder("server");

        // server.xml references a variable defined only in the sibling configDropins
        File serverXml = new File(serverDir, "server.xml");
        write(serverXml,
                "<server>\n" +
                "    <httpEndpoint id=\"defaultHttpEndpoint\" httpPort=\"${ext.http.port}\"/>\n" +
                "</server>");

        // Sibling configDropins/overrides defines the variable
        File overrides = new File(serverDir, "configDropins/overrides");
        overrides.mkdirs();
        write(new File(overrides, "port.xml"),
                "<server>\n" +
                "    <variable name=\"ext.http.port\" value=\"9098\"/>\n" +
                "</server>");

        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        assertEquals(9098, u.resolveEffectiveContainerPort(9080, "httpPort"));
    }

    @Test
    public void testResolveBothPortsInSinglePass() throws Exception {
        File serverDir = tmp.newFolder("server");
        File serverXml = writeServerXml(serverDir, "9090", "9453");
        DevTestUtil u = util(serverDir, tmp.newFolder("build"), serverXml);

        java.util.Map<String, Integer> defaults = new java.util.HashMap<String, Integer>();
        defaults.put("httpPort", 9080);
        defaults.put("httpsPort", 9443);

        java.util.Map<String, Integer> resolved = u.resolveEffectiveContainerPorts(defaults);
        assertEquals(Integer.valueOf(9090), resolved.get("httpPort"));
        assertEquals(Integer.valueOf(9453), resolved.get("httpsPort"));
    }
}
