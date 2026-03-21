package com.gocodalone.workflow.ide.marketplace

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.gocodalone.workflow.ide.BinaryDownloader
import com.gocodalone.workflow.ide.WorkflowBundle
import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.OSProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.io.HttpRequests
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

private val LOG = Logger.getInstance(MarketplaceToolWindowFactory::class.java)

private const val REGISTRY_URL = "https://gocodealone.github.io/workflow-registry/v1/index.json"
private const val NOTIFICATION_GROUP = "Workflow Engine"

class MarketplaceToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MarketplacePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Plugins", false)
        toolWindow.contentManager.addContent(content)
    }
}

/** Read-only table model — prevents accidental in-place cell editing. */
private class ReadOnlyTableModel(columns: Array<String>) :
    DefaultTableModel(columns, 0) {
    override fun isCellEditable(row: Int, column: Int): Boolean = false
}

class MarketplacePanel(private val project: Project) : JPanel(BorderLayout()) {
    private val tableModel = ReadOnlyTableModel(arrayOf("Name", "Version", "Tier", "Description"))
    private val table = JBTable(tableModel)
    private val statusLabel = JLabel("Loading plugins\u2026", SwingConstants.CENTER)
    private val installButton = JButton("Install").apply { isEnabled = false }

    init {
        // Toolbar with Install button
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)
        toolbar.add(installButton)
        toolbar.add(Box.createHorizontalGlue())

        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        // Enable Install button when a row is selected
        table.selectionModel.addListSelectionListener {
            installButton.isEnabled = table.selectedRow >= 0
        }

        // Install on button click
        installButton.addActionListener {
            installSelectedPlugin()
        }

        // Install on double-click
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && table.selectedRow >= 0) {
                    installSelectedPlugin()
                }
            }
        })

        loadPlugins()
    }

    private fun installSelectedPlugin() {
        val row = table.selectedRow
        if (row < 0) return
        val pluginName = tableModel.getValueAt(row, 0)?.toString() ?: return

        val settings = WorkflowSettings.getInstance()
        val resolved = BinaryDownloader.resolveFromPathOrCache(WorkflowBundle.WFCTL_BINARY, settings.wfctlPath)
        if (resolved == null) {
            BinaryDownloader.promptAndDownload(
                project,
                WorkflowBundle.WFCTL_BINARY,
                "wfctl was not found. Download it to install plugins?"
            )
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Installing plugin $pluginName\u2026", false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Running wfctl plugin install $pluginName\u2026"

                val (exitCode, stdout, stderr) = try {
                    val cmdLine = GeneralCommandLine(listOf(resolved, "plugin", "install", pluginName)).apply {
                        project.basePath?.let { setWorkDirectory(it) }
                        withEnvironment(System.getenv())
                    }
                    val handler = OSProcessHandler(cmdLine)
                    val output = CapturingProcessRunner(handler).runProcess(60_000)
                    Triple(output.exitCode, output.stdout, output.stderr)
                } catch (e: Exception) {
                    Triple(-1, "", "Failed to run wfctl: ${e.message}")
                }

                ApplicationManager.getApplication().invokeLater {
                    val type = if (exitCode == 0) NotificationType.INFORMATION else NotificationType.ERROR
                    val title = if (exitCode == 0) "Plugin installed: $pluginName" else "Plugin install failed: $pluginName"
                    val content = buildString {
                        if (stdout.isNotBlank()) append(stdout.trim())
                        if (stderr.isNotBlank()) {
                            if (isNotEmpty()) append("\n\n")
                            append("<b>stderr:</b>\n").append(stderr.trim())
                        }
                        if (isEmpty()) append("(no output)")
                    }
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup(NOTIFICATION_GROUP)
                        ?.createNotification(title, content, type)
                        ?.notify(project)
                }
            }
        })
    }

    fun loadPlugins() {
        SwingUtilities.invokeLater {
            tableModel.rowCount = 0
            statusLabel.text = "Loading plugins\u2026"
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val json = HttpRequests.request(REGISTRY_URL).readString()
                val allPlugins = parseRegistryIndex(json)
                val publicPlugins = filterPublicPlugins(allPlugins)
                SwingUtilities.invokeLater {
                    tableModel.rowCount = 0
                    for (p in publicPlugins) {
                        tableModel.addRow(arrayOf(
                            p["name"]?.toString() ?: "",
                            p["version"]?.toString() ?: "",
                            p["tier"]?.toString() ?: "",
                            p["description"]?.toString() ?: ""
                        ))
                    }
                    val shown = publicPlugins.size
                    statusLabel.text = if (shown == 0) "No public plugins found." else "$shown plugin(s) available. Double-click or select a row and click Install."
                }
            } catch (e: HttpRequests.HttpStatusException) {
                LOG.warn("Workflow plugin registry returned HTTP ${e.statusCode}: $REGISTRY_URL")
                SwingUtilities.invokeLater {
                    statusLabel.text = "Could not load plugins (HTTP ${e.statusCode})."
                }
            } catch (e: JsonSyntaxException) {
                LOG.warn("Workflow plugin registry returned malformed JSON", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "Could not load plugins (invalid response)."
                }
            } catch (e: Exception) {
                LOG.warn("Workflow plugin registry unavailable", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "Could not load plugins (network error)."
                }
            }
        }
    }
}

/**
 * Parses the registry index JSON into a list of plugin maps.
 * Returns an empty list if the JSON is null, empty, or malformed.
 */
internal fun parseRegistryIndex(json: String): List<Map<String, Any>> {
    if (json.isBlank()) return emptyList()
    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
    return Gson().fromJson<List<Map<String, Any>>>(json, type) ?: emptyList()
}

/**
 * Filters out private plugins. Handles boolean and string "true"/"false"
 * for the `private` field, defaulting to public when the field is absent.
 */
internal fun filterPublicPlugins(plugins: List<Map<String, Any>>): List<Map<String, Any>> {
    return plugins.filter { p ->
        val isPrivate = when (val raw = p["private"]) {
            is Boolean -> raw
            is String -> raw.equals("true", ignoreCase = true)
            else -> false
        }
        !isPrivate
    }
}
