/**
 * (C) Copyright IBM Corporation 2021, 2024.
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public abstract class PrepareFeatureUtil extends ServerFeatureUtil {

	private final File installDirectory;

	private String openLibertyVersion;

	public static final String OPEN_LIBERTY_GROUP_IDENTIFIER = "io.openliberty.features";
	public static final String INSTALL_MAP_ARTIFACT_IDENTIFIER = "install-map";
	public static final String FEATURES_JSON_ARTIFACT_IDENTIFIER = "features";
	private static final String MIN_FEATURE_VERSION = "21.0.0.11";
	private static final String INSTALL_MAP_PREFIX = "com.ibm.ws.install.map";
	private static final String JAR_EXT = ".jar";

	private File installJarFile;
	private File jsonFile;
	

	public PrepareFeatureUtil(File installDirectory, String openLibertyVersion)
			throws PluginScenarioException, PluginExecutionException {
		this.installDirectory = installDirectory;
		this.openLibertyVersion = openLibertyVersion;
		installJarFile = loadInstallJarFile(installDirectory);

		// check if the openliberty kernel meets min required version 21.0.0.11
		if (VersionUtility.compareArtifactVersion(openLibertyVersion, MIN_FEATURE_VERSION, true) < 0) {
			throw new PluginScenarioException(
					"Installing user features on Liberty version "+openLibertyVersion+" is not supported. The minimum required version of Liberty for installing user features is "+ MIN_FEATURE_VERSION +".");
		}
		if (installJarFile == null) {
			throw new PluginScenarioException("Install map jar not found.");
		}
	}

	public void prepareFeatures(List<String> featureBOMs) throws PluginExecutionException {
		Map<File, String> esaMap = new HashMap<File, String>();
		for (String BOMCoordinate : featureBOMs) {
			String[] coord = BOMCoordinate.split(":");
			String groupId = coord[0];
			String artifactId = coord[1];
			String version = coord[2];
			File additionalBOM = downloadArtifact(groupId, artifactId, "pom", version);
			esaMap.putAll(populateESAMap(additionalBOM));
			if(esaMap.isEmpty()) {
			    warn("The features.json could not be generated due to errors encountered while resolving the feature ESA file specified in feautres-bom file at coordinates " + groupId + ":" +artifactId + ":" +version);
			}else {
			    prepareFeature(groupId, artifactId, version, additionalBOM, esaMap);
			}
		}

	}
	
	private Map<File, String> populateESAMap(File additionalBOM) {
		Map<File, String> result = new HashMap<File, String>();
		try {	
		    result = downloadArtifactsFromBOM(additionalBOM);
		} catch (PluginExecutionException e) {
		    warn(e.getMessage());
		}
		
		return result;
	}

	/**
	 * generate JSON if not already present at the desired location
	 * 
	 * @param groupId
	 * @param artifactId
	 * @param version
	 * @throws PluginExecutionException
	 */
	private void prepareFeature(String groupId, String artifactId, String version, File additionalBOM, Map<File, String> esaMap) {
	    try {
            String repoLocation = parseRepositoryLocation(additionalBOM, groupId, artifactId, "pom", version);
            String targetJsonFile = createArtifactFilePath(repoLocation, groupId, FEATURES_JSON_ARTIFACT_IDENTIFIER, "json",
                version);
            File generatedJson = generateJson(targetJsonFile, esaMap);
            if (generatedJson.exists()) {
                jsonFile = generatedJson;
                provideJsonFileDependency(generatedJson, groupId, version);
                info("The features.json has been generated at the following location: " + generatedJson);
            }else {
                warn("The features.json could not be generated at the following location: " + generatedJson);
            }
	    } catch (PluginExecutionException e) {
            warn("Error: The features.json could not be generated.");
            warn(e.getMessage());
	    }
	}

	/**
	 * Download the Artifacts mentioned within the additionalBOM pom file.
	 * Required artifact properties are "groupId, artifactId, version and type".
	 * 
	 * @param additionalBOM The BOM file
	 * @return A map of Files to groupIds
	 * @throws PluginExecutionException throws error if unable to download the
	 *                                  artifacts
	 */
	private Map<File, String> downloadArtifactsFromBOM(File additionalBOM) throws PluginExecutionException {
	    Map<File, String> result = new HashMap<File, String>();
	    ArrayList<String> missing_tags = new ArrayList<>();
	    try {
            DocumentBuilder db = getDocumentBuilder();
            Document doc = db.parse(additionalBOM);
            doc.getDocumentElement().normalize();
            NodeList dependencyList = doc.getElementsByTagName("dependency");
            for (int itr = 0; itr < dependencyList.getLength(); itr++) {
                Node node = dependencyList.item(itr);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) node;
                
                if(eElement.getElementsByTagName("groupId").item(0) == null ) {
                    missing_tags.add("groupId");
                }
                if(eElement.getElementsByTagName("artifactId").item(0) == null ) {
                    missing_tags.add("artifactId ");
                }
                if(eElement.getElementsByTagName("type").item(0) == null ) {
                    missing_tags.add("type");
                }
                if(eElement.getElementsByTagName("version").item(0) == null ) {
                    missing_tags.add("version");
                }
                
                if(!missing_tags.isEmpty()) {
                    throw new PluginExecutionException("Error: "+ missing_tags.toString()  + " tag(s) not found in features-bom file " + additionalBOM);
                }
                
                String groupId = eElement.getElementsByTagName("groupId").item(0).getTextContent();
                String artifactId = eElement.getElementsByTagName("artifactId").item(0).getTextContent();
                String type = eElement.getElementsByTagName("type").item(0).getTextContent();
                String version = eElement.getElementsByTagName("version").item(0).getTextContent();
                
                File artifactFile = downloadArtifact(groupId, artifactId, type, version);
                result.put(artifactFile, groupId);
                }
            }
		} catch (SAXException | IOException e) {
		    throw new PluginExecutionException("Cannot read the features-bom file " + additionalBOM.getAbsolutePath() + ". " + e.getMessage());
		    
		} 
		return result;
	}

	/**
	 * Format the artifact file path
	 * 
	 * @param repoLocation The repository location
	 * @param groupId      The groupId
	 * @param artifactId   The artifactId
	 * @param fileType     The file type
	 * @param version      The version
	 * @return The formatted filepath for the given artifact in the repoLocation
	 *         repository
	 */
	private String createArtifactFilePath(String repoLocation, String groupId, String artifactId, String fileType,
			String version) {
		if (OSUtil.isWindows()) {
			groupId = groupId.replace(".", "\\");
			return String.format("%s%s\\%s\\%s\\%s-%s.%s", repoLocation, groupId, artifactId, version, artifactId, version,
					fileType);
		} else {
			groupId = groupId.replace(".", "/");
			return String.format("%s%s/%s/%s/%s-%s.%s", repoLocation, groupId, artifactId, version, artifactId, version,
					fileType);
		}
	}

	/**
	 * Parse the repository location when given a path to an artifact in the
	 * repository
	 * 
	 * @param fileFromRepo The file from the repository
	 * @param groupId      The groupdId
	 * @param artifactId   The artifactId
	 * @param fileType     The fileType
	 * @param version      The version
	 * @return The repository location of the fileFromRepo file
	 */
	private String parseRepositoryLocation(File fileFromRepo, String groupId, String artifactId, String fileType,
			String version) {
		String absFileFromRepo = fileFromRepo.getAbsolutePath();
		String fileSubString = "";
		if (OSUtil.isWindows()) {
			groupId = groupId.replace(".", "\\");
			fileSubString = String.format("%s\\%s\\%s\\%s-%s.%s", groupId, artifactId, version, artifactId, version,
					fileType);
		}  else {
			groupId = groupId.replace(".", "/");
			fileSubString = String.format("%s/%s/%s/%s-%s.%s", groupId, artifactId, version, artifactId, version,
					fileType);
		}
		
		return absFileFromRepo.replace(fileSubString, "");
	}

	/**
	 * Generate JSON at targetJsonFile location for the list of ESAs in esaFiles
	 * under the given groupId
	 * 
	 * @param targetJsonFile The target file location for the resulting JSON
	 * @param esaFileMap     Map of esa Files to their groupIds
	 * @throws PluginExecutionException Throws an error if unable to generate JSON
	 */
	public File generateJson(String targetJsonFile, Map<File, String> esaFileMap) throws PluginExecutionException {
		try {
			Path targetDir = Files.createTempDirectory("generatedJson");
			URL installJarURL = null;
			try {
				installJarURL = installJarFile.toURI().toURL();
			} catch (MalformedURLException e) {
				throw new PluginExecutionException("Could not resolve URL from file " + installJarFile+"with error message "+ e.getMessage());
			}
			Map<String, Object> mapBasedInstallKernel = null;
			File json = null;
			List<File> esaFileList = new ArrayList<File>();
			esaFileList.addAll(esaFileMap.keySet());
			try (final URLClassLoader loader = new URLClassLoader(new URL[] { installJarURL },
					getClass().getClassLoader())) {
				mapBasedInstallKernel = createMapBasedInstallKernelInstance(loader, installDirectory);
				mapBasedInstallKernel.put("individual.esas", esaFileList);
				mapBasedInstallKernel.put("target.json.dir", targetDir.toFile());
				mapBasedInstallKernel.put("generate.json.group.id.map", esaFileMap);
				mapBasedInstallKernel.put("generate.json", true);
				json = (File) mapBasedInstallKernel.get("generate.json");
				
				if (mapBasedInstallKernel.get("action.error.message") != null) {
					debug("generateJson action.error.message: "
							+ mapBasedInstallKernel.get("action.error.message"));
					debug("generateJson action.exception.stacktrace: "
							+ mapBasedInstallKernel.get("action.exception.stacktrace"));
				}	

			} catch (PrivilegedActionException e) {
				debug(e);
				throw new PluginExecutionException("Could not load the jar " + installJarFile.getAbsolutePath(), e);
			}
			File targetFile = new File(targetJsonFile);
			targetFile.getParentFile().mkdirs();
			try(FileInputStream instream = new FileInputStream(json); FileOutputStream outstream = new FileOutputStream(targetFile)){
			    byte[] buffer = new byte[1024];
				int length;
				while ((length = instream.read(buffer)) > 0) {
					outstream.write(buffer, 0, length);
				}
			}
			return targetFile;
		} catch (IOException e) {
			debug(e);
			throw new PluginExecutionException("Cannot read or create json file " + targetJsonFile+" with error message "+ e.getMessage());
		} 
	}

	private File loadInstallJarFile(File installDirectory) {
		if (openLibertyVersion != null) {
			File installJarOverride = downloadOverrideJar(OPEN_LIBERTY_GROUP_IDENTIFIER, INSTALL_MAP_ARTIFACT_IDENTIFIER);
			if (installJarOverride != null && installJarOverride.exists()) {
				return installJarOverride;
			}
		}
		return InstallFeatureUtil.getMapBasedInstallKernelJar(new File(installDirectory, "lib"), INSTALL_MAP_PREFIX, JAR_EXT);
	}

	private File downloadOverrideJar(String groupId, String artifactId) {
		try {
			return downloadArtifact(groupId, artifactId, "jar", String.format("[%s)",
					openLibertyVersion + ", " + InstallFeatureUtil.getNextProductVersion(openLibertyVersion)));
		} catch (PluginExecutionException e) {
			debug("Could not find override bundle " + groupId + ":" + artifactId
					+ " for the current Open Liberty version " + openLibertyVersion + e.getMessage());
			return null;
		}
	}

	private Map<String, Object> createMapBasedInstallKernelInstance(final ClassLoader loader, File installDirectory)
			throws PrivilegedActionException, PluginExecutionException {
		Map<String, Object> mapBasedInstallKernel = AccessController
				.doPrivileged(new PrivilegedExceptionAction<Map<String, Object>>() {
					@SuppressWarnings({ "unchecked" })
					@Override
					public Map<String, Object> run() throws Exception {

						Class<Map<String, Object>> clazz;
						clazz = (Class<Map<String, Object>>) loader.loadClass("com.ibm.ws.install.map.InstallMap");
						return clazz.newInstance();
					}
				});
		if (mapBasedInstallKernel == null) {
			throw new PluginExecutionException("Cannot run install jar file " + installJarFile);
		}

		// Init
		String bundle = getOverrideBundleDescriptor(OPEN_LIBERTY_GROUP_IDENTIFIER, REPOSITORY_RESOLVER_ARTIFACT_IDENTIFIER);
		if (bundle != null) {
			List<String> bundles = new ArrayList<String>();
			bundles.add(bundle);
			debug("Overriding jar using: " + bundle);
			mapBasedInstallKernel.put("override.jar.bundles", bundles);
		}
		mapBasedInstallKernel.put("runtime.install.dir", installDirectory);
		try {
			mapBasedInstallKernel.put("install.map.jar.file", installJarFile);
			debug("install.map.jar.file: " + installJarFile);
		} catch (RuntimeException e) {
			debug("This version of the install map does not support the key \"install.map.jar.file\"", e);
			String installJarFileSubpath = installJarFile.getParentFile().getName() + File.separator
					+ installJarFile.getName();
			mapBasedInstallKernel.put("install.map.jar", installJarFileSubpath);
			debug("install.map.jar: " + installJarFileSubpath);
		}
		debug("install.kernel.init.code: " + mapBasedInstallKernel.get("install.kernel.init.code"));
		debug("install.kernel.init.error.message: " + mapBasedInstallKernel.get("install.kernel.init.error.message"));
		File usrDir = new File(installDirectory, "usr");
		mapBasedInstallKernel.put("target.user.directory", usrDir);
		return mapBasedInstallKernel;
	}

	/**
	 * Download the override bundle from the repository with the given groupId and
	 * artifactId, corresponding to the latest version in the range between the
	 * current Open Liberty version (inclusive) and the next version (exclusive).
	 * Returns a string in the format "filepath;BundleName" where BundleName is the
	 * bundle symbolic name from its manifest.
	 *
	 * @param groupId    the groupId of the bundle to download
	 * @param artifactId the artifactId of the bundle to download
	 * @return a String representing the bundle in filepath;BundleName format
	 */
	public String getOverrideBundleDescriptor(String groupId, String artifactId) throws PluginExecutionException {
		File overrideJar = downloadOverrideJar(groupId, artifactId);
		if (overrideJar != null && overrideJar.exists()) {
			String symbolicName = InstallFeatureUtil.extractSymbolicName(overrideJar);
			if (symbolicName != null) {
				return overrideJar.getAbsolutePath() + ";" + symbolicName;
			}
		}
		return null;
	}
	
	/**
	 * Provide the file dependency of the generated JSON file for Gradle plugin
	 * 
	 * @param file		 The Features JSON file
	 * @param groupId	 The groupId 
	 * @param version    The version
	 */
	public void provideJsonFileDependency(File file, String groupId, String version) {
		
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

	private DocumentBuilder getDocumentBuilder() throws PluginExecutionException {
		DocumentBuilder docBuilder;

		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		docBuilderFactory.setIgnoringComments(true);
		docBuilderFactory.setCoalescing(true);
		docBuilderFactory.setIgnoringElementContentWhitespace(true);
		docBuilderFactory.setValidating(false);
		try {
			docBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			docBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			docBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			docBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			docBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			docBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			docBuilderFactory.setXIncludeAware(false);
			docBuilderFactory.setNamespaceAware(true);
			docBuilderFactory.setExpandEntityReferences(false);
			docBuilder = docBuilderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			// fail catastrophically if we can't create a document builder
			throw new PluginExecutionException("Cannot read the features-bom file " + e.getMessage());
		}

		return docBuilder;
	}


}
