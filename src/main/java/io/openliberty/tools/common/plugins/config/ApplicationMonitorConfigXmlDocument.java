/**
 * (C) Copyright IBM Corporation 2023.
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
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import io.openliberty.tools.common.plugins.util.PluginExecutionException;

public class ApplicationMonitorConfigXmlDocument extends XmlDocument {
    public static final String APPLICATION_CONFIG_XML_FILENAME = "liberty-plugin-app-monitor-config.xml";
    private static List<String> APP_MON_VALUE_LIST = Arrays.asList("polled", "mbean", "disabled");

    String tool = null;

    HashMap<String, String> attributes = new HashMap<>();

    public ApplicationMonitorConfigXmlDocument(String tool) {
        this.tool = tool;
    }

    public void writeAppMonitorConfigXmlDocument(File serverDirectory, String appMonitorTrigger) throws IOException, TransformerException, ParserConfigurationException, PluginExecutionException {
        File appMonXml = getAppMonitorConfigXmlFile(serverDirectory);
        // if applicationMonitor not set, return
        if (appMonitorTrigger == null) {
            if (appMonXml.exists()) {
                appMonXml.delete();
            }
            return;
        }
        // continue with creating configDropins/override file
        if (!APP_MON_VALUE_LIST.contains(appMonitorTrigger)) {
            throw new PluginExecutionException("The appMonitorTrigger value \"" + appMonitorTrigger + "\" is not supported. Please use one of: " + APP_MON_VALUE_LIST);
        }
        attributes.put("updateTrigger", appMonitorTrigger);

        if (!appMonXml.getParentFile().exists()) {
            appMonXml.getParentFile().mkdirs();
        }

        ServerConfigXmlDocument configDocument = ServerConfigXmlDocument.newInstance();
        configDocument.createGeneratedByComment(tool);
        configDocument.createServerElementWithAttributes("applicationMonitor", attributes);
        configDocument.writeXMLDocument(appMonXml);
    }
    
    public File getAppMonitorConfigXmlFile(File serverDirectory) {
        File f = new File(serverDirectory, "configDropins/overrides/" + APPLICATION_CONFIG_XML_FILENAME); 
        return f;
    }
}
