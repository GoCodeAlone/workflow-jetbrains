package com.gocodalone.workflow.ide.settings

import com.gocodalone.workflow.ide.WorkflowBundle
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WorkflowSettingsTest : BasePlatformTestCase() {

    fun testAppSettingsDefaults() {
        val settings = WorkflowSettings.getInstance()
        assertEquals("wfctlPath should default to empty", "", settings.wfctlPath)
        assertEquals("lspServerPath should default to empty", "", settings.lspServerPath)
        assertTrue("enableLsp should default to true", settings.enableLsp)
        assertTrue("autoRegisterMcp should default to true", settings.autoRegisterMcp)
    }

    fun testLspEnabledSettingCanBeToggled() {
        val settings = WorkflowSettings.getInstance()
        assertTrue("enableLsp should default to true", settings.enableLsp)
        settings.enableLsp = false
        assertFalse("enableLsp should be false after toggle", settings.enableLsp)
        // Reset
        settings.enableLsp = true
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

    fun testWorkflowBundleConstants() {
        assertEquals("wfctl", WorkflowBundle.WFCTL_BINARY)
        assertEquals("workflow-lsp-server", WorkflowBundle.LSP_SERVER_BINARY)
        assertEquals("modules:", WorkflowBundle.WORKFLOW_CONTENT_KEY)
        assertTrue(
            "File patterns should include workflow.yaml",
            WorkflowBundle.WORKFLOW_FILE_PATTERNS.contains("workflow.yaml")
        )
        assertTrue(
            "File patterns should include app.yaml",
            WorkflowBundle.WORKFLOW_FILE_PATTERNS.contains("app.yaml")
        )
        assertTrue(
            "File patterns should include workflow.yml",
            WorkflowBundle.WORKFLOW_FILE_PATTERNS.contains("workflow.yml")
        )
        assertTrue(
            "File patterns should include app.yml",
            WorkflowBundle.WORKFLOW_FILE_PATTERNS.contains("app.yml")
        )
        assertTrue(
            "File patterns should include infra.yaml",
            WorkflowBundle.WORKFLOW_FILE_PATTERNS.contains("infra.yaml")
        )
        assertTrue(
            "File patterns should include infra.yml",
            WorkflowBundle.WORKFLOW_FILE_PATTERNS.contains("infra.yml")
        )
        assertFalse(
            "App workflow file patterns should not include wfctl.yaml",
            WorkflowBundle.WORKFLOW_FILE_PATTERNS.contains("wfctl.yaml")
        )
        assertFalse(
            "App workflow file patterns should not include wfctl.yml",
            WorkflowBundle.WORKFLOW_FILE_PATTERNS.contains("wfctl.yml")
        )
        assertTrue(
            "wfctl manifest names should be tracked separately",
            WorkflowBundle.WFCTL_MANIFEST_FILE_NAMES.contains("wfctl.yaml")
        )
        assertTrue(
            "wfctl manifest names should be tracked separately",
            WorkflowBundle.WFCTL_MANIFEST_FILE_NAMES.contains("wfctl.yml")
        )
    }

    fun testWorkflowBundleGitHubReleasesUrl() {
        assertTrue(
            "GitHub releases URL should point to GoCodeAlone/workflow",
            WorkflowBundle.GITHUB_RELEASES_URL.contains("GoCodeAlone/workflow")
        )
    }
}
