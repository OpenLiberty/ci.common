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
import io.openliberty.tools.common.ai.tools.CodingTools;

public class ReadFileAgent {

    private CodingTools codingTools = new CodingTools();

    @Agent(description = "Read file content", outputName = "fileContent")
    public String readFile(@V("fileName") String fileName) {
        try {
			String fileContent = codingTools.readFile(fileName);
			return fileContent;
		} catch (Exception e) {
		    System.err.println("ReadFileAgent exceptiom: " + e.getMessage());
			return "";
		}
    }

}
