package com.gocodalone.workflow.ide.lsp

import com.gocodalone.workflow.ide.WorkflowBundle
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for WorkflowLspServerSupportProvider configuration and constants.
 * These are unit tests that validate the provider setup without requiring
 * a running LSP server or full IDE environment.
 */
class LspSupportProviderTest : BasePlatformTestCase() {

    fun testServerIdConstant() {
        assertEquals(
            "Server ID must be workflow-lsp-server",
            "workflow-lsp-server",
            WorkflowLspServerSupportProvider.SERVER_ID
        )
    }

    fun testServerNameConstant() {
        assertEquals(
            "Server name must be Workflow LSP",
            "Workflow LSP",
            WorkflowLspServerSupportProvider.SERVER_NAME
        )
    }

    fun testServerIdMatchesBinaryName() {
        // The server ID should match the binary name for consistency
        assertEquals(
            "Server ID should match the LSP binary name from WorkflowBundle",
            com.gocodalone.workflow.ide.WorkflowBundle.LSP_SERVER_BINARY,
            WorkflowLspServerSupportProvider.SERVER_ID
        )
    }

    fun testServerIdIsNotEmpty() {
        assertTrue(
            "Server ID must not be empty",
            WorkflowLspServerSupportProvider.SERVER_ID.isNotEmpty()
        )
    }

    fun testServerNameIsNotEmpty() {
        assertTrue(
            "Server name must not be empty",
            WorkflowLspServerSupportProvider.SERVER_NAME.isNotEmpty()
        )
    }

    fun testLsp4ijFileNamePatternMappingIncludesKnownRoots() {
        val mapping = lsp4ijConfig()

        listOf("workflow.yaml", "workflow.yml", "app.yaml", "app.yml", "infra.yaml", "infra.yml")
            .forEach { fileName ->
                assertTrue("LSP4IJ mapping should include $fileName", mapping.contains(fileName))
            }

        listOf("wfctl.yaml", "wfctl.yml")
            .forEach { fileName ->
                assertFalse("LSP4IJ mapping should not include $fileName", mapping.contains(fileName))
            }
    }

    fun testLsp4ijFileNamePatternMappingMatchesRuntimeRoots() {
        val patterns = fileNamePatternMappingPatterns()

        assertEquals(
            "LSP4IJ runtime mapping should exactly match workflow root file names",
            WorkflowBundle.WORKFLOW_ROOT_FILE_NAMES,
            patterns
        )
    }

    fun testLsp4ijConfigDoesNotClaimDescriptorRuntimeFiltering() {
        val mapping = lsp4ijConfig()

        assertFalse(
            "LSP4IJ config should not claim descriptor-based runtime file filtering",
            mapping.contains("WorkflowLspServerDescriptor")
        )
        assertFalse(
            "LSP4IJ config should not claim configPaths are wired into LSP activation",
            mapping.contains("configPaths")
        )
        assertFalse(
            "LSP4IJ config should not claim content detection is wired into LSP activation",
            mapping.contains("content detection")
        )
    }

    fun testProviderCreatesWorkflowStreamConnectionProvider() {
        val provider = WorkflowLspServerSupportProvider().createConnectionProvider(project)

        assertTrue(
            "Provider should create the Workflow stream connection provider used by LSP4IJ",
            provider is WorkflowLspStreamConnectionProvider
        )
        val processProvider = provider as ProcessStreamConnectionProvider
        assertEquals("LSP process command should have one executable", 1, processProvider.commands.size)
        assertTrue(
            "LSP process command should resolve to the workflow-lsp-server binary",
            processProvider.commands.single().endsWith(WorkflowBundle.LSP_SERVER_BINARY)
        )
    }

    private fun lsp4ijConfig(): String =
        javaClass.getResourceAsStream("/META-INF/workflow-lsp4ij.xml")!!
            .bufferedReader()
            .readText()

    private fun fileNamePatternMappingPatterns(): Set<String> {
        val match = Regex("""fileNamePatternMapping\s+patterns="([^"]+)"""")
            .find(lsp4ijConfig())
        assertNotNull("LSP4IJ config should declare fileNamePatternMapping patterns", match)
        val patternsAttribute = match!!.groupValues[1]
        return patternsAttribute.split(";").toSet()
    }
}
