package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.vfs.VirtualFile

/**
 * Runs `wfctl schema` to print the workflow config JSON schema.
 */
class WorkflowSchemaAction : WfctlAction(
    text = "Show Schema",
    description = "Run wfctl schema to display the workflow config schema"
) {
    override fun buildArgs(currentFile: VirtualFile?): List<String> {
        return listOf("schema")
    }

    override fun isApplicable(file: VirtualFile): Boolean = true
}
