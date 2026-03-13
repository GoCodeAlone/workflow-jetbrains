package com.gocodalone.workflow.ide.settings

import com.gocodalone.workflow.ide.BinaryDownloader
import com.gocodalone.workflow.ide.WorkflowBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JOptionPane

class WorkflowSettingsConfigurable : Configurable {

    private val appSettings: WorkflowSettings = WorkflowSettings.getInstance()

    private lateinit var wfctlPathField: TextFieldWithBrowseButton
    private lateinit var lspServerPathField: TextFieldWithBrowseButton
    private lateinit var enableLspCheckbox: JBCheckBox
    private lateinit var autoRegisterMcpCheckbox: JBCheckBox
    private lateinit var wfctlInstallButton: JButton
    private lateinit var lspInstallButton: JButton

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
        autoRegisterMcpCheckbox = JBCheckBox("Prompt to register MCP server on project open")

        wfctlInstallButton = JButton("Install from GitHub Releases")
        wfctlInstallButton.addActionListener {
            installBinary(WorkflowBundle.WFCTL_BINARY, wfctlPathField, wfctlInstallButton)
        }

        lspInstallButton = JButton("Install from GitHub Releases")
        lspInstallButton.addActionListener {
            installBinary(WorkflowBundle.LSP_SERVER_BINARY, lspServerPathField, lspInstallButton)
        }

        updateInstallButtonStates()

        return panel {
            group("Binary Paths") {
                row("wfctl path:") {
                    cell(wfctlPathField).align(AlignX.FILL)
                }
                row {
                    cell(wfctlInstallButton)
                    comment("Downloads the latest wfctl binary for your platform")
                }
                row("LSP server path:") {
                    cell(lspServerPathField).align(AlignX.FILL)
                }
                row {
                    cell(lspInstallButton)
                    comment("Downloads the latest workflow-lsp-server binary for your platform")
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
                    comment("Writes wfctl MCP server entry to .mcp.json in the project root for AI assistant integration")
                }
            }
        }
    }

    private fun installBinary(binaryName: String, pathField: TextFieldWithBrowseButton, button: JButton) {
        button.isEnabled = false
        button.text = "Downloading..."

        BinaryDownloader.downloadWithProgress(null, binaryName).thenAccept { path ->
            ApplicationManager.getApplication().invokeLater {
                if (path != null) {
                    pathField.text = path
                    button.text = "Installed"
                } else {
                    button.text = "Install from GitHub Releases"
                    button.isEnabled = true
                }
            }
        }
    }

    private fun updateInstallButtonStates() {
        val wfctlResolved = BinaryDownloader.resolveFromPathOrCache(
            WorkflowBundle.WFCTL_BINARY, appSettings.wfctlPath
        )
        if (wfctlResolved != null) {
            wfctlInstallButton.text = "Installed"
            wfctlInstallButton.isEnabled = false
        }

        val lspResolved = BinaryDownloader.resolveFromPathOrCache(
            WorkflowBundle.LSP_SERVER_BINARY, appSettings.lspServerPath
        )
        if (lspResolved != null) {
            lspInstallButton.text = "Installed"
            lspInstallButton.isEnabled = false
        }
    }

    override fun isModified(): Boolean {
        return wfctlPathField.text != appSettings.wfctlPath ||
                lspServerPathField.text != appSettings.lspServerPath ||
                enableLspCheckbox.isSelected != appSettings.enableLsp ||
                autoRegisterMcpCheckbox.isSelected != appSettings.autoRegisterMcp
    }

    override fun apply() {
        appSettings.wfctlPath = wfctlPathField.text.trim()
        appSettings.lspServerPath = lspServerPathField.text.trim()
        appSettings.enableLsp = enableLspCheckbox.isSelected
        appSettings.autoRegisterMcp = autoRegisterMcpCheckbox.isSelected
    }

    override fun reset() {
        wfctlPathField.text = appSettings.wfctlPath
        lspServerPathField.text = appSettings.lspServerPath
        enableLspCheckbox.isSelected = appSettings.enableLsp
        autoRegisterMcpCheckbox.isSelected = appSettings.autoRegisterMcp
        updateInstallButtonStates()
    }
}
