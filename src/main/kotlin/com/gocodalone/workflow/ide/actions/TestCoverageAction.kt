package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.vfs.VirtualFile

class TestCoverageAction : WfctlAction(
    text = "Test Coverage",
    description = "Run wfctl test --coverage to show pipeline test coverage"
) {
    override fun buildArgs(currentFile: VirtualFile?): List<String> {
        return if (currentFile != null) {
            listOf("test", "--coverage", currentFile.parent?.path ?: ".")
        } else {
            listOf("test", "--coverage", ".")
        }
    }

    override fun isApplicable(file: VirtualFile): Boolean {
        val name = file.name
        return name.endsWith("_test.yaml") || name.endsWith("_test.yml") ||
            name.endsWith(".yaml") || name.endsWith(".yml")
    }
}
