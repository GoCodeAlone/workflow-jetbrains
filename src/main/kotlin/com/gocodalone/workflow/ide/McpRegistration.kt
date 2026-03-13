package com.gocodalone.workflow.ide

import com.gocodalone.workflow.ide.settings.WorkflowProjectSettings
import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

object McpRegistration {

    private val LOG = Logger.getInstance(McpRegistration::class.java)
    private const val MCP_FILE = ".mcp.json"
    private const val SERVER_KEY = "workflow"

    fun checkAndRegister(project: Project) {
        val appSettings = WorkflowSettings.getInstance()
        if (!appSettings.autoRegisterMcp) return

        val projectSettings = WorkflowProjectSettings.getInstance(project)
        if (projectSettings.mcpRegistered || projectSettings.mcpRegistrationDeclined) return

        val basePath = project.basePath ?: return
        val mcpFile = File(basePath, MCP_FILE)

        // Check if workflow server already registered
        if (mcpFile.exists()) {
            try {
                val content = mcpFile.readText()
                if (content.contains("\"$SERVER_KEY\"")) {
                    projectSettings.mcpRegistered = true
                    return
                }
            } catch (_: Exception) {
                // Ignore read errors
            }
        }

        // Resolve wfctl path
        val wfctlPath = BinaryDownloader.resolveFromPathOrCache(
            WorkflowBundle.WFCTL_BINARY, appSettings.wfctlPath
        ) ?: return // Can't register without wfctl

        promptRegistration(project, wfctlPath, mcpFile, projectSettings)
    }

    private fun promptRegistration(
        project: Project,
        wfctlPath: String,
        mcpFile: File,
        projectSettings: WorkflowProjectSettings
    ) {
        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup("Workflow Engine") ?: return

        val notification = group.createNotification(
            "Workflow MCP Server",
            "Register wfctl as an MCP server in .mcp.json for AI assistant integration?",
            NotificationType.INFORMATION
        )

        notification.addAction(object : com.intellij.notification.NotificationAction("Add") {
            override fun actionPerformed(
                e: com.intellij.openapi.actionSystem.AnActionEvent,
                notification: com.intellij.notification.Notification
            ) {
                notification.expire()
                writeMcpConfig(mcpFile, wfctlPath)
                projectSettings.mcpRegistered = true
                LOG.info("MCP server config written to ${mcpFile.absolutePath}")
            }
        })

        notification.addAction(object : com.intellij.notification.NotificationAction("Not Now") {
            override fun actionPerformed(
                e: com.intellij.openapi.actionSystem.AnActionEvent,
                notification: com.intellij.notification.Notification
            ) {
                notification.expire()
            }
        })

        notification.addAction(object : com.intellij.notification.NotificationAction("Never for this project") {
            override fun actionPerformed(
                e: com.intellij.openapi.actionSystem.AnActionEvent,
                notification: com.intellij.notification.Notification
            ) {
                notification.expire()
                projectSettings.mcpRegistrationDeclined = true
            }
        })

        notification.notify(project)
    }

    private fun writeMcpConfig(mcpFile: File, wfctlPath: String) {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()

        val existing: MutableMap<String, Any> = if (mcpFile.exists()) {
            try {
                val content = mcpFile.readText()
                gson.fromJson(content, MutableMap::class.java) as? MutableMap<String, Any>
                    ?: mutableMapOf()
            } catch (_: Exception) {
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }

        @Suppress("UNCHECKED_CAST")
        val servers = (existing["mcpServers"] as? MutableMap<String, Any>) ?: mutableMapOf()
        servers[SERVER_KEY] = mapOf(
            "type" to "stdio",
            "command" to wfctlPath,
            "args" to listOf("mcp"),
        )
        existing["mcpServers"] = servers

        mcpFile.writeText(gson.toJson(existing) + "\n")
    }
}
