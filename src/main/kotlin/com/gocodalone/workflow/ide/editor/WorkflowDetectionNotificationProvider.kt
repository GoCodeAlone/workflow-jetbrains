package com.gocodalone.workflow.ide.editor

import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import java.nio.file.FileSystems

class WorkflowDetectionNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {

    override fun getKey() = KEY

    override fun createNotificationPanel(
        file: VirtualFile,
        editor: FileEditor,
        project: Project,
    ): EditorNotificationPanel? {
        val settings = WorkflowSettings.getInstance()
        if (settings.suppressDetectionPrompt) return null
        if (isExplicitMatch(file, project)) return null
        if (!isContentMatch(file)) return null

        return EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
            text = "This looks like a Workflow config."
            createActionLabel("Open Visual Editor") {
                val action = WorkflowVisualEditorAction()
                val dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                    .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                    .add(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE, file)
                    .build()
                val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext(
                    "notification", null, dataContext
                )
                action.actionPerformed(event)
            }
            createActionLabel("Always for this file") {
                val relativePath = file.path.removePrefix("${project.basePath}/")
                settings.configPaths = settings.configPaths + relativePath
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
            createActionLabel("Don't ask again") {
                settings.suppressDetectionPrompt = true
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
        }
    }

    private fun isExplicitMatch(file: VirtualFile, project: Project): Boolean {
        val settings = WorkflowSettings.getInstance()
        val configPaths = settings.configPaths
        if (configPaths.isEmpty()) return false
        val projectBase = project.basePath ?: return false
        val relativePath = file.path.removePrefix("$projectBase/")
        for (pattern in configPaths) {
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            if (matcher.matches(java.nio.file.Path.of(relativePath))) return true
        }
        return false
    }

    private fun isContentMatch(file: VirtualFile): Boolean {
        if (!file.name.endsWith(".yaml") && !file.name.endsWith(".yml")) return false
        return try {
            val content = String(file.contentsToByteArray(), Charsets.UTF_8)
            val lines = content.lineSequence().take(50).toList()
            val hasModules = lines.any { it.trimStart().startsWith("modules:") }
            val hasWorkflows = lines.any { it.trimStart().startsWith("workflows:") }
            hasModules && hasWorkflows
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        val KEY = com.intellij.openapi.util.Key.create<EditorNotificationPanel>(
            "com.gocodalone.workflow.ide.editor.WorkflowDetectionNotificationProvider"
        )
    }
}
