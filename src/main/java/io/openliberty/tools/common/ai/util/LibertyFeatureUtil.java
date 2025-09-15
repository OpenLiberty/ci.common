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

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibertyFeatureUtil {

    private static final String[] libertyKernelLibs = new String[] {
        "org.eclipse.osgi_",
        "com.ibm.ws.crypto.passwordutil_",
        "com.ibm.ws.install_",
        "com.ibm.ws.kernel.boot_",
        "com.ibm.ws.kernel.cmdline_",
        "com.ibm.ws.kernel.feature_",
        "com.ibm.ws.logging_",
        "com.ibm.ws.org.apache.aries.util_",
        "com.ibm.ws.org.glassfish.json.1.0_",
        "com.ibm.ws.product.utility_",
        "com.ibm.ws.repository_",
        "com.ibm.ws.repository.liberty_",
        "com.ibm.ws.repository.resolver_"
    };

    private static final String[] libertyDevAPILibs = new String[] {
        "com.ibm.websphere.javaee.jsonp.1.0_"
    };

    private static URLClassLoader classLoader = null;
    private static String workingDirectory = System.getProperty("user.dir");
    private static File wlpFile = new File(workingDirectory, "target/liberty/wlp");
    private static File wlpLibFile = new File(wlpFile, "lib");
    private static File wlpDevApiFile = new File(wlpFile, "dev");
    
    private static List<LibertyFeature> libertyFeatures = null;
    private static List<String> libertyInstalledFeatures = null;
    private static List<String> libertyPlatforms = null;
    private static Boolean isVersionless = null;

    private static List<URL> getLibertyKernelLibURLs() {
        ArrayList <URL> matchingFiles = new ArrayList<URL>();
        try {
            Path dir = Paths.get(wlpLibFile.getAbsolutePath());
            Files.walk(dir).forEach(file -> {
                for (String l : libertyKernelLibs) {
                    if (file.getFileName().toString().contains(l)) {
                        try {
                            matchingFiles.add(file.toUri().toURL());
                            break;
                        } catch (MalformedURLException e) {}
                    }
                }
            });
        } catch (Exception e) {}
        return matchingFiles;
    }

    private static List<URL> getLibertyDevApiURLs() {
        ArrayList <URL> matchingFiles = new ArrayList<URL>();
        try {
            Path dir = Paths.get(wlpDevApiFile.getAbsolutePath());
            Files.walk(dir).forEach(file -> {
                for (String l : libertyDevAPILibs) {
                    if (file.getFileName().toString().contains(l)) {
                        try {
                            matchingFiles.add(file.toUri().toURL());
                            break;
                        } catch (MalformedURLException e) {}
                    }
                }
            });
        } catch (Exception e) {}
        return matchingFiles;
    }

    private static URLClassLoader getClassLoader() throws Exception {
        if (classLoader == null) {
            List<URL> libs = getLibertyKernelLibURLs();
            libs.addAll(getLibertyDevApiURLs());
            classLoader = new URLClassLoader(
                libs.toArray(new URL[libs.size()]),
                System.class.getClassLoader());
        }
        return classLoader;
    }

    private static void addPlatform(String platform) {
        if (libertyPlatforms == null) {
            libertyPlatforms = new ArrayList<String>(2);
        }
        if (platform == null) {
            return;
        }
        String[] names = platform.split("-");
        int oldPlatform = -1;
        for (int i = 0; i < libertyPlatforms.size(); i++) {
            if (libertyPlatforms.get(i).startsWith(names[0])) {
                oldPlatform = i;
            }
        }
        if (oldPlatform > -1) {
            String[] n = libertyPlatforms.get(oldPlatform).split("-");
            Double v1 = Double.valueOf(names[1]);
            Double v2 = Double.valueOf(n[1]);
            if (v1 > v2) {
                libertyPlatforms.remove(oldPlatform);
                libertyPlatforms.add(platform);
            }
        } else {
            libertyPlatforms.add(platform);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<LibertyFeature> getLibertyFeatures() {
        if (libertyFeatures == null) {
            libertyFeatures = new ArrayList<LibertyFeature>();
            try {
                Class<?> installKernelImplClass = getClassLoader().loadClass("com.ibm.ws.install.internal.InstallKernelImpl");
                File installDir = new File("/Users/gkwan/tasks/CNAI/guides/working/sample-langchain4j/format-message-encoder/sample-langchain4j/tools/target/liberty/wlp");
                Object installKernel = installKernelImplClass.getDeclaredConstructor(File.class).newInstance(installDir);
                Method queryFeaturesMethod = installKernelImplClass.getMethod("queryFeatures", String.class);

                List<Object> esaList = (List<Object>) queryFeaturesMethod.invoke(installKernel, "");
                for (Object esa : esaList) {
                    LibertyFeature feature = new LibertyFeature();
                    Method getShortNameMethod = esa.getClass().getMethod("getShortName");
                    Method getNameMethod = esa.getClass().getMethod("getName");
                    Method getShortDescriptionMethod = esa.getClass().getMethod("getShortDescription");
                    feature.setShortName((String) getShortNameMethod.invoke(esa));
                    feature.setName((String) getNameMethod.invoke(esa));
                    feature.setShortDescription((String) getShortDescriptionMethod.invoke(esa));
                    Method getPlatformMethod = esa.getClass().getMethod("getPlatforms");
                    List<String> platforms = (List<String>) getPlatformMethod.invoke(esa);
                    if (platforms != null && !platforms.isEmpty()) {
                        feature.setPlatforms(platforms);
                    }
                    libertyFeatures.add(feature);
                }
            } catch (Exception e) {
            }
        }
        return libertyFeatures;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getLibertyInstalledFeatures() {
        if (libertyInstalledFeatures == null) {
            libertyInstalledFeatures = new ArrayList<String>();
            try {
                String manifestFileProcessorClassName = "com.ibm.ws.kernel.feature.internal.generator.ManifestFileProcessor";
                Class<?> manifestFileProcessorClass = getClassLoader().loadClass(manifestFileProcessorClassName);
                Object manifestFileProcessor = manifestFileProcessorClass.getDeclaredConstructor().newInstance();
                Method method = manifestFileProcessorClass.getMethod("getFeatureDefinitions", String.class);
                Map<String, Object> features = (Map<String, Object>) method.invoke(manifestFileProcessor, "core");
                for (Object key : features.keySet()) {
                    Object featureDefinition = features.get(key);
                    Class<?> featureDefinitionClass = featureDefinition.getClass();
                    Method getShortNameMethod = (Method) featureDefinitionClass.getMethod("getIbmShortName");
                    Method isVersionlessMethod = (Method) featureDefinitionClass.getMethod("isVersionless");
                    String shortName = (String) getShortNameMethod.invoke(featureDefinition);
                    Method getPlatformNameMethod = (Method) featureDefinitionClass.getMethod("getPlatformName");
                    addPlatform((String) getPlatformNameMethod.invoke(featureDefinition));
                    if ((boolean) isVersionlessMethod.invoke(featureDefinition)) {
                        isVersionless = Boolean.TRUE;
                        String platform = (String) getPlatformNameMethod.invoke(featureDefinition);
                        addPlatform(platform);
                    }
                    if (shortName != null) {
                        libertyInstalledFeatures.add(shortName);
                    }
                }
                if (isVersionless == null) {
                    isVersionless = Boolean.FALSE;
                }
            } catch (Exception e) {
            }
        }
        return libertyInstalledFeatures;
    }

    public static LibertyFeature getLibertyFeature(String shortName) {
        for (LibertyFeature f : getLibertyFeatures()) {
            if (f.getShortName().equals(shortName)) {
                return f;
            }
        }
        return null;
    }

    public static Boolean isVersionless() {
        if (isVersionless == null) {
            getLibertyInstalledFeatures();
        }
        return isVersionless;
    }

    public static List<String> getPlatforms() {
        if (libertyPlatforms == null) {
            getLibertyInstalledFeatures();
        }
        return libertyPlatforms;    
    }

    public static void printLibertyInstalledFeatures() throws Exception {
        for (String f : getLibertyInstalledFeatures()) {
            LibertyFeature libertyFeature = getLibertyFeature(f);
            System.out.println(libertyFeature);
            if (!libertyFeature.getPlatforms().isEmpty()) {
                System.out.println("  platforms:");
                for (String p : libertyFeature.getPlatforms()) {
                    System.out.println("    " + p);
                }
            }
            System.out.println("  description: " + libertyFeature.getShortDescription());
            System.out.println();
        }
        System.out.println("isVersionless: " + isVersionless());
        if (getPlatforms() != null && !getPlatforms().isEmpty()) {
            System.out.println("Using platforms:");
            for (String p : getPlatforms()) {
                System.out.println("  " + p);
            }
        }
    }

    public static void printLibertyFeatures() throws Exception {
        for (LibertyFeature f : getLibertyFeatures()) {
            System.out.println(f);
            if (!f.getPlatforms().isEmpty()) {
                System.out.println("  platforms:");
                for (String p : f.getPlatforms()) {
                    System.out.println("    " + p);
                }               
            }
            System.out.println("  description: " + f.getShortDescription());
            System.out.println();
        }
    }

    public static void reset() {
        libertyFeatures = null;
        libertyInstalledFeatures = null;
        libertyPlatforms = null;
        isVersionless = null;
    }

}
