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
package io.openliberty.tools.common.ai;

import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import io.openliberty.tools.common.ai.util.Assistant;
import io.openliberty.tools.common.ai.util.MarkdownConsoleFormatter;
import io.openliberty.tools.common.ai.util.ModelBuilder;

public class ChatAgent {

	private ModelBuilder modelBuilder = new ModelBuilder();
    private MarkdownConsoleFormatter mdFormatter = new MarkdownConsoleFormatter();
    private Assistant assistant = null;
    private int memoryId;
    private boolean toolsEnabled = false;

    public ChatAgent(int memoryId) throws Exception {
    	this.memoryId = memoryId;
    	getAssistant();
    }

    public void clearAssistant() {
        resetChat();
        this.assistant = null;
    }

    public Assistant getAssistant() throws Exception {
        if (assistant == null) {
            //add tools, as needed, to the builder below
            AiServices<Assistant> builder =
                AiServices.builder(Assistant.class)
                    .chatModel(modelBuilder.getChatModel())
                    .chatMemoryProvider(
                         sessionId -> MessageWindowChatMemory.withMaxMessages(modelBuilder.getMaxMessages()));
            assistant = builder.build();

            try {
                assistant.chat(memoryId, "test");
                toolsEnabled = true;
            } catch (InvalidRequestException e) {
                toolsEnabled = false;
                if (e.getMessage().contains("does not support tools")) {
                    builder = AiServices.builder(Assistant.class)
                        .chatModel(modelBuilder.getChatModel())
                        .chatMemoryProvider(
                            sessionId -> MessageWindowChatMemory.withMaxMessages(modelBuilder.getMaxMessages()));
                    assistant = builder.build();
                } else {
                    throw new Exception(e.getMessage());
                }
            } catch (RuntimeException runtimeException) {
                throw new Exception(runtimeException);              
            } catch (Exception e) {
                throw new Exception(e.getMessage());
            } finally {
                resetChat();
            }
        }
        return assistant;
    }

    public String chat(String message) throws Exception {
        if (message.equalsIgnoreCase("reset")) {
            resetChat();
            return "The current chat session is reset.\n";
        } else {
            String response = getAssistant().chat(memoryId, message).content();
            if (response == null || response.isBlank()) {
                return "AI reponsonded with nothing. Try your message again or a new message.\n";
            }
            return mdFormatter.rerender(response.trim());
        }
    }

    public void resetChat() {
        assistant.evictChatMemory(memoryId);
    }

    public String getModelName() {
        return modelBuilder.getModelName();
    }

    public String getProvider() {
        return modelBuilder.getProvider();
    }
    
    public String getToolsEnabled() {
        return toolsEnabled ? "enabled" : "unavailable";
    }

    public Integer getTimeOut() {
        return modelBuilder.getTimeOut();
    }

    public Integer getMaxNewToken() {
        return modelBuilder.getMaxNewToken();
    }

    public Double getTemperature() {
        return modelBuilder.getTemperature();
    }

}
