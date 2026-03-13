package com.gocodalone.workflow.ide.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JOptionPane

class WorkflowProjectSettingsConfigurableProvider(private val project: Project) : ConfigurableProvider() {
    override fun createConfigurable(): Configurable = WorkflowProjectSettingsConfigurable(project)
}

class WorkflowProjectSettingsConfigurable(private val project: Project) : Configurable {

    private val projectSettings: WorkflowProjectSettings = WorkflowProjectSettings.getInstance(project)

    private lateinit var configPathsModel: DefaultListModel<String>

    override fun getDisplayName(): String = "Workflow Engine"

    override fun createComponent(): JComponent {
        configPathsModel = DefaultListModel<String>()
        projectSettings.configPaths.forEach { configPathsModel.addElement(it) }

        val configPathsList = JBList(configPathsModel)
        val configPathsPanel = ToolbarDecorator.createDecorator(configPathsList)
            .setAddAction {
                val pattern = JOptionPane.showInputDialog(
                    null,
                    "Enter a glob pattern relative to project root:\n(e.g. config/app.yaml, config/**/*.yaml)",
                    "Add Workflow Config Path",
                    JOptionPane.PLAIN_MESSAGE
                )
                if (!pattern.isNullOrBlank()) {
                    configPathsModel.addElement(pattern.trim())
                }
            }
            .setRemoveAction {
                val idx = configPathsList.selectedIndex
                if (idx >= 0) configPathsModel.removeElementAt(idx)
            }
            .disableUpDownActions()
            .createPanel()

        return panel {
            group("Workflow Config Files") {
                row {
                    cell(configPathsPanel).align(AlignX.FILL).align(AlignY.FILL)
                }
                row {
                    comment("Glob patterns for files to treat as workflow configs (enables schema validation, visual editor). You can also right-click files/folders in the Project view and select 'Mark as Workflow Config'.")
                }
            }
        }
    }

    private fun getConfigPathsFromModel(): List<String> {
        return (0 until configPathsModel.size()).map { configPathsModel.getElementAt(it) }
    }

    override fun isModified(): Boolean {
        return getConfigPathsFromModel() != projectSettings.configPaths
    }

    override fun apply() {
        projectSettings.configPaths = getConfigPathsFromModel()
    }

    override fun reset() {
        configPathsModel.clear()
        projectSettings.configPaths.forEach { configPathsModel.addElement(it) }
    }
}
