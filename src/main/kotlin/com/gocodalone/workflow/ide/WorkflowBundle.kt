package com.gocodalone.workflow.ide

import java.nio.file.FileSystems
import java.nio.file.Path

object WorkflowBundle {
    const val PLUGIN_ID = "com.gocodalone.workflow"
    const val DISPLAY_NAME = "Workflow Engine"

    // GitHub release base URL for downloading binaries
    const val GITHUB_RELEASES_URL = "https://github.com/GoCodeAlone/workflow/releases/latest/download"

    // Default binary names
    const val WFCTL_BINARY = "wfctl"
    const val LSP_SERVER_BINARY = "workflow-lsp-server"

    // Workflow app/infra root config file names handled by the app config schema and LSP.
    val WORKFLOW_ROOT_FILE_NAMES = setOf(
        "workflow.yaml",
        "workflow.yml",
        "app.yaml",
        "app.yml",
        "infra.yaml",
        "infra.yml"
    )

    // wfctl manifests use a plugin/registry manifest shape, not the app config shape.
    val WFCTL_MANIFEST_FILE_NAMES = setOf(
        "wfctl.yaml",
        "wfctl.yml"
    )

    // Workflow config file patterns
    val WORKFLOW_FILE_PATTERNS = WORKFLOW_ROOT_FILE_NAMES + listOf(
        "*-workflow.yaml",
        "*-workflow.yml"
    )

    fun isWorkflowConfigFileName(fileName: String): Boolean =
        WORKFLOW_FILE_PATTERNS.any { pattern ->
            try {
                FileSystems.getDefault().getPathMatcher("glob:$pattern").matches(Path.of(fileName))
            } catch (_: Exception) {
                fileName == pattern
            }
        }

    // Key that identifies a workflow config file by content
    const val WORKFLOW_CONTENT_KEY = "modules:"
}
