package com.gocodalone.workflow.ide

import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class WorkflowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        McpRegistration.checkAndRegister(project)
        checkLspAvailability(project)
        scheduleVersionChecks(project)
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
                "Install the LSP4IJ plugin for Workflow Engine language server support (autocompletion, diagnostics).",
                NotificationType.INFORMATION
            ).addAction(NotificationAction.createSimpleExpiring("Install LSP4IJ") {
                BrowserUtil.browse("https://plugins.jetbrains.com/plugin/23257-lsp4ij", project)
            })
                .notify(project)
        }
    }

    /**
     * Schedules binary version checks on a pooled thread so they don't block startup.
     * Checks wfctl and workflow-lsp-server using their resolved paths (settings → PATH → cache).
     */
    private fun scheduleVersionChecks(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val settings = WorkflowSettings.getInstance()

            val wfctlPath = BinaryDownloader.resolveFromPathOrCache("wfctl", settings.wfctlPath)
            if (wfctlPath != null) {
                BinaryVersionCheck.check(project, "wfctl", wfctlPath)
            }

            val lspPath = BinaryDownloader.resolveFromPathOrCache("workflow-lsp-server", settings.lspServerPath)
            if (lspPath != null) {
                BinaryVersionCheck.check(project, "workflow-lsp-server", lspPath)
            }
        }
    }
}
