package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.vfs.VirtualFile

/**
 * Runs `wfctl template validate` on the current file.
 *
 * This action is a direct alias of ValidateAction exposed under a distinct action ID
 * so it can be assigned to a separate keyboard shortcut if desired.
 */
class WorkflowTemplateValidateAction : WfctlAction(
    text = "Template Validate",
    description = "Run wfctl template validate on the current workflow config file"
) {
    override fun buildArgs(currentFile: VirtualFile?): List<String> {
        return if (currentFile != null) {
            listOf("template", "validate", "--config", currentFile.path)
        } else {
            listOf("template", "validate")
        }
    }
}
