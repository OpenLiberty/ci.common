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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.openliberty.tools.common.ai.util.LibertyConfiguration.Attribute;
import io.openliberty.tools.common.ai.util.LibertyConfiguration.Sequence;
import io.openliberty.tools.common.ai.util.LibertyConfiguration.Type;

public class LibertyServerConfigurationUtil {

    private static Document document = null;
    private static Element serverType = null;

    private static Map<String, LibertyConfiguration> configurations = null;

    private static Map<String, LibertyConfiguration> getConfigurations() {
        if (configurations != null) {
            return configurations;
        }
        configurations = new HashMap<String, LibertyConfiguration>();
        return configurations;   
    }

    private static Document getDocument() throws Exception {
        if (document == null) {
            //String xsdPath = "https://raw.githubusercontent.com/OpenLiberty/liberty-language-server/refs/heads/main/lemminx-liberty/src/main/resources/schema/xsd/liberty/server-cached-25.0.0.6.xsd";
            ClassLoader classLoader = LibertyServerConfigurationUtil.class.getClassLoader();
            InputStream xsdInputStream = classLoader.getResourceAsStream("server-cached-25.0.0.6.xsd");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            //document = builder.parse(xsdPath);
            document = builder.parse(xsdInputStream);
            document.getDocumentElement().normalize();
        }
        return document;
    }

    private static Element getServerType() throws Exception {
        if (serverType == null) {
            NodeList complexTypes = getDocument().getElementsByTagName("xsd:complexType");
            for (int i = 0; i < complexTypes.getLength(); i++) {
                Element complexType = (Element) complexTypes.item(i);
                String name = complexType.getAttribute("name");
                if (name.equals("serverType")) {
                    serverType = complexType;
                    break;
                }
            }
        }
        return serverType;
    }

    private static String getAttribute(Node extensionNode, String attributeName) {
        NamedNodeMap attributes = extensionNode.getAttributes();
        Node n = attributes.getNamedItem(attributeName);
        if (n != null) {
              return n.getNodeValue();
        }
        return null;
    }

    private static Node getExtensionNode(Element complexTypeElement) throws Exception {
        Node complexContentNode = getNode(complexTypeElement, "xsd:complexContent");
        if (complexContentNode != null) {
            Node extensionNode = getNode(complexContentNode, "xsd:extension");
            if (extensionNode != null) {
                return extensionNode;
            }
        }
        return null;
    }
        
    private static Element getComplexType(String type) throws Exception {
        NodeList complexTypes = getDocument().getElementsByTagName("xsd:complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            String name = complexType.getAttribute("name");
            if (name.equals(type)) {
                return complexType;
            }
        }
        return null;
    }
    
