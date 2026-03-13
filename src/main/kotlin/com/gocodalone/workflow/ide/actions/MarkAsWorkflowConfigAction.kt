package com.gocodalone.workflow.ide.actions

import com.gocodalone.workflow.ide.settings.WorkflowProjectSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.ui.EditorNotifications

class MarkAsWorkflowConfigAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null &&
            (file.isDirectory || file.name.endsWith(".yaml") || file.name.endsWith(".yml"))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val projectBase = project.basePath ?: return

        val relativePath = file.path.removePrefix("$projectBase/")
        val pattern = if (file.isDirectory) "$relativePath/**/*.yaml" else relativePath

        val settings = WorkflowProjectSettings.getInstance(project)
        if (pattern !in settings.configPaths) {
            settings.configPaths = settings.configPaths + pattern
        }

        EditorNotifications.getInstance(project).updateAllNotifications()
    }
}
