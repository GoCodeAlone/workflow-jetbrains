package com.gocodalone.workflow.ide

import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class WorkflowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        McpRegistration.checkAndRegister(project)
        checkLspAvailability(project)
    }

    private fun checkLspAvailability(project: Project) {
        val settings = WorkflowSettings.getInstance()
        if (!settings.enableLsp) return

        val lsp4ijInstalled = PluginManagerCore.getPlugin(
            PluginId.getId("com.redhat.devtools.lsp4ij")
        ) != null

        if (!lsp4ijInstalled) {
            val group = NotificationGroupManager.getInstance()
                .getNotificationGroup("Workflow Engine") ?: return

            group.createNotification(
                "Workflow LSP",
                "Install the <a href=\"https://plugins.jetbrains.com/plugin/23257-lsp4ij\">LSP4IJ</a> plugin for Workflow Engine language server support (autocompletion, diagnostics).",
                NotificationType.INFORMATION
            ).setListener(NotificationListener.URL_OPENING_LISTENER)
                .notify(project)
        }
    }
}