    private static String getConfigurationType(String feature) throws Exception {
        NodeList elements = getServerType().getElementsByTagName("xsd:element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (element != null) {
                if (feature.equals(element.getAttribute("name"))) {
                    return element.getAttribute("type");
                }
            }
        }
        return null;
    }

    private static String getConfigurationName(String feature) throws Exception {
        NodeList elements = getServerType().getElementsByTagName("xsd:element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (element != null) {
                if (feature.equals(element.getAttribute("type"))) {
                    return element.getAttribute("name");
                }
            }
        }
        return null;
    }

    private static Node getNode(Node n, String nodeName) {
        if (n == null) {
            return null;
        }
        NodeList children = n.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            String iNodeName = children.item(i).getNodeName();
            if (iNodeName.equals(nodeName)) {
                return children.item(i);
            }
        }
        return null;
    }

    private static List<Node> getNodes(Node n, String nodeName) {
        List<Node> nodes = new ArrayList<Node>();
           NodeList children = n.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            String iNodeName = children.item(i).getNodeName();
            if (iNodeName.equals(nodeName)) {
                nodes.add(children.item(i));
            }
        }
        return nodes;
    }

    private static String normalizeType(String type) {
        if (type.startsWith("xsd:")) {
            String t = type.substring(4);
            if (t.equalsIgnoreCase("int")) {
                return "integer";
            }
            return t;
        } else if (type.equalsIgnoreCase("pidType") || type.equalsIgnoreCase("factoryIdType")) {
            return "id";
        } else if (type.equalsIgnoreCase("booleanType")) {
            return "boolean";
        } else if (type.equalsIgnoreCase("intType")) {
            return "integer";
        }
        return type;
    }
    
    private static Type getSimpleType(Node attributeNode) {
        Type type = new Type();
        NamedNodeMap attributes = attributeNode.getAttributes();
        Node typeNode = attributes.getNamedItem("type");
        if (typeNode == null) {
            Node simplyTypeNode = getNode(attributeNode, "xsd:simpleType");
            if (simplyTypeNode == null) {
                return null;
            }
            Node unionNode = getNode(simplyTypeNode, "xsd:union");
            if (unionNode == null) {
                return null;
            }
            simplyTypeNode = getNode(unionNode, "xsd:simpleType");
            if (simplyTypeNode == null) {
                return null;
            }
            Node restrictionNode = getNode(simplyTypeNode, "xsd:restriction");
            if (restrictionNode == null) {
                return null;
            }
            String t = getAttribute(restrictionNode, "base");
            type.setName(normalizeType(t));
            if (t.equalsIgnoreCase("xsd:int") || t.equalsIgnoreCase("xsd:short")) {
                Node minNode = getNode(restrictionNode, "xsd:minInclusive");
                if (minNode != null) {
                    String min = getAttribute(minNode, "value");
                    type.setMin(Integer.valueOf(min));
                }
                Node maxNode = getNode(restrictionNode, "xsd:maxInclusive");
                if (maxNode != null) {
                    String max = getAttribute(maxNode, "value");
                    type.setMax(Integer.valueOf(max));
                }
            }
            List<Node> enumerationNodes = getNodes(restrictionNode, "xsd:enumeration");
            for (Node enumerationNode : enumerationNodes) {
                type.addEnumeration(getAttribute(enumerationNode, "value"));
            }
        } else {
            String t = typeNode.getNodeValue();
            type.setName(normalizeType(t));
        }
        return type;
    }

    private static void getTextAndDescription(Node attributeNode, ILibertyConfiguration configuration) {
        Node annotationNode = getNode(attributeNode, "xsd:annotation");
        if (annotationNode != null) {
            Node documentationNode = getNode(annotationNode, "xsd:documentation");
            if (documentationNode != null) {
                configuration.setDescription(documentationNode.getTextContent());
            }
            Node appinfoNode = getNode(annotationNode, "xsd:appinfo");
            if (appinfoNode != null) {
                Node labelNode = getNode(appinfoNode, "ext:label");
                if (labelNode != null) {
                    configuration.setName(labelNode.getTextContent());
                }
            }
        }
    }

    private static Attribute getAttribute(Node attributeNode) {
        Attribute attribute = new Attribute();
        NamedNodeMap attributes = attributeNode.getAttributes();
        Node n = attributes.getNamedItem("name");
        attribute.setLabel(n.getNodeValue());
        attribute.setType(getSimpleType(attributeNode));
        Node u = attributes.getNamedItem("use");
        attribute.setUse(u == null ? null : u.getNodeValue());
        Node d = attributes.getNamedItem("default");
        attribute.setDefaultValue(d == null ? null : d.getNodeValue());
        getTextAndDescription(attributeNode, attribute);
        return attribute;
    }

    private static Attribute getChoice(Node choiceNode) {
        Attribute attribute = new Attribute();
        NamedNodeMap attributes = choiceNode.getAttributes();
        Node nameNode = attributes.getNamedItem("name");
        attribute.setLabel(nameNode.getNodeValue());
        Node typeNode = attributes.getNamedItem("type");
        if (typeNode != null) {
            String t = typeNode.getNodeValue();
            if (t != null) {
                Type type = new Type();
                if (t.startsWith("xsd:")) {
                    type.setName(normalizeType(t));
                } else {
                    try {
                        String name = getConfigurationName(t);
                        if (name == null && !t.endsWith("-factory")) {
                            name = getConfigurationName(t + "-factory");
                        }
                        type.setName(name);
                    } catch (Exception e) {
                        return null;
                    }
                }
                attribute.setType(type);
            }
        }
        getTextAndDescription(choiceNode, attribute);
        return attribute;
    }

    private static void getAttributes(Node node, LibertyConfiguration configuration) {
        List<Node> attributeNodes = getNodes(node, "xsd:attribute");
        for (Node attributeNode : attributeNodes) {
            configuration.addAttribute(getAttribute(attributeNode));
        }
    }

    private static void getConfiguration(String configurationLabel, Element complexType) {
        LibertyConfiguration configuration = getConfigurations().get(configurationLabel);
        if (configuration == null) {
            configuration = new LibertyConfiguration();
            configuration.setLabel(configurationLabel);
        } else {
            return;
        }
        Node extensionNode;
        try {
            extensionNode = getExtensionNode(complexType);
            if (extensionNode == null) {
                getLibertyConfiguration(complexType, configuration);
            } else {
                getAttributes(extensionNode, configuration);
                String extension = getAttribute(extensionNode, "base");
                Element extensionComplexType = getComplexType(extension);
                    getTextAndDescription(extensionComplexType, configuration);
                    getAttributes(extensionComplexType, configuration);
            }
            getConfigurations().put(configurationLabel, configuration);
        } catch (Exception e) {
            return;
        }
    }

    private static void getLibertyConfiguration(Node configurationComplexType, LibertyConfiguration configuration) {

        getTextAndDescription(configurationComplexType, configuration);
        getAttributes(configurationComplexType, configuration);

        Node choiceNode = getNode(configurationComplexType, "xsd:choice");
        if (choiceNode != null) {
            List<Node> choiceElementNodes = getNodes(choiceNode, "xsd:element");
            for (Node choiceElementNode : choiceElementNodes) {
                configuration.addChoice(getChoice(choiceElementNode));
            }
        }
           
        Node sequenceNode = getNode(configurationComplexType, "xsd:sequence");
        if (sequenceNode != null) {
            Node sequenceElementNode = getNode(sequenceNode, "xsd:element");
            if (sequenceElementNode != null) {
                Sequence sequence = new Sequence();
                sequence.setLabel(getAttribute(sequenceElementNode, "name"));
                sequence.setDefaultValue(getAttribute(sequenceElementNode, "default"));
                String minOccurs = getAttribute(sequenceElementNode, "minOccurs");
                if (minOccurs != null) {
                    sequence.setMinOccurs(Integer.valueOf(minOccurs));
                }
                String maxOccurs = getAttribute(sequenceElementNode, "maxOccurs");
                if (maxOccurs != null) {
                    if (!maxOccurs.equalsIgnoreCase("unbounded")) {
                        sequence.setMaxOccurs(Integer.valueOf(maxOccurs));
                    }
                }
                String type = getAttribute(sequenceElementNode, "type");
                if (type != null) {
                    if (type.startsWith("xsd:")) {
                        sequence.setType(type.substring(4));
                    } else {
                        try {
                            String name = getConfigurationName(type);
                            if (name == null && !type.endsWith("-factory")) {
                                name = getConfigurationName(type + "-factory");
                            }
                            sequence.setType(name);
                            Element complexType = getComplexType(type);
                            if (complexType != null) {
                                getConfiguration(sequence.getLabel(), complexType);
                            }
                        } catch (Exception e) {
                               sequence.setType(type);
                        }
                    }
                }
                getTextAndDescription(sequenceElementNode, sequence);
                configuration.setSequence(sequence);                   
            }
        }
    }
        
    private static LibertyConfiguration getLibertyConfiguration(String configurationLabel) {

        LibertyConfiguration configuration = getConfigurations().get(configurationLabel);
        if (configuration != null) {
            return configuration;
        }

        configuration = new LibertyConfiguration();
        configuration.setLabel(configurationLabel);
        Element configurationComplexType = null ;
        try {
            String configurationType = getConfigurationType(configurationLabel);
               configurationComplexType = getComplexType(configurationType);
               Node extensionNode = getExtensionNode(configurationComplexType);
               if (extensionNode != null) {
                   String extension = getAttribute(extensionNode, "base");
                configurationComplexType = getComplexType(extension);
                getAttributes(extensionNode, configuration);
               }
        } catch (Exception e) {
            return null;
        }
        getLibertyConfiguration(configurationComplexType, configuration);
        getConfigurations().put(configurationLabel, configuration);
        return configuration;
    }

    public static String getConfigurationTemplate(String name) {
        LibertyConfiguration configuration = getLibertyConfiguration(name);
        if (configuration == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("```xml\n");
        sb.append("<" + name);
        List<Attribute> attributes = configuration.getAttributes();
        for (Attribute a : attributes) {
            if (a.getLabel().equalsIgnoreCase("id")) {
                String first = name.substring(0, 1);
                String rest = name.substring(1);
                sb.append(" id=\"default" + first.toUpperCase() + rest.replaceAll(" ", "") + "\"");
                break;
            }
        }
        sb.append("\n");
        for (Attribute a : attributes) {
            if (!a.getLabel().equalsIgnoreCase("id")) {
                String defaultValue = a.getDefaultValue();
                sb.append("    " + a.getLabel() + "=\"" + (defaultValue == null ? "" : defaultValue)  + "\"\n");
            }
        }
        sb.append("/>\n");
        sb.append("```\n");
        return sb.toString();
    }

    public static String getConfigurationAttributes(String name) {
        LibertyConfiguration configuration = getLibertyConfiguration(name);
        if (configuration == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("\n### Explanation of ");
        sb.append(configuration.getLabel() + " (" + configuration.getName() + ")\n\n");
        List<Attribute> attributes = configuration.getAttributes();
        for (Attribute a : attributes) {
            sb.append("- ")
              .append(a.getLabel() + ": ")
              .append(a.getUse())
              .append(", name=" + a.getName())
              .append(", type=" + a.getType())
              .append(", defaultValue=" + a.getDefaultValue())
              .append(", description=" + a.getDescription())
              .append("\n");
        }
        return sb.toString();
    }

    // Following code is for debug purpose
    private static void printNode(String prefix, int i, Node n) {
        System.out.println(prefix + i + " -\n"
            + prefix + "  getNodeName - " + n.getNodeName() + ",\n"
            + prefix + "  getNodeValue - " + n.getNodeValue() + ",\n"
            + prefix + "  getLocalName - " + n.getLocalName() + ",\n"
            + prefix + "  getNodeType - " + n.getNodeType() + ",\n"
            + prefix + "  getTextContent - " + n.getTextContent() + ",\n"
            + prefix + "  getChildNodes - " + n.getChildNodes().getLength() + "\n");
    }
    
    private static void printConfiguration(LibertyConfiguration configuration) {
        System.out.println("\n\n------------------------------");
        System.out.println(configuration.getName() + " (" + configuration.getLabel() + ")");
        System.out.println("  description: " + configuration.getDescription());
        List<Attribute> attributes = configuration.getAttributes();
        if (attributes != null) {
            Collections.sort(attributes, (a1, a2) -> a1.getLabel().compareTo(a2.getLabel()));
            if (!attributes.isEmpty()) {
                System.out.println("  attributes:");
                for (Attribute a  :  attributes) {
                     System.out.println("    " + a.getLabel() + ": "
                         + a.getUse()
                         + ", name=" + a.getName()
                         + ", type=" + a.getType()
                         + ", defaultValue=" + a.getDefaultValue()
                         + ", description=" + a.getDescription());
                }
            }
        }
        List<Attribute> choices = configuration.getChoices();
        if (choices != null) {
            Collections.sort(choices, (a1, a2) -> a1.getLabel().compareTo(a2.getLabel()));
            if (!choices.isEmpty()) {
                System.out.println("  choices:");
                for (Attribute a  :  choices) {
                    System.out.println("    " + a.getLabel() + ": "
                        + a.getUse()
                        + ", name=" + a.getName()
                        + ", type=" + a.getType()
                        + ", defaultValue=" + a.getDefaultValue()
                        + ", description=" + a.getDescription());
                }
            }           
        }
        
        Sequence sequence = configuration.getSequence();
        if (sequence != null) {
            System.out.println("  sequence:");
            System.out.println("    " + sequence.getLabel() + ": "
                + "name=" + sequence.getName()
                + ", type=" + sequence.getType()
                + ", minOccurs=" + sequence.getMinOccurs()
                + ", maxOccurs=" + sequence.getMaxOccurs()
                + ", defaultValue=" + sequence.getDefaultValue()
                + ", description=" + sequence.getDescription());
        }
    }

    public static void main(String[] args) throws Exception {
        printConfiguration(getLibertyConfiguration("acmeCA"));
        printConfiguration(getLibertyConfiguration("grpcClient"));
        printConfiguration(getLibertyConfiguration("ssl"));
        printConfiguration(getLibertyConfiguration("connectionFactory"));
        printConfiguration(getLibertyConfiguration("connectionManager"));
        printConfiguration(getLibertyConfiguration("auditFileHandler"));
        printConfiguration(getLibertyConfiguration("logstashCollector"));
        printConfiguration(getLibertyConfiguration("keyStore"));
        printConfiguration(getLibertyConfiguration("keyEntry"));
        System.out.println("\n\n==========");
        System.out.println(getConfigurationTemplate("acmeCA"));
        System.out.println(getConfigurationTemplate("grpcClient"));
        System.out.println(getConfigurationTemplate("ssl"));
        System.out.println(getConfigurationTemplate("connectionFactory"));
        System.out.println(getConfigurationTemplate("connectionManager"));
        System.out.println(getConfigurationTemplate("auditFileHandler"));
        System.out.println(getConfigurationTemplate("logstashCollector"));
        System.out.println(getConfigurationTemplate("keyStore"));
        System.out.println(getConfigurationTemplate("keyEntry"));           
    }
}
