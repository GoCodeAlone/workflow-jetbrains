package com.gocodalone.workflow.ide.marketplace

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.io.HttpRequests
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

private val LOG = Logger.getInstance(MarketplaceToolWindowFactory::class.java)

private const val REGISTRY_URL = "https://gocodealone.github.io/workflow-registry/v1/index.json"

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

    init {
        add(JScrollPane(table), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        loadPlugins()
    }

    fun loadPlugins() {
        SwingUtilities.invokeLater {
            tableModel.rowCount = 0
            statusLabel.text = "Loading plugins\u2026"
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val json = HttpRequests.request(REGISTRY_URL).readString()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val plugins: List<Map<String, Any>> = Gson().fromJson(json, type)
                SwingUtilities.invokeLater {
                    tableModel.rowCount = 0
                    var shown = 0
                    for (p in plugins) {
                        // Gson deserialises JSON booleans as Boolean, but guard against
                        // string "true"/"false" in case the registry schema evolves.
                        val isPrivate = when (val raw = p["private"]) {
                            is Boolean -> raw
                            is String -> raw.equals("true", ignoreCase = true)
                            else -> false
                        }
                        if (!isPrivate) {
                            tableModel.addRow(arrayOf(
                                p["name"]?.toString() ?: "",
                                p["version"]?.toString() ?: "",
                                p["tier"]?.toString() ?: "",
                                p["description"]?.toString() ?: ""
                            ))
                            shown++
                        }
                    }
                    statusLabel.text = if (shown == 0) "No public plugins found." else "$shown plugin(s) available."
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
