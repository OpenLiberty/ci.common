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

import static java.time.Duration.ofSeconds;

import java.util.List;
import java.util.Scanner;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.response.Model;

public class ModelBuilder {

    public static final String OLLAMA = "Ollama";

    private static String OLLAMA_BASE_URL = null;

    private static String model;
    private static String provider;
    private static ChatModel chatModel = null;

    private Integer TIMEOUT;
    private Integer MAX_NEW_TOKEN;
    private Integer MAX_MESSAGES;
    private Double TEMPERATURE;

    private static Scanner scan = new Scanner(System.in);

    private static void modelSelection() {
        System.out.print("\nPress enter to use the default model " + Utils.bold(model) + " or type in a model name: ");
        String modelName = scan.nextLine().trim();
        if (!modelName.isBlank()) {
            model = modelName;
        }
    }

    public static ChatModel chatModel() {
        return chatModel;
    }

    public static void cleanInputProvider() {
        OLLAMA_BASE_URL = null;
        model = null;
        provider = null;
    }

    public static boolean selectInputProvider() throws Exception {

        provider = OLLAMA;
        OLLAMA_BASE_URL = System.getProperty("ollama.base.url");
        
        if (OLLAMA_BASE_URL == null || OLLAMA_BASE_URL.isBlank()) {
            OLLAMA_BASE_URL = "http://localhost:11434";
        }
        
        model = System.getProperty("chat.model.id");
        
        if (model == null || model.isBlank()) {
            Ollama ollamaAPI = new Ollama(OLLAMA_BASE_URL);
            List<Model> models;
            try {
                models = ollamaAPI.listModels();
                for (Model installedModel : models) {
                    String modelName = installedModel.getModelName();
                    if (modelName.equals("gpt-oss")) {
                        model = modelName;
                        break;
                    }
                }
                if (model == null || model.isBlank()) {
                    return false;
                }
            } catch (Exception exception) {
                throw exception;
            }
        }
        return true;
    }

    public static void findModel() {
        if (OLLAMA_BASE_URL != null && OLLAMA_BASE_URL.toLowerCase().startsWith("http")) {
            provider = OLLAMA;
            if (model == null) {
                model = System.getProperty("chat.model.id");
                if (model == null || model.isBlank()) {
                    Ollama ollamaAPI = new Ollama(OLLAMA_BASE_URL);
                    List<Model> models;
                    try {
                        models = ollamaAPI.listModels();
                        for (Model m : models) {
                            String modelName = m.getModelName();
                            if (modelName.equals("gpt-oss") ||
                                modelName.equals("devstral")) {
                                model = modelName;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Exception occured: " + e.getMessage());
                    } finally {
                        if (model == null) {
                            model = "llama3.2";
                        }
                    }
                }
            }
        }
    }

    public ChatModel getChatModel() {
        if (chatModel == null) {
            if (provider.equals(OLLAMA)) {
                chatModel = OllamaChatModel.builder()
                            .baseUrl(OLLAMA_BASE_URL)
                            .modelName(model)
                            .timeout(ofSeconds(getTimeOut()))
                            .temperature(getTemperature())
                            .numPredict(getMaxNewToken())
                            .build();
            }
        }
        return chatModel;
    }

    public String getModelName() {
        return model == null ? "No model is being used" : model;
    }

    public String getProvider() {
        return provider == null ? "No provider is being used" : provider;
    }

    public Integer getTimeOut() {
        if (TIMEOUT == null) {
            TIMEOUT = Integer.parseInt(System.getProperty("chat.model.timeout", "120"));
        }
        return TIMEOUT;
    }

    public Integer getMaxNewToken() {
        if (MAX_NEW_TOKEN == null) {
            MAX_NEW_TOKEN = Integer.parseInt(System.getProperty("chat.model.max.token", "2000"));
        }
        return MAX_NEW_TOKEN;
    }

    public Double getTemperature() {
        if (TEMPERATURE == null) {
            TEMPERATURE = Double.parseDouble(System.getProperty("chat.model.temperature", "1.0"));
        }
        return TEMPERATURE;
    }

    public Integer getMaxMessages() {
        if (MAX_MESSAGES == null) {
            MAX_MESSAGES = Integer.parseInt(System.getProperty("chat.memory.max.messages", "20"));
        }
        return MAX_MESSAGES;
    }

}
