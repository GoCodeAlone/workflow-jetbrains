package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.vfs.VirtualFile

/**
 * Runs `wfctl inspect -deps <current-file>`.
 */
class InspectAction : WfctlAction(
    text = "Inspect Dependencies",
    description = "Run wfctl inspect -deps to show module dependency graph"
) {
    override fun buildArgs(currentFile: VirtualFile?): List<String> {
        return if (currentFile != null) {
            listOf("inspect", "-deps", currentFile.path)
        } else {
            listOf("inspect", "-deps")
        }
    }
}
