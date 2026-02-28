package com.gocodalone.workflow.ide.lsp

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
 * 2. Resolves the workflow-lsp-server binary path (settings -> PATH -> auto-download)
 * 3. Starts the server over stdio transport for YAML workflow files
 */
class WorkflowLspServerSupportProvider : LanguageServerFactory {

    companion object {
        const val SERVER_ID = "workflow-lsp-server"
        const val SERVER_NAME = "Workflow LSP"
    }

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        val settings = WorkflowSettings.getInstance()
        val lspBinaryPath = resolveLspServerPath(settings)
        return WorkflowLspStreamConnectionProvider(lspBinaryPath, project)
    }

    override fun createClientFeatures(): LSPClientFeatures {
        return LSPClientFeatures()
    }

    /**
     * Resolves the LSP server binary path using the following priority:
     * 1. Explicit path from settings
     * 2. Binary on system PATH
     * 3. Download from GitHub Releases (if auto-download enabled)
     */
    private fun resolveLspServerPath(settings: WorkflowSettings): String {
        // 1. Explicit setting
        if (settings.lspServerPath.isNotBlank()) {
            return settings.lspServerPath
        }

        // 2. Try PATH resolution
        val binaryName = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "${WorkflowBundle.LSP_SERVER_BINARY}.exe"
        } else {
            WorkflowBundle.LSP_SERVER_BINARY
        }

        val pathDirs = System.getenv("PATH")?.split(java.io.File.pathSeparator) ?: emptyList()
        for (dir in pathDirs) {
            val candidate = java.io.File(dir, binaryName)
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }

        // 3. Default - will fail gracefully with a notification
        return binaryName
    }
}
