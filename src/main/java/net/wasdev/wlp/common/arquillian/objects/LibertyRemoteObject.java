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
package net.wasdev.wlp.common.arquillian.objects;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import net.wasdev.wlp.common.arquillian.util.Constants;

public class LibertyRemoteObject {

	
	private final Map<LibertyRemoteProperty, String> arquillianProperties;

	public LibertyRemoteObject(Map<LibertyRemoteProperty, String> arquillianProperties) {
		this.arquillianProperties = arquillianProperties;
	}

	public Map<LibertyRemoteProperty, String> getArquillianProperties() {
		return arquillianProperties;
	}

	public void build(File arquillianXml) throws IOException {
		// Generate the XML
		String xmlStart = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<arquillian xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
				+ "	xmlns=\"http://jboss.org/schema/arquillian\"\n"
				+ "	xsi:schemaLocation=\"http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd\">\n"
				+ "	<engine>\n"
				+ "		<property name=\"deploymentExportPath\">target/</property>\n"
				+ "	</engine>\n"
				+ "	<container qualifier=\"wlp-remote\" default=\"true\">\n"
				+ "		<configuration>\n";
				
		String xmlEnd = 
				"		</configuration>\n"
				+ "	</container>\n"
				+ "</arquillian>\n"
				+ Constants.CONFIGURE_ARQUILLIAN_COMMENT;

		String xmlProperties = "";
		for (Entry<LibertyRemoteProperty, String> e : arquillianProperties.entrySet()) {
			String key = e.getKey().name();
			xmlProperties += "			<property name=\"" + key + "\">" + e.getValue() + "</property>\n";
		}

		// Write to file
		FileWriter writer = new FileWriter(arquillianXml);
		writer.write(xmlStart + xmlProperties + xmlEnd);
		writer.close();
	}
	

}
