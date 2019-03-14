/**
 * (C) Copyright IBM Corporation 2018.
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
package net.wasdev.wlp.common.plugins.util;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import java.util.Enumeration;

public class SpringBootUtil {

    public static final String BOOT_VERSION_ATTRIBUTE = "Spring-Boot-Version";
    public static final String BOOT_START_CLASS_ATTRIBUTE = "Start-Class";
    public static final String BOOT_JAR_EXPRESSION = "BOOT-INF/lib/spring-boot-\\d[\\.]\\d[\\.]\\d.RELEASE.jar";
    public static final String BOOT_WAR_EXPRESSION = "WEB-INF/lib/spring-boot-\\d[\\.]\\d[\\.]\\d.RELEASE.jar";
    
    /**
     * Check whether the given artifact is a Spring Boot Uber JAR
     * @param artifact
     * @return true if so, false otherwise
     */
    public static boolean isSpringBootUberJar(File artifact) {
        if (artifact == null || !artifact.exists() || !artifact.isFile()) {
            return false;
        }

        try (JarFile jarFile = new JarFile(artifact)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                if(attributes.getValue(BOOT_VERSION_ATTRIBUTE) != null
                        && attributes.getValue(BOOT_START_CLASS_ATTRIBUTE) != null) {
                    return true;
                } else { //Checking that there is a spring-boot-VERSION.RELEASE.jar in the BOOT-INF/lib directory
                         //Handles the Gradle case where the spring plugin does not set the properties in the manifest
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while(entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();
                        if (!entryName.startsWith("org") && (entryName.matches(BOOT_JAR_EXPRESSION) || entryName.matches(BOOT_WAR_EXPRESSION))) {                        
                            return true;
                        }         
                    }
                }
            }
        } catch (IOException e) {}
        return false;
    }
    
}