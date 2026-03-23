package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.vfs.VirtualFile

class RunTestsAction : WfctlAction(
    text = "Run Tests",
    description = "Run wfctl test on the current workflow test file or project"
) {
    override fun buildArgs(currentFile: VirtualFile?): List<String> {
        return if (currentFile != null &&
            (currentFile.name.endsWith("_test.yaml") || currentFile.name.endsWith("_test.yml"))
        ) {
            listOf("test", currentFile.path)
        } else {
            listOf("test", ".")
        }
    }

    override fun isApplicable(file: VirtualFile): Boolean {
        val name = file.name
        return name.endsWith("_test.yaml") || name.endsWith("_test.yml") ||
            name.endsWith(".yaml") || name.endsWith(".yml")
    }
}
