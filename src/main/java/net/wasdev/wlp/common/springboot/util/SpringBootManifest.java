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
package net.wasdev.wlp.common.springboot.util;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Relevant Spring Boot information contained in the Spring Boot JAR manifest.
 */
public class SpringBootManifest {
	private static final String JAR_MAIN_CLASS = "Main-Class";
	private static final String SPRING_START_CLASS_HEADER = "Start-Class";
	private static final String SPRING_BOOT_CLASSES_HEADER = "Spring-Boot-Classes";
	private static final String SPRING_BOOT_LIB_HEADER = "Spring-Boot-Lib";
	private final String springStartClass;
	private final String springBootClasses;
	private final String springBootLib;
	private final String springBootLibPrivided;

	enum SpringLauncher {
		JarLauncher("JarLauncher", "BOOT-INF/lib/", "BOOT-INF/classes/"), WarLauncher("WarLauncher", "WEB-INF/lib/",
				"WEB-INF/classes/", "-provided");
		
		private final String name;
		private final String libDefault;
		private final String classesDefault;
		private final String libProvidedSuffix;


		private SpringLauncher(String name, String libDefault, String classesDefault) {
			this(name, libDefault, classesDefault, null);
		}

		private SpringLauncher(String name, String libDefault, String classesDefault, String libProvidedSuffix) {
			this.name = name;
			this.libDefault = libDefault;
			this.classesDefault = classesDefault;
			this.libProvidedSuffix = libProvidedSuffix;
		}
		
		private static SpringLauncher fromMainClass(String className) {
			if (className != null) {
				String mainClass = className.trim();
				for (SpringLauncher l : SpringLauncher.values()) {
					if (mainClass.endsWith(l.name)) {
						return l;
					}
				}
			}
			return JarLauncher;
		}

		String getDefault(String springBootHeaderKey) {
			switch (springBootHeaderKey) {
			case SPRING_BOOT_CLASSES_HEADER:
				return classesDefault;
			case SPRING_BOOT_LIB_HEADER:
				return libDefault;
			default:
				return null;
			}
		}

		String getLibProvidedSuffix() {
			return libProvidedSuffix;
		}
	}


	/**
	 * Returns the start class for the Spring Boot application.
	 *
	 * @return the start class
	 */
	public String getSpringStartClass() {
		return springStartClass;
	}

	/**
	 * Returns the path to the classes directory for the Spring Boot Application
	 *
	 * @return the path to the classes directory
	 */
	public String getSpringBootClasses() {
		return springBootClasses;
	}

	/**
	 * Returns the path to the lib folder for the Spring Boot application
	 *
	 * @return the path to the lib folder
	 */
	public String getSpringBootLib() {
		return springBootLib;
	}

	/**
	 * Returns the path to the lib provided folder for the Spring Boot application
	 *
	 * @return the path to the lib provided folder
	 */
	public String getSpringBootLibProvided() {
		return springBootLibPrivided;
	}

	public SpringBootManifest(Manifest mf) {
		Attributes attributes = mf.getMainAttributes();
		String mainClass = attributes.getValue(JAR_MAIN_CLASS);
		SpringLauncher launcher = SpringLauncher.fromMainClass(mainClass);
		springStartClass = attributes.getValue(SPRING_START_CLASS_HEADER);
		springBootClasses = getSpringHeader(attributes, SPRING_BOOT_CLASSES_HEADER, launcher);
		springBootLib = getSpringHeader(attributes, SPRING_BOOT_LIB_HEADER, launcher);
		springBootLibPrivided = getLibProvided(launcher, springBootLib);
	}

	private static String getSpringHeader(Attributes attributes, String springBootHeaderKey, SpringLauncher launcher) {
		String value = attributes.getValue(springBootHeaderKey);
		if (value == null) {
			value = launcher.getDefault(springBootHeaderKey);
		}
		return value;
	}

	private static String getLibProvided(SpringLauncher launcher, String springBootLib) {
		String suffix = launcher.getLibProvidedSuffix();
		if (suffix != null) {
			return springBootLib + suffix;
		}
		return null;
	}
}
