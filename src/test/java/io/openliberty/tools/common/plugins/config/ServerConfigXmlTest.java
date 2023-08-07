package io.openliberty.tools.common.plugins.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class ServerConfigXmlTest extends BaseConfigXmlTest {

    @Test
    public void createAppMonitorConfigTest() throws Exception {
        File serverDirectory = new File(serverConfigDir, "liberty/wlp/usr/servers/defaultServer");

        ApplicationMonitorConfigXmlDocument appMonXml = new ApplicationMonitorConfigXmlDocument("test");
        appMonXml.writeAppMonitorConfigXmlDocument(serverDirectory, "mbean");

        File newFile = appMonXml.getAppMonitorConfigXmlFile(serverDirectory);
        String content = Files.readString(newFile.toPath());
        assertTrue(content.contains("Generated by liberty-test-plugin"));
        assertTrue(content.contains("<applicationMonitor updateTrigger=\"mbean\"/>"));
    }

    @Test
    public void createAppMonitorConfigWithInvalidValueTest() throws Exception {
        File serverDirectory = new File(serverConfigDir, "liberty/wlp/usr/servers/defaultServer");

        ApplicationMonitorConfigXmlDocument appMonXml = new ApplicationMonitorConfigXmlDocument("test");
        try {
            appMonXml.writeAppMonitorConfigXmlDocument(serverDirectory, "asdf");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("The appMonitorTrigger value \"asdf\" is not supported."));
        }
    }

    @Test
    public void createAppMonitorConfigWithoutValueTest() throws Exception {
        File serverDirectory = new File(serverConfigDir, "liberty/wlp/usr/servers/defaultServer");

        ApplicationMonitorConfigXmlDocument appMonXml = new ApplicationMonitorConfigXmlDocument("test");
        appMonXml.writeAppMonitorConfigXmlDocument(serverDirectory, null);

        File newFile = appMonXml.getAppMonitorConfigXmlFile(serverDirectory);
        assertFalse(newFile.exists());
    }
}
