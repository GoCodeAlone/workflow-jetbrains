package com.gocodalone.workflow.ide.marketplace

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.io.HttpRequests
import javax.swing.*
import javax.swing.table.DefaultTableModel

class MarketplaceToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MarketplacePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Plugins", false)
        toolWindow.contentManager.addContent(content)
    }
}

class MarketplacePanel(private val project: Project) : JPanel() {
    private val tableModel = DefaultTableModel(arrayOf("Name", "Version", "Tier", "Description"), 0)
    private val table = JBTable(tableModel)

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JScrollPane(table))
        loadPlugins()
    }

    private fun loadPlugins() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val json = HttpRequests.request("https://gocodealone.github.io/workflow-registry/v1/index.json")
                    .readString()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val plugins: List<Map<String, Any>> = Gson().fromJson(json, type)
                SwingUtilities.invokeLater {
                    tableModel.rowCount = 0
                    for (p in plugins) {
                        val isPrivate = p["private"] as? Boolean ?: false
                        if (!isPrivate) {
                            tableModel.addRow(arrayOf(
                                p["name"] ?: "",
                                p["version"] ?: "",
                                p["tier"] ?: "",
                                p["description"] ?: ""
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fail — marketplace is optional
            }
        }
    }
}
