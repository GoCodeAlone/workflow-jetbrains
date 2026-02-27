package com.gocodalone.workflow.ide.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider

/**
 * Stream connection provider that launches workflow-lsp-server over stdio.
 */
class WorkflowLspStreamConnectionProvider(
    private val lspServerPath: String,
    private val project: Project
) : ProcessStreamConnectionProvider() {

    init {
        // Configure the command: workflow-lsp-server with stdio transport
        val commands = mutableListOf(lspServerPath)
        setCommands(commands)

        // Set working directory to the project root
        setWorkingDirectory(project.basePath)
    }
}
