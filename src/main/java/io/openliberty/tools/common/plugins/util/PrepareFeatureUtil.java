/**
 * (C) Copyright IBM Corporation 2021.
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public abstract class PrepareFeatureUtil extends ServerFeatureUtil {

	private final File installDirectory;

	private String openLibertyVersion;

	public static final String OPEN_LIBERTY_GROUP_ID = "io.openliberty.features";
	public static final String INSTALL_MAP_ARTIFACT_ID = "install-map";
	public static final String FEATURES_JSON_ARTIFACT_ID = "features";
	private static final String MIN_USER_FEATURE_VERSION = "21.0.0.6";

	private File installJarFile;
	private File jsonFile;

	public PrepareFeatureUtil(File installDirectory, String openLibertyVersion)
			throws PluginScenarioException, PluginExecutionException {
		this.installDirectory = installDirectory;
		this.openLibertyVersion = openLibertyVersion;
		installJarFile = loadInstallJarFile(installDirectory);

		// check if the openliberty kernel meets min required version 21.0.0.6
		DefaultArtifactVersion minVersion = new DefaultArtifactVersion(MIN_USER_FEATURE_VERSION);
		DefaultArtifactVersion version = new DefaultArtifactVersion(openLibertyVersion);

		if (version.compareTo(minVersion) < 0) {
			throw new PluginScenarioException(
					"Installing user features on OpenLiberty version "+version+" is not supported. The minimum required version of OpenLiberty for installing user features is "+minVersion+".");
		}
		if (installJarFile == null) {
			throw new PluginScenarioException("Install map jar not found.");
		}
	}

	public void prepareFeatures(List<String> featureBOMs) throws PluginExecutionException {
		for (String BOMCoordinate : featureBOMs) {
			String[] coord = BOMCoordinate.split(":");
			String groupId = coord[0];
			String artifactId = coord[1];
			String version = coord[2];
			prepareFeature(groupId, artifactId, version);
		}

	}

	/**
	 * generate JSON if not already present at the desired location
	 * 
	 * @param groupId
	 * @param artifactId
	 * @param version
	 * @throws PluginExecutionException
	 */
	private void prepareFeature(String groupId, String artifactId, String version) throws PluginExecutionException {
		File json = null;
		try {
			json = downloadArtifact(groupId, FEATURES_JSON_ARTIFACT_ID, "json", version);
		} catch (PluginExecutionException e) {
			debug(e);
			info(String.format("The features.json file was not found at the expected coordinate %s:"+FEATURES_JSON_ARTIFACT_ID+":%s in connected repositories.", groupId,
					artifactId, version));
		}
		if (json != null) {
			info("The features.json already exists at the following location: " + json);
			jsonFile = json;
		} else {
			try {
				File additionalBOM = downloadArtifact(groupId, artifactId, "pom", version);
				String repoLocation = parseRepositoryLocation(additionalBOM, groupId, artifactId, "pom", version);
				String targetJsonFile = createArtifactFilePath(repoLocation, groupId, FEATURES_JSON_ARTIFACT_ID, "json",
						version);
				Map<File, String> esaFiles = downloadArtifactsFromBOM(additionalBOM);
				File generatedJson = generateJson(targetJsonFile, esaFiles);
				if (generatedJson.exists()) {
					jsonFile = generatedJson;
					info("The features.json has been generated at the following location: " + generatedJson);
				}
			} catch (PluginExecutionException e) {
				error(e.getMessage());
				warn("A features-bom file must be provided at the given groupId " + groupId + ".");
			}
		}
	}

	/**
	 * Download the Artifacts mentioned within the additionalBOM pom file
	 * 
	 * @param additionalBOM The BOM file
	 * @return A map of Files to groupIds
	 * @throws PluginExecutionException throws error if unable to download the
	 *                                  artifacts
	 */
	private Map<File, String> downloadArtifactsFromBOM(File additionalBOM) throws PluginExecutionException {
		Map<File, String> result = new HashMap<File, String>();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(additionalBOM);
			doc.getDocumentElement().normalize();
			NodeList dependencyList = doc.getElementsByTagName("dependency");
			for (int itr = 0; itr < dependencyList.getLength(); itr++) {
				Node node = dependencyList.item(itr);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) node;
					String groupId = eElement.getElementsByTagName("groupId").item(0).getTextContent();
					String artifactId = eElement.getElementsByTagName("artifactId").item(0).getTextContent();
					String version = eElement.getElementsByTagName("version").item(0).getTextContent();
					String type = eElement.getElementsByTagName("type").item(0).getTextContent();

					File artifactFile = downloadArtifact(groupId, artifactId, type, version);
					result.put(artifactFile, groupId);
				}
			}
		} catch (PluginExecutionException e) { // we were unable to download artifact mentioned in BOM
			throw e;
		} catch (Exception e) {
			throw new PluginExecutionException("Cannot read the BOM file " + additionalBOM.getAbsolutePath(), e);
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
		groupId = groupId.replace(".", "/");
		return String.format("%s%s/%s/%s/%s-%s.%s", repoLocation, groupId, artifactId, version, artifactId, version,
				fileType);
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
		groupId = groupId.replace(".", "/");
		String fileSubString = String.format("%s/%s/%s/%s-%s.%s", groupId, artifactId, version, artifactId, version,
				fileType);

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
		FileInputStream instream = null;
		FileOutputStream outstream = null;
		try {
			Path targetDir = Files.createTempDirectory("generatedJson");
			URL installJarURL = null;
			try {
				installJarURL = installJarFile.toURI().toURL();
			} catch (MalformedURLException e) {
				throw new PluginExecutionException("Could not resolve URL from file " + installJarFile, e);
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
					debug("generateJson action.exception.stacktrace: "
							+ mapBasedInstallKernel.get("action.error.stacktrace"));
				}

			} catch (PrivilegedActionException e) {
				throw new PluginExecutionException("Could not load the jar " + installJarFile.getAbsolutePath(), e);
			}
			File targetFile = new File(targetJsonFile);
			instream = new FileInputStream(json);
			targetFile.getParentFile().mkdirs();
			outstream = new FileOutputStream(targetFile);
			byte[] buffer = new byte[1024];
			int length;
			while ((length = instream.read(buffer)) > 0) {
				outstream.write(buffer, 0, length);
			}
			
			return targetFile;
		} catch (IOException e) {
			debug(e);
			throw new PluginExecutionException("Cannot read or create json file " + targetJsonFile, e);
		} finally {
			try {
				instream.close();
				outstream.close();
			} catch (IOException e) {			
			}
		}
	}

	private File loadInstallJarFile(File installDirectory) {
		if (openLibertyVersion != null) {
			File installJarOverride = downloadOverrideJar(OPEN_LIBERTY_GROUP_ID, INSTALL_MAP_ARTIFACT_ID);
			if (installJarOverride != null && installJarOverride.exists()) {
				return installJarOverride;
			}
		}
		return InstallFeatureUtil.getMapBasedInstallKernelJar(new File(installDirectory, "lib"));
	}

	private File downloadOverrideJar(String groupId, String artifactId) {
		try {
			return downloadArtifact(groupId, artifactId, "jar", String.format("[%s)",
					openLibertyVersion + ", " + InstallFeatureUtil.getNextProductVersion(openLibertyVersion)));
		} catch (PluginExecutionException e) {
			debug("Could not find override bundle " + groupId + ":" + artifactId
					+ " for the current Open Liberty version " + openLibertyVersion, e);
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
		String bundle = getOverrideBundleDescriptor(OPEN_LIBERTY_GROUP_ID, REPOSITORY_RESOLVER_ARTIFACT_ID);
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
		File usrDir = new File(installDirectory, "usr/tmp");
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

}
