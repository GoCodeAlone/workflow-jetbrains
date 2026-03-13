package com.gocodalone.workflow.ide.lsp

import com.gocodalone.workflow.ide.BinaryDownloader
import com.gocodalone.workflow.ide.WorkflowBundle
import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * LSP4IJ server support provider that manages the workflow-lsp-server lifecycle.
 *
 * The provider:
 * 1. Checks if the LSP is enabled in settings
 * 2. Resolves the workflow-lsp-server binary path (settings -> PATH -> downloaded cache -> prompt)
 * 3. Starts the server over stdio transport for YAML workflow files
 */
class WorkflowLspServerSupportProvider : LanguageServerFactory {

    companion object {
        const val SERVER_ID = "workflow-lsp-server"
        const val SERVER_NAME = "Workflow LSP"
    }

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        val settings = WorkflowSettings.getInstance()
        val lspBinaryPath = resolveLspServerPath(settings, project)
        return WorkflowLspStreamConnectionProvider(lspBinaryPath, project)
    }

    override fun createClientFeatures(): LSPClientFeatures {
        return LSPClientFeatures()
    }

    /**
     * Resolves the LSP server binary path using the following priority:
     * 1. Explicit path from settings
     * 2. Binary on system PATH
     * 3. Previously downloaded binary in plugin cache
     * 4. Prompt user to download from GitHub Releases
     */
    private fun resolveLspServerPath(settings: WorkflowSettings, project: Project): String {
        val resolved = BinaryDownloader.resolveFromPathOrCache(
            WorkflowBundle.LSP_SERVER_BINARY,
            settings.lspServerPath
        )

        if (resolved != null) {
            return resolved
        }

        // Binary not found — prompt user to download
        BinaryDownloader.promptAndDownload(
            project,
            WorkflowBundle.LSP_SERVER_BINARY,
            "workflow-lsp-server was not found. Download it from GitHub Releases for LSP support?",
            downloadAction = "Install",
            skipAction = "Disable LSP"
        ).thenAccept { path ->
            if (path == null) {
                // User chose to disable LSP
                settings.enableLsp = false
            }
        }

        // Return the expected path — LSP4IJ will retry when binary appears
        return BinaryDownloader.getDefaultBinaryPath(WorkflowBundle.LSP_SERVER_BINARY)
    }
}
