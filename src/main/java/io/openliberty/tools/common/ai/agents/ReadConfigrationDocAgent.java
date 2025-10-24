/**
 * (C) Copyright IBM Corporation 2025
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
package io.openliberty.tools.common.ai.agents;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

public class ReadConfigrationDocAgent {

    @Agent(description = "Read Liberty configuration document", outputName = "configurationDoc")
    public String readConfigrationDoc(@V("configurationName") String configurationName) {
        try {
            if (configurationName.contains("-")) {
                configurationName = configurationName.substring(0, configurationName.indexOf("-"));
            }
            URI configurationUri = new URI("https://raw.githubusercontent.com/OpenLiberty/docs-generated/refs/heads/vNext/modules/reference/pages/config/" + configurationName + ".adoc");
            URL url = configurationUri.toURL();
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
		} catch (Exception e) {
            System.err.println("ReadConfigrationDocAgent exception: " + e.getMessage());
			return "";
		}
    }

}
