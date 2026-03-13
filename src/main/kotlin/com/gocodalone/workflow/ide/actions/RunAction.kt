package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.vfs.VirtualFile

class RunAction : WfctlAction(
    text = "Run Workflow",
    description = "Run wfctl run -config on the current workflow config file"
) {
    override fun buildArgs(currentFile: VirtualFile?): List<String> {
        return if (currentFile != null) {
            listOf("run", "-config", currentFile.path)
        } else {
            listOf("run")
        }
    }
}
