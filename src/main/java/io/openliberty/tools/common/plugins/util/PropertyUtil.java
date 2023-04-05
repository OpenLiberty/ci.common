/**
 * (C) Copyright IBM Corporation 2023
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

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.openliberty.tools.common.CommonLoggerI;

public class PropertyUtil {
    public static String evaluateExpression(CommonLoggerI log, Properties properties, String expression,  Map<String, File> libertyDirectoryPropertyToFile) {
        return evaluateExpression(log, properties, new Properties(), expression, libertyDirectoryPropertyToFile);
    }

    public static String evaluateExpression(CommonLoggerI log, Properties properties, Properties defaultProps, String expression,  Map<String, File> libertyDirectoryPropertyToFile) {
            String value = expression;
        if (expression != null) {
            Pattern p = Pattern.compile("\\$\\{(.*?)\\}");
            Matcher m = p.matcher(expression);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String variable = m.group(1);
                
                String propertyValue = properties.getProperty(variable, "${" + variable + "}");
                
                // Remove encapsulating ${} characters and validate that a valid liberty directory property was configured
                propertyValue = removeEncapsulatingEnvVarSyntax(log, propertyValue, properties, defaultProps, libertyDirectoryPropertyToFile); 
                
                if (propertyValue == null) {
                    return null;
                }

                m.appendReplacement(sb, propertyValue);
            }
            m.appendTail(sb);
            value = sb.toString();
        }
        // For Windows, avoid escaping the backslashes by changing to forward slashes
        value = value.replace("\\","/");
        log.debug("Include location attribute "+ expression +" evaluated and replaced with "+value);
        return value;
    }

    public static String removeEncapsulatingEnvVarSyntax(CommonLoggerI log, String propertyValue, Properties properties, Properties defaultProps, Map<String, File> libertyDirectoryPropertyToFile){
        Pattern p = Pattern.compile("\\$\\{(.*?)\\}");
        Matcher m = p.matcher(propertyValue);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String envDirectoryProperty = m.group(1);
            if(!libertyDirectoryPropertyToFile.containsKey(envDirectoryProperty)) {
                // Check if property is a reference to a configured property
                String value = properties.getProperty(envDirectoryProperty);
                if (value == null) {
                    // Check for default value since no other value found.
                    value = defaultProps.getProperty(envDirectoryProperty);
                }
                if (value == null && envDirectoryProperty.startsWith("env.") && envDirectoryProperty.length() > 4) {
                    // Look for property without the 'env.' prefix
                    String newPropName = envDirectoryProperty.substring(4);
                    value = properties.getProperty(newPropName);
                    if (value == null) {
                        // Check for default value since no other value found.
                        value = defaultProps.getProperty(newPropName);
                    }
                }

                if(value != null) {
                    // For Windows, avoid escaping the backslashes by changing to forward slashes
                    value = value.replace("\\","/");
                    m.appendReplacement(sb, removeEncapsulatingEnvVarSyntax(log, value, properties, defaultProps, libertyDirectoryPropertyToFile));
                } else {
                    log.warn("The referenced property " + envDirectoryProperty + " is not a predefined Liberty directory property or a configured bootstrap property.");
                    return null;
                }
            } else {
                File envDirectory = libertyDirectoryPropertyToFile.get(envDirectoryProperty);
                String path = envDirectory.toString();
                // For Windows, avoid escaping the backslashes by changing to forward slashes
                path = path.replace("\\","/");
                m.appendReplacement(sb, path);
            }
        }
        m.appendTail(sb);
        String returnValue = sb.toString();
        if (sb.charAt(0) == '"' && sb.charAt(sb.length()-1) == '"') {
            if (sb.length() > 2) {
                returnValue = sb.substring(1,sb.length()-1);
            } else {
                // The sb variable just contains a beginning and ending quote. Return an empty String.
                returnValue = "";
            }
        }
        // For Windows, avoid escaping the backslashes by changing to forward slashes
        returnValue = returnValue.replace("\\","/");
        log.debug("Include location attribute property value "+ propertyValue +" replaced with "+ returnValue);
        return returnValue;
    }
}
