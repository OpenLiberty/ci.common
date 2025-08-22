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
package io.openliberty.tools.common.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class StackOverFlowTools {

    private static String stackOverflowSite = "https://api.stackexchange.com/2.3/search/advanced?site=stackoverflow";
    private static String size = "&pagesize=3";
    private static String filter = "&filter=N9UNq*RI0tsSN35uJKC0nk_HM";
    
    private static String stackOverflowMethod = stackOverflowSite + filter + size + "&order=desc&sort=relevance&answers=1";

    private static String findAnswer = "https://api.stackexchange.com"
                                       + "/2.3/questions/%s/answers?order=desc&sort=votes&site=stackoverflow&"
                                       + "filter=CKAkJFla(8TLNtkfr1ytJZj94MlNVo6Ee";

    private String encodeUrl(String url) throws UnsupportedEncodingException {
        return url.replace(" ", "%20");
    }

    private Map<String, Object> stringToMap(String jsonString) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
    }

    private List<Map<String, Object>> clientSearch(String question) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                                  .uri(URI.create(question))
                                  .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> arrayData = (List<Map<String, Object>>) stringToMap(response.body()).get("items");
        return arrayData;
    }

    private ArrayList<String> questionAndAnswer(String url) throws Exception {
        ArrayList <String> questionAnswer = new ArrayList<>();
        for (Map<String, Object> data : clientSearch(url)) {
            String topAnswer = clientSearch(String.format(findAnswer, data.get("question_id")))
                                    .get(0).get("body").toString();
            questionAnswer.add(
                "Link: " + "https://stackoverflow.com/questions/" + data.get("question_id") + 
                " Problem: " + data.get("body") +
                " Answer: " + topAnswer
            );
        }
        return questionAnswer;
    }

    @Tool("Search a question online")
    public ArrayList <String> search(@P("Question you are searching") String question) throws Exception {
        String targetUrl = stackOverflowMethod + "&q=" + question;
        try {
            return questionAndAnswer(encodeUrl(targetUrl));
        } catch (ConnectException e) {
            throw new Exception("No internet connection");
        }
    }

}
