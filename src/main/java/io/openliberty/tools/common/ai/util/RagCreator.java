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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class RagCreator {

    private static final String[] MD_FILES = {
        "logs-1.md", "mp-health-1.md", "security-1.md"
    };

    private static final String PROMPT_TEMPLATE = """
        {{userMessage}}

        Use relevent information from the stored documents:
        {{contents}}.""";

    private static Double MIN_SCORE = null;
    private static Integer MAX_RESULTS = null;

    private Double getMinScore() {
        if (MIN_SCORE == null) {
            MIN_SCORE = Double.parseDouble(System.getProperty("rag.retriever.min.score", "0.85"));
        }
        return MIN_SCORE;
    }

    private Integer getMaxResults() {
        if (MAX_RESULTS == null) {
            MAX_RESULTS = Integer.parseInt(System.getProperty("rag.retriever.max.results", "10"));
        }
        return MAX_RESULTS;
    }

    private void addTextToStore(String text, EmbeddingModel model, EmbeddingStore<TextSegment> store) {
        TextSegment segment = TextSegment.from(text);
        Embedding embedding = model.embed(segment).content();
        store.add(embedding, segment);
    }

    public RetrievalAugmentor getRetrievalAugmentor() throws Exception {
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        AllMiniLmL6V2QuantizedEmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        ClassLoader classLoader = RetrievalAugmentor.class.getClassLoader();
        for (String mdFile: MD_FILES) {
            InputStream inStream = classLoader.getResourceAsStream("documents/" + mdFile);
            if (inStream != null) {
                InputStreamReader reader = new InputStreamReader(inStream,StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(reader);
                StringBuffer docSB = new StringBuffer();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith(":summary:") || line.startsWith(":question:") || line.startsWith(":problem:")) {
                        if (!docSB.isEmpty()) {
                            addTextToStore(docSB.toString(), embeddingModel, embeddingStore);
                            docSB = new StringBuffer();
                        }
                    }
                    docSB.append(line).append("\n");
                }
                if (!docSB.isEmpty()) {
                    addTextToStore(docSB.toString(), embeddingModel, embeddingStore);
                }
                br.close();
                reader.close();
                inStream.close();
            }
        }

        ContentRetriever contentRetriever =
            EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(getMaxResults())
                .minScore(getMinScore())
                .build();

        ContentInjector contentInjector =
            DefaultContentInjector.builder()
                .promptTemplate(PromptTemplate.from(PROMPT_TEMPLATE))
                .metadataKeysToInclude(List.of("file_name"))
                .build();

        return DefaultRetrievalAugmentor.builder()
                   .contentRetriever(contentRetriever)
                   .contentInjector(contentInjector)
                   .build();

    }

}