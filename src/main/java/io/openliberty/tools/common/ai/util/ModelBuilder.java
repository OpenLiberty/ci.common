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

import static dev.langchain4j.model.github.GitHubModelsEmbeddingModelName.TEXT_EMBEDDING_3_SMALL;
import static dev.langchain4j.model.mistralai.MistralAiEmbeddingModelName.MISTRAL_EMBED;
import static java.time.Duration.ofSeconds;

import java.util.List;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.github.GitHubModelsChatModel;
import dev.langchain4j.model.github.GitHubModelsChatModel.Builder;
import dev.langchain4j.model.github.GitHubModelsEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.Model;

public class ModelBuilder {

    public static final String OLLAMA = "Ollama";
    public static final String GITHUB = "Github";
    public static final String MISTRAL_AI = "Mistral AI";
    public static final String GEMINI = "Google Gemini";

    private static String OLLAMA_BASE_URL = System.getenv("OLLAMA_BASE_URL");
    private static String GITHUB_API_KEY = System.getenv("GITHUB_API_KEY");
    private static String MISTRAL_AI_API_KEY = System.getenv("MISTRAL_AI_API_KEY");
    private static String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");

    private String model;
    private String provider;

    private String GITHUB_ENDPOINT;

    private Integer TIMEOUT;
    private Integer MAX_NEW_TOKEN;
    private Integer MAX_MESSAGES;
    private Double TEMPERATURE;

    private ChatModel chatModel = null;
    private EmbeddingModel embeddingModel = null;

    public void findModel() {
        if (OLLAMA_BASE_URL != null && OLLAMA_BASE_URL.startsWith("http")) {
            provider = OLLAMA;
            if (model == null) {
                model = System.getProperty("chat.model.id");
                if (model == null || model.isBlank()) {
                    OllamaAPI ollamaAPI = new OllamaAPI(OLLAMA_BASE_URL);
                    List<Model> models;
                    try {
                        models = ollamaAPI.listModels();
                        for (Model m : models) {
                            String modelName = m.getModelName();
                            if (modelName.equals("codestral") ||
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
        } else if (GITHUB_API_KEY != null &&
            (GITHUB_API_KEY.startsWith("ghp_") || GITHUB_API_KEY.startsWith("github_pat_"))) {
            provider = GITHUB;
            if (model == null) {
                model = System.getProperty("chat.model.id");
                if (model == null || model.isBlank()) {
                    model = "Codestral-2501";
                } else {
                    String endpoint = System.getProperty("github.chat.endpoint");
                    if (endpoint != null && !endpoint.isEmpty() && !endpoint.isBlank()) {
                        GITHUB_ENDPOINT = endpoint;
                    }
                }
            }
        } else if (MISTRAL_AI_API_KEY != null && MISTRAL_AI_API_KEY.length() > 30) {
            provider = MISTRAL_AI;
            if (model == null) {
                model = System.getProperty("chat.model.id");
                if (model == null || model.isBlank()) {
                    model = "codestral-latest";
                }
            }
        } else if (GEMINI_API_KEY != null && GEMINI_API_KEY.length() > 30) {
            provider = GEMINI;
            if (model == null) {
                model = System.getProperty("chat.model.id");
                if (model == null || model.isBlank()) {
                    model = "gemini-2.5-flash";
                }
            }
        } else {
            /*
            System.err.println("If use AI assistant, " +
                "make sure to set either the OLLAMA_BASE_URL, GITHUB_API_KEY, or MISTRAL_AI_API_KEY " +
                "environment variable with a valid URL or API key?\n");
            */
        }
    }

    public ChatModel getChatModel() {
        if (chatModel == null) {
            findModel();
            if (provider.equals(GITHUB)) {
                Builder g = GitHubModelsChatModel.builder()
                            .gitHubToken(GITHUB_API_KEY)
                            .modelName(model)
                            .timeout(ofSeconds(getTimeOut()))
                            .temperature(getTemperature())
                            .maxTokens(getMaxNewToken());
                if (GITHUB_ENDPOINT != null) {
                    g.endpoint(GITHUB_ENDPOINT);
                }
                chatModel = g.build();
            } else if (provider.equals(OLLAMA)) {
                chatModel = OllamaChatModel.builder()
                            .baseUrl(OLLAMA_BASE_URL)
                            .modelName(model)
                            .timeout(ofSeconds(getTimeOut()))
                            .temperature(getTemperature())
                            .numPredict(getMaxNewToken())
                            .build();
            } else if (provider.equals(MISTRAL_AI)) {
                chatModel = MistralAiChatModel.builder()
                            .apiKey(MISTRAL_AI_API_KEY)
                            .modelName(model)
                            .timeout(ofSeconds(getTimeOut()))
                            .temperature(getTemperature())
                            .maxTokens(getMaxNewToken())
                            .build();
            } else if (provider.equals(GEMINI)) {
                chatModel = GoogleAiGeminiChatModel.builder()
                            .apiKey(GEMINI_API_KEY)
                            .modelName(model)
                            .timeout(ofSeconds(getTimeOut()))
                            .temperature(getTemperature())
                            .maxOutputTokens(getMaxNewToken())
                            .build();
            }
        }
        return chatModel;
    }


    public EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            if (provider.equals(GITHUB)) {
                embeddingModel = GitHubModelsEmbeddingModel.builder()
                    .gitHubToken(GITHUB_API_KEY)
                    .modelName(TEXT_EMBEDDING_3_SMALL)
                    .timeout(ofSeconds(getTimeOut()))
                    .build();
            } else if (provider.equals(OLLAMA)) {
                embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(OLLAMA_BASE_URL)
                    .modelName("all-minilm")
                    .timeout(ofSeconds(getTimeOut()))
                    .build();
            } else if (provider.equals(MISTRAL_AI)) {
                embeddingModel = MistralAiEmbeddingModel.builder()
                    .apiKey(MISTRAL_AI_API_KEY)
                    .modelName(MISTRAL_EMBED)
                    .timeout(ofSeconds(getTimeOut()))
                    .build();
            } else if (provider.equals(GEMINI)) {
                embeddingModel = GoogleAiEmbeddingModel.builder()
                    .apiKey(GEMINI_API_KEY)
                    .modelName("embedding-001")
                    .timeout(ofSeconds(getTimeOut()))
                    .build();
            }
        }
        return embeddingModel;
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
