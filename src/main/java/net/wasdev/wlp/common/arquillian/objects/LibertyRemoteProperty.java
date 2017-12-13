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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.wasdev.wlp.common.arquillian.util.ArquillianConfigurationException;

/**
 * These properties should correspond with the parameters in WLPRemoteContainerConfiguration
 * @author ctianus.ibm.com
 *
 */
public enum LibertyRemoteProperty {
	serverName,
    serverStartTimeout,
    appDeployTimeout,
    appUndeployTimeout,
    username,
    password,
    hostName,
    httpPort,
    httpsPort,
    outputToConsole;
	
	/**
	 * Converts Map<String, String> to Map<LibertyRemoteProperty, String>. Validates each key property name.
	 * @param arquillianProperties
	 * @return a map of converted arquillianProperties
	 * @throws ArquillianConfigurationException if a property name is invalid.
	 */
	public static Map<LibertyRemoteProperty, String> getArquillianProperties(Map<String, String> arquillianProperties) throws ArquillianConfigurationException {
		Map<LibertyRemoteProperty, String> props = new HashMap<LibertyRemoteProperty, String>();
		if(arquillianProperties != null && !arquillianProperties.isEmpty()) {
			for (Entry<String, String> entry : arquillianProperties.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if(key != null && value != null) {
					LibertyRemoteProperty p = LibertyRemoteProperty.getArquillianProperty(key);
					props.put(p, value);
				}
			}
		}
		return props;
	}

	/**
	 * Check that the given key exists in ArquillianProperties
	 * 
	 * @param key
	 * @return true if so, fail the build otherwise
	 * @throws MojoFailureException
	 */
	private static LibertyRemoteProperty getArquillianProperty(String key) throws ArquillianConfigurationException {
		try {
			return LibertyRemoteProperty.valueOf(key);
		} catch (IllegalArgumentException e) {
			throw new ArquillianConfigurationException(
					"Property \"" + key + "\" in arquillianProperties does not exist. You probably have a typo.");
		}
	}
}