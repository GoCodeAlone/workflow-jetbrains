package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.vfs.VirtualFile

/**
 * Runs `wfctl template validate --config <current-file>`.
 */
class ValidateAction : WfctlAction(
    text = "Validate Config",
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
