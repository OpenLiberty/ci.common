/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package net.wasdev.wlp.common.springboot.util;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 */
public class LibIndexCreateTest {
	@Rule
	public final TemporaryFolder workingArea = new TemporaryFolder();
	
	private File sourceFatJar;

	@Before
	public void setUp() throws Exception{
		List<String> filePaths = Arrays.asList("org/", "org/springframework/", "org/springframework/boot/",
				"org/springframework/boot/loader/", "org/springframework/boot/loader/Archive",
				"org/springframework/boot/loader/Archive$Entry.class", "BOOT-INF/", "BOOT-INF/classes/",
				"BOOT-INF/classes/org/", "BOOT-INF/classes/org/petclinic/",
				"BOOT-INF/classes/org/petclinic/Vets.class", "BOOT-INF/classes/org/petclinic/PetValidator.class",
				"BOOT-INF/classes/org/petclinic/OwnerController.class", "BOOT-INF/lib/",
				"BOOT-INF/lib/jboss-logging-3.3.2.Final.jar", "BOOT-INF/lib/hibernate-jpa-2.1-api-1.0.0.Final.jar",
				"BOOT-INF/lib/hibernate-commons-annotations-5.0.1.Final.jar");
		
		String manifestContents = "Manifest-Version: 1.0\n"
				+ "Main-Class: org.springframework.boot.loader.JarLauncher\n"
				+ "Start-Class: org.springframework.samples.petclinic.PetClinicApplicatio\n" + " n\n"
				+ "Spring-Boot-Classes: BOOT-INF/classes/\n" + "Spring-Boot-Lib: BOOT-INF/lib/\n";
		ByteArrayInputStream bais = new ByteArrayInputStream(manifestContents.getBytes("UTF8"));
		Manifest manifest = new Manifest(bais);
		sourceFatJar = createSourceFatJar(filePaths, manifest);

	}
	

	@Test
	public void testWriteToOutputDir() throws Exception {

		File thinJar = workingArea.newFile("thinJar.jar");
		File applicationLibs = workingArea.newFolder("AppLibs");

		SpringBootThinUtil util = new TestThinUtil(sourceFatJar, thinJar, applicationLibs);
		Stack<String> hashes = new Stack<String>();
		for (String s : Arrays.asList("aa003", "aa002", "aa001")) {
			hashes.push(s);
		}
		((TestThinUtil) util).hashValues = hashes;
		util.execute();

		// verify thin jar contents;
		List<String> jarContents = Arrays.asList("org/", "org/springframework/", "org/springframework/boot/",
				"META-INF/MANIFEST.MF", "BOOT-INF/", "BOOT-INF/classes/", "BOOT-INF/classes/org/",
				"BOOT-INF/classes/org/petclinic/", "BOOT-INF/classes/org/petclinic/Vets.class",
				"BOOT-INF/classes/org/petclinic/PetValidator.class",
				"BOOT-INF/classes/org/petclinic/OwnerController.class", "BOOT-INF/lib/", "META-INF/spring.lib.index");

		HashSet<String> expectedThinJarContents = new HashSet<>(jarContents);
		verifyJarEntryPaths(thinJar, expectedThinJarContents);

		
		// verify dependencies jar contents
		List<String> dirContents = Arrays.asList("aa/", "aa/001/", "aa/001/jboss-logging-3.3.2.Final.jar",
				"aa/002/", "aa/002/hibernate-jpa-2.1-api-1.0.0.Final.jar", "aa/003/",
				"aa/003/hibernate-commons-annotations-5.0.1.Final.jar");

		HashSet<String> expectedDirContents = new HashSet<String>(dirContents);	
		verifyDirEntryPaths(applicationLibs, expectedDirContents);

	}

