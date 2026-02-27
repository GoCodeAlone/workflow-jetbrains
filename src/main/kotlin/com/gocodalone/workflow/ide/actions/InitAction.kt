package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

/**
 * Shows a template picker dialog and runs `wfctl template init --template <name> --output <dir>`.
 */
class InitAction : WfctlAction(
    text = "Init from Template...",
    description = "Scaffold a new workflow config from a built-in template"
) {
    private val templates = listOf(
        "http-api",
        "messaging",
        "statemachine",
        "microservice",
        "ecommerce"
    )

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Always enable — init doesn't require a specific file to be open
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val selected = Messages.showEditableChooseDialog(
            "Select a workflow template to scaffold:",
            "Init Workflow from Template",
            Messages.getQuestionIcon(),
            templates.toTypedArray(),
            templates.first(),
            null
        ) ?: return

        val outputDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let {
            if (it.isDirectory) it.path else it.parent?.path
        } ?: project.basePath ?: return

        // Delegate to base class execution with constructed args
        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            object : com.intellij.openapi.progress.Task.Backgroundable(
                project, "Running wfctl template init...", false
            ) {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    indicator.isIndeterminate = true
                    // The base class runWfctl is private, so we duplicate the invocation here
                    val settings = com.gocodalone.workflow.ide.settings.WorkflowSettings.getInstance()
                    val wfctlPath = if (settings.wfctlPath.isNotBlank()) settings.wfctlPath else "wfctl"
                    val args = listOf("template", "init", "--template", selected, "--output", outputDir)
                    try {
                        val cmdLine = com.intellij.execution.configurations.GeneralCommandLine(
                            listOf(wfctlPath) + args
                        ).apply {
                            setWorkDirectory(outputDir)
                            withEnvironment(System.getenv())
                        }
                        val processHandler = com.intellij.execution.process.OSProcessHandler(cmdLine)
                        val runner = com.intellij.execution.process.CapturingProcessRunner(processHandler)
                        val output = runner.runProcess(30_000)

                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            val vfsOut = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                                .refreshAndFindFileByPath(outputDir)
                            vfsOut?.refresh(true, true)

                            if (output.exitCode == 0) {
                                Messages.showInfoMessage(
                                    project,
                                    "Template '$selected' scaffolded in $outputDir",
                                    "wfctl template init"
                                )
                            } else {
                                Messages.showErrorDialog(
                                    project,
                                    "wfctl template init failed:\n${output.stderr}",
                                    "wfctl template init"
                                )
                            }
                        }
                    } catch (ex: Exception) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "Failed to run wfctl: ${ex.message}",
                                "wfctl template init"
                            )
                        }
                    }
                }
            }
        )
    }

    override fun buildArgs(currentFile: VirtualFile?): List<String> {
        // Not used — actionPerformed is fully overridden
        return listOf("template", "init")
    }
}
