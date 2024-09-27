/**
 * (C) Copyright IBM Corporation 2017, 2024
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
package io.openliberty.tools.common.arquillian.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.w3c.dom.NodeList;

public class HttpPortUtil {

    public static final int DEFAULT_PORT = 9080;
    private static final XPath XPATH = XPathFactory.newInstance().newXPath();

    private static DocumentBuilderFactory factory ;

    public static DocumentBuilderFactory getBuilderFactory() throws ParserConfigurationException {
        if (factory == null) {
            factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false); 
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        }
        return factory;
   }

    public static Integer getHttpPort(File serverXML, File bootstrapProperties)
            throws FileNotFoundException, IOException, ParserConfigurationException, SAXException,
            XPathExpressionException, ArquillianConfigurationException {
        return getHttpPort(serverXML, bootstrapProperties, null);
    }

    public static Integer getHttpPort(File serverXML, File bootstrapProperties, File configVariableXML)
            throws FileNotFoundException, IOException, ParserConfigurationException, SAXException,
            XPathExpressionException, ArquillianConfigurationException {
        if (serverXML != null && serverXML.exists() && serverXML.isFile()) {
            byte[] encoded = Files.readAllBytes(Paths.get(serverXML.getCanonicalPath()));
            String serverXMLAsString = new String(encoded, StandardCharsets.UTF_8);

            Properties prop = new Properties();
            if (bootstrapProperties != null && bootstrapProperties.exists()) {
                prop.load(new FileInputStream(bootstrapProperties));
            }

            String configVariableXMLAsString = null;
            if (configVariableXML != null && configVariableXML.exists() && configVariableXML.isFile()) {
                byte[] configVarBytes = Files.readAllBytes(Paths.get(configVariableXML.getCanonicalPath()));
                configVariableXMLAsString = new String(configVarBytes, StandardCharsets.UTF_8);
            }

            return getHttpPortForServerXML(serverXMLAsString, prop, configVariableXMLAsString);
        }
        throw new FileNotFoundException(
                "The given server.xml file at " + serverXML.getCanonicalPath() + " was not found.");
    }

    protected static Integer getHttpPortForServerXML(String serverXML, Properties bootstrapProperties, String configVariableXML) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException,
            ArquillianConfigurationException {

        DocumentBuilder builder = getBuilderFactory().newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(serverXML.getBytes()));

        XPathExpression httpEndpointExpr = XPATH.compile("/server/httpEndpoint");
        Object httpEndpointObj = httpEndpointExpr.evaluate(doc, XPathConstants.NODE);
        if (httpEndpointObj == null) {
            return DEFAULT_PORT;
        }

        Element httpEndpointElement = (Element) httpEndpointObj;
        String portString = httpEndpointElement.getAttribute("httpPort");

        try {
            return Integer.parseInt(portString);
        } catch (NumberFormatException e) { // Probably a variable
            Pattern p = Pattern.compile("^\\$\\{(.*)\\}$");
            Matcher m = p.matcher(portString);
            while (m.find()) {
                String variable = m.group(1);
                // First look for variable in configVariableXML if provided
                String variableValue = getHttpPortFromConfigVariableXML(configVariableXML, variable);

                if (variableValue != null) {
                    try {
                        return Integer.parseInt(variableValue);
                    } catch (NumberFormatException ex) {
                        // Config variable value is not a number, return error
                        throw new ArquillianConfigurationException(
                            "liberty-plugin-variable-config.xml variable " + variable + " is not in the correct format.");
                    }
                } else {
                    return getHttpPortFromBootstrapProperties(variable, bootstrapProperties);
                }
            }
            throw new ArquillianConfigurationException(
                    "Bootstrap properties variable " + portString + " is not in the correct format.");
        }
    }

    // Loop through all variables and look for ones that match the passed in variableName.
    // If a matching variable has a value attribute, return that.
    // Else if a matching variable has a defaultValue attribute, return that.
    // Otherwise, return null.
    // 
    private static String getHttpPortFromConfigVariableXML(String configVariableXML, String variableName) throws ParserConfigurationException, SAXException, XPathExpressionException, IOException {
        // If no configVariableXML is specified, return null.
        if (configVariableXML == null || configVariableXML.length() == 0) {
            return null;
        }
        DocumentBuilderFactory inputBuilderFactory = getBuilderFactory();
        inputBuilderFactory.setNamespaceAware(false);
        inputBuilderFactory.setIgnoringComments(true);
        inputBuilderFactory.setCoalescing(true);
        inputBuilderFactory.setIgnoringElementContentWhitespace(true);
        inputBuilderFactory.setValidating(false);
        DocumentBuilder inputBuilder = inputBuilderFactory.newDocumentBuilder();
        Document inputDoc = inputBuilder.parse(new ByteArrayInputStream(configVariableXML.getBytes()));
        
        // parse input XML Document
        String expression = "/server/variable";
        NodeList nodes = (NodeList) XPATH.compile(expression).evaluate(inputDoc, XPathConstants.NODESET);

        String variableValue = null;
        String variableDefaultValue = null;

        // iterate through nodes
        for (int i=0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String varName = el.getAttribute("name");

            if (varName != null && varName.equals(variableName)) {
                String varValue = el.getAttribute("value");
                if (varValue != null && !varValue.isEmpty() && variableValue == null) {
                    variableValue = varValue;
                }
                String varDefaultValue = el.getAttribute("defaultValue");
                if (varDefaultValue != null && !varDefaultValue.isEmpty() && variableDefaultValue == null) {
                    variableDefaultValue = varDefaultValue;
                }
            }
        }

        return (variableValue != null ? variableValue : variableDefaultValue);
    }

    private static Integer getHttpPortFromBootstrapProperties(String variable, Properties bootstrapProperties)
            throws ArquillianConfigurationException {
        if (bootstrapProperties != null) {
            String value = bootstrapProperties.getProperty(variable);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // Bootstrap properties value is not a number, return error
                }
            }
        }
        throw new ArquillianConfigurationException(
                "Unable to find variable \"" + variable + "\" in bootstrap properties.");
    }

}
