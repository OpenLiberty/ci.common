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
package io.openliberty.tools.common.ai.util;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;

public interface Assistant extends ChatMemoryAccess {
    @SystemMessage("You are a Java, Jakarta EE and MicroProfile coding helper for Open Liberty and WebSphere Liberty, " +
        "people will go to you for questions around coding. " +
        "Do NOT add more parameters than needed" +
        "NEVER give the user unnecessary information. " +
        "Whenever you have a source, show it to the user. " +
        "NEVER lie or make information up, if you are unsure say so.")
    Result<String> chat(@MemoryId int memoryId, @UserMessage String userMessage);
}
