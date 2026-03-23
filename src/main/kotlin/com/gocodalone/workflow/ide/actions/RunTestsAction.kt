package com.gocodalone.workflow.ide.actions

import com.gocodalone.workflow.ide.editor.TestResultService
import com.intellij.openapi.project.Project
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

    override fun onResult(project: Project, file: VirtualFile?, result: WfctlResult) {
        val results = TestResultService.parseOutput(result.stdout + "\n" + result.stderr)
        if (results.isEmpty()) return

        val service = TestResultService.getInstance(project)
        if (file != null && (file.name.endsWith("_test.yaml") || file.name.endsWith("_test.yml"))) {
            service.store(file.path, results)
        } else {
            // Workspace-wide run — store under every open _test.yaml so gutter icons appear everywhere
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).allEditors
                .mapNotNull { it.file }
                .filter { it.name.endsWith("_test.yaml") || it.name.endsWith("_test.yml") }
                .forEach { service.store(it.path, results) }
            service.store(file?.path ?: project.basePath ?: ".", results)
        }

        // Forward to JCEF webview if open
        service.sendToWebview(results)

        // Repaint open editors so gutter icons update immediately
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).allEditors
            .mapNotNull { it.file }
            .filter { it.name.endsWith("_test.yaml") || it.name.endsWith("_test.yml") }
            .forEach { vFile ->
                com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project)
                    .restart(com.intellij.psi.PsiManager.getInstance(project).findFile(vFile) ?: return@forEach)
            }
    }
}
