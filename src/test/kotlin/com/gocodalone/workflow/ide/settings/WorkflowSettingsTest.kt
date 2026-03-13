package com.gocodalone.workflow.ide.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WorkflowSettingsTest : BasePlatformTestCase() {

    fun testAppSettingsDefaults() {
        val settings = WorkflowSettings.getInstance()
        assertEquals("wfctlPath should default to empty", "", settings.wfctlPath)
        assertEquals("lspServerPath should default to empty", "", settings.lspServerPath)
        assertTrue("enableLsp should default to true", settings.enableLsp)
        assertTrue("autoRegisterMcp should default to true", settings.autoRegisterMcp)
    }

    fun testProjectSettingsDefaults() {
        val settings = WorkflowProjectSettings.getInstance(project)
        assertTrue("configPaths should default to empty list", settings.configPaths.isEmpty())
        assertFalse("suppressDetectionPrompt should default to false", settings.suppressDetectionPrompt)
        assertFalse("mcpRegistered should default to false", settings.mcpRegistered)
        assertFalse("mcpRegistrationDeclined should default to false", settings.mcpRegistrationDeclined)
    }

    fun testProjectSettingsConfigPathsPersist() {
        val settings = WorkflowProjectSettings.getInstance(project)
        settings.configPaths = listOf("config/**/*.yaml", "app.yaml")
        assertEquals(2, settings.configPaths.size)
        assertEquals("config/**/*.yaml", settings.configPaths[0])
        assertEquals("app.yaml", settings.configPaths[1])

        // Cleanup
        settings.configPaths = emptyList()
    }
}
