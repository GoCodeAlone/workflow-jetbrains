package com.gocodalone.workflow.ide.actions

import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.OSProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Base class for wfctl command actions.
 *
 * Subclasses implement [buildArgs] to provide the wfctl subcommand and flags,
 * and optionally override [isApplicable] to restrict availability.
 */
abstract class WfctlAction(text: String, description: String) : AnAction(text, description, null) {

    companion object {
        const val NOTIFICATION_GROUP = "Workflow Engine"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && isApplicable(file)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val args = buildArgs(file)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running wfctl ${args.firstOrNull() ?: ""}...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val result = runWfctl(project, args, file?.parent?.path ?: project.basePath)
                ApplicationManager.getApplication().invokeLater {
                    showResult(project, args.firstOrNull() ?: "wfctl", result)
                }
            }
        })
    }

    /**
     * Returns the wfctl arguments for this action.
     * [currentFile] may be null if no file is open.
     */
    abstract fun buildArgs(currentFile: VirtualFile?): List<String>

    /**
     * Override to restrict the action to specific file types.
     * Default: enabled for any YAML file.
     */
    open fun isApplicable(file: VirtualFile): Boolean {
        return file.extension == "yaml" || file.extension == "yml"
    }

    private fun runWfctl(project: Project, args: List<String>, workDir: String?): WfctlResult {
        val settings = WorkflowSettings.getInstance()
        val wfctlPath = if (settings.wfctlPath.isNotBlank()) settings.wfctlPath else "wfctl"

        return try {
            val cmdLine = GeneralCommandLine(listOf(wfctlPath) + args).apply {
                if (workDir != null) setWorkDirectory(workDir)
                withEnvironment(System.getenv())
            }

            val processHandler = OSProcessHandler(cmdLine)
            val runner = CapturingProcessRunner(processHandler)
            val output = runner.runProcess(30_000) // 30s timeout

            WfctlResult(
                exitCode = output.exitCode,
                stdout = output.stdout,
                stderr = output.stderr
            )
        } catch (e: Exception) {
            WfctlResult(
                exitCode = -1,
                stdout = "",
                stderr = "Failed to run wfctl: ${e.message}\n\nMake sure wfctl is installed and its path is configured under Tools > Workflow Engine settings."
            )
        }
    }

    private fun showResult(project: Project, command: String, result: WfctlResult) {
        val type = if (result.exitCode == 0) NotificationType.INFORMATION else NotificationType.ERROR
        val title = if (result.exitCode == 0) "wfctl $command succeeded" else "wfctl $command failed (exit ${result.exitCode})"
        val content = buildString {
            if (result.stdout.isNotBlank()) append(result.stdout.trim())
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("<b>stderr:</b>\n").append(result.stderr.trim())
            }
            if (isEmpty()) append("(no output)")
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(title, content, type)
            .notify(project)
    }
}

data class WfctlResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}
