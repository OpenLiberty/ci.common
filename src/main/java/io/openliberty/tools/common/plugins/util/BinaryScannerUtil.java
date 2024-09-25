/**
 * (C) Copyright IBM Corporation 2021, 2022.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BinaryScannerUtil {

    public static final String BINARY_SCANNER_MAVEN_GROUP_ID = "com.ibm.websphere.appmod.tools";
    public static final String BINARY_SCANNER_MAVEN_ARTIFACT_ID = "binary-app-scanner";
    public static final String BINARY_SCANNER_MAVEN_TYPE = "jar";
    public static final String BINARY_SCANNER_MAVEN_VERSION = "[21.0.0.5-SNAPSHOT,)";

    public static final String GENERATED_FEATURES_FILE_NAME = "generated-features.xml";
    public static final String GENERATED_FEATURES_FILE_PATH = "configDropins/overrides/" + GENERATED_FEATURES_FILE_NAME;
    private static final String FEATURE_MODIFIED_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.RequiredFeatureModifiedException";
    private static final String FEATURE_CONFLICT_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.FeatureConflictException";
    private static final String PROVIDED_FEATURE_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.ProvidedFeatureConflictException";
    private static final String FEATURE_NOT_AVAILABLE_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.FeatureNotAvailableAtRequestedLevelException";
    private static final String ILLEGAL_TARGET_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.IllegalTargetException";
    private static final String ILLEGAL_TARGET_COMBINATION_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.IllegalTargetCombinationException";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE1 = "A working set of features could not be generated due to conflicts " +
            "between configured features and the application's API usage: %s. Review and update your server configuration and " +
            "application to ensure they are not using conflicting features and APIs from different levels of MicroProfile, " +
            "Java EE, or Jakarta EE. Refer to the following set of suggested features for guidance: %s.";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE2 = "A working set of features could not be generated due to conflicts " +
            "between configured features: %s. Review and update your server configuration to ensure it is not using conflicting " +
            "features from different levels of MicroProfile, Java EE, or Jakarta EE. Refer to the following set of " +
            "suggested features for guidance: %s.";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE3 = "A working set of features could not be generated due to conflicts " +
            "in the application’s API usage: %s. Review and update your application to ensure it is not using conflicting APIs " +
            "from different levels of MicroProfile, Java EE, or Jakarta EE.";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE4 = "[None available]"; // format should match JVM Set.toString()
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE5 = "A working set of features could not be generated due to conflicts " +
            "in the required features: %s and required levels of MicroProfile: %s, Java EE or Jakarta EE: %s. Review and update your application to ensure it " +
            "is using the correct levels of MicroProfile, Java EE, or Jakarta EE, or consider removing the following set of features: %s.";
    public static final String BINARY_SCANNER_INVALID_MP_MESSAGE = "The MicroProfile version number %s specified in the build file " +
            "is not supported for feature generation.";
    public static final String BINARY_SCANNER_INVALID_EE_MESSAGE = "The Java EE or Jakarta EE version number %s specified in the build file " +
            "is not supported for feature generation.";
    public static final String BINARY_SCANNER_INVALID_EEMPARG_MESSAGE = "Either the Java EE or Jakarta EE version number or the MicroProfile version number specified in the build file " +
            "is not supported for feature generation."; // We need to be prepared for this situation from the binary scanner.
    public static final String BINARY_SCANNER_INVALID_COMBO_MESSAGE = "The Java EE or Jakarta EE version number %s specified in the build file " +
            "in combination with the MicroProfile version number %s specified in the build file " +
            "is not supported for feature generation.";

    // Strings recognized by the binary scanner arguments for Java/Jakarta EE and MicroProfile
    // Valid ee6, ee7, ee8, ee9 and so on
    public static final String BINARY_SCANNER_EE_PREFIX = "ee";
    // Valid mp1, mp1.2, mp1.3 and so on
    public static final String BINARY_SCANNER_MP_PREFIX = "mp";

    public abstract void debug(String message);
    public abstract void debug(String message, Throwable e);
    public abstract void error(String message);
    public abstract void warn(String message);
    public abstract void info(String message);
    public abstract boolean isDebugEnabled();

    // The jar file containing the binary scanner code
    private File binaryScanner;
    private URLClassLoader binaryScannerClassLoader = null;
    private Class binaryScannerClass = null;
    private Method binaryScannerMethod = null;

    public BinaryScannerUtil(File binaryScanner) {
        this.binaryScanner = binaryScanner;
    }

    /**
     * Call the binary scanner to generate a list of Liberty features to run an application. It will scan the
     * classFiles parameter or scan all the classes in the allClassesDirectories parameter depending on the
     * optimize parameter. The currentFeatureSet parameter indicates the starting list of features and all the
     * generated features will be compatible. The generated features will also be compatible with the indicated
     * versions of Java EE or Jakarta EE and MicroProfile.
     *
     * @param currentFeatureSet - the features already specified in the server configuration
     * @param classFiles - a set of class files for the scanner to handle. Should be a subset of allClassesDirectories
     * @param allClassesDirectories - the directories containing all the class files of the application
     * @param logLocation - directory name relative to project or absolute path passed to binary scanner
     * @param targetJavaEE - generate features valid for the indicated version of EE
     * @param targetMicroProfile - generate features valid for the indicated version of MicroProfile
     * @param optimize - true value means to scan all the classes in allClassesDirectories rather than just the
     *                   classes in the classFiles parameter. currentFeatureSet is still used as the basis of
     *                   the feature set.
     * @return - a set of features that will allow the application to run in a Liberty server
     * @throws PluginExecutionException - any exception that prevents the scanner from running
     * @throws NoRecommendationException - indicates a problem and there are no recommended features
     * @throws RecommendationSetException - indicates a problem but the scanner was able to generate a set of
     *                                      features that should work to run the application
     * @throws FeatureModifiedException - indicates a problem but the scanner was able to generate a set of features
     *                                      that should work if certain features are modified
     * @throws FeatureUnavailableException - indicates a problem between required features and required MP/EE levels but
     *                                      the scanner was able to generate a set of features that should be removed
     * @throws IllegalTargetException - indicates one or both of the MP or EE versions is not supported by the binary scanner
     * @throws IllegalTargetComboException - indicates the MP or EE version parameters are not supported by the binary
     *                                       scanner when used in combination with each other. E.g. EE 7 and MP 2.1
     */
    public Set<String> runBinaryScanner(Set<String> currentFeatureSet, List<String> classFiles, Set<String> allClassesDirectories,
                                        String logLocation, String targetJavaEE, String targetMicroProfile, boolean optimize)
            throws PluginExecutionException, NoRecommendationException, RecommendationSetException, FeatureModifiedException,
            FeatureUnavailableException, IllegalTargetException, IllegalTargetComboException {
        Set<String> featureList = null;
        if (binaryScanner != null && binaryScanner.exists()) {
            // if we are already generating features for all class files (optimize=true) and
            // we are not passing any user specified features (currentFeatureSet is empty)
            // we do not need to rerun the binary scanner if it fails
            boolean reRunIfFailed = !currentFeatureSet.isEmpty() || !optimize;
            try {
                Method generateFeatureSetMethod = getScannerMethod();
                // names: binaryInputs, targetJavaEE, targetMicroProfile, currentFeatures, logLocation, logLevel, locale
                Set<String> binaryInputs = getBinaryInputs(classFiles, allClassesDirectories, optimize);
                String logLevel;
                if (isDebugEnabled()) {
                    logLevel = "*=FINE";  // generate messages for debugging by support team
                } else {
                    logLevel = null;
                    logLocation = null;
                }
                debug("Calling " + binaryScanner.getName() + " with the following inputs...\n" +
                        "  binaryInputs: " + binaryInputs + "\n" +
                        "  targetJavaEE: " + targetJavaEE + "\n" +
                        "  targetMicroP: " + targetMicroProfile + "\n" +
                        "  currentFeatures: " + currentFeatureSet + "\n" +
                        "  logLocation: " + logLocation + "\n" +
                        "  logLevel: " + logLevel + "\n" +
                        "  locale: " + java.util.Locale.getDefault());
                featureList = (Set<String>) generateFeatureSetMethod.invoke(null, binaryInputs, targetJavaEE, targetMicroProfile,
                        currentFeatureSet, logLocation, logLevel, java.util.Locale.getDefault());
                for (String s : featureList) {debug(s);};
            } catch (InvocationTargetException ite) {
                // This is the exception from the JVM that indicates there was an exception in the method we
                // called through reflection. We must extract the actual exception from the 'cause' field.
                // 1. ProvidedFeatureConflictException means the currentFeatureSet contains conflicts.
                // 2. FeatureConflictException means the binary files scanned conflict with each other or with
                // the currentFeatureSet parameter.
                // 3. RequiredFeatureModifiedException means the scanner can make a working list of features but
                // only if certain inputs are changed.
                // 4. FeatureNotAvailableAtRequestedLevelException means the features passed or binary files
                // scanned require features that do not exist at the requested EE or MP levels.
                // 5. IllegalTargetException means that the Java or Jakarta EE version or the MicroProfile version
                // we read from the build file is out of range for the binary scanner. For EE we only use the first
                // digit: ee6 to ee9. For MP we use the first two digits mp1.2 to mp5.0.
                // 6. IllegalTargetCombinationException means that the EE level and the MP level are not compatible.
                Throwable scannerException = ite.getCause();
                if (scannerException.getClass().getName().equals(PROVIDED_FEATURE_EXCEPTION)) {
                    // The list of features from the app is passed in but it contains conflicts
                    Set<String> conflicts = getFeatures(scannerException);
                    // always rerun binary scanner in this scenario, this exception only occurs if a current feature list is passed to binary scanner
                    Set<String> sampleFeatureList = reRunBinaryScanner(allClassesDirectories, logLocation, targetJavaEE, targetMicroProfile);
                    if (sampleFeatureList == null) {
                        throw new NoRecommendationException(conflicts);
                    } else {
                        throw new RecommendationSetException(true, conflicts, sampleFeatureList);
                    }
                } else if (scannerException.getClass().getName().equals(FEATURE_CONFLICT_EXCEPTION)) {
                    // The scanned files conflict with each other or with current features
                    Set<String> conflicts = getFeatures(scannerException);
                    //  rerun binary scanner with all class files and without the current feature set to get feature recommendations
                    Set<String> sampleFeatureList = reRunIfFailed ? reRunBinaryScanner(allClassesDirectories, logLocation, targetJavaEE, targetMicroProfile): null;
                    if (sampleFeatureList == null) {
                        throw new NoRecommendationException(conflicts);
                    } else {
                        throw new RecommendationSetException(false, conflicts, sampleFeatureList);
                    }
                } else if (scannerException.getClass().getName().equals(FEATURE_MODIFIED_EXCEPTION)) {
                    // The scanned files conflict and the scanner suggests modifying some features
                    Set<String> modifications = getFeatures(scannerException);
                    //  rerun binary scanner with all class files and without the current feature set
                    Set<String> sampleFeatureList = reRunIfFailed ? reRunBinaryScanner(allClassesDirectories, logLocation, targetJavaEE, targetMicroProfile) : null;
                    throw new FeatureModifiedException(modifications,
                            (sampleFeatureList == null) ? getNoSampleFeatureList() : sampleFeatureList, scannerException.getLocalizedMessage());
                } else if (scannerException.getClass().getName().equals(FEATURE_NOT_AVAILABLE_EXCEPTION)) {
                    // The list of features required by app or passed to binary scanner do not exist
                    // at the required EE or MP level
                    Set<String> conflicts = getFeatures(scannerException);
                    Set<String> unavailableFeatures = getUnavailableEEFeatures(scannerException);
                    unavailableFeatures.addAll(getUnavailableMPFeatures(scannerException));
                    throw new FeatureUnavailableException(conflicts, unavailableFeatures, targetMicroProfile,
                            targetJavaEE);
                } else if (scannerException.getClass().getName().equals(ILLEGAL_TARGET_EXCEPTION)) {
                    // The EE and/or the MP version number is out of range
                    throw new IllegalTargetException(getInvalidEETarget(scannerException), getInvalidMPTarget(scannerException));
                } else if (scannerException.getClass().getName().equals(ILLEGAL_TARGET_COMBINATION_EXCEPTION)) {
                    // The EE and MP version numbers are in range but they are not compatible with each other based on the standards.
                    throw new IllegalTargetComboException(getInvalidEETarget(scannerException), getInvalidMPTarget(scannerException));
                } else if (scannerException.getClass().getName().contains("java.lang.IllegalArgumentException")) {
                    // Used by binary scanner 22.0.0.3, remove after 22.0.0.4 is in sonatype
                    // TODO: Affected by issue #1558
                    String msg = scannerException.getMessage();
                    if (msg.contains("CWMIG12056E")) {
                        if (msg.contains("targetJavaEE")) {
                            throw new PluginExecutionException(BINARY_SCANNER_INVALID_EE_MESSAGE);
                        } else if (msg.contains("targetMicroProfile")) {
                            throw new PluginExecutionException(BINARY_SCANNER_INVALID_MP_MESSAGE);
                        }
                    }
                    // otherwise exit this if statement and execute default behaviour.
                }
                debug("Exception from binary scanner.", scannerException);
                throw new PluginExecutionException("Error scanning the application for Liberty features: " + scannerException.toString());
            } catch (MalformedURLException|ClassNotFoundException|NoSuchMethodException|IllegalAccessException loadingException){
                Object o = loadingException.getCause();
                if (o != null) {
                    debug("Caused by exception:"+loadingException.getCause().getClass().getName());
                    debug("Caused by exception message:"+loadingException.getCause().getMessage());
                }
                throw new PluginExecutionException("An error occurred when trying to call the binary scanner jar: " + loadingException.toString());
            }
        } else {
            if (binaryScanner == null) {
                throw new PluginExecutionException("The binary scanner jar location is not defined.");
            } else {
                throw new PluginExecutionException("Could not find the binary scanner jar at " + binaryScanner.getAbsolutePath());
            }
        }
        return featureList;
    }

    /**
     * The method is intended to call the binary scanner to generate a list of the optimal features for an
     * application. This optimal list can be reported to the user as a suggested list of features.
     *
     * In order to generate the optimal list we must scan all classes in the application and we do not consider
     * the features already specified in the server configuration (server.xml).
     *
     * @param allClassesDirectories - the scanner will find all the class files in this set of directories
     * @param logLocation - directory name relative to project or absolute path passed to binary scanner
     * @param targetJavaEE - generate features valid for the indicated version of EE
     * @param targetMicroProfile - generate features valid for the indicated version of MicroProfile
     * @return - a set of features that will allow the application to run in a Liberty server
     * @throws PluginExecutionException - any exception that prevents the scanner from running
     */
    public Set<String> reRunBinaryScanner(Set<String> allClassesDirectories, String logLocation, String targetJavaEE, String targetMicroProfile)
            throws PluginExecutionException {
        Set<String> featureList = null;
        try {
            Method generateFeatureSetMethod = getScannerMethod();
            Set<String> binaryInputs = allClassesDirectories;
            Set<String> currentFeaturesSet = new HashSet<String>(); // when re-running always pass in no features
            String logLevel;
            if (isDebugEnabled()) {
                logLevel = "*=FINE";  // generate messages for debugging by support team
            } else {
                logLevel = null;
                logLocation = null;
            }
            debug("Recalling binary scanner with the following inputs...\n" +
                    "  binaryInputs: " + binaryInputs + "\n" +
                    "  targetJavaEE: " + targetJavaEE + "\n" +
                    "  targetMicroP: " + targetMicroProfile + "\n" +
                    "  currentFeatures: " + currentFeaturesSet + "\n" +
                    "  logLocation: " + logLocation + "\n" +
                    "  logLevel: " + logLevel + "\n" +
                    "  locale: " + java.util.Locale.getDefault());
            featureList = (Set<String>) generateFeatureSetMethod.invoke(null, binaryInputs, targetJavaEE, targetMicroProfile,
                    currentFeaturesSet, logLocation, logLevel, java.util.Locale.getDefault());
            for (String s : featureList) {debug(s);};
        } catch (InvocationTargetException ite) {
            Throwable scannerException = ite.getCause();
            if (scannerException.getClass().getName().equals(PROVIDED_FEATURE_EXCEPTION)) {
                // this happens when the list of features passed in contains conflicts so now no recommendation possible
                debug("RuntimeException from re-run of binary scanner", scannerException); // shouldn't happen
                featureList = null;
            } else if (scannerException.getClass().getName().equals(FEATURE_CONFLICT_EXCEPTION)) {
                // The features in the scanned files conflict with each other, no recommendation possible
                featureList = getNoSampleFeatureList();
            } else if (scannerException.getClass().getName().equals(FEATURE_MODIFIED_EXCEPTION)) {
                // The features in the scanned files conflict with each other, no recommendation possible
                featureList = getNoSampleFeatureList();
            } else {
                debug("Exception from rerunning binary scanner.", scannerException);
                throw new PluginExecutionException("Error scanning the application for Liberty feature recommendations: " + scannerException.toString());
            }
        } catch (MalformedURLException|ClassNotFoundException|NoSuchMethodException|IllegalAccessException loadingException){
            Object o = loadingException.getCause();
            if (o != null) {
                debug("Caused by exception2:"+loadingException.getCause().getClass().getName());
                debug("Caused by exception message2:"+loadingException.getCause().getMessage());
            }
            throw new PluginExecutionException("An error occurred when trying to call the binary scanner jar for recommendations: " + loadingException.toString());
        }
        return featureList;
    }

    private Set<String> getNoSampleFeatureList() {
        Set<String> sampleFeatureList;
        sampleFeatureList = new HashSet<String>();
        sampleFeatureList.add(BINARY_SCANNER_CONFLICT_MESSAGE4);
        return sampleFeatureList;
    }

    private ClassLoader getScannerClassLoader() throws MalformedURLException {
        if (binaryScannerClassLoader == null) {
            ClassLoader cl = this.getClass().getClassLoader();
            binaryScannerClassLoader = new URLClassLoader(new URL[] { binaryScanner.toURI().toURL() }, cl);
        }
        return binaryScannerClassLoader;
    }

    private Class getScannerClass() throws MalformedURLException, ClassNotFoundException {
        if (binaryScannerClass == null) {
            ClassLoader cl = getScannerClassLoader();
            binaryScannerClass = cl.loadClass("com.ibm.websphere.binary.cmdline.BinaryScanner");
        }
        return binaryScannerClass;
    }

    private Method getScannerMethod() throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, PluginExecutionException, SecurityException {
        if (binaryScannerMethod == null) {
            Class driveScan = getScannerClass();
            // args: Set<String>, String, String, Set<String>, String, String, Locale
            // names: binaryInputs, targetJavaEE, targetMicroProfile, currentFeatures, logLocation, logLevel, locale
            binaryScannerMethod = driveScan.getMethod("generateFeatureList", Set.class, String.class, String.class,
                    Set.class, String.class, String.class, java.util.Locale.class);
            if (binaryScannerMethod == null) {
                throw new PluginExecutionException("Error finding binary scanner method using reflection");
            }
        }
        return binaryScannerMethod;
    }

    private static Set<String> getBinaryInputs(List<String> classFiles, Set<String> classDirectories, boolean optimize) throws PluginExecutionException {
        Set<String> resultSet;
        if (optimize) {
            if (classDirectories == null || classDirectories.isEmpty()) {
                return new HashSet<String>();
            }
            resultSet = classDirectories;
        } else {
            if (classFiles != null && !classFiles.isEmpty()) {
                resultSet = new HashSet<String>(classFiles);
            } else {
                return new HashSet<String>();
            }
        }
        return resultSet;
    }

    @SuppressWarnings("unchecked")
    private Set<String> getFeatures(Throwable scannerResponse) {
        return (Set<String>) getMethodResult(scannerResponse, "getFeatures");
    }

    @SuppressWarnings("unchecked")
    private Set<String> getUnavailableMPFeatures(Throwable scannerResponse) {
        return (Set<String>) getMethodResult(scannerResponse, "getUnavailableMPFeatures");
    }
    @SuppressWarnings("unchecked")
    private Set<String> getUnavailableEEFeatures(Throwable scannerResponse) {
        return (Set<String>) getMethodResult(scannerResponse, "getUnavailableEEFeatures");
    }

    @SuppressWarnings("unchecked")
    private String getInvalidMPTarget(Throwable scannerResponse) {
        return (String) getMethodResult(scannerResponse, "getIllegalMPTarget");
    }
    @SuppressWarnings("unchecked")
    private String getInvalidEETarget(Throwable scannerResponse) {
        return (String) getMethodResult(scannerResponse, "getIllegalEETarget");
    }

    @SuppressWarnings("unchecked")
    private Object getMethodResult(Throwable scannerResponse, String method) {
        try {
            ClassLoader cl = getScannerClassLoader();
            @SuppressWarnings("rawtypes")
            Class featureConflictException = cl.loadClass(scannerResponse.getClass().getName());
            Method featureMethod = featureConflictException.getMethod(method);
            if (featureMethod == null) {
                debug("Error finding " + scannerResponse.getClass().getName() + " method " + method + " using reflection");
                return null;
            }
            return featureMethod.invoke(scannerResponse);
        } catch (ClassNotFoundException | MalformedURLException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException x) {
            debug("An error occurred when trying to call the binary scanner jar " + method + ":"+x.getClass().getName(), x);
            Throwable cause = x.getCause();
            if (cause != null) {
                debug("Caused by exception:"+cause.getClass().getName());
                debug("Caused by exception message:" + cause.getMessage());
            }
        }
        return null;
    }

    /**
     * Create the string required by the binary scanner parameter targetJavaEE
     * E.g. ee7, ee8 etc
     * @param ver the String value version number read from the build file (pom.xml, build.gradle)
     *           E.g. 8, 8.0, 8.0.0 etc. This is verified by the parser and cannot be blank.
     * @return String parameter passed to binary scanner
     */
    public static String composeEEVersion(String ver) {
        if (ver == null) {
            return null;
        }
        String majorVersion;
        int offset = ver.indexOf(".");
        majorVersion = (offset == -1) ? ver : ver.substring(0, offset);
        return BINARY_SCANNER_EE_PREFIX + majorVersion;
    }

    /**
     * Create the string required by the binary scanner parameter targetMicroProfile
     * E.g. mp1.3, mp4.1 etc
     * @param ver the String value version number read from the build file (pom.xml, build.gradle)
     *           E.g. 1, 2.1 etc. This is verified by the parser and cannot be blank.
     * @return String parameter passed to binary scanner or null in case of error
     */
    public static String composeMPVersion(String ver) {
        if (ver == null) {
            return null;
        }
        int offset = ver.indexOf("-RC"); // clean up 4.1-RC to 4.1
        if (offset > 0) {
            ver = ver.substring(0, offset);
        }
        String[] parts = ver.split("\\.", 3); // binary scanner only recognises the first two values. Regex for "." char
        if (parts.length > 1 &&
                parts[0] != null && !parts[0].isEmpty() && parts[1] != null && !parts[1].isEmpty()) {
            return BINARY_SCANNER_MP_PREFIX + parts[0] + "." + parts[1];
        }
        return null;
    }

    /**
     * Convenience method to build the string reported to the user when the exception is detected.
     *
     * This is used after the caller has analyzed the Java or Jakarta EE version number and the MicroProfile
     * version number and generated argument values to pass to the binary scanner. If the binary scanner
     * detects a problem and throws an exception it reports the invalid arguments. We must map the invalid
     * arguments back to the user specified version number in order to fix the problem.
     *
     * @param invalidEEArg - the argument passed to the binary scanner which may be returned as invalid.
     * @param invalidMPArg - the argument passed to the binary scanner which may be returned as invalid.
     * @param eeVersion - the user specified version string from the build file used to generate the arg.
     * @param mpVersion - the user specified version string from the build file used to generate the arg.
     * @return a string we can report to the user to report an error or errors and guide the effort to fix it.
     */
    public static String buildInvalidArgExceptionMessage(String invalidEEArg, String invalidMPArg, String eeVersion, String mpVersion) {
        String messages = null;
        if (invalidEEArg != null) {
            messages = String.format(BINARY_SCANNER_INVALID_EE_MESSAGE, eeVersion);
        }
        if (invalidMPArg != null) {
            if (messages != null) {
                messages += "\n" ;
                messages += String.format(BINARY_SCANNER_INVALID_MP_MESSAGE, mpVersion);
            } else {
                messages = String.format(BINARY_SCANNER_INVALID_MP_MESSAGE, mpVersion);
            }
        }
        if (messages == null) { // We need to be prepared for this situation from the binary scanner.
            messages = BINARY_SCANNER_INVALID_EEMPARG_MESSAGE;
        }
        return messages;
    }

    // A class to pass the list of conflicts back to the caller.
    public class NoRecommendationException extends Exception {
        private static final long serialVersionUID = 1L;
        Set<String> conflicts;
        NoRecommendationException(Set<String> conflictSet) {
            conflicts = conflictSet;
        }
        public Set<String> getConflicts() {
            return conflicts;
        }
    }

    // A class that encapsulates a list of conflicting features, a suggested list of replacements
    // and a flag that indicates whether the conflicts were found in the features existing in the
    // app's server config or if the conflicts exist in the binary files we examined.
    public class RecommendationSetException extends Exception {
        private static final long serialVersionUID = 1L;
        boolean existingFeaturesConflict;
        Set<String> conflicts;
        Set<String> suggestions;
        RecommendationSetException(boolean existing, Set<String> conflictSet, Set<String> suggestionSet) {
            existingFeaturesConflict = existing;
            conflicts = conflictSet;
            suggestions = suggestionSet;
        }
        public boolean isExistingFeaturesConflict() {
            return existingFeaturesConflict;
        }
        public Set<String> getConflicts() {
            return conflicts;
        }
        public Set<String> getSuggestions() {
            return suggestions;
        }
    }

    // A class to pass the list of modified features back to the caller.
    public class FeatureModifiedException extends Exception {
        private static final long serialVersionUID = 1L;
        Set<String> features;
        Set<String> suggestions;
        String message;
        FeatureModifiedException(Set<String> featureSet, Set<String> suggestionSet, String message) {
            features = featureSet;
            suggestions = suggestionSet;
            this.message = message;
        }
        public Set<String> getFeatures() {
            return features;
        }
        public Set<String> getSuggestions() {
            return suggestions;
        }
        public String getMessage() {
            return message;
        }
    }

    // A class to pass the list of unavailable features back to the caller.
    public class FeatureUnavailableException extends Exception {
        private static final long serialVersionUID = 1L;
        Set<String> conflicts;
        Set<String> unavailableFeatures;
        String mpLevel;
        String eeLevel;
        FeatureUnavailableException(Set<String> conflictsSet, Set<String> unavailableFeaturesSet, String mpLevel, String eeLevel) {
            conflicts = conflictsSet;
            unavailableFeatures = unavailableFeaturesSet;
            this.mpLevel = mpLevel;
            this.eeLevel = eeLevel;
        }
        public Set<String> getConflicts() {
            return conflicts;
        }
        public Set<String> getUnavailableFeatures() {
            return unavailableFeatures;
        }
        public String getEELevel() {
            return eeLevel;
        }
        public String getMPLevel() {
            return mpLevel;
        }
    }

    public abstract class AbstractIllegalTargetException extends Exception {
        private static final long serialVersionUID = 1L;
        String eeLevel;
        String mpLevel;
        AbstractIllegalTargetException(String eeLevel, String mpLevel) {
            this.eeLevel = eeLevel;
            this.mpLevel = mpLevel;
        }
        public String getEELevel() {
            return eeLevel;
        }
        public String getMPLevel() {
            return mpLevel;
        }
    }

    // A class to pass the invalid EE and MP parameters back to the caller.
    public class IllegalTargetException extends AbstractIllegalTargetException {
        private static final long serialVersionUID = 1L;
        IllegalTargetException(String eeLevel, String mpLevel) {
            super(eeLevel, mpLevel);
        }
    }

    // A class to pass the EE and MP parameters which do not work together back to the caller.
    public class IllegalTargetComboException extends AbstractIllegalTargetException {
        private static final long serialVersionUID = 1L;
        IllegalTargetComboException(String eeLevel, String mpLevel) {
            super(eeLevel, mpLevel);
        }
    }
}