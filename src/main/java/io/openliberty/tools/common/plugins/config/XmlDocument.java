/**
 * (C) Copyright IBM Corporation 2017, 2026.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

public abstract class XmlDocument {
    
    protected Document doc;
    
    public void createDocument(String rootElement) throws ParserConfigurationException {
        DocumentBuilder docBuilder = getDocumentBuilder();
        doc = docBuilder.newDocument();
        doc.setXmlStandalone(true);
        Element element = doc.createElement(rootElement);
        doc.appendChild(element);
    }
    
    public void createDocument(File xmlFile) throws ParserConfigurationException, SAXException, IOException {
        doc = parseDocument(xmlFile);
    }

    public void writeXMLDocument(String fileName) throws IOException, TransformerException {
        File f = new File(fileName);
        writeXMLDocument(f);
    }
    
    public void writeXMLDocument(File f) throws IOException, TransformerException {
        if (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        OutputStream outFile = Files.newOutputStream(f.toPath());
        
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new OutputStreamWriter(outFile, StandardCharsets.UTF_8));
        
        TransformerFactory transformerFactory = getTransformerFactory();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        transformer.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
        if (isIndented()) {
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
        } else {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        }
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        
        transformer.transform(source, result);
        outFile.close();
    }

    protected boolean isIndented() {
        // if the first child is just white space then the document contains indentation information
        Node x = doc.getDocumentElement().getFirstChild();
        return isWhitespace(x);
    }

    protected boolean isWhitespace(Node node) {
        return node != null && node instanceof Text && ((Text)node).getData().trim().isEmpty();
    }

    /**
     * Creates and returns a securely configured {@link DocumentBuilder}.
     */
    public static DocumentBuilder getDocumentBuilder() {
        DocumentBuilder docBuilder;
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setIgnoringComments(true);
        docBuilderFactory.setCoalescing(true);
        docBuilderFactory.setIgnoringElementContentWhitespace(true);
        docBuilderFactory.setValidating(false);
        try {
            docBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            docBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            docBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            docBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            docBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            docBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            docBuilderFactory.setXIncludeAware(false);
            docBuilderFactory.setExpandEntityReferences(false);
            docBuilder = docBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            // fail if we can't create a document builder
            throw new RuntimeException(e);
        }
        return docBuilder;
    }

    public static Document parseDocument(File file) throws IOException, SAXException {
        try (InputStream is = Files.newInputStream(file.toPath())) {
            Document document = parseDocument(is);
            document.setDocumentURI(file.getCanonicalPath());
            return document;
        }
    }

    public static Document parseDocument(InputStream in) throws SAXException, IOException {
        try (InputStream ins = in) {
            return getDocumentBuilder().parse(ins);
        }
    }

    /**
     * Returns the text content of the first element matching {@code tagName} in an XML file,
     * or {@code null} if the file is absent, the tag is missing, or any parse error occurs.
     */
    public static String readTextElementFromXmlFile(File xmlFile, String tagName) {
        if (xmlFile == null || !xmlFile.isFile()) {
            return null;
        }
        try {
            Document doc = parseDocument(xmlFile);
            NodeList nodes = doc.getElementsByTagName(tagName);
            if (nodes.getLength() == 0) {
                return null;
            }
            String text = nodes.item(0).getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns a {@link File} for the text content of the first element matching {@code tagName}
     * in an XML file, or {@code null} if the file is absent, the tag is missing, or any parse error occurs.
     */
    public static File getFileElementFromXmlFile(File xmlFile, String tagName) {
        String path = readTextElementFromXmlFile(xmlFile, tagName);
        return (path != null) ? new File(path) : null;
    }

    public static void addNewlineBeforeFirstElement(File f) throws IOException {
        // look for "<?xml version="1.0" ... ?><server .../>" and add a newline
        byte[] contents = Files.readAllBytes(f.toPath());
        String xmlContents = new String(contents, StandardCharsets.UTF_8);
        xmlContents = xmlContents.replace("?><", "?>"+System.getProperty("line.separator")+"<");
        Files.write(f.toPath(), xmlContents.getBytes());
    }

    private static TransformerFactory getTransformerFactory() throws TransformerConfigurationException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        // XMLConstants.ACCESS_EXTERNAL_DTD uses an empty string to deny all access to external references;
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        // XMLConstants.ACCESS_EXTERNAL_STYLESHEET uses an empty string to deny all access to external references;
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return transformerFactory;
    }
}
