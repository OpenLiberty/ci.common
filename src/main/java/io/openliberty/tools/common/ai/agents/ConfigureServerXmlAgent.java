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
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ConfigureServerXmlAgent {

    @Agent("Construct a Liberty configuration definition in xml format")
    @SystemMessage("""
        You construct the configuration xml element of the Liberty feature {{configurationName}} 
        for the user provided server.xml content,
        simply use the xml from the following Liberty configuration document without adding any comment
        and include the explanation of the attributes
        by using the following Liberty configuration document:
        {{configurationDoc}}
        """)
    @UserMessage("""
        Here is the content of my server.xml:
        {{fileContent}}
        """)
    public String addConfiguration(@V("configurationName") String configurationName, @V("configurationDoc") String configurationDoc, @V("fileContent") String fileContent);

}
