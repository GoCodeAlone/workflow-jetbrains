package com.gocodalone.workflow.ide.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Settings page registered under Tools > Workflow Engine.
 *
 * Provides UI for:
 * - wfctl binary path
 * - LSP server binary path
 * - Enable/disable LSP integration
 * - Auto-register MCP server with IDE AI assistant
 */
class WorkflowSettingsConfigurable : Configurable {

    private val settings: WorkflowSettings = WorkflowSettings.getInstance()

    private lateinit var wfctlPathField: TextFieldWithBrowseButton
    private lateinit var lspServerPathField: TextFieldWithBrowseButton
    private lateinit var enableLspCheckbox: JBCheckBox
    private lateinit var autoRegisterMcpCheckbox: JBCheckBox

    override fun getDisplayName(): String = "Workflow Engine"

    override fun createComponent(): JComponent {
        wfctlPathField = TextFieldWithBrowseButton()
        wfctlPathField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.singleFile()
                .withTitle("Select wfctl Binary")
                .withDescription("Path to the wfctl command-line tool")
        )

        lspServerPathField = TextFieldWithBrowseButton()
        lspServerPathField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.singleFile()
                .withTitle("Select workflow-lsp-server Binary")
                .withDescription("Path to the Workflow Engine LSP server binary")
        )

        enableLspCheckbox = JBCheckBox("Enable LSP server integration (requires workflow-lsp-server)")
        autoRegisterMcpCheckbox = JBCheckBox("Auto-register MCP server with AI Assistant")

        return panel {
            group("Binary Paths") {
                row("wfctl path:") {
                    cell(wfctlPathField).align(AlignX.FILL)
                }
                row {
                    comment("Leave blank to auto-detect from PATH or download from GitHub Releases")
                }
                row("LSP server path:") {
                    cell(lspServerPathField).align(AlignX.FILL)
                }
                row {
                    comment("Leave blank to auto-detect from PATH or download from GitHub Releases")
                }
            }
            group("Features") {
                row {
                    cell(enableLspCheckbox)
                }
                row {
                    comment("Provides hover documentation, go-to-definition, and diagnostics in workflow YAML files")
                }
                row {
                    cell(autoRegisterMcpCheckbox)
                }
                row {
                    comment("Registers wfctl as the MCP server with the IDE's AI assistant for context-aware assistance")
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return wfctlPathField.text != settings.wfctlPath ||
                lspServerPathField.text != settings.lspServerPath ||
                enableLspCheckbox.isSelected != settings.enableLsp ||
                autoRegisterMcpCheckbox.isSelected != settings.autoRegisterMcp
    }

    override fun apply() {
        settings.wfctlPath = wfctlPathField.text.trim()
        settings.lspServerPath = lspServerPathField.text.trim()
        settings.enableLsp = enableLspCheckbox.isSelected
        settings.autoRegisterMcp = autoRegisterMcpCheckbox.isSelected
    }

    override fun reset() {
        wfctlPathField.text = settings.wfctlPath
        lspServerPathField.text = settings.lspServerPath
        enableLspCheckbox.isSelected = settings.enableLsp
        autoRegisterMcpCheckbox.isSelected = settings.autoRegisterMcp
    }
}
