/**
 * (C) Copyright IBM Corporation 2017.
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
package net.wasdev.wlp.common.arquillian.objects;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import net.wasdev.wlp.common.arquillian.util.Constants;

/**
 * Data object for the arquillian.xml configuration for the WLP remote container.
 * @author ctianus.ibm.com
 *
 */
public class LibertyRemoteObject {
    
    /**
     * These properties should correspond with the parameters in
     * WLPRemoteContainerConfiguration
     * 
     * @author ctianus.ibm.com
     *
     */
    public enum LibertyRemoteProperty implements LibertyProperty.LibertyPropertyI {
        serverName, 
        serverStartTimeout, 
        appDeployTimeout, 
        appUndeployTimeout, 
        username, 
        password, 
        hostName, 
        httpPort, 
        httpsPort, 
        outputToConsole;
    }
    
    private static final String XML_START
            = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<arquillian xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "    xmlns=\"http://jboss.org/schema/arquillian\"\n"
            + "    xsi:schemaLocation=\"http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd\">\n"
            + "    <container qualifier=\"liberty_remote\" default=\"true\">\n" 
            + "        <configuration>\n";
    
    private static final String XML_END
            = "        </configuration>\n" 
            + "    </container>\n"
            + "</arquillian>\n";

    private final Map<LibertyProperty.LibertyPropertyI, String> arquillianProperties;

    public LibertyRemoteObject(Map<LibertyProperty.LibertyPropertyI, String> arquillianProperties) {
        this.arquillianProperties = arquillianProperties;
    }

    public Map<LibertyProperty.LibertyPropertyI, String> getArquillianProperties() {
        return arquillianProperties;
    }

    public void build(File arquillianXml) throws IOException {
        
        // Generate the XML
        StringBuilder xml = new StringBuilder(XML_START);

        for (Entry<LibertyProperty.LibertyPropertyI, String> e : arquillianProperties.entrySet()) {
            LibertyRemoteProperty property = (LibertyRemoteProperty) e.getKey();
            String key = property.name();
            xml.append("            <property name=\"").append(key).append("\">").append(e.getValue()).append("</property>\n");
        }
        
        xml.append(XML_END);
        xml.append(Constants.CONFIGURE_ARQUILLIAN_COMMENT);
        
        // Write to file
        LibertyProperty.write(xml, arquillianXml);
    }

}
