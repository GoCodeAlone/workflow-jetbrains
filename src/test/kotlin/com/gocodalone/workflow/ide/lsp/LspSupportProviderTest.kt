package com.gocodalone.workflow.ide.lsp

import junit.framework.TestCase

/**
 * Tests for WorkflowLspServerSupportProvider configuration and constants.
 * These are unit tests that validate the provider setup without requiring
 * a running LSP server or full IDE environment.
 */
class LspSupportProviderTest : TestCase() {

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
        val mapping = javaClass.getResourceAsStream("/META-INF/workflow-lsp4ij.xml")!!
            .bufferedReader()
            .readText()

        listOf("workflow.yaml", "workflow.yml", "app.yaml", "app.yml", "infra.yaml", "infra.yml")
            .forEach { fileName ->
                assertTrue("LSP4IJ mapping should include $fileName", mapping.contains(fileName))
            }

        listOf("wfctl.yaml", "wfctl.yml")
            .forEach { fileName ->
                assertFalse("LSP4IJ mapping should not include $fileName", mapping.contains(fileName))
            }
    }
}
