/**
 * (C) Copyright IBM Corporation 2017.
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
package net.wasdev.wlp.common.arquillian.util;

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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class HttpPortUtil {

	public static final int DEFAULT_PORT = 9080;
	private static final XPath XPATH = XPathFactory.newInstance().newXPath();

	private static final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	static {
		factory.setNamespaceAware(true);
	}

	public static Integer getHttpPort(File serverXML, File bootstrapProperties) throws FileNotFoundException,
			IOException, ParserConfigurationException, SAXException, XPathExpressionException, ArquillianConfigurationException {
		if (serverXML != null && serverXML.exists() && serverXML.isFile()) {
			byte[] encoded = Files.readAllBytes(Paths.get(serverXML.getCanonicalPath()));
			Properties prop = new Properties();
			prop.load(new FileInputStream(bootstrapProperties));
			return getHttpPort(new String(encoded, StandardCharsets.UTF_8), prop);
		}
		throw new FileNotFoundException(
				"The given server.xml file at " + serverXML.getCanonicalPath() + " was not found.");
	}

	protected static Integer getHttpPort(String serverXML, Properties bootstrapProperties)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException, ArquillianConfigurationException {
		DocumentBuilder builder = factory.newDocumentBuilder();
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
				return getHttpPortFromBootstrapProperties(variable, bootstrapProperties);
			}
			throw new ArquillianConfigurationException("Bootstrap properties variable " + portString + " is not in the correct format.");
		}
	}

	private static Integer getHttpPortFromBootstrapProperties(String variable, Properties bootstrapProperties) throws ArquillianConfigurationException {
		if(bootstrapProperties != null) {
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
