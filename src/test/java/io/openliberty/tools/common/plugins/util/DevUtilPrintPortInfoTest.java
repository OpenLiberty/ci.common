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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Tests that printPortInfo outputs a Liberty welcome page URL alongside each port line,
 * for both non-container (server) and container modes.
 */
public class DevUtilPrintPortInfoTest extends BaseDevUtilTest {

    private DevTestUtil newUtil() throws Exception {
        return (DevTestUtil) getNewDevUtil(null);
    }

    // -----------------------------------------------------------------------
    // Non-container: HTTP port
    // -----------------------------------------------------------------------

    @Test
    public void testServerHttpPortUrlPrinted() throws Exception {
        DevTestUtil util = newUtil();
        int portPrefixIndex = util.parseHostName(
                "Web application available (default_host): http://myhostname:9080/myapp/");
        util.parseHttpPort(
                "Web application available (default_host): http://myhostname:9080/myapp/", portPrefixIndex);

        util.printPortInfo(true);

        assertTrue("Expected HTTP port line to be printed",
                util.hasMessage("Liberty server HTTP port: [ 9080 ]"));
        assertTrue("Expected HTTP URL to be printed",
                util.hasMessage("Liberty welcome page: http://myhostname:9080/"));
    }

    @Test
    public void testServerHttpPortUrlUsesActualHostname() throws Exception {
        DevTestUtil util = newUtil();
        // Simulate Liberty bound to a real IP address (not localhost)
        int portPrefixIndex = util.parseHostName(
                "Web application available (default_host): http://192.168.1.26:9080/myapp/");
        util.parseHttpPort(
                "Web application available (default_host): http://192.168.1.26:9080/myapp/", portPrefixIndex);

        util.printPortInfo(true);

        assertTrue("Expected URL with actual IP address",
                util.hasMessage("Liberty welcome page: http://192.168.1.26:9080/"));
        assertFalse("localhost should not appear in URL when server bound to IP",
                util.hasMessage("Liberty welcome page: http://localhost:9080/"));
    }

    // -----------------------------------------------------------------------
    // Non-container: HTTPS port
    // -----------------------------------------------------------------------

    @Test
    public void testServerHttpsPortUrlPrinted() throws Exception {
        DevTestUtil util = newUtil();
        int portPrefixIndex = util.parseHostName(
                "Web application available (default_host): http://myhostname:9080/myapp/");
        util.parseHttpPort(
                "Web application available (default_host): http://myhostname:9080/myapp/", portPrefixIndex);

        List<String> tcpMessages = new ArrayList<>();
        tcpMessages.add("CWWKO0219I: TCP Channel defaultHttpEndpoint-ssl has been started and is now listening for requests on host myhostname port 9443.");
        util.parseHttpsPort(tcpMessages);

        util.printPortInfo(true);

        assertTrue("Expected HTTPS port line to be printed",
                util.hasMessage("Liberty server HTTPS port: [ 9443 ]"));
        assertTrue("Expected HTTPS URL to be printed",
                util.hasMessage("Liberty welcome page: https://myhostname:9443/"));
    }

    // -----------------------------------------------------------------------
    // Non-container: no port — no URL printed
    // -----------------------------------------------------------------------

    @Test
    public void testNoUrlPrintedWhenNoPortAvailable() throws Exception {
        DevTestUtil util = newUtil();
        // Do not parse any port — httpPort and httpsPort remain null

        util.printPortInfo(true);

        assertFalse("No URL should be printed when no port is available",
                util.hasMessage("Liberty welcome page: http://"));
        assertFalse("No URL should be printed when no port is available",
                util.hasMessage("Liberty welcome page: https://"));
    }

    // -----------------------------------------------------------------------
    // Non-container: non-default port
    // -----------------------------------------------------------------------

    @Test
    public void testServerNonDefaultHttpPortUrl() throws Exception {
        DevTestUtil util = newUtil();
        int portPrefixIndex = util.parseHostName(
                "Web application available (default_host): http://myhostname:9085/myapp/");
        util.parseHttpPort(
                "Web application available (default_host): http://myhostname:9085/myapp/", portPrefixIndex);

        util.printPortInfo(true);

        assertTrue("Expected HTTP port line with non-default port",
                util.hasMessage("Liberty server HTTP port: [ 9085 ]"));
        assertTrue("Expected HTTP URL with non-default port",
                util.hasMessage("Liberty welcome page: http://myhostname:9085/"));
    }

    // -----------------------------------------------------------------------
    // Non-container: localhost
    // -----------------------------------------------------------------------

    @Test
    public void testServerLocalhostHttpPortUrl() throws Exception {
        DevTestUtil util = newUtil();
        int portPrefixIndex = util.parseHostName(
                "Web application available (default_host): http://localhost:9080/myapp/");
        util.parseHttpPort(
                "Web application available (default_host): http://localhost:9080/myapp/", portPrefixIndex);

        util.printPortInfo(true);

        assertTrue("Expected HTTP URL with localhost",
                util.hasMessage("Liberty welcome page: http://localhost:9080/"));
    }

    // -----------------------------------------------------------------------
    // Container: HTTP port
    // -----------------------------------------------------------------------

    @Test
    public void testContainerHttpPortUrlPrinted() throws Exception {
        DevTestUtil util = getNewContainerUtil();
        // Internal container port 9080 mapped to host port 9080
        util.setContainerPorts("9080", "9080", null, null);

        util.printPortInfo(true);

        assertTrue("Expected container HTTP port line to be printed",
                util.hasMessage("Internal container HTTP port [ 9080 ] is mapped to container host port [ 9080 ]"));
        assertTrue("Expected container HTTP welcome page URL to be printed",
                util.hasMessage("Liberty welcome page: http://localhost:9080/"));
    }

    // -----------------------------------------------------------------------
    // Container: HTTPS port
    // -----------------------------------------------------------------------

    @Test
    public void testContainerHttpsPortUrlPrinted() throws Exception {
        DevTestUtil util = getNewContainerUtil();
        // Internal container port 9443 mapped to host port 9443
        util.setContainerPorts(null, null, "9443", "9443");

        util.printPortInfo(true);

        assertTrue("Expected container HTTPS port line to be printed",
                util.hasMessage("Internal container HTTPS port [ 9443 ] is mapped to container host port [ 9443 ]"));
        assertTrue("Expected container HTTPS welcome page URL to be printed",
                util.hasMessage("Liberty welcome page: https://localhost:9443/"));
    }
}
