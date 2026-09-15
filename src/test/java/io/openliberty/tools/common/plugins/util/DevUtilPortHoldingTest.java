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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.junit.Test;

public class DevUtilPortHoldingTest extends BaseDevUtilTest {

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }

    @Test
    public void testFindAvailablePort_returnsFreePort() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "test-app", null);
        int freePort = findFreePort();
        int found = util.findAvailablePort(freePort, false);
        assertTrue("findAvailablePort must return a port >= 1024", found >= 1024);
    }

    @Test
    public void testBindPortSocket_succeeds_onFreePort() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "test-app", null);
        int port = findFreePort();
        ServerSocket sock = util.callBindPortSocket(port);
        try {
            assertNotNull("bindPortSocket should return a non-null socket for a free port", sock);
            assertEquals("Bound port must match requested port", port, sock.getLocalPort());
        } finally {
            if (sock != null) sock.close();
        }
    }

    @Test
    public void testBindPortSocket_returnsNull_whenPortAlreadyBound() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "test-app", null);
        int port = findFreePort();
        try (ServerSocket occupier = new ServerSocket()) {
            occupier.setReuseAddress(false);
            occupier.bind(new InetSocketAddress(InetAddress.getByName(null), port), 1);

            ServerSocket result = util.callBindPortSocket(port);
            assertNull("bindPortSocket must return null when the port is already in use", result);
        }
    }

    @Test
    public void testPortHeldByBindSocket_notReturnedByFindAvailablePort() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "test-app", null);
        int port = findFreePort();
        ServerSocket held = util.callBindPortSocket(port);
        assertNotNull("Pre-condition: bindPortSocket should succeed on a free port", held);
        try {
            int next = util.findAvailablePort(port, false);
            assertNotEquals(
                "findAvailablePort must not return a port that is already held open",
                port, next);
        } finally {
            held.close();
        }
    }

    @Test
    public void testTwoSequentialFindAvailablePort_returnDifferentPorts_whenFirstIsHeld() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "test-app", null);
        int preferredPort = findFreePort();

        int portA = util.findAvailablePort(preferredPort, false);
        ServerSocket holdA = util.callBindPortSocket(portA);
        assertNotNull("Module A must be able to hold its chosen port", holdA);

        try {
            int portB = util.findAvailablePort(preferredPort, false);
            assertNotEquals(
                "Module B must receive a different port from module A's held port",
                portA, portB);
        } finally {
            holdA.close();
        }
    }
}
