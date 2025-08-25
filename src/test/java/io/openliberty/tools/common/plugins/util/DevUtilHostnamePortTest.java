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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class DevUtilHostnamePortTest extends BaseDevUtilTest {

    @Test
    public void testParseHostnameHttpPort() throws Exception {
        testHostnameAndHttpPort("Web application available (default_host): http://myhostname:9085/myapp/");
    }

    @Test
    public void testParseHostnameHttpPort2() throws Exception {
        testHostnameAndHttpPort("Web application available (default_host): http://myhostname:9085/");
    }

    @Test
    public void testParseHostnameHttpPort3() throws Exception {
        testHostnameAndHttpPort("Web application available (default_host): http://myhostname:9085");
    }

    @Test
    public void testParseHostnameHttpPortWithEscaped() throws Exception {
        testHostnameAndHttpPort("Web application available (default_host):http:\\/\\/myhostname:9085");
    }

    @Test
    public void testParseHostnameHttpPortWithEscaped2() throws Exception {
        testHostnameAndHttpPort("Web application available (default_host): http:\\/\\/myhostname:9085\\/ifix\\/");
    }

    @Test
    public void testParseHostnameHttpPortFromHttps() throws Exception {
        String message = "Web application available (default_host): https://myhostname:9085/myapp/";

        DevUtil util = getNewDevUtil(null);
        int portPrefixIndex = util.parseHostName(message);
        util.parseHttpPort(message, portPrefixIndex);
        assertEquals("myhostname", util.getHostName());
        assertEquals(null, util.getHttpPort());
    }

    private void testHostnameAndHttpPort(String message) throws PluginExecutionException, IOException {
        DevUtil util = getNewDevUtil(null);
        int portPrefixIndex = util.parseHostName(message);
        util.parseHttpPort(message, portPrefixIndex);
        assertEquals("myhostname", util.getHostName());
        assertEquals("9085", util.getHttpPort());
    }

    private final String[] tcpChannelTranslations = {
        "{\"type\":\"liberty_message\",\"host\":\"46410dbe01bd\",\"ibm_userDir\":\"\\/opt\\/ol\\/wlp\\/usr\\/\",\"ibm_serverName\":\"defaultServer\",\"message\":\"CWWKO0219I: TCP Channel {0} has been started and is now listening for requests on host {1}  (IPv6) port {2}.\",\"ibm_threadId\":\"00000033\",\"ibm_datetime\":\"2025-08-25T03:59:03.002+0000\",\"ibm_messageId\":\"CWWKO0219I\",\"module\":\"com.ibm.ws.tcpchannel.internal.TCPPort\",\"loglevel\":\"INFO\",\"ibm_sequence\":\"1756094343002_0000000000023\",\"ext_thread\":\"Default Executor-thread-1\"}",
        "[8/25/25, 13:29:17:236 UTC] 00000033 com.ibm.ws.tcpchannel.internal.TCPPort                       I CWWKO0219I: TCP Channel {0} has been started and is now listening for requests on host *  (IPv6) port {2}.",
        "[8/25/25, 13:36:19:701 UTC] 00000033 TCPPort       I   CWWKO0219I: TCP Channel {0} has been started and is now listening for requests on host *  (IPv6) port {2}.",
        "CWWKO0219I: TCP Channel {0} has been started and is now listening for requests on host {1} port {2}.",
        "CWWKO0219I: Kan\u00e1l TCP {0} byl spu\u0161t\u011bn a nyn\u00ed naslouch\u00e1 po\u017eadavk\u016fm na hostiteli {1} na portu {2}.",
        "CWWKO0219I: Der TCP-Kanal {0} wurde gestartet und ist jetzt f\u00fcr Anforderungen auf dem Host {1} an Port {2} empfangsbereit.",
        "CWWKO0219I: El canal TCP {0} se ha iniciado y ahora est\u00e1 a la escucha de solicitudes en el host {1} puerto {2}.",
        "CWWKO0219I: Le canal TCP {0} a \u00e9t\u00e9 d\u00e9marr\u00e9 et \u00e9coute \u00e0 pr\u00e9sent les demandes parvenant \u00e0 l''h\u00f4te {1}, sur le port {2}.",
        "CWWKO0219I: A(z) {0} TCP csatorna elindult, \u00e9s most a(z) {1} hoszt {2} portj\u00e1n figyeli a k\u00e9r\u00e9seket.",
        "CWWKO0219I: Il canale TCP {0} \u00e8 stato avviato ed ora \u00e8 in ascolto delle richieste sull''host {1} porta {2}.",
        "CWWKO0219I: TCP \u30c1\u30e3\u30cd\u30eb {0} \u304c\u958b\u59cb\u3055\u308c\u3001\u73fe\u5728\u3001\u30db\u30b9\u30c8 {1}\u3001\u30dd\u30fc\u30c8 {2} \u306e\u8981\u6c42\u3092 listen \u3057\u3066\u3044\u307e\u3059\u3002",
        "CWWKO0219I: {0} TCP \ucc44\ub110\uc774 \uc2dc\uc791\ub418\uc5c8\uc73c\uba70 \ud604\uc7ac \ud638\uc2a4\ud2b8 {1} \ud3ec\ud2b8 {2}\uc5d0\uc11c \uc694\uccad\uc744 \uccad\ucde8 \uc911\uc785\ub2c8\ub2e4.",
        "CWWKO0219I: Kana\u0142 TCP {0} zosta\u0142 uruchomiony i obecnie nas\u0142uchuje \u017c\u0105da\u0144 na ho\u015bcie {1} (port {2}).",
        "CWWKO0219I: O Canal TCP {0} foi iniciado e agora est\u00e1 atendendo solicita\u00e7\u00f5es no host {1} porta {2}.",
        "CWWKO0219I: Canalul TCP {0} a fost pornit \u015fi acum ascult\u0103 cereri pe gazda {1} portul {2}.",
        "CWWKO0219I: \u041a\u0430\u043d\u0430\u043b TCP {0} \u0437\u0430\u043f\u0443\u0449\u0435\u043d \u0438 \u043f\u0440\u0438\u043d\u0438\u043c\u0430\u0435\u0442 \u0437\u0430\u043f\u0440\u043e\u0441\u044b \u043d\u0430 \u0445\u043e\u0441\u0442\u0435 {1}, \u043f\u043e\u0440\u0442 {2}.",
        "CWWKO0219I: \u5df2\u555f\u52d5 TCP \u901a\u9053 {0}\uff0c\u4e14\u73fe\u5728\u6b63\u5728\u4e3b\u6a5f {1} \u57e0 {2} \u4e0a\u63a5\u807d\u8981\u6c42\u3002",
        "CWWKO0219I: TCP \u901a\u9053 {0} \u5df2\u542f\u52a8\u5e76\u4e14\u6b63\u5728\u4fa6\u542c\u4e3b\u673a {1} \u7aef\u53e3 {2} \u4e0a\u7684\u8bf7\u6c42\u3002"
    };

    @Test
    public void testParseHttpsPort() throws PluginExecutionException, IOException {
        for (String message : tcpChannelTranslations) {
            String injectedHttp = message.replace("{0}", "defaultHttpEndpoint").replace("{1}", "localhost  (IPv4: 127.0.0.1)").replace("{2}", "9080");
            String injectedHttps = message.replace("{0}", "defaultHttpEndpoint-ssl").replace("{1}", "localhost  (IPv4: 127.0.0.1)").replace("{2}", "9443");
            DevUtil util = getNewDevUtil(null);
            List<String> messages = new ArrayList<String>();
            messages.add(injectedHttp);
            messages.add(injectedHttps);
            util.parseHttpsPort(messages);
            assertEquals("Incorrect https port parsed from messages: " + messages, "9443", util.getHttpsPort());
        }
    }

}