	@Test
	public void testMultLibJarsWithSameContents() throws Exception {
		File thinJar = workingArea.newFile("thinJar.jar");
		File applicationLibsDir = workingArea.newFolder("AppLibs");

		SpringBootThinUtil util = new TestThinUtil(sourceFatJar, thinJar, applicationLibsDir);
		Stack<String> hashes = new Stack<String>();
		for (String s : Arrays.asList("bb001", "aa001", "aa001")) {
			hashes.push(s);
		}
		((TestThinUtil) util).hashValues = hashes;
		util.execute();
		// verify thin jar contents;
		List<String> jarContents = Arrays.asList("org/", "org/springframework/", "org/springframework/boot/",
				"META-INF/MANIFEST.MF", "BOOT-INF/", "BOOT-INF/classes/", "BOOT-INF/classes/org/",
				"BOOT-INF/classes/org/petclinic/", "BOOT-INF/classes/org/petclinic/Vets.class",
				"BOOT-INF/classes/org/petclinic/PetValidator.class",
				"BOOT-INF/classes/org/petclinic/OwnerController.class", "BOOT-INF/lib/", "META-INF/spring.lib.index");

		HashSet<String> expectedThinJarContents = new HashSet<>(jarContents);
		verifyJarEntryPaths(thinJar, expectedThinJarContents);

		// verify dependencies jar contents
		List<String> dirContents = Arrays.asList("aa/", "aa/001/", "aa/001/jboss-logging-3.3.2.Final.jar",
				"aa/001/hibernate-jpa-2.1-api-1.0.0.Final.jar", "bb/", "bb/001/",
				"bb/001/hibernate-commons-annotations-5.0.1.Final.jar");

		HashSet<String> expectedDirContents = new HashSet<String>(dirContents);
		verifyDirEntryPaths(applicationLibsDir, expectedDirContents);

	}

	private File createSourceFatJar(List<String> filePaths, Manifest manifest) throws Exception {
		File fatJar = workingArea.newFile("fat.jar");
		JarOutputStream fatJarStream = new JarOutputStream(new FileOutputStream(fatJar), manifest);
		for (String filePath : filePaths) {
			ZipEntry ze = new ZipEntry(filePath);
			fatJarStream.putNextEntry(ze);
			if (!filePath.endsWith(File.separator)) {
				// if this is an actual file entry write some data. The content is irrelevant
				// to the test. We only care about the structure of the zip file.
				fatJarStream.write(new byte[] { 'H', 'e', 'l', 'o' }, 0, 4);
			}
		}
		fatJarStream.close();
		return fatJar;
	}

	// verify that a passed in jarfile contains EXACTLY the set of expected entries.
	@SuppressWarnings("resource")
	private void verifyJarEntryPaths(File jarFile, Set<String> expectedEntries) throws IOException {
		Enumeration<? extends ZipEntry> entries = new ZipFile(jarFile).entries();
		ZipEntry zipEntry;
		while (entries.hasMoreElements()) {
			zipEntry = entries.nextElement();
			assertTrue("Unexpected path found in zip: " + zipEntry.toString(),
					expectedEntries.remove(zipEntry.toString()));
		}
		
		assertTrue("Missing " + expectedEntries.size() + " expected paths from jar.", expectedEntries.isEmpty());
	}

	private void verifyDirEntryPaths(File directory, Set<String> expectedEntries) throws IOException {
		ArrayList<String> paths = new ArrayList<>();
		int dirPathLen = directory.getAbsolutePath().length();
		getEntryPaths(directory, paths, dirPathLen);
		String separator = File.separator;
		for (String path : paths) {
			if(!("/").equals(separator)){
				path = path.replace(separator, "/");
			}
			assertTrue("Unexpected path found in directory: " + path, expectedEntries.remove(path));
		}
		assertTrue("Missing " + expectedEntries.size() + " expected paths from jar.", expectedEntries.isEmpty());
	}

	private void getEntryPaths(File directory, ArrayList<String> paths, int dirPathLen) {
		File[] files = directory.listFiles();
		for (File file : files) {
			String actualPath = file.getAbsolutePath().substring(dirPathLen + 1);
			if (file.isDirectory()) {
				actualPath += File.separator;
				getEntryPaths(file, paths, dirPathLen);
			}
			paths.add(actualPath);
		}
	}
	
	
	private static class TestThinUtil extends SpringBootThinUtil {
		private Stack<String> hashValues;

		TestThinUtil(File sourceFatJar, File targetThinJar, File libIndexCache) throws Exception {
			super(sourceFatJar, targetThinJar, libIndexCache);
		}

		@Override
		protected String hash(JarFile jf, ZipEntry entry) throws IOException, NoSuchAlgorithmException {
			return hashValues.pop();
		}
	}

}
