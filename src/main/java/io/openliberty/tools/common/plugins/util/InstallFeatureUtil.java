/**
 * (C) Copyright IBM Corporation 2018, 2023.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;

/**
 * Utility class to install features from Maven repositories.
 */
public abstract class InstallFeatureUtil extends ServerFeatureUtil {

    public static final String OPEN_LIBERTY_GROUP_ID = "io.openliberty.features";
    public static final String REPOSITORY_RESOLVER_ARTIFACT_ID = "repository-resolver";
    public static final String INSTALL_MAP_ARTIFACT_ID = "install-map";
    public static final String CONFLICT = "CWWKF0033E.*", INCOMPATIBLE_SINGLETON = "CWWKF1405E.*",
            MISSING_MULTIPLE_DEPENDENT = "CWWKF1385E.*", SAME_MODEL_CONFLICT = "CWWKF0043E.*",
            DIFF_MODEL_CONFLICT = "CWWKF0044E.*", SAME_INDIRECT_MODEL_CONFLICT = "CWWKF0047E.*",
            EE_CONFLICT = SAME_MODEL_CONFLICT + "|" + DIFF_MODEL_CONFLICT + "|" + SAME_INDIRECT_MODEL_CONFLICT,
            ANY_CONFLICT = CONFLICT + "|" + MISSING_MULTIPLE_DEPENDENT + "|" + INCOMPATIBLE_SINGLETON + "|"
                    + EE_CONFLICT;
    public static final Pattern conflictPattern = Pattern.compile(ANY_CONFLICT);
    public static final String CONFLICT_MESSAGE = "A feature conflict error occurred while installing features: ";

    private final File installDirectory;

    private final File buildDirectory;

    private final List<ProductProperties> propertiesList;

    private final String to;

    private Set<File> downloadedJsons;
    
    private final List<String> additionalJsons;
    
    private final Set<String> pluginListedEsas;
    
    private final VerifyOption verifyOption;
    
    private final Collection<Map<String, String>> keyMap;
    
    private final Map<String, String> environmentVariables;
    
    /**
     * An enum for specifying verify option
     */
    public enum VerifyOption {
        enforce, warn, skip, all;
    }
    
    //Map (symbolic name, short name) of the manually installed user feature. 
    private Map<String, String> manuallyInstalledUsrFeatureMap;

    private static final String INSTALL_MAP_PREFIX = "com.ibm.ws.install.map";
    private static final String JAR_EXT = ".jar";
    private static final String OPEN_LIBERTY_PRODUCT_ID = "io.openliberty";
    private static final String CLOSED_LIBERTY_PRODUCT_ID = "com.ibm.websphere.appserver";
    private static final String FEATURES_BOM_ARTIFACT_ID = "features-bom";
    private static final String FEATURES_JSON_ARTIFACT_ID = "features";
    private static final String TO_USER = "usr";
    private static final String MIN_USER_FEATURE_VERSION = "21.0.0.11";
    private static final String MIN_VERIFY_FEATURE_VERSION = "23.0.0.9";
    private static final String MIN_VERSIONLESS_FEATURE_VERSION = "24.0.0.10";

    private String openLibertyVersion;
    private static Boolean saveURLCacheStatus = null;
    
    private final String containerName;

    /**
     * Initialize the utility and check for unsupported scenarios.
     * Calls the constructor InstallFeatureUtil(java.io.File, java.io.File, java.lang.String, java.lang.String, java.util.Set, java.util.List, java.lang.String, java.lang.String, java.util.List, java.lang.String, java.util.Collection, java.util.Map)
     * with environmentVariables set to null.
     *
     * @param installDirectory   The install directory
     * @param buildDirectory     The build directory
     * @param from               The "from" parameter specified in the plugin
     *                           configuration, or null if not specified
     * @param to                 The "to" parameter specified in the plugin
     *                           configuration, or null if not specified
     * @param pluginListedEsas   The list of ESAs specified in the plugin
     *                           configuration, or null if not specified
     * @param propertiesList     The list of product properties installed with the
     *                           Open Liberty runtime
     * @param openLibertyVersion The version of the Open Liberty runtime
     * @param containerName      The container name if the features should be
     *                           installed in a container. Otherwise null.
     * @param additionalJsons    The list of additional JSONS to search
     *                           for features from
     * @param verifyValue        The verify option value
     * @param keyMap             The key map for feature verification
     * @throws PluginScenarioException  If the current scenario is not supported
     * @throws PluginExecutionException If properties files cannot be found in the
     *                                  installDirectory/lib/versions
     */
    public InstallFeatureUtil(File installDirectory, File buildDirectory, String from, String to, Set<String> pluginListedEsas, List<ProductProperties> propertiesList, String openLibertyVersion, String containerName, List<String> additionalJsons, String verifyValue, Collection<Map<String, String>> keyMap) throws PluginScenarioException, PluginExecutionException {
        this(installDirectory, buildDirectory, from, to, pluginListedEsas, propertiesList, openLibertyVersion, containerName, additionalJsons, verifyValue, keyMap, null);
    }

