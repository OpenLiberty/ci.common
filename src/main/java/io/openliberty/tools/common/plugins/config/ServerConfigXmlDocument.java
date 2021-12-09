/**
 * (C) Copyright IBM Corporation 2019, 2021.
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
package io.openliberty.tools.common.plugins.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import org.w3c.dom.Element;
import org.w3c.dom.Comment;

public class ServerConfigXmlDocument extends XmlDocument {

    private Element featureManager = null;

    private ServerConfigXmlDocument() {
    }

    /**
     * Create a new Document Object Model for a server configuration file with a single element: server.
     * @return A reference to the object representing a new DOM
     * @throws ParserConfigurationException
     */
    public static ServerConfigXmlDocument newInstance() throws ParserConfigurationException {
        ServerConfigXmlDocument configDocument = new ServerConfigXmlDocument();
        configDocument.createDocument("server");
        return configDocument;
    }

    /**
     * Create a Document Object Model for a server configuration file and populate it from an existing XML file.
     * The file should be a valid server configuration file with one top-most element called "server."
     * @param f  An XML file to read in and store in the created DOM.
     * @return   A reference to the object representing a new DOM
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public static ServerConfigXmlDocument newInstance(File f) throws ParserConfigurationException, SAXException, IOException {
        if (f == null || !f.exists()) {
            return null;
        }
        ServerConfigXmlDocument configDocument = new ServerConfigXmlDocument();
        configDocument.createDocument(f);
        configDocument.featureManager = configDocument.findFeatureManager();
        return configDocument;
    }

    public void createComment(String comment) {
        createComment(doc.getDocumentElement(), comment);
    }

    // Return true if the document was changed and false otherwise.
    public boolean createFMComment(String comment) {
        if (featureManager == null) {
            return false;
        }
        if (!hasFMComment(comment)) {
            // First try to add some blank text to maintain the indentation level.
            createPaddingText(featureManager);
            createComment(featureManager, comment);
            return true;
        }
        return false;
    }

    public void removeFMComment(String comment) {
        Node commentNode = findFMComment(comment);
        if (commentNode != null) {
            Node parent = commentNode.getParentNode();
            Node sibling = commentNode.getPreviousSibling();
            if (isWhitespace(sibling)) {
                parent.removeChild(sibling);
            }
            parent.removeChild(commentNode);
        }
    }

    private void createPaddingText(Element elem) {
        // Assuming the model expresses the document
        // <element>
        // <text "    "><childE1> etc
        // First child of <element> is four (or eight or more) blanks. Second child is the actual content.
        // This can happen when you parse an existing document into the model rather than create it from scratch.
        Node first = elem.getFirstChild();

        Node text = null;
        if (first != null && first instanceof Text) {
            text = first.cloneNode(true); // try to copy blanks to maintain indentation level
        }
        if (text != null) {
            insertBeforeBlanks(elem, text); // add blanks between comment and next node: </featureManager>
        }
    }

    public void createComment(Element elem, String comment) {
        Comment commentElement = doc.createComment(comment);
        insertBeforeBlanks(elem, commentElement);
    }

    private void insertBeforeBlanks(Element elem, Node childElement) {
        Node lastchild = elem.getLastChild();
        if (isWhitespace(lastchild)) {
            // last child is the whitespace preceding the </element> so insert before that
            elem.insertBefore(childElement, lastchild);
        } else {
            elem.appendChild(childElement);
        }
    }

    public void createVariableWithValue(String varName, String varValue, boolean isDefaultValue) {
        createVariableWithValue(doc.getDocumentElement(), varName, varValue, isDefaultValue);
    }

    public void createVariableWithValue(Element elem, String varName, String varValue, boolean isDefaultValue) {
        if (varValue == null) {
            return;
        }
        Element child = doc.createElement("variable");
        child.setAttribute("name", varName);
        String valueAttr = isDefaultValue ? "defaultValue" : "value";
        child.setAttribute(valueAttr, varValue);
        elem.appendChild(child);
    }

    /**
     * Add the given feature to the feature manager element. Also creates the
     * feature manager element if it does not already exist.
     * 
     * @param name feature name
     */
    public void createFeature(String name) {
        createFeatureManager();
        Element child = doc.createElement("feature");
        Node text = doc.createTextNode(name);
        child.appendChild(text);
        featureManager.appendChild(child);
    }

    /**
     * Create the feature manager element if it does not already exist
     */
    public Element createFeatureManager() {
        if (featureManager == null) {
            featureManager = doc.createElement("featureManager");
            doc.getDocumentElement().appendChild(featureManager);
        }
        return featureManager;
    }

    public Element findFeatureManager() {
        Element serverElement = findServerElement();
        if (serverElement == null) {
            return null;
        }
        Node fmNode = serverElement.getElementsByTagName("featureManager").item(0); // assume just one
        if (fmNode instanceof Element) {
            return (Element) fmNode;
        }
        return null;
    }

    public boolean hasFMComment(String comment) {
        return findFMComment(comment) != null;
    }

    public Node findFMComment(String comment) {
        List<Comment> l = findCommentNodes(featureManager);
        for (Comment c : l) {
            if (c.getData().contains(comment)) {
                return c;
            }
        }
        return null;
    }

    public List<Comment> findCommentNodes(Element e) {
        NodeList list = e.getChildNodes();
        List<Comment> result = new ArrayList<Comment>();
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i).getNodeType() == Element.COMMENT_NODE) {
                if (list.item(i) instanceof Comment) {
                    result.add((Comment)list.item(i));
                }
            }
        }
        return result;
    }

    public Element findServerElement() {
        return doc.getDocumentElement(); // defined for this type of file
    }
}
