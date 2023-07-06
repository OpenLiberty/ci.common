package io.openliberty.tools.common.plugins.config;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class BaseConfigXmlTest {
    
    public static final String RESOURCES_DIR = "src/test/resources";
    
    private static final String RESOURCES_INSTALL_DIR = RESOURCES_DIR + "/serverConfig";
    
	protected File serverConfigDir = new File(RESOURCES_INSTALL_DIR);
    public File buildDir;
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Before
    public void setupInstallDir() throws IOException {
        serverConfigDir = temp.newFolder();
        File src = new File(RESOURCES_INSTALL_DIR);
        FileUtils.copyDirectory(src, serverConfigDir);

        buildDir = temp.newFolder();
    }

}