    /**
     * Initialize the utility and check for unsupported scenarios.
     *
     * @param installDirectory   The install directory
     * @param buildDirectory     The build directory
     * @param from               The "from" parameter specified in the plugin
     *                           configuration, or null if not specified
     * @param to                 The "to" parameter specified in the plugin
     *                           configuration, or null if not specified
     * @param pluginListedEsas   The list of ESAs specified in the plugin
     *                           configuration, or null if not specified
     * @param propertiesList     The list of product properties installed with the
     *                           Open Liberty runtime
     * @param openLibertyVersion The version of the Open Liberty runtime
     * @param containerName      The container name if the features should be
     *                           installed in a container. Otherwise null.
     * @param additionalJsons	 The list of additional JSONS to search
     * 							 for features from
     * @param verifyValue        The verify option value
     * @param keyMap             The key map for feature verification
     * @param environmentVariables Environment variables to pass to processes (e.g., JAVA_HOME)
     * @throws PluginScenarioException  If the current scenario is not supported
     * @throws PluginExecutionException If properties files cannot be found in the
     *                                  installDirectory/lib/versions
     */
    public InstallFeatureUtil(File installDirectory, File buildDirectory, String from, String to, Set<String> pluginListedEsas, List<ProductProperties> propertiesList, String openLibertyVersion, String containerName, List<String> additionalJsons, String verifyValue, Collection<Map<String, String>> keyMap, Map<String, String> environmentVariables) throws PluginScenarioException, PluginExecutionException {
        this.installDirectory = installDirectory;
        this.buildDirectory = buildDirectory;
        this.to = to;
        this.propertiesList = propertiesList;
        this.openLibertyVersion = openLibertyVersion;
        this.containerName = containerName;
        this.additionalJsons = additionalJsons;
        this.pluginListedEsas = pluginListedEsas;
        this.manuallyInstalledUsrFeatureMap = new HashMap<String, String>();
        
        if(keyMap == null) {
            this.keyMap = new ArrayList<>();
        }else {
            this.keyMap = keyMap;
        }

        this.environmentVariables = environmentVariables == null ? new HashMap<>() : environmentVariables;

        try {
          this.verifyOption = VerifyOption.valueOf(verifyValue);
	} catch (IllegalArgumentException e) {
	    throw new PluginExecutionException("The "+ verifyValue + " verify option isn't valid. Specify one of the following valid options: enforce, warn, skip, all");
	}
        
        if (containerName == null) {
            installJarFile = loadInstallJarFile(installDirectory);
            if (installJarFile == null) {
                throw new PluginScenarioException("Install map jar not found.");
            }
            downloadedJsons = downloadProductJsons();
            
            //check if the openliberty kernel meets min required version 21.0.0.11      	        		
            if (additionalJsons != null && !additionalJsons.isEmpty() && openLibertyVersion != null) {
                if (VersionUtility.compareArtifactVersion(openLibertyVersion, MIN_USER_FEATURE_VERSION, true) >= 0) {
            		Set<File> groupIDJsons = getAdditionalJsons();
                    if (groupIDJsons != null) {
                        downloadedJsons.addAll(groupIDJsons);
                    }
            	}
            }
            
            if (downloadedJsons.isEmpty()) {
                throw new PluginScenarioException(
                        "Cannot find JSONs for to the installed runtime from the Maven repository.");
            }
            if (from != null) {
            	debug("has from: " + from);
                throw new PluginScenarioException(
                        "Cannot install features from a Maven repository when using 'from' parameter.");

            }
        }
    }



	private void copyZipEntry(ZipFile zip, ZipEntry entry, File targetFile) throws IOException, FileNotFoundException {
		try(InputStream is = zip.getInputStream(entry)){	
			// write file content
		    try(FileOutputStream fos = new FileOutputStream(targetFile)){
		    	int len;
		    	byte[] buffer = new byte[1024];
		        while ((len = is.read(buffer)) > 0) {
		            fos.write(buffer, 0, len);
		        }
		    }
		}
	}
    

    /**
     * Retrieve additional JSONS from the coordinates provided
     * 
     * @return a set of additional JSON files
     */
    private Set<File> getAdditionalJsons() {
        Set<File> Jsons = new HashSet<File>();
        for (String mavenCoord: additionalJsons) {
        	String[] coord = mavenCoord.split(":");
			String groupId = coord[0];
			String artifactId = coord[1];
			String version = coord[2];
			try {
				File additionalJson = downloadArtifact(groupId, artifactId, "json", version);
				Jsons.add(additionalJson);
			} catch (PluginExecutionException e) {
                warn("Unable to find the following additional features JSON in the connected repositories: " + mavenCoord + ". Please ignore this warning if this is not a user feature.");
                debug("Unable to find additional features JSON: ", e);
            }
        }

        return Jsons;
    }

    /**
     * Log debug
     * 
     * @param msg
     */
    public abstract void debug(String msg);

    /**
     * Log debug
     * 
     * @param msg
     * @param e
     */
    public abstract void debug(String msg, Throwable e);

    /**
     * Log debug
     * 
     * @param e
     */
    public abstract void debug(Throwable e);

    /**
     * Log warning
     * 
     * @param msg
     */
    public abstract void warn(String msg);

    /**
     * Log info
     * 
     * @param msg
     */
    public abstract void info(String msg);

    /**
     * Log error
     * 
     * @param msg
     */
    public abstract void error(String msg);

    /**
     * Log error
     * 
     * @param msg
     * @param e
     */
    public abstract void error(String msg, Throwable e);

    /**
     * Check if any of the logged errors contain the given string.
     * Default does nothing - 
     * Override this method to use as needed.
     * 
     * @param msg
     * @return
     */
    public boolean containsErrorMessage(String msg) {
        return false;
    }

    /**
     * Returns whether debug is enabled by the current logger
     * 
     * @return whether debug is enabled
     */
    public abstract boolean isDebugEnabled();

    /**
     * Download the artifact from the specified Maven coordinates, or retrieve it
     * from the cache if it already exists.
     * 
     * @param groupId    The group ID
     * @param artifactId The artifact ID
     * @param type       The type e.g. esa
     * @param version    The version
     * @return The file corresponding to the downloaded artifact
     * @throws PluginExecutionException If the artifact could not be downloaded
     */
    public abstract File downloadArtifact(String groupId, String artifactId, String type, String version)
            throws PluginExecutionException;
    /**
     * Download the signature file from the specified Maven coordinates, or retrieve it
     * from the cache if it already exists. This is same as downloadArtifact method, but for Gradle 
     * the signature file need to be copied to ESA file directory to be verified. 
     * 
     * @param esa    	 The ESA file for the signature
     * @param groupId    The group ID
     * @param artifactId The artifact ID
     * @param type       The type e.g. esa
     * @param version    The version
     * @return The file corresponding to the downloaded artifact
     * @throws PluginExecutionException If the artifact could not be downloaded
     */
    public abstract File downloadSignature(File esa, String groupId, String artifactId, String type, String version) 
	    throws PluginExecutionException;

