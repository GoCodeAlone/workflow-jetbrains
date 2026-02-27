package com.gocodalone.workflow.ide.lsp

import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Describes the workflow-lsp-server configuration including command line and file associations.
 *
 * This descriptor is used by LSP4IJ to determine:
 * - Which files to send to the LSP server
 * - How to start the LSP server process
 * - The working directory for the server
 */
class WorkflowLspServerDescriptor(project: Project) {

    val project: Project = project

    /**
     * Determines if the LSP server should handle the given file.
     * Matches YAML files that appear to be workflow configs.
     */
    fun isSupportedFile(file: VirtualFile): Boolean {
        val name = file.name
        if (!name.endsWith(".yaml") && !name.endsWith(".yml")) {
            return false
        }

        // Match common workflow config names
        if (name == "workflow.yaml" || name == "workflow.yml" ||
            name == "app.yaml" || name == "app.yml"
        ) {
            return true
        }

        // Content-based detection
        return try {
            val text = String(file.contentsToByteArray(), Charsets.UTF_8)
            text.contains("modules:") && (text.contains("workflows:") || text.contains("pipelines:"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds the command line to start the LSP server.
     */
    fun buildCommandLine(): List<String> {
        val settings = WorkflowSettings.getInstance()
        val binaryPath = if (settings.lspServerPath.isNotBlank()) {
            settings.lspServerPath
        } else {
            "workflow-lsp-server"
        }
        return listOf(binaryPath)
    }
}
