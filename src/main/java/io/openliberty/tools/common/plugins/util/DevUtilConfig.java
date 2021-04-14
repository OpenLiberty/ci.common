package io.openliberty.tools.common.plugins.util;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class DevUtilConfig {

	public DevUtilConfig setBuildDirectory(File buildDirectory) {
		this.buildDirectory = buildDirectory;
        return this;
	}

	public DevUtilConfig setServerDirectory(File serverDirectory) {
		this.serverDirectory = serverDirectory;
        return this;
	}

	public DevUtilConfig setSourceDirectory(File sourceDirectory) {
		this.sourceDirectory = sourceDirectory;
        return this;
	}

	public DevUtilConfig setWebResourceDirs(List<Path> webResourceDirs) {
		this.webResourceDirs = webResourceDirs;
        return this;
	}

	public DevUtilConfig setTestSourceDirectory(File testSourceDirectory) {
		this.testSourceDirectory = testSourceDirectory;
        return this;
	}

	public DevUtilConfig setConfigDirectory(File configDirectory) {
		this.configDirectory = configDirectory;
        return this;
	}

	public DevUtilConfig setProjectDirectory(File projectDirectory) {
		this.projectDirectory = projectDirectory;
        return this;
	}

	public DevUtilConfig setMultiModuleProjectDirectory(File multiModuleProjectDirectory) {
		this.multiModuleProjectDirectory = multiModuleProjectDirectory;
        return this;
	}

	public DevUtilConfig setResourceDirs(List<File> resourceDirs) {
		this.resourceDirs = resourceDirs;
        return this;
	}

	public DevUtilConfig setHotTests(boolean hotTests) {
		this.hotTests = hotTests;
        return this;
	}

	public DevUtilConfig setSkipTests(boolean skipTests) {
		this.skipTests = skipTests;
        return this;
	}

	public DevUtilConfig setSkipUTs(boolean skipUTs) {
		this.skipUTs = skipUTs;
        return this;
	}

	public DevUtilConfig setSkipITs(boolean skipITs) {
		this.skipITs = skipITs;
        return this;
	}

	public DevUtilConfig setApplicationId(String applicationId) {
		this.applicationId = applicationId;
        return this;
	}

	public DevUtilConfig setServerStartTimeout(long serverStartTimeout) {
		this.serverStartTimeout = serverStartTimeout;
        return this;
	}

	public DevUtilConfig setAppStartupTimeout(int appStartupTimeout) {
		this.appStartupTimeout = appStartupTimeout;
        return this;
	}

	public DevUtilConfig setAppUpdateTimeout(int appUpdateTimeout) {
		this.appUpdateTimeout = appUpdateTimeout;
        return this;
	}

	public DevUtilConfig setCompileWaitMillis(long compileWaitMillis) {
		this.compileWaitMillis = compileWaitMillis;
        return this;
	}

	public DevUtilConfig setLibertyDebug(boolean libertyDebug) {
		this.libertyDebug = libertyDebug;
        return this;
	}

	public DevUtilConfig setUseBuildRecompile(boolean useBuildRecompile) {
		this.useBuildRecompile = useBuildRecompile;
        return this;
	}

	public DevUtilConfig setGradle(boolean gradle) {
		this.gradle = gradle;
        return this;
	}

	public DevUtilConfig setPollingTest(boolean pollingTest) {
		this.pollingTest = pollingTest;
        return this;
	}

	public DevUtilConfig setContainer(boolean container) {
		this.container = container;
        return this;
	}

	public DevUtilConfig setDockerfile(File dockerfile) {
		this.dockerfile = dockerfile;
        return this;
	}

	public DevUtilConfig setDockerBuildContext(File dockerBuildContext) {
		this.dockerBuildContext = dockerBuildContext;
        return this;
	}

	public DevUtilConfig setDockerRunOpts(String dockerRunOpts) {
		this.dockerRunOpts = dockerRunOpts;
        return this;
	}

	public DevUtilConfig setDockerBuildTimeout(int dockerBuildTimeout) {
		this.dockerBuildTimeout = dockerBuildTimeout;
        return this;
	}

	public DevUtilConfig setSkipDefaultPorts(boolean skipDefaultPorts) {
		this.skipDefaultPorts = skipDefaultPorts;
        return this;
	}

	public DevUtilConfig setCompilerOptions(JavaCompilerOptions compilerOptions) {
		this.compilerOptions = compilerOptions;
        return this;
	}

	public DevUtilConfig setKeepTempDockerfile(boolean keepTempDockerfile) {
		this.keepTempDockerfile = keepTempDockerfile;
        return this;
	}

	public DevUtilConfig setMavenCacheLocation(String mavenCacheLocation) {
		this.mavenCacheLocation = mavenCacheLocation;
        return this;
	}

	public File buildDirectory;
	public File serverDirectory;
	public File sourceDirectory;
	public List<Path> webResourceDirs;
	public File testSourceDirectory;
	public File configDirectory;
	public File projectDirectory;
	public File multiModuleProjectDirectory;
	public List<File> resourceDirs;
	public boolean hotTests;
	public boolean skipTests;
	public boolean skipUTs;
	public boolean skipITs;
	public String applicationId;
	public long serverStartTimeout;
	public int appStartupTimeout;
	public int appUpdateTimeout;
	public long compileWaitMillis;
	public boolean libertyDebug;
	public boolean useBuildRecompile;
	public boolean gradle;
	public boolean pollingTest;
	public boolean container;
	public File dockerfile;
	public File dockerBuildContext;
	public String dockerRunOpts;
	public int dockerBuildTimeout;
	public boolean skipDefaultPorts;
	public JavaCompilerOptions compilerOptions;
	public boolean keepTempDockerfile;
	public String mavenCacheLocation;

	public DevUtilConfig() { }
}