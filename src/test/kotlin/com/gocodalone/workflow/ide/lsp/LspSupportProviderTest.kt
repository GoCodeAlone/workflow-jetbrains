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
}
