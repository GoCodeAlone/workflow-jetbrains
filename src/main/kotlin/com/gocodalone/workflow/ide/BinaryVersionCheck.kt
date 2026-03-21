package com.gocodalone.workflow.ide

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Checks if downloaded workflow binaries are outdated compared to the latest GitHub release.
 * Notifications are shown once per 24 hours per binary.
 */
object BinaryVersionCheck {

    private val LOG = Logger.getInstance(BinaryVersionCheck::class.java)
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val NOTIFICATION_GROUP = "Workflow Engine"

    /**
     * Checks a binary for available updates. Runs on the calling thread — callers should
     * invoke this on a background/pooled thread. All errors are silently ignored.
     *
     * @param project       the current project (for notification context)
     * @param binaryName    name of the binary (e.g. "wfctl", "workflow-lsp-server")
     * @param binaryPath    resolved path to the binary on disk
     */
    fun check(project: Project?, binaryName: String, binaryPath: String) {
        try {
            // Skip if binary doesn't exist at this path
            val file = File(binaryPath)
            if (!file.exists() || !file.canExecute()) {
                return
            }

            // Skip if checked within the last 24 hours
            val lastCheckKey = "workflow.lastVersionCheck.$binaryName"
            val props = PropertiesComponent.getInstance()
            val lastCheck = props.getLong(lastCheckKey, 0L)
            if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                LOG.debug("[$binaryName] skipping version check (last checked <24h ago)")
                return
            }

            val currentVersion = getCurrentVersion(binaryPath, binaryName) ?: run {
                LOG.warn("[$binaryName] could not determine current version")
                return
            }

            val latestTag = fetchLatestReleaseTag()

            // Save timestamp regardless of whether update is needed
            props.setValue(lastCheckKey, System.currentTimeMillis().toString())

            if (currentVersion == latestTag) {
                LOG.info("[$binaryName] up to date ($currentVersion)")
                return
            }

            LOG.info("[$binaryName] update available: $currentVersion → $latestTag")
            showUpdateNotification(project, binaryName, currentVersion, latestTag)

        } catch (e: Exception) {
            // Silently ignore all errors (network issues, binary errors, etc.)
            LOG.debug("[$binaryName] version check failed: ${e.message}")
        }
    }

    private fun getCurrentVersion(binaryPath: String, binaryName: String): String? {
        return try {
            val versionFlag = if (binaryName == "wfctl") "version" else "-version"
            val proc = ProcessBuilder(binaryPath, versionFlag)
                .redirectErrorStream(true)
                .start()

            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()

            // Extract version string like v0.3.51
            val match = Regex("v\\d+\\.\\d+\\.\\d+").find(output)
            match?.value ?: output.split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchLatestReleaseTag(): String {
        val body = com.intellij.util.io.HttpRequests
            .request("https://api.github.com/repos/GoCodeAlone/workflow/releases/latest")
            .accept("application/vnd.github.v3+json")
            .userAgent("workflow-jetbrains")
            .connectTimeout(10_000)
            .readTimeout(10_000)
            .readString()

        val json = com.google.gson.JsonParser.parseString(body).asJsonObject
        return json.get("tag_name").asString
    }

    private fun showUpdateNotification(
        project: Project?,
        binaryName: String,
        currentVersion: String,
        latestTag: String,
    ) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val group = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP) ?: return@invokeLater

            val notification = group.createNotification(
                "Workflow $binaryName update available",
                "$currentVersion → $latestTag",
                NotificationType.INFORMATION,
            )

            notification.addAction(object : com.intellij.notification.NotificationAction("Update") {
                override fun actionPerformed(
                    e: com.intellij.openapi.actionSystem.AnActionEvent,
                    notification: com.intellij.notification.Notification,
                ) {
                    notification.expire()
                    BinaryDownloader.downloadWithProgress(project, binaryName)
                }
            })

            notification.addAction(object : com.intellij.notification.NotificationAction("Dismiss") {
                override fun actionPerformed(
                    e: com.intellij.openapi.actionSystem.AnActionEvent,
                    notification: com.intellij.notification.Notification,
                ) {
                    notification.expire()
                }
            })

            notification.notify(project)
        }
    }
}