    /**
     * Combine the given String collections into a set using case-insensitive
     * matching. If there are multiple instances of the same string but with
     * different capitalization, only the first one found will be included.
     * 
     * @param collections a collection of strings
     * @return the combined set of strings, ignoring case
     */
    public Set<String> combineToSet(Collection<String>... collections) {
        Set<String> result = new HashSet<String>();
        Set<String> lowercaseSet = new HashSet<String>();
        for (Collection<String> collection : collections) {
            if (collection != null) {
                for (String value : collection) {
                    if (!lowercaseSet.contains(value.toLowerCase())) {
                        lowercaseSet.add(value.toLowerCase());
                        // if the value is empty or blank, issue a warning and do not add it to the list of features to install
                        if (value.isEmpty() || value.trim().isEmpty()) {
                            warn("An empty feature was specified in a server configuration file. Ensure that the features are valid.");
                        } else {
                            result.add(value);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Get the JSON files corresponding to the product properties from the
     * lib/versions/*.properties files
     * 
     * @return the set of JSON files for the product
     * @throws PluginExecutionException if properties files could not be found from
     *                                  lib/versions
     */
    private Set<File> downloadProductJsons() throws PluginExecutionException {
        // download JSONs
        Set<File> downloadedJsons = new HashSet<File>();
        for (ProductProperties properties : propertiesList) {
            File json = downloadJsons(properties.getId(), properties.getVersion());
            if (json != null) {
                downloadedJsons.add(json);
            }
        }
        return downloadedJsons;
    }

    /**
     * Download the JSON file for the given product.
     * 
     * @param productId      The product ID from the runtime's properties file
     * @param productVersion The product version from the runtime's properties file
     * @return The JSON file, or null if not found
     */
    private File downloadJsons(String productId, String productVersion) {
        String jsonGroupId = productId + ".features";
        try {
            return downloadArtifact(jsonGroupId, "features", "json", productVersion);
        } catch (PluginExecutionException e) {
            debug("Cannot find json for productId " + productId + ", productVersion " + productVersion, e);
            return null;
        }
    }
    
    public static List<ProductProperties> loadProperties(File installDir) throws PluginExecutionException {
        List<ProductProperties> list = new ArrayList<ProductProperties>();
        File dir = new File(installDir, "lib/versions");

        File[] propertiesFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".properties");
            }
        });

        if (propertiesFiles != null) {
            for (File propertiesFile : propertiesFiles) {
                Properties properties = new Properties();
                InputStream input = null;
                try {
                    input = new FileInputStream(propertiesFile);
                    properties.load(input);
                    String productId = properties.getProperty("com.ibm.websphere.productId");
                    String productVersion = properties.getProperty("com.ibm.websphere.productVersion");
                    if (productId == null) {
                        throw new PluginExecutionException(
                                "Cannot find the \"com.ibm.websphere.productId\" property in the file "
                                        + propertiesFile.getAbsolutePath()
                                        + ". Ensure the file is valid properties file for the Liberty product or extension.");
                    }
                    if (productVersion == null) {
                        throw new PluginExecutionException(
                                "Cannot find the \"com.ibm.websphere.productVersion\" property in the file "
                                        + propertiesFile.getAbsolutePath()
                                        + ". Ensure the file is valid properties file for the Liberty product or extension.");
                    }
                    list.add(new InstallFeatureUtil.ProductProperties(productId, productVersion));
                } catch (IOException e) {
                    throw new PluginExecutionException(
                            "Cannot read the product properties file " + propertiesFile.getAbsolutePath(), e);
                } finally {
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            throw new PluginExecutionException("Could not find any properties file in the " + dir
                    + " directory. Ensure the directory " + installDir + " contains a Liberty installation.");
        }

        return list;
    }

    public static String getOpenLibertyVersion(List<ProductProperties> propList) {
        for (ProductProperties properties : propList) {
            if (properties.getId().equals(OPEN_LIBERTY_PRODUCT_ID)) {
                return properties.getVersion();
            }
        }
        return null;
    }
    
    public static boolean isClosedLiberty(List<ProductProperties> propList) {
    	for (ProductProperties properties : propList) {
            if (properties.getId().equals(CLOSED_LIBERTY_PRODUCT_ID)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOpenLibertyBetaVersion(String olVersion) {
        if (olVersion != null && olVersion.endsWith("-beta")) {
            return true;
        }
        return false;
    }

    public static class ProductProperties {
        private String id;
        private String version;

        public ProductProperties(String id, String version) {
            this.id = id;
            this.version = version;
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }
    }

    private File downloadEsaArtifact(String mavenCoordinates) throws PluginExecutionException {
        String[] mavenCoordinateArray = mavenCoordinates.split(":");
        String groupId = mavenCoordinateArray[0];
        String artifactId = mavenCoordinateArray[1];
        String version = mavenCoordinateArray[2];
        File downloadedEsa = downloadArtifact(groupId, artifactId, "esa", version);
        if(this.verifyOption != VerifyOption.skip) {
            //download signature file for this esa
            try {
        	downloadSignature(downloadedEsa, groupId, artifactId, "esa.asc", version);
            }catch(PluginExecutionException e) {
        	if(this.verifyOption == VerifyOption.all) {
        	    //At this point, we don't know if the download failed for the Liberty feature or the user feature. 
        	    //Only throw exception for VerifyOption.all. 
        	    throw e;
        	}else {
        	    warn("Signature at coordinates " + mavenCoordinates + " could not be downloaded." + e.getMessage());
        	}
            }
            
        }
        return downloadedEsa;
    }

    private Map<File, String> downloadEsas(Collection<?> mavenCoordsList, Map<String, String> artifactIdToExt) throws PluginExecutionException {
        Map<File, String> featurepathToExt = new HashMap<File, String>();
        for (Object coordinate : mavenCoordsList) {
        	String[] coordinateArray = ((String) coordinate).split(":");
        	String artifactId = coordinateArray[1];
        	
        	featurepathToExt.put((downloadEsaArtifact((String) coordinate)), artifactIdToExt.get(artifactId));
        }
        return featurepathToExt;
    }

    /**
     * Gets the set of all Open Liberty features by scanning the product JSONs.
     * 
     * @param jsons The set of product JSON files to scan
     * @return set of all Open Liberty features
     * @throws PluginExecutionException if any of the JSONs could not be found
     */
    public static Set<String> getOpenLibertyFeatureSet(Set<File> jsons) throws PluginExecutionException {
        Set<String> libertyFeatures = new HashSet<String>();
        for (File file : jsons) {
            Scanner s = null;
            try {
                s = new Scanner(file);
                // scan Maven coordinates for artifactIds that belong to the Open Liberty
                // groupId
                while (s.findWithinHorizon(OPEN_LIBERTY_GROUP_ID + ":([^:]*):", 0) != null) {
                    MatchResult match = s.match();
                    if (match.groupCount() >= 1) {
                        libertyFeatures.add(match.group(1));
                    }
                }
            } catch (FileNotFoundException e) {
                throw new PluginExecutionException("The JSON file is not found at " + file.getAbsolutePath(), e);
            } finally {
                if (s != null) {
                    s.close();
                }
            }
        }
        return libertyFeatures;
    }

    /**
     * Returns true if all features in featuresToInstall are Open Liberty features.
     * 
     * @param featuresToInstall list of features to check
     * @return true if featureToInstall has only Open Liberty features
     * @throws PluginExecutionException if any of the downloaded JSONs could not be
     *                                  found
     */
    private boolean isOnlyOpenLibertyFeatures(List<String> featuresToInstall) throws PluginExecutionException {
        boolean result = containsIgnoreCase(getOpenLibertyFeatureSet(downloadedJsons), featuresToInstall);
        debug("Is installing only Open Liberty features? " + result);
        return result;
    }

    /**
     * Returns whether the reference collection contains all of the strings in the
     * target collection, ignoring case.
     * 
     * @param reference The reference collection
     * @param target    The target collection
     * @return true if reference contains all Strings from target, ignoring case
     */
    public static boolean containsIgnoreCase(Collection<String> reference, Collection<String> target) {
        return toLowerCase(reference).containsAll(toLowerCase(target));
    }

    private static Set<String> toLowerCase(Collection<String> strings) {
        Set<String> result = new HashSet<String>(strings.size());
        for (String s : strings) {
            result.add(s.toLowerCase());
        }
        return result;
    }
    
    /*
     * @param pluginListedEsas plugin listed esa file paths for feature install
     * 	manifest files will be copied to  ${wlp.user.dir}/extension/lib/features
     * 	OSGi bundles will be copied to ${wlp.user.dir}/extension/lib
     */
	public void copyUserFeature(Set<String> pluginListedEsas, File installDirectory ) throws PluginExecutionException {
		File userExtDirectory = getUserExtensionPath() != null ? getUserExtensionPath() : new File(installDirectory, "usr/extension");
		File libDirectory = new File (userExtDirectory, "lib");
		File featuresDirectory = new File(libDirectory, "features");
		libDirectory.mkdirs();
		featuresDirectory.mkdirs();		  
		
		for(String esa : pluginListedEsas) {
			debug("Copying " + esa + " to Liberty image.");
			try (ZipFile zip = new ZipFile(esa)){	
				Enumeration<? extends ZipEntry> zipEntries = zip.entries();
				
				while (zipEntries.hasMoreElements()) {
					ZipEntry entry = zipEntries.nextElement();	   
		            String fileName = entry.getName();
		            if (fileName.toLowerCase().endsWith("subsystem.mf")) {
		            	Manifest m = new Manifest(zip.getInputStream(entry));
		            	String symbolicName = m.getMainAttributes().getValue("Subsystem-SymbolicName").split(";")[0];	            	
		            	String shortName = m.getMainAttributes().getValue("IBM-ShortName");
		            	
		            	if(shortName != null) {
		            		manuallyInstalledUsrFeatureMap.put(symbolicName.toLowerCase(), shortName.toLowerCase());
		            	} else {
		            		manuallyInstalledUsrFeatureMap.put(symbolicName.toLowerCase(), "");
		            	}
		            	File targetFile = new File(featuresDirectory, symbolicName + ".mf");
		            	if(targetFile.exists()) {
		            		info("The feature " + esa + " is already installed.");
		            		break;
		            	}
		            	copyZipEntry(zip, entry, targetFile);
		            	
		            }else if(fileName.toLowerCase().endsWith(".jar")){
		            	File targetFile = new File(libDirectory, fileName);
		            	copyZipEntry(zip, entry, targetFile);	
		            }
		        }
				

			} catch (IOException e) {
				throw new PluginExecutionException(e);
			}		
		}	
	}

    /**
     * Resolve, download, and install features from a Maven repository. This method
     * calls the resolver with the given JSONs and feature list, downloads the ESAs
     * corresponding to the resolved features, then installs those features.
     * 
     * If user listed feature esa and installUtility/featureUtility is not available 
     * to install user feature esa, then copy esa feature manually to Liberty install. 
     * 
     * @param isAcceptLicense  flag for accepting the license terms
     * @param featuresList The list of features to install.
     * @throws PluginExecutionException if any of the features could not be
     *                                  installed
     */
    @SuppressWarnings("unchecked")
    public void installFeatures(boolean isAcceptLicense, List<String> featuresList, List<String> platformsList)
            throws PluginExecutionException {
    	
    	Map<String, String> featureToExtMap = new HashMap<String, String>();
    	List<String> featuresToInstall = new ArrayList<String>();
    	
    	if(openLibertyVersion != null) {
    		info("plugin listed esa: " + pluginListedEsas.toString());
            if ((VersionUtility.compareArtifactVersion(openLibertyVersion, MIN_USER_FEATURE_VERSION, true) < 0) && !pluginListedEsas.isEmpty()) {
    			//manually install user feature esas
    			info("Neither InstallUtility nor FeatureUtility is available to install user feature esa.");
    			info("Attempting to manually install the user feature esa without resolving its dependencies.");
    			info("Recommended user action: upgrade to OpenLiberty version " + MIN_USER_FEATURE_VERSION + " or higher and provide features-bom file for the user feature esa.");
    			
    			copyUserFeature(pluginListedEsas, installDirectory);
            } else {
    		    featuresToInstall.addAll(pluginListedEsas);
            }
    	}
    		   	
        boolean containsVersionlessFeature = false;
    	for (String feature: featuresList) {
    		if (feature.contains(":")) {
    			String[] userFeatureSplit = feature.split(":");
    			String userFeatureName = userFeatureSplit[1].toLowerCase();
    			featureToExtMap.put(userFeatureName, userFeatureSplit[0]);
    			//check if user feature esa was installed manually. userFeatureName could be either symbolic name or short name of the user feature. 
    			if(!manuallyInstalledUsrFeatureMap.containsValue(userFeatureName) && !manuallyInstalledUsrFeatureMap.containsKey(userFeatureName)) {
    				featuresToInstall.add(userFeatureName);
    			}
    		} else {
                if (!feature.contains("-")) {
                    containsVersionlessFeature = true;
                }
    			featureToExtMap.put(feature, "");
    			featuresToInstall.add(feature);
    		}
    	}

        if ((openLibertyVersion != null) && (!platformsList.isEmpty() || containsVersionlessFeature)) {
            if (VersionUtility.compareArtifactVersion(openLibertyVersion, MIN_VERSIONLESS_FEATURE_VERSION, true) < 0) {
                if (!platformsList.isEmpty()) {
                    String message = "Detected versionless feature(s) for installation. The minimum required Liberty version for versionless feature support is " + MIN_VERSIONLESS_FEATURE_VERSION;
                    error(message);
                    throw new PluginExecutionException(message);
                } else {
                    warn("Detected possible versionless feature(s) for installation. The minimum required Liberty version for versionless feature support is " + MIN_VERSIONLESS_FEATURE_VERSION);
                }
            }
        }
    	    		
    	if(featuresToInstall.isEmpty()) {
    	    debug("featuresToInstall is empty");
    		return;
    	}
    	
        if (containerName != null) {
            installFeaturesOnContainer(featuresToInstall, isAcceptLicense, verifyOption);
            return;
        }
        
        List<File> jsonRepos = new ArrayList<File>(downloadedJsons);
        debug("JSON repos: " + jsonRepos);

        // override license acceptance if installing only Open Liberty features
        boolean acceptLicenseMapValue = isOnlyOpenLibertyFeatures(featuresToInstall) ? true : isAcceptLicense;

        URL installJarURL = null;
        try {
            installJarURL = installJarFile.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new PluginExecutionException("Could not resolve URL from file " + installJarFile, e);
        }

        disableCacheInURLClassLoader();
        try {
            String bundle = getOverrideBundleDescriptor(OPEN_LIBERTY_GROUP_ID, REPOSITORY_RESOLVER_ARTIFACT_ID);
	        mapBasedInstallKernel = createMapBasedInstallKernelInstance(bundle, installDirectory);
	    
	    
	        Collection<?> resolvedFeatures = resolveFeatures(featuresToInstall, platformsList,jsonRepos, acceptLicenseMapValue, pluginListedEsas);
	        if(resolvedFeatures == null || resolvedFeatures.isEmpty()) {
		        return;
	        }
	    
            Map<File, String> artifactsToExt = downloadEsas(resolvedFeatures, featureToExtMap);
            Set<File> artifacts = artifactsToExt.keySet();
            
            
            if (verifyOption != null && verifyOption != VerifyOption.skip) {
		        verifyFeatures(artifacts, installJarURL);
            }
            

            info("Installing features: " + featuresToInstall);
            StringBuilder installedFeaturesBuilder = new StringBuilder();
            Collection<String> actionReturnResult = new ArrayList<String>();
            for (File esaFile : artifacts) {
                mapBasedInstallKernel.put("license.accept", acceptLicenseMapValue);
                mapBasedInstallKernel.put("action.install", esaFile);
                String ext = artifactsToExt.get(esaFile);
                mapBasedInstallKernel.put("to.extension", TO_USER);
                
                if (ext!= null && !ext.equals("") && to != null) {
                	warn("The product extension location \""+ext+"\" specified in the server.xml file overrides the to extension \""+to+"\" specified in the build file.");
                }
                if (ext != null && !ext.equals("")) {
                	mapBasedInstallKernel.put("to.extension", ext);
                	debug("Installing to extension from server.xml: " + ext);
                } else if (to != null) {
                    mapBasedInstallKernel.put("to.extension", to);
                    debug("Installing to extension: " + to);
                }
                
                Integer ac = (Integer) mapBasedInstallKernel.get("action.result");
                debug("action.result: " + ac);
                debug("action.error.message: " + mapBasedInstallKernel.get("action.error.message"));
                if (mapBasedInstallKernel.get("action.error.message") != null) {
                    debug("action.exception.stacktrace: " + mapBasedInstallKernel.get("action.exception.stacktrace"));
                    String exceptionMessage = (String) mapBasedInstallKernel.get("action.error.message");
                    debug(exceptionMessage);
                    throw new PluginExecutionException(exceptionMessage);
                } else if (mapBasedInstallKernel.get("action.install.result") != null) {
                    actionReturnResult.addAll((Collection<String>) mapBasedInstallKernel.get("action.install.result"));
                }
            }
            for (String installResult : actionReturnResult) {
                installedFeaturesBuilder.append(installResult).append(" ");
            }
            productInfoValidate();
            info("The following features have been installed: " + installedFeaturesBuilder.toString());

            // Look for and remove .libertyls folder (Liberty Tools) to cause regeneration of the schema
            File schemaGenDir = new File(buildDirectory, ".libertyls");
            if (schemaGenDir.exists() && schemaGenDir.isDirectory()) {
                try {
                    FileUtils.deleteDirectory(schemaGenDir);
                    debug("Deleted .libertyls directory after installing features.");
                } catch (IOException e) {
                    debug("Could not delete .libertyls directory after installing features.");
                }
            }
        } catch (PrivilegedActionException e) {
            throw new PluginExecutionException("Could not load the jar " + installJarFile.getAbsolutePath(), e);
        } catch (IOException e) {
            throw new PluginExecutionException("Could not close the jar " + installJarFile.getAbsolutePath() + " after installing features.", e);
        } finally {
            if (mapBasedInstallKernel != null) {
                try {
                    mapBasedInstallKernel.clear();
                } catch (UnsupportedOperationException e) {
                    debug("This version of the install map does not support the clear operation.", e);
                } catch (RuntimeException e) {
                    throw new PluginExecutionException("Could not close resources after installing features.", e);
                }
            }
            restoreCacheInURLClassLoader();
        }
    }



    /**
     * @param featuresToInstall
     * @param jsonRepos
     * @param acceptLicenseMapValue
     * @return Collection of resolved features
     * @throws PluginExecutionException
     */
    private Collection<?> resolveFeatures(List<String> featuresToInstall,List<String> platforms, List<File> jsonRepos,
	    boolean acceptLicenseMapValue, Set<String> localESA) throws PluginExecutionException {

        if (!platforms.isEmpty()) {
            info("Resolving features: " + featuresToInstall + " using platforms: " + platforms);
        } else {
            info("Resolving features: " + featuresToInstall);
        }

        mapBasedInstallKernel.put("install.local.esa", true);
        mapBasedInstallKernel.put("single.json.file", jsonRepos);
        mapBasedInstallKernel.put("features.to.resolve", featuresToInstall);
        mapBasedInstallKernel.put("platforms", platforms);
        mapBasedInstallKernel.put("license.accept", acceptLicenseMapValue);
        mapBasedInstallKernel.put("is.install.server.feature", true);
        if(!localESA.isEmpty()) {
            mapBasedInstallKernel.put("install.individual.esas", true);
            mapBasedInstallKernel.put("individual.esas", localESA.stream().map(File::new).collect(Collectors.toList()));
        }

        Collection<?> resolvedFeatures = (Collection<?>) mapBasedInstallKernel.get("action.result");
        if (resolvedFeatures == null) {
            debug("action.exception.stacktrace: " + mapBasedInstallKernel.get("action.exception.stacktrace"));
            String exceptionMessage = (String) mapBasedInstallKernel.get("action.error.message");
            throw new PluginExecutionException(exceptionMessage);
        } else if (resolvedFeatures.isEmpty()) {
            debug("action.exception.stacktrace: " + mapBasedInstallKernel.get("action.exception.stacktrace"));
            String exceptionMessage = (String) mapBasedInstallKernel.get("action.error.message");
            if (exceptionMessage == null) {
                debug("resolvedFeatures was empty but the install kernel did not issue any messages");
                info("The features are already installed, so no action is needed.");
                return resolvedFeatures;
            } else if (exceptionMessage.contains("CWWKF1250I")) {
                info(exceptionMessage);
                info("The features are already installed, so no action is needed.");
                return resolvedFeatures;
            } else {
                if (isFeatureConflict(exceptionMessage)) {
                    throw new PluginExecutionException(
                        CONFLICT_MESSAGE + featuresToInstall
                                    + ": " + exceptionMessage);
                }
                throw new PluginExecutionException(exceptionMessage);
            }
        }
        return resolvedFeatures;
    }

    // Attempt to disable connection caching in the URLClassLoader so that the jar files will
    // all close when we close the class loader. Use reflection because this is not supported
    // in Java 8. Save the current value to restore it later for performance reasons.
    private synchronized void disableCacheInURLClassLoader() {
        try {
            if (saveURLCacheStatus == null) {
                Method getDefaultCaching = java.net.URLConnection.class.getMethod("getDefaultUseCaches", String.class);
                saveURLCacheStatus = Boolean.valueOf((boolean) getDefaultCaching.invoke(null, "jar")); // null = static method
                Method disableCaching = java.net.URLConnection.class.getMethod("setDefaultUseCaches", String.class, boolean.class);
                disableCaching.invoke(null, "jar", false); // null = static method, false = do not cache
            }
        } catch (NoSuchMethodException e) {  // ignore the exception in Java 8.
            debug("NoSuchMethodException trying to invoke java.net.URLConnection.setDefaultUseCaches(S,b) in disable");
        } catch (Exception e) {  // warn if some other exception occurred
            warn("Could not disable caching for URLConnection: " + e.getMessage());
            debug("Exception trying to invoke java.net.URLConnection.setDefaultUseCaches(S,b) in disable", e);
        }
    }

    // Attempt to restore the connection caching value in the URLClassLoader so that the jar files
    // will be cached or not as previously set. Use reflection because this is not supported
    // in Java 8.
    private synchronized void restoreCacheInURLClassLoader() {
        try {
            if (saveURLCacheStatus != null) {
                Method disableCaching = java.net.URLConnection.class.getMethod("setDefaultUseCaches", String.class, boolean.class);
                disableCaching.invoke(null, "jar", saveURLCacheStatus.booleanValue()); // null = static method
            }
        } catch (NoSuchMethodException e) {  // ignore the exception in Java 8.
            debug("NoSuchMethodException trying to invoke java.net.URLConnection.setDefaultUseCaches(S,b) in restore");
        } catch (Exception e) {  // warn if some other exception occurred
            warn("Could not enable caching for URLConnection: " + e.getMessage());
            debug("Exception trying to invoke java.net.URLConnection.setDefaultUseCaches(S,b) in restore", e);
        } finally {
            saveURLCacheStatus = null;
        }
    }
    
    private File downloadOverrideJar(String groupId, String artifactId) {
        try {
            return downloadArtifact(groupId, artifactId, "jar",
                    String.format("[%s)", openLibertyVersion + ", " + getNextProductVersion(openLibertyVersion)));
        } catch (PluginExecutionException e) {
            debug("Using jar from Liberty directory for " + artifactId + " bundle.");
            return null;
        }
    }
    
    private File loadInstallJarFile(File installDirectory) {
	if(installJarFile == null) {
	    if (openLibertyVersion != null) {
		File installJarOverride = downloadOverrideJar(OPEN_LIBERTY_GROUP_ID, INSTALL_MAP_ARTIFACT_ID);
		if (installJarOverride != null && installJarOverride.exists()) {
		    installJarFile = installJarOverride;
		} else {
		    installJarFile = getMapBasedInstallKernelJar(new File(installDirectory, "lib"), INSTALL_MAP_PREFIX,
			    JAR_EXT);
		}
	    } else {
		installJarFile = getMapBasedInstallKernelJar(new File(installDirectory, "lib"), INSTALL_MAP_PREFIX,
			JAR_EXT);
	    }
	}
   	
   	return installJarFile;
    }

    /**
     * Download the override bundle from the repository with the given groupId and
     * artifactId, corresponding to the latest version in the range between the
     * current Open Liberty version (inclusive) and the next version
     * (exclusive). Returns a string in the format "filepath;BundleName" where
     * BundleName is the bundle symbolic name from its manifest.
     *
     * @param groupId
     *            the groupId of the bundle to download
     * @param artifactId
     *            the artifactId of the bundle to download
     * @return a String representing the bundle in filepath;BundleName format
     */
    public String getOverrideBundleDescriptor(String groupId, String artifactId) throws PluginExecutionException {
        File overrideJar = downloadOverrideJar(groupId, artifactId);
        if (overrideJar != null && overrideJar.exists()) {
            String symbolicName = extractSymbolicName(overrideJar);
            if (symbolicName != null) {
                return overrideJar.getAbsolutePath() + ";" + symbolicName;
            }
        }
        return null;
    }

    

    /**
     * Gets the next product version number.
     * 
     * @param version
     *            the product version
     * @return the String representation of the next product version
     */
    public static String getNextProductVersion(String version) throws PluginExecutionException {
        String result = null;
        int versionSplittingIndex = version.lastIndexOf(".") + 1;
        if (versionSplittingIndex == 0) {
            throw new PluginExecutionException("Product version " + version
                    + " is not in the expected format. It must have period separated version segments.");
        }
        String quarterVersion = version.substring(versionSplittingIndex);
        int nextQuarterSpecifier;
        try {
            nextQuarterSpecifier = Integer.parseInt(quarterVersion) + 1;
        } catch (NumberFormatException e) {
            throw new PluginExecutionException("Product version " + version
                    + " is not in the expected format. Its last segment is expected to be an integer.", e);
        }
        result = version.substring(0, versionSplittingIndex) + nextQuarterSpecifier;
        return result;
    }

    /**
     * Extracts the bundle symbolic name from the jar manifest.
     * 
     * @param jar
     *            the jar from which the symbolic name will be extracted
     * @return the Bundle-SymbolicName
     */
    public static String extractSymbolicName(File jar) throws PluginExecutionException {
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(jar);
            return jarFile.getManifest().getMainAttributes().getValue("Bundle-SymbolicName");
        } catch (IOException e) {
            throw new PluginExecutionException("Could not load the jar " + jar.getAbsolutePath(), e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    // nothing to do here
                }
            }
        }
    }    /**
     * Find latest install map jar from specified directory
     * 
     * @return the install map jar file
     */
    public static File getMapBasedInstallKernelJar(File dir, String prefix, String suffix) {

        File[] installMapJars = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(INSTALL_MAP_PREFIX) && name.endsWith(JAR_EXT);
            }
        });

        File result = null;
        if (installMapJars != null) {
            for (File jar : installMapJars) {
                if (isReplacementJar(result, jar)) {
                    result = jar;
                }
            }
        }

        return result;
    }
    
    /**
     * Returns whether file2 can replace file1 as the install map jar.
     *
     * @param file1
     * @param file2
     * @return true if file2 is a replacement jar for file1 false otherwise
     */
    private static boolean isReplacementJar(File file1, File file2) {
        if (file1 == null) {
            return true;
        } else if (file2 == null) {
            return false;
        } else {
            String version1 = extractVersion(file1.getName());
            String version2 = extractVersion(file2.getName());
            return compare(version1, version2) < 0;
        }
    }
    
    /**
     * Returns the extracted version from fileName
     *
     * @param fileName
     * @return the version extracted from fileName
     */
    private static String extractVersion(String fileName) {
        int startIndex = INSTALL_MAP_PREFIX.length() + 1; // skip the underscore after the prefix
        int endIndex = fileName.lastIndexOf(JAR_EXT);
        if (startIndex < endIndex) {
            return fileName.substring(startIndex, endIndex);
        } else {
            return null;
        }
    }

    /**
     * Performs pairwise comparison of version strings, including nulls and non-integer components.
     * @param version1
     * @param version2
     * @return positive if version1 is greater, negative if version2 is greater, otherwise 0
     */
    private static int compare(String version1, String version2) {
        if (version1 == null && version2 == null) {
            return 0;
        } else if (version1 == null && version2 != null) {
            return -1;
        } else if (version1 != null && version2 == null) {
            return 1;
        }
        String[] components1 = version1.split("\\.");
        String[] components2 = version2.split("\\.");
        for (int i = 0; i < components1.length && i < components2.length; i++) {
            int comparison;
            try {
                comparison = new Integer(components1[i]).compareTo(new Integer(components2[i]));
            } catch (NumberFormatException e) {
                comparison = components1[i].compareTo(components2[i]);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return components1.length - components2.length;
    }
    
    /**
     * Performs product validation by running bin/productInfo validate
     * 
     * @throws PluginExecutionException
     *             if product validation failed or could not be run
     */
    private void productInfoValidate() throws PluginExecutionException {
        if (environmentVariables != null && environmentVariables.containsKey("JAVA_HOME")) {
            info("Product validation is using toolchain JAVA_HOME: " + environmentVariables.get("JAVA_HOME"));
        }
        String output = productInfo(installDirectory, "validate", environmentVariables);
        if (output == null) {
            throw new PluginExecutionException(
                    "Could not perform product validation. The productInfo command returned with no output");
        } else if (output.contains("[ERROR]")) {
            throw new PluginExecutionException(output);
        } else {
            info("Product validation completed successfully.");
        }
    }
    
    /**
     * Runs the productInfo command and returns the output
     * Made public static for tests to use in LMP/LGP
     * 
     * @param installDirectory The directory of the installed runtime
     * @param action           The action to perform for the productInfo command
     * @return The command output
     * @throws PluginExecutionException if the exit value of the command was not 0
     */
    public static String productInfo(File installDirectory, String action) throws PluginExecutionException {
        return productInfo(installDirectory, action, null);
    }

    /**
     * Runs the productInfo command with environment variables and returns the output
     * Made public static for tests to use in LMP/LGP
     *
     * @param installDirectory The directory of the installed runtime
     * @param action           The action to perform for the productInfo command
     * @param environmentVariables          Environment variables to set (e.g., JAVA_HOME). Can be null.
     * @return The command output
     * @throws PluginExecutionException if the exit value of the command was not 0
     */
    public static String productInfo(File installDirectory, String action, Map<String, String> environmentVariables) throws PluginExecutionException {
        Process pr = null;
        BufferedReader in = null;
        StringBuilder sb = new StringBuilder();
        try {
            String productInfoFile;
            if (OSUtil.isWindows()) {
                // quote the entire productInfo command to guard against special characters like parentheses in the path
                productInfoFile = "\"" + installDirectory + "\\bin\\productInfo.bat\"";
            } else {
                productInfoFile = installDirectory + "/bin/productInfo";
            }

            ProcessBuilder pb = new ProcessBuilder(productInfoFile, action);
            Properties sysp = System.getProperties() != null ? System.getProperties() : new Properties();
            String javaHome = sysp.getProperty("java.home");
            if (javaHome != null) {
                pb.environment().put("JAVA_HOME", javaHome);
            }

            // Apply environment variables from toolchain if provided
            if (environmentVariables != null && !environmentVariables.isEmpty()) {
                pb.environment().putAll(environmentVariables);
            }

            pb.redirectErrorStream(true);
            pr = pb.start();

            in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }

            boolean exited = pr.waitFor(300, TimeUnit.SECONDS);
            if(!exited) { // Command did not exit in time
                throw new PluginExecutionException("productInfo command timed out");
            }

            int exitValue = pr.exitValue();
            if (exitValue != 0) {
                throw new PluginExecutionException("productInfo exited with return code " + exitValue +". The productInfo command run was `"+productInfoFile+" "+action+"`");
            }
            return sb.toString();
        } catch (IOException ex) {
            throw new PluginExecutionException("productInfo error: " + ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PluginExecutionException("productInfo error: " + ex);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            if (pr != null) {
                pr.destroy();
            }
        }
    }

    private void installFeaturesOnContainer(List<String> features, boolean acceptLicense, VerifyOption verifyOption) throws PluginExecutionException {
        if (features == null || features.isEmpty()) {
            debug("Skipping installing features on container " + containerName + " since no features were specified.");
            return;
        }

        info("Installing features " + features + " on container " + containerName);

        StringBuilder featureList = new StringBuilder();
        for (String feature : features) {
            featureList.append(feature).append(" ");
        }

        String featureUtilityCommand = getContainerCommandPrefix() + " exec -e FEATURE_LOCAL_REPO=/devmode-maven-cache " + containerName + " featureUtility installFeature " + featureList;
        if (acceptLicense) {
            featureUtilityCommand += " --acceptLicense";
        }
        

        if(verifyOption != null) {
            featureUtilityCommand += " --verify=" + verifyOption.name();
        }
        
        String cmdResult = execContainerCmd(featureUtilityCommand, 600, false);
        if (cmdResult.contains(" RC=")) { // This piece of the string is added in execDockerCmd if there is an error
            if (cmdResult.contains("CWWKF1250I")) {
                // The features are already installed message
                debug(cmdResult);
            } else {
                if (isFeatureConflict(cmdResult)) {
                    error("A feature conflict error occurred while installing features: " + features + ": "
                            + cmdResult);
                } else {
                    error("An error occurred while installing features: " + cmdResult);
                }
            }
        } else {
            // Log the successful output as debug
            debug(cmdResult);
        }
    }

    // Return true if feature conflict code is detected in the exception message
    private boolean isFeatureConflict(String exceptionMessage) {
        Matcher m = conflictPattern.matcher(exceptionMessage);
        return m.find();
    }

    
    /**
     * Verifies signatures(.asc) of downloaded artifacts(.esa), which is available from Liberty 23.0.0.9
     * @param artifacts downloaded artifacts  
     * @param installJarURL
     * @throws PluginExecutionException
     */
    public void verifyFeatures(Set<File> artifacts, URL installJarURL) throws PluginExecutionException {
	
	if (VersionUtility.compareArtifactVersion(openLibertyVersion, MIN_VERIFY_FEATURE_VERSION, true) < 0) {
	    warn("Skipping feature verification. Minimum required Liberty version is " + MIN_VERIFY_FEATURE_VERSION);
	    return;
	}
	
	downloadPublicKeys();
	
	info("Verifying features");
	mapBasedInstallKernel.put("action.verify", artifacts.stream().collect(Collectors.toList()));
	mapBasedInstallKernel.get("action.result");
	if (mapBasedInstallKernel.get("action.error.message") != null) {
	    // error with installation
	    if (mapBasedInstallKernel.get("action.exception.stacktrace") != null) {
		debug("action.exception.stacktrace: " + mapBasedInstallKernel.get("action.exception.stacktrace"));
	    }
	    throw new PluginExecutionException((String) mapBasedInstallKernel.get("action.error.message"));
	}

    }



    /**
     * Downloads public key to verify signatures
     * @throws PluginExecutionException
     */
    private void downloadPublicKeys() throws PluginExecutionException {
	info("Downloading public key(s) for signature verification");
	mapBasedInstallKernel.get("environment.variable.map");
	mapBasedInstallKernel.put("verify.option", verifyOption.name());
	mapBasedInstallKernel.put("user.public.keys", keyMap);
	mapBasedInstallKernel.get("download.pubkeys");

	if (mapBasedInstallKernel.get("action.error.message") != null) {
	if (mapBasedInstallKernel.get("action.exception.stacktrace") != null) {
	    debug("action.exception.stacktrace: " + mapBasedInstallKernel.get("action.exception.stacktrace"));
	}
	throw new PluginExecutionException((String) mapBasedInstallKernel.get("action.error.message"));
	}
    }
    
}
