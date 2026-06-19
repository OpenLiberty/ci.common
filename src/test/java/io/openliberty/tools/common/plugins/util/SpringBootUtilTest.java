/**
 * (C) Copyright IBM Corporation 2026.
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

/**
 * Unit tests for SpringBootUtil
 * Tests both Tier 1 (manifest) and Tier 2 (regex) detection for Spring Boot applications
 */
public class SpringBootUtilTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ========== Manifest Attributes based Detection Tests ==========

    @Test
    public void testIsSpringBootUberJar_SpringBoot2_WithManifest() throws Exception {
        File testJar = createSpringBootJarWithManifest("2.7.18", "BOOT-INF/lib/spring-boot-2.7.18.RELEASE.jar");
        assertTrue("Should detect Spring Boot 2.7.18 via manifest", 
                   SpringBootUtil.isSpringBootUberJar(testJar));
    }

    @Test
    public void testIsSpringBootUberJar_SpringBoot3_WithManifest() throws Exception {
        File testJar = createSpringBootJarWithManifest("3.1.3", "BOOT-INF/lib/spring-boot-3.1.3.jar");
        assertTrue("Should detect Spring Boot 3.1.3 via manifest", 
                   SpringBootUtil.isSpringBootUberJar(testJar));
    }

    @Test
    public void testIsSpringBootUberJar_SpringBoot4_WithManifest() throws Exception {
        File testJar = createSpringBootJarWithManifest("4.0.0", "BOOT-INF/lib/spring-boot-4.0.0.jar");
        assertTrue("Should detect Spring Boot 4.0.0 via manifest", 
                   SpringBootUtil.isSpringBootUberJar(testJar));
    }

    @Test
    public void testIsSpringBootUberJar_SpringBoot4_War_WithManifest() throws Exception {
        File testWar = createSpringBootWarWithManifest("4.0.0", "WEB-INF/lib/spring-boot-4.0.0.jar");
        assertTrue("Should detect Spring Boot 4.0.0 WAR via manifest", 
                   SpringBootUtil.isSpringBootUberJar(testWar));
    }

    // ========== Regex based Detection Tests ( Fallback) ==========

    @Test
    public void testIsSpringBootUberJar_SpringBoot2_WithoutManifest() throws Exception {
        File testJar = createSpringBootJarWithoutManifest("BOOT-INF/lib/spring-boot-2.7.18.RELEASE.jar");
        assertTrue("Should detect Spring Boot 2.7.18.RELEASE via regex", 
                   SpringBootUtil.isSpringBootUberJar(testJar));
    }

    @Test
    public void testIsSpringBootUberJar_SpringBoot3_WithoutManifest() throws Exception {
        File testJar = createSpringBootJarWithoutManifest("BOOT-INF/lib/spring-boot-3.1.3.jar");
        assertTrue("Should detect Spring Boot 3.1.3 via regex", 
                   SpringBootUtil.isSpringBootUberJar(testJar));
    }

    @Test
    public void testIsSpringBootUberJar_SpringBoot4_WithoutManifest() throws Exception {
        File testJar = createSpringBootJarWithoutManifest("BOOT-INF/lib/spring-boot-4.0.0.jar");
        assertTrue("Should detect Spring Boot 4.0.0 via regex - CRITICAL TEST", 
                   SpringBootUtil.isSpringBootUberJar(testJar));
    }

    @Test
    public void testIsSpringBootUberJar_SpringBoot3_4_WithoutManifest() throws Exception {
        File testJar = createSpringBootJarWithoutManifest("BOOT-INF/lib/spring-boot-3.4.13.jar");
        assertTrue("Should detect Spring Boot 3.4.13 via regex", 
                   SpringBootUtil.isSpringBootUberJar(testJar));
    }

    @Test
    public void testIsSpringBootUberJar_SpringBoot4_War_WithoutManifest() throws Exception {
        File testWar = createSpringBootJarWithoutManifest("WEB-INF/lib/spring-boot-4.0.0.jar");
        assertTrue("Should detect Spring Boot 4.0.0 WAR via regex", 
                   SpringBootUtil.isSpringBootUberJar(testWar));
    }

    @Test
    public void testIsSpringBootUberJar_MultiDigitVersion() throws Exception {
        File testJar = createSpringBootJarWithoutManifest("BOOT-INF/lib/spring-boot-10.5.3.jar");
        assertTrue("Should detect Spring Boot 10.5.3 via regex - future-proof test", 
                   SpringBootUtil.isSpringBootUberJar(testJar));
    }

    // ========== Negative Tests ==========

    @Test
    public void testIsSpringBootUberJar_RegularJar() throws Exception {
        File testJar = tempFolder.newFile("regular.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(testJar))) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            jos.putNextEntry(new JarEntry("com/example/Main.class"));
            jos.write(new byte[0]);
            jos.closeEntry();
        }
        assertFalse("Should NOT detect regular JAR as Spring Boot", 
                    SpringBootUtil.isSpringBootUberJar(testJar));
    }

    @Test
    public void testIsSpringBootUberJar_NullFile() {
        assertFalse("Should return false for null file", 
                    SpringBootUtil.isSpringBootUberJar(null));
    }

    @Test
    public void testIsSpringBootUberJar_NonExistentFile() {
        File nonExistent = new File("non-existent-file.jar");
        assertFalse("Should return false for non-existent file", 
                    SpringBootUtil.isSpringBootUberJar(nonExistent));
    }
    
    /**
     * Creates a Spring Boot JAR with manifest attributes
     */
    private File createSpringBootJarWithManifest(String version, String springBootJarPath) throws Exception {
        File testJar = tempFolder.newFile("spring-boot-" + version + "-test.jar");
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(testJar))) {
            // Create manifest with Spring Boot attributes
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue(SpringBootUtil.BOOT_VERSION_ATTRIBUTE, version);
            manifest.getMainAttributes().putValue(SpringBootUtil.BOOT_START_CLASS_ATTRIBUTE, "com.example.Application");
            
            // Write manifest
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(manifestEntry);
            manifest.write(jos);
            jos.closeEntry();
            
            // Add spring-boot jar entry
            JarEntry springBootEntry = new JarEntry(springBootJarPath);
            jos.putNextEntry(springBootEntry);
            jos.write(new byte[0]);
            jos.closeEntry();
        }
        
        return testJar;
    }

    /**
     * Creates a Spring Boot WAR with manifest attributes
     */
    private File createSpringBootWarWithManifest(String version, String springBootJarPath) throws Exception {
        File testWar = tempFolder.newFile("spring-boot-" + version + "-test.war");
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(testWar))) {
            // Create manifest with Spring Boot attributes
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue(SpringBootUtil.BOOT_VERSION_ATTRIBUTE, version);
            manifest.getMainAttributes().putValue(SpringBootUtil.BOOT_START_CLASS_ATTRIBUTE, "com.example.Application");
            
            // Write manifest
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(manifestEntry);
            manifest.write(jos);
            jos.closeEntry();
            
            // Add spring-boot jar entry
            JarEntry springBootEntry = new JarEntry(springBootJarPath);
            jos.putNextEntry(springBootEntry);
            jos.write(new byte[0]);
            jos.closeEntry();
        }
        
        return testWar;
    }

    /**
     * Creates a Spring Boot JAR WITHOUT manifest attributes
     * This forces the regex-based detection to be used
     */
    private File createSpringBootJarWithoutManifest(String springBootJarPath) throws Exception {
        File testJar = tempFolder.newFile("spring-boot-no-manifest-test.jar");
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(testJar))) {
            // Create manifest WITHOUT Spring Boot attributes
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            // NO Spring-Boot-Version attribute
            // NO Start-Class attribute
            
            // Write manifest
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(manifestEntry);
            manifest.write(jos);
            jos.closeEntry();
            
            // Add spring-boot jar entry - this is what regex will detect
            JarEntry springBootEntry = new JarEntry(springBootJarPath);
            jos.putNextEntry(springBootEntry);
            jos.write(new byte[0]);
            jos.closeEntry();
        }
        
        return testJar;
    }
}