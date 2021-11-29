/**
 * (C) Copyright IBM Corporation 2021.
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
package io.openliberty.tools.common.plugins.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.w3c.dom.Node;

import io.openliberty.tools.common.plugins.config.ServerConfigXmlDocument;

public class ServerConfigFileTest {

    public static final String RESOURCES_DIR = "src/test/resources";

    private static final String RESOURCES_SERVER_DIR = RESOURCES_DIR + "/servers";

    private File serversDir = new File(RESOURCES_SERVER_DIR);

    @Test
    public void testNewInstance() throws Exception {
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance();
        assertTrue(doc.findServerElement().getNodeName().equals("server"));
        assertTrue(doc.findServerElement().getChildNodes().getLength() == 0);
        assertTrue(doc.findFeatureManager() == null);
    }

    @Test
    public void testCreateVars() throws Exception {
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance();
        assertTrue(doc.findServerElement().getChildNodes().getLength() == 0);
        doc.createVariableWithValue("name1", "value1", true);
        doc.createVariableWithValue("name2", "value2", false);
        assertTrue(doc.findServerElement().getChildNodes().getLength() == 2);
        assertTrue(doc.findServerElement().getChildNodes().item(0).getNodeType() == Node.ELEMENT_NODE);
        assertTrue(doc.findServerElement().getChildNodes().item(1).getNodeType() == Node.ELEMENT_NODE);
    }

    @Test
    public void testCreateFeatures() throws Exception {
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance();
        doc.createFeature("feature1");
        assertTrue(doc.findServerElement().getChildNodes().getLength() == 1);
        Node featureMananger = doc.findServerElement().getFirstChild();
        assertTrue(featureMananger.getChildNodes().getLength() == 1);
        doc.createFeature("feature2");
        assertTrue(featureMananger.getChildNodes().getLength() == 2);
    }

    @Test
    public void testCreateComment() throws Exception {
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance();
        doc.createComment("Test comment");
        assertTrue(doc.findServerElement().getChildNodes().getLength() == 1);
        assertTrue(doc.findServerElement().getChildNodes().item(0).getNodeType() == Node.COMMENT_NODE);
    }

    @Test
    public void testLMPvars() throws Exception {
        // Test creating the type of variable file used by LMP.
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance();
        doc.createComment("Test comment");
        doc.createVariableWithValue("name1", "value1", true);
        doc.createVariableWithValue("name2", "value2", false);
        assertTrue(doc.findServerElement().getChildNodes().getLength() == 3);
    }

    @Test
    public void testNewFileInstance() throws Exception {
        File serverXml = new File(serversDir, "server.xml");
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance(serverXml);
        assertTrue(doc.findServerElement().getNodeName().equals("server"));
        assertTrue(doc.findServerElement().getChildNodes().getLength() > 0);
    }

    @Test
    public void testAddFMComment() throws Exception {
        File serverXml = new File(serversDir, "server.xml");
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance(serverXml);
        assertTrue(doc.findFeatureManager() != null);
        assertTrue(doc.createFMComment("test comment"));
        assertTrue(doc.hasFMComment("test comment"));
        Node comment = doc.findFMComment("test comment");
        assertTrue(comment.getNodeType() == Node.COMMENT_NODE);
    }

    @Test
    public void testInvalidAddFMComment() throws Exception {
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance();
        assertTrue(doc.findFeatureManager() == null);
        assertFalse(doc.createFMComment("test comment"));
        assertTrue(doc.findServerElement().getChildNodes().getLength() == 0);
    }

    @Test
    public void testRemoveFMComment() throws Exception {
        File serverXml = new File(serversDir, "server.xml");
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance(serverXml);
        assertTrue(doc.findFeatureManager() != null);
        assertTrue(doc.createFMComment("test comment"));
        assertTrue(doc.hasFMComment("test comment"));
        doc.removeFMComment("test comment");
        assertFalse(doc.hasFMComment("test comment"));
    }

    @Test
    public void testSpacing() throws Exception {
        File serverXml = new File(serversDir, "server.xml");
        ServerConfigXmlDocument doc = ServerConfigXmlDocument.newInstance(serverXml);
        Node featureManager = doc.findFeatureManager();
        int indent = -1;
        Node text = featureManager.getFirstChild(); // expecting children text("\n        "), element(feature), text("\n    ")
        if (text.getNodeType() == Node.TEXT_NODE) {
            indent = text.getTextContent().length();
        }
        assertTrue(indent == 9);
        assertTrue(doc.createFMComment("test comment"));
        Node comment = doc.findFMComment("test comment");
        Node spacing = comment.getPreviousSibling();
        assertTrue(spacing.getNodeType() == Node.TEXT_NODE);
        assertTrue(spacing.getTextContent().length() == indent); // comment indented to same level as features
        Node closingSpace = comment.getNextSibling();
        assertTrue(closingSpace.getNodeType() == Node.TEXT_NODE);
        assertTrue(closingSpace.getTextContent().length() < indent); // </featureManager> indented less than features
    }
}
