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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import io.openliberty.tools.common.ai.agents.ConfigureServerXmlAgent;
import io.openliberty.tools.common.ai.agents.ReadConfigrationTemplateAgent;
import io.openliberty.tools.common.ai.agents.ReadFileAgent;
import io.openliberty.tools.common.ai.util.ModelBuilder;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

public class OpenLibertyTools {

    private static final String OL_IO_FEED_XML = "https://openliberty.io/feed.xml";
    private static Jsonb JSONB = JsonbBuilder.create();

    @Tool("Get the OpenLiberty blogs")
    public ArrayList<String> getOpenLibertyBlogs() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(OL_IO_FEED_XML);

        ArrayList<String> blogData = new ArrayList<>();
        NodeList entries = document.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            NodeList entryData = entries.item(i).getChildNodes();
            StringBuilder blogEntry = new StringBuilder();
            for (int j = 0; j < entryData.getLength(); j++) {
                Node entryNode = entryData.item(j);
                if (entryNode.getNodeName() == "title") {
                    blogEntry.append("Title: " + entryNode.getFirstChild().getNodeValue() + "\n");
                } else if (entryNode.getNodeName() == "summary") {
                    blogEntry.append("Summary: " + entryNode.getFirstChild().getNodeValue() + "\n");
                } else if (entryNode.getNodeName() == "link") {
                    String url = entryNode.getAttributes()
                                     .getNamedItem("href").getNodeValue();
                    blogEntry.append("URL: " + url + "\n");
                } else if (entryNode.getNodeName() == "updated") {
                    blogEntry.append("Date: " + entryNode.getFirstChild().getNodeValue() + "\n");
                } else if (entryNode.getNodeName() == "author") {
                    blogEntry.append("Author: " + entryNode.getFirstChild().getFirstChild().getNodeValue() + "\n");
                }
            }
            blogData.add(blogEntry.toString());
        }
        return blogData;
    }

    private String readGuideReadme(String guide, String topic) throws Exception {
        String guideName = guide.replace("guide-", "");
        String guideUrl = "https://openliberty.io/guides/" + guideName + ".html";
        String guideTitle = "";
        String guideDescription = "";
        URL guideURL = new URI("https://raw.githubusercontent.com/OpenLiberty/"
                           + guide + "/refs/heads/prod/README.adoc").toURL();
        InputStream is = guideURL.openStream();
        InputStreamReader reader = new InputStreamReader(is,StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(reader);
        boolean matched = false;
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith(":page-tags:")) {
                if (line.indexOf(topic.toLowerCase()) >= 0) {
                    matched = true;
                }
            } else if (line.startsWith("= ")) {
                guideTitle = line.substring(2).trim();
            } else if (line.startsWith("NOTE: ")) {
                br.readLine();
                guideDescription = br.readLine();
                break;
            }
        }
        br.close();
        reader.close();
        is.close();
        if (matched) {
            StringBuilder guideSB = new StringBuilder();
            guideSB.append("Title: " + guideTitle + "\n");
            guideSB.append("Description: " + guideDescription + "\n");
            guideSB.append("URL: " + guideUrl + "\n");
            return guideSB.toString();
        }
        return null;
    }

    @Tool("Get OpenLiberty guides or tutorials")
    public List<String> getOpenLibertyGuidesTutorials(
        @P("Topic (example: MicroProfile, Jakarta-EE, Kubernetes, Cloud, Security, Maven, or Docker)") String topic) throws Exception {
        if (topic == null || topic.isBlank()) {
            throw new Exception("No topic is specified");
        }
        List<String> guides = new ArrayList<String>();
        for (int p = 1; p < 3; p++) {
            URL olURL = new URI("https://api.github.com/orgs/OpenLiberty/repos?type=public&per_page=100&page="+p).toURL();
            InputStream is = olURL.openStream();
            InputStreamReader reader = new InputStreamReader(is,StandardCharsets.UTF_8);
            JsonArray olRepositories = JSONB.fromJson(reader, JsonArray.class);
            reader.close();
            is.close();
            for ( JsonValue repository : olRepositories) {
                 String name = repository.asJsonObject().getString("name");
                 if (name.startsWith("guide-")) {
                     String guideData= readGuideReadme(name, topic);
                     if (guideData != null) {
                         guides.add(guideData);
                     }
                 }
            }
        }
        return guides;
    }

    @Tool("Add Liberty configuration")
    public String addLibertyConfiguration(
        @P("Configuration name") String configuration) {

        if (configuration == null || configuration.isBlank()) {
            return "No configuration name is specified";
        } else {
            configuration = configuration.contains("-") ?
                configuration.substring(0, configuration.indexOf("-")) : configuration;
        }

        ConfigureServerXmlAgent configureServerXmlAgent = AgenticServices
            .agentBuilder(ConfigureServerXmlAgent.class)
            .chatModel(ModelBuilder.chatModel())
            .outputName("configuredServerXml")
            .build();
        boolean isGood[] = new boolean[]{ false };
        UntypedAgent addLibertyConfiguration = AgenticServices
            .sequenceBuilder()
            .subAgents(
                new ReadFileAgent(),
                new ReadConfigrationTemplateAgent(),
                configureServerXmlAgent,
                // this code is for debug
                AgenticServices.agentAction(agenticScope -> {
                    String configurationName = (String) agenticScope.readState("configurationName");
                    String configuredServerXml = (String) agenticScope.readState("configuredServerXml");
                    isGood[0] = configuredServerXml.contains("<" + configurationName + "\n");
                })
            )
            .outputName("configuredServerXml")
            .build();
        Map<String, Object> arguments = Map.of(
            "fileName", "server.xml",
            "configurationName", configuration
        );
        String result = (String) addLibertyConfiguration.invoke(arguments);
        return result;
    }
}
