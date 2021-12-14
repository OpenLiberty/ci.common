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
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BinaryScannerUtil {

    public static final String BINARY_SCANNER_CONFLICT_MESSAGE1 = "A working set of features could not be generated due to conflicts " +
            "between configured features and the application's API usage: %s. Review and update your server configuration and " +
            "application to ensure they are not using conflicting features and APIs from different levels of MicroProfile, " +
            "Java EE, or Jakarta EE. Refer to the following set of suggested features for guidance: %s";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE2 = "A working set of features could not be generated due to conflicts " +
            "between configured features: %s. Review and update your server configuration to ensure it is not using conflicting " +
            "features from different levels of MicroProfile, Java EE, or Jakarta EE. Refer to the following set of " +
            "suggested features for guidance: %s";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE3 = "A working set of features could not be generated due to conflicts " +
            "in the application’s API usage: %s. Review and update your application to ensure it is not using conflicting APIs " +
            "from different levels of MicroProfile, Java EE, or Jakarta EE.";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE4 = "[None available]"; // format should match JVM Set.toString()

    public abstract void debug(String message);
    public abstract void debug(String message, Throwable e);
    public abstract void error(String message);
    public abstract void warn(String message);
    public abstract void info(String message);

    // The jar file containing the binary scanner code
    private File binaryScanner;
    private URLClassLoader binaryScannerClassLoader = null;
    
    public BinaryScannerUtil(File binaryScanner) {
        this.binaryScanner = binaryScanner;
    }

    public Set<String> runBinaryScanner(Set<String> currentFeatureSet, List<String> classFiles, Set<String> allClassesDirectories,
            String eeVersion, String mpVersion, boolean optimize)
            throws PluginExecutionException, InvocationTargetException, NoRecommendationException, RecommendationSetException {
        Set<String> featureList = null;
        if (binaryScanner != null && binaryScanner.exists()) {
            try {
                ClassLoader cl = getScannerClassLoader();
                Class driveScan = cl.loadClass("com.ibm.ws.report.binary.cmdline.DriveScan");
                // args: String[], String, String, List, java.util.Locale
                java.lang.reflect.Method driveScanMavenFeatureList = driveScan.getMethod("driveScanMavenFeatureList", String[].class, String.class, String.class, List.class, java.util.Locale.class);
                if (driveScanMavenFeatureList == null) {
                    debug("Error finding binary scanner method using reflection");
                    return null;
                }

                String[] binaryInputs = getBinaryInputs(classFiles, allClassesDirectories, optimize);
                List<String> currentFeatures;
                if (currentFeatureSet == null) { // signifies we are calling the binary scanner for a sample list of features
                    currentFeatures = new ArrayList<String>();
                } else {
                    currentFeatures = new ArrayList<String>(currentFeatureSet);
                }
                debug("Calling binary scanner with the following inputs...\n" +
                      "  binaryInputs: " + Arrays.toString(binaryInputs) + "\n" +
                      "  eeVersion: " + eeVersion + "\n" +
                      "  mpVersion: " + mpVersion + "\n" +
                      "  currentFeatures: " + currentFeatures + "\n" +
                      "  locale: " + java.util.Locale.getDefault());
                debug("The following messages are from the application binary scanner used to generate Liberty features");
                featureList = (Set<String>) driveScanMavenFeatureList.invoke(null, binaryInputs, eeVersion, mpVersion, currentFeatures, java.util.Locale.getDefault());
                debug("End of messages from application binary scanner. Features recommended :");
                for (String s : featureList) {debug(s);};
            } catch (InvocationTargetException ite) {
                // This is the exception from the JVM that indicates there was an exception in the method we
                // called through reflection. We must extract the actual exception from the 'cause' field.
                // A RuntimeException means the currentFeatureSet contains conflicts.
                // A FeatureConflictException means the binary files scanned conflict with each other or with
                // the currentFeatureSet parameter.
                Throwable scannerException = ite.getCause();
                if (scannerException instanceof RuntimeException) {
                    // The list of features from the app is passed in but it contains conflicts 
                    String problemMessage = scannerException.getMessage();
                    if (problemMessage == null || problemMessage.isEmpty()) {
                        debug("RuntimeException from binary scanner without descriptive message", scannerException);
                        throw new PluginExecutionException("Error scanning the application for Liberty features: " + scannerException.toString(), scannerException);
                    } else {
                        Set<String> conflicts = parseScannerMessage(problemMessage);
                        Set<String> sampleFeatureList = null;
                        try {
                            sampleFeatureList = runBinaryScanner(null, classFiles, allClassesDirectories, eeVersion, mpVersion, true);
                        } catch (InvocationTargetException retryException) {
                            // binary scanner should not return a RuntimeException since there is no list of app features passed in
                            sampleFeatureList = getNoSampleFeatureList();
                        }
                        throw new RecommendationSetException(true, conflicts, sampleFeatureList);
                    }
                } else if (scannerException.getClass().getName().endsWith("FeatureConflictException")) {
                    // The scanned files conflict with each other or with current features
                    Set<String> conflicts = getConflicts(scannerException);
                    Set<String> sampleFeatureList = null;
                    if (currentFeatureSet != null) {
                        try {
                            sampleFeatureList = runBinaryScanner(null, classFiles, allClassesDirectories, eeVersion, mpVersion, true);
                        } catch (InvocationTargetException retryException) {
                            Throwable scannerSecondException = retryException.getCause();
                            if (scannerSecondException.getClass().getName().endsWith("FeatureConflictException")) {
                                // Even after removing the server.xml feature list there are still conflicts in the binaries
                                throw new NoRecommendationException(conflicts);
                            } else {
                                debug("Unexpected failure on retry call to binary scanner", scannerSecondException);
                                debug("Passed directories to binary scanner:"+allClassesDirectories);
                                sampleFeatureList = getNoSampleFeatureList();
                            }
                        }
                        throw new RecommendationSetException(false, conflicts, sampleFeatureList);
                    } else {
                        throw ite;
                    }
                } else {
                    //TODO handle more exceptions from binary scanner e.g. com.ibm.ws.report.exceptions.RequiredFeatureModifiedException
                    debug("Exception from binary scanner.", scannerException);
                    throw new PluginExecutionException("Error scanning the application for Liberty features: " + scannerException.toString());
                }
            } catch (MalformedURLException|ClassNotFoundException|NoSuchMethodException|IllegalAccessException x){
                Object o = x.getCause();
                if (o != null) {
                    debug("Caused by exception:"+x.getCause().getClass().getName());
                    debug("Caused by exception message:"+x.getCause().getMessage());
                }
                throw new PluginExecutionException("An error occurred when trying to call the binary scanner jar: " + x.toString());
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

    private static String[] getBinaryInputs(List<String> classFiles, Set<String> classDirectories, boolean optimize) throws PluginExecutionException {
        Collection<String> resultSet;
        if (optimize) {
            if (classDirectories == null || classDirectories.isEmpty()) {
                throw new PluginExecutionException("Error collecting list of directories to send to binary scanner, list is null or empty.");
            }
            resultSet = classDirectories;
        } else {
            if (classFiles != null && !classFiles.isEmpty()) {
                resultSet = classFiles;
            } else {
                return new String[0];
            }
        }
        String[] result = resultSet.toArray(new String[resultSet.size()]);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Set<String> getConflicts(Throwable scannerResponse) {
        try {
            ClassLoader cl = getScannerClassLoader();
            @SuppressWarnings("rawtypes")
            Class featureConflictException = cl.loadClass("com.ibm.ws.report.exceptions.FeatureConflictException");
            java.lang.reflect.Method conflictFeatureList = featureConflictException.getMethod("getFeatures");
            if (conflictFeatureList == null) {
                debug("Error finding FeatureConflictException method getFeatures using reflection");
                return null;
            }
            return (Set<String>) conflictFeatureList.invoke(scannerResponse);
        } catch (ClassNotFoundException | MalformedURLException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException x) {
            //TODO maybe nothing
            error("Exception:"+x.getClass().getName());
            error("Message:"+x.getMessage());
            Object o = x.getCause();
            if (o != null) {
                warn("Caused by exception:"+x.getCause().getClass().getName());
                warn("Caused by exception message:"+x.getCause().getMessage());
            }
        }
        return null;
    }

    private Set<String> parseScannerMessage(String messages) {
        Set<String> features = new HashSet<String>();
        String[] messageArray = messages.split("\n");
        for (String message : messageArray) {
            if (message.startsWith("CWMIG12083")) {
                String [] messageParts = message.split(" ");
                if (messageParts.length > 4) { // should be 20
                    features.add(messageParts[2]);
                    features.add(messageParts[messageParts.length-2]);
                }
            }
        }
        return features;
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
}
