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

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import io.openliberty.tools.common.ai.util.LibertyServerConfigurationUtil;

public class ReadConfigrationTemplateAgent {

    @Agent(description = "Read Liberty configuration document", outputName = "configurationDoc")
    public String readConfigrationDoc(@V("configurationName") String configurationName) {
        if (configurationName.contains("-")) {
            configurationName = configurationName.substring(0, configurationName.indexOf("-"));
        }
        StringBuffer sb = new StringBuffer();
        sb.append(LibertyServerConfigurationUtil.getConfigurationTemplate(configurationName))
          .append(LibertyServerConfigurationUtil.getConfigurationAttributes(configurationName));
        return sb.toString();
    }

}
