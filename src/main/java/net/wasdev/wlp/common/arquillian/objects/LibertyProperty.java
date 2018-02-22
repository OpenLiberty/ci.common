/**
 * (C) Copyright IBM Corporation 2017, 2018.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.wasdev.wlp.common.arquillian.util.ArquillianConfigurationException;

public class LibertyProperty {

    public interface LibertyPropertyI {
    }

    /**
     * Converts Map<String, String> to Map<LibertyPropertyI, String>. Validates
     * each key property name.
     * 
     * @param arquillianProperties
     * @return a map of converted arquillianProperties
     * @throws ArquillianConfigurationException
     *             if a property name is invalid.
     */
    public static Map<LibertyPropertyI, String> getArquillianProperties(Map<String, String> arquillianProperties,
            Class<?> cls) throws ArquillianConfigurationException {
        Map<LibertyPropertyI, String> props = new HashMap<LibertyPropertyI, String>();
        if (arquillianProperties != null && !arquillianProperties.isEmpty()) {
            for (Entry<String, String> entry : arquillianProperties.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null) {
                    LibertyPropertyI p = getArquillianProperty(key, cls);
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
    private static LibertyPropertyI getArquillianProperty(String key, Class<?> cls)
            throws ArquillianConfigurationException {
        try {
            if (cls == LibertyManagedObject.LibertyManagedProperty.class) {
                return LibertyManagedObject.LibertyManagedProperty.valueOf(key);
            } else if (cls == LibertyRemoteObject.LibertyRemoteProperty.class) {
                return LibertyRemoteObject.LibertyRemoteProperty.valueOf(key);
            }
        } catch (IllegalArgumentException e) {
            throw new ArquillianConfigurationException(
                    "Property \"" + key + "\" in arquillianProperties does not exist. You probably have a typo.");
        }
        throw new ArquillianConfigurationException("This should never happen.");
    }

    protected static void write(StringBuilder xml, File arquillianXml) throws IOException {
        // First create the parent folder of arquillian.xml if it doesn't exist
        // (this happens in gradle if you don't specify any resources in
        // src/test/resources)
        File arquillianXmlFolder = arquillianXml.getParentFile();
        if (!arquillianXmlFolder.exists()) {
            arquillianXmlFolder.mkdirs();
        }
        
        // Now that we are guaranteed that the folder exists, we can write
        FileWriter writer = new FileWriter(arquillianXml);
        writer.write(xml.toString());
        writer.close();
    }

}
