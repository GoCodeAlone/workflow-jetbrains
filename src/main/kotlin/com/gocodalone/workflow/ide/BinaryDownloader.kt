package com.gocodalone.workflow.ide

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * Downloads workflow binaries (wfctl, workflow-lsp-server) from GitHub Releases.
 *
 * Binaries are stored under the IDE's plugin data directory:
 *   {pluginsPath}/workflow-engine/bin/{os}-{arch}/{binaryName}
 */
object BinaryDownloader {

    private val LOG = Logger.getInstance(BinaryDownloader::class.java)
    private const val GITHUB_API_URL = "https://api.github.com/repos/GoCodeAlone/workflow/releases/latest"
    private const val GITHUB_REPO = "GoCodeAlone/workflow"
    private const val NOTIFICATION_GROUP = "Workflow Engine"

    /**
     * Returns the platform suffix for GitHub release asset names (e.g., "darwin-arm64").
     */
    fun getPlatformSuffix(): String {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val os = when {
            osName.contains("mac") || osName.contains("darwin") -> "darwin"
            osName.contains("linux") -> "linux"
            osName.contains("windows") -> "windows"
            else -> throw UnsupportedOperationException("Unsupported OS: $osName")
        }

        val cpuArch = when {
            arch == "aarch64" || arch == "arm64" -> "arm64"
            arch == "amd64" || arch == "x86_64" -> "amd64"
            else -> "amd64"
        }

        return "$os-$cpuArch"
    }

    fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")

    /**
     * Returns the default binary storage path for a given binary name.
     */
    fun getDefaultBinaryPath(binaryName: String): String {
        val suffix = getPlatformSuffix()
        val fileName = if (isWindows()) "$binaryName.exe" else binaryName
        val basePath = try {
            PathManager.getPluginsPath()
        } catch (_: Exception) {
            null
        } ?: System.getProperty("java.io.tmpdir")
        val pluginDir = File(basePath, "workflow-engine/bin/$suffix")
        return File(pluginDir, fileName).absolutePath
    }

    /**
     * Checks if a binary exists at its default download location.
     */
    fun isDownloaded(binaryName: String): Boolean {
        return File(getDefaultBinaryPath(binaryName)).let { it.exists() && it.canExecute() }
    }

    /**
     * Resolves a binary path: settings → PATH → downloaded cache.
     * Returns the path if found, null otherwise.
     */
    fun resolveFromPathOrCache(binaryName: String, settingsPath: String): String? {
        // 1. Explicit setting
        if (settingsPath.isNotBlank()) {
            val f = File(settingsPath)
            if (f.exists() && f.canExecute()) return settingsPath
        }

        // 2. System PATH
        val exeName = if (isWindows()) "$binaryName.exe" else binaryName
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        for (dir in pathDirs) {
            val candidate = File(dir, exeName)
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }

        // 3. Previously downloaded
        val cached = getDefaultBinaryPath(binaryName)
        val cachedFile = File(cached)
        if (cachedFile.exists() && cachedFile.canExecute()) {
            return cached
        }

        return null
    }

    /**
     * Shows a notification prompting the user to download a missing binary.
     * Returns a future that resolves to the downloaded path, or null if skipped.
     */
    fun promptAndDownload(
        project: Project?,
        binaryName: String,
        promptMessage: String,
        downloadAction: String = "Install",
        skipAction: String = "Skip"
    ): CompletableFuture<String?> {
        val future = CompletableFuture<String?>()
        val destPath = getDefaultBinaryPath(binaryName)

        ApplicationManager.getApplication().invokeLater {
            val group = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
            if (group == null) {
                LOG.warn("Notification group '$NOTIFICATION_GROUP' not registered, skipping download prompt")
                future.complete(null)
                return@invokeLater
            }

            val notification = group.createNotification(
                "Workflow Engine",
                promptMessage,
                NotificationType.INFORMATION
            )

            notification.addAction(object : com.intellij.notification.NotificationAction(downloadAction) {
                override fun actionPerformed(
                    e: com.intellij.openapi.actionSystem.AnActionEvent,
                    notification: com.intellij.notification.Notification
                ) {
                    notification.expire()
                    downloadWithProgress(project, binaryName, destPath, future)
                }
            })

            notification.addAction(object : com.intellij.notification.NotificationAction(skipAction) {
                override fun actionPerformed(
                    e: com.intellij.openapi.actionSystem.AnActionEvent,
                    notification: com.intellij.notification.Notification
                ) {
                    notification.expire()
                    future.complete(null)
                }
            })

            notification.notify(project)
        }

        return future
    }

    fun downloadWithProgress(
        project: Project?,
        binaryName: String,
        destPath: String = getDefaultBinaryPath(binaryName),
        future: CompletableFuture<String?> = CompletableFuture()
    ): CompletableFuture<String?> {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Downloading $binaryName...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Fetching latest release info..."
                    indicator.fraction = 0.1

                    val tag = fetchLatestReleaseTag()
                    indicator.text = "Downloading $binaryName $tag..."
                    indicator.fraction = 0.3

                    downloadBinary(binaryName, tag, destPath)
                    indicator.fraction = 1.0

                    LOG.info("Downloaded $binaryName $tag to $destPath")

                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP)
                            ?.createNotification(
                                "$binaryName installed",
                                "Downloaded $tag to $destPath",
                                NotificationType.INFORMATION
                            )
                            ?.notify(project)
                    }

                    future.complete(destPath)
                } catch (e: Exception) {
                    LOG.warn("Failed to download $binaryName: ${e.message}", e)

                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP)
                            ?.createNotification(
                                "Failed to download $binaryName",
                                e.message ?: "Unknown error",
                                NotificationType.ERROR
                            )
                            ?.notify(project)
                    }
                    future.complete(null)
                }
            }
        })
        return future
    }

    private fun fetchLatestReleaseTag(): String {
        val body = HttpRequests.request(GITHUB_API_URL)
            .accept("application/vnd.github.v3+json")
            .userAgent("workflow-jetbrains")
            .connectTimeout(10_000)
            .readTimeout(10_000)
            .readString()

        val json = com.google.gson.JsonParser.parseString(body).asJsonObject
        return json.get("tag_name").asString
    }

    private fun downloadBinary(binaryName: String, tag: String, destPath: String) {
        val suffix = getPlatformSuffix()
        val assetName = if (isWindows()) "$binaryName-$suffix.exe" else "$binaryName-$suffix"
        val downloadUrl = "https://github.com/$GITHUB_REPO/releases/download/$tag/$assetName"

        val destFile = File(destPath)
        destFile.parentFile.mkdirs()

        // Follow redirects manually since GitHub release downloads redirect to CDN
        var currentUrl = downloadUrl
        var redirects = 0
        while (redirects < 5) {
            val url = URI(currentUrl).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "workflow-jetbrains")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307) {
                currentUrl = conn.getHeaderField("Location")
                    ?: throw RuntimeException("Redirect with no Location header")
                conn.disconnect()
                redirects++
                continue
            }

            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw RuntimeException("Download failed: HTTP $code for $currentUrl")
            }

            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            break
        }

        if (!isWindows()) {
            destFile.setExecutable(true, false)
        }
    }
}
