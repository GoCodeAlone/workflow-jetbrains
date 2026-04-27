package com.gocodalone.workflow.ide

object WorkflowBundle {
    const val PLUGIN_ID = "com.gocodalone.workflow"
    const val DISPLAY_NAME = "Workflow Engine"

    // GitHub release base URL for downloading binaries
    const val GITHUB_RELEASES_URL = "https://github.com/GoCodeAlone/workflow/releases/latest/download"

    // Default binary names
    const val WFCTL_BINARY = "wfctl"
    const val LSP_SERVER_BINARY = "workflow-lsp-server"

    // Workflow root config file names
    val WORKFLOW_ROOT_FILE_NAMES = setOf(
        "workflow.yaml",
        "workflow.yml",
        "app.yaml",
        "app.yml",
        "wfctl.yaml",
        "wfctl.yml",
        "infra.yaml",
        "infra.yml"
    )

    // Workflow config file patterns
    val WORKFLOW_FILE_PATTERNS = WORKFLOW_ROOT_FILE_NAMES + listOf(
        "*-workflow.yaml",
        "*-workflow.yml"
    )

    // Key that identifies a workflow config file by content
    const val WORKFLOW_CONTENT_KEY = "modules:"
}
