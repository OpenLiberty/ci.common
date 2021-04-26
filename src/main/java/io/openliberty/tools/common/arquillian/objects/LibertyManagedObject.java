/**
 * (C) Copyright IBM Corporation 2017, 2021.
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
package io.openliberty.tools.common.arquillian.objects;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import io.openliberty.tools.common.arquillian.util.Constants;

/**
 * Data object for the arquillian.xml configuration for the WLP managed
 * container.
 * 
 * @author ctianus.ibm.com
 *
 */
public class LibertyManagedObject {

    /**
     * These properties should correspond with the parameters in
     * WLPManagedContainerConfiguration
     * 
     * @author ctianus.ibm.com
     *
     */
    public enum LibertyManagedProperty implements LibertyProperty.LibertyPropertyI {
        usrDir,
        serverStartTimeout,
        serverStopTimeout, 
        appDeployTimeout, 
        appUndeployTimeout, 
        sharedLib, 
        apiTypeVisibility,
        deployType, 
        javaVmArguments, 
        addLocalConnector, 
        securityConfiguration, 
        failSafeUndeployment, 
        outputToConsole, 
        allowConnectingToRunningServer, 
        verifyApps,
        verifyAppDeployTimeout,
        fileDeleteRetries,
        standardFileDeleteRetryInterval;
    }

    private static final String XML_START 
            = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<arquillian xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "	xmlns=\"http://jboss.org/schema/arquillian\"\n"
            + "	xsi:schemaLocation=\"http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd\">\n"
            + "	<container qualifier=\"liberty_managed\" default=\"true\">\n" 
            + "		<configuration>\n";

    private static final String XML_END
            = "		</configuration>\n" 
            + "	</container>\n" 
            + "</arquillian>\n";

    private final String wlpHome;
    private final String serverName;
    private final int httpPort;
    private final Map<LibertyProperty.LibertyPropertyI, String> arquillianProperties;

    private String userDirectory;

    public LibertyManagedObject(String wlpHome, String serverName, int httpPort,
            Map<LibertyProperty.LibertyPropertyI, String> arquillianProperties) {
        this.wlpHome = wlpHome;
        this.serverName = serverName;
        this.httpPort = httpPort;
        this.arquillianProperties = arquillianProperties;
    }

    public LibertyManagedObject(String wlpHome, String serverName, String userDirectory, int httpPort,
            Map<LibertyProperty.LibertyPropertyI, String> arquillianProperties) {
        this(wlpHome, serverName, httpPort, arquillianProperties);
        this.userDirectory = userDirectory;
    }

    public String getWlpHome() {
        return wlpHome;
    }

    public String getServerName() {
        return serverName;
    }

    public String getUserDirectory() {
        return userDirectory;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public Map<LibertyProperty.LibertyPropertyI, String> getArquillianProperties() {
        return arquillianProperties;
    }

    public void build(File arquillianXml) throws IOException {
        // Generate the XML
        StringBuilder xml = new StringBuilder(XML_START);
        xml.append("			<property name=\"wlpHome\">").append(getWlpHome()).append("</property>\n");
        xml.append("			<property name=\"serverName\">").append(getServerName()).append("</property>\n");
        xml.append("			<property name=\"httpPort\">").append(getHttpPort()).append("</property>\n");

        boolean containsUsrDirProperty = false;

        for (Entry<LibertyProperty.LibertyPropertyI, String> e : arquillianProperties.entrySet()) {
            LibertyManagedProperty property = (LibertyManagedProperty) e.getKey();
            String key = property.name();
            if (key.equals("usrDir")) {
                containsUsrDirProperty = true;
            }
            xml.append("			<property name=\"").append(key).append("\">").append(e.getValue())
                    .append("</property>\n");
        }

        // if the arquillianProperties contains a usrDir property, do not override with the passed in userDirectory 
        if ((getUserDirectory() != null) && !containsUsrDirProperty) {
            xml.append("			<property name=\"usrDir\">").append(getUserDirectory()).append("</property>\n");
        }

        xml.append(XML_END);
        xml.append(Constants.CONFIGURE_ARQUILLIAN_COMMENT);

        // Write to file
        LibertyProperty.write(xml, arquillianXml);
    }

}
