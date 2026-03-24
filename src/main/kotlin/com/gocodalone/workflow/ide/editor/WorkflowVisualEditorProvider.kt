package com.gocodalone.workflow.ide.editor

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.CefApp
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class WorkflowVisualEditorAction : AnAction("Open Visual Editor", "Open workflow visual editor", null) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!file.name.endsWith(".yaml") && !file.name.endsWith(".yml")) return

        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Workflow Visual Editor") ?: return
        toolWindow.show {
            VisualEditorLoader.loadFile(project, file, toolWindow)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && file != null &&
            (file.name.endsWith(".yaml") || file.name.endsWith(".yml"))
    }
}

class WorkflowVisualEditorToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Try to load the currently open file
        val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val selectedFileType = selectedFile?.let { WorkflowFileDetector.detectFileType(project, it) }
        when (selectedFileType) {
            WorkflowFileType.CONFIG -> VisualEditorLoader.loadFile(project, selectedFile!!, toolWindow)
            WorkflowFileType.PARTIAL -> VisualEditorLoader.loadPartialFile(project, selectedFile!!, toolWindow)
            else -> VisualEditorLoader.showPlaceholder(toolWindow)
        }

        // Listen for file selection changes to auto-update the editor
        project.messageBus.connect(toolWindow.disposable).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val file = event.newFile ?: return
                    if (!toolWindow.isVisible) return
                    val fileType = WorkflowFileDetector.detectFileType(project, file)
                    when (fileType) {
                        WorkflowFileType.PARTIAL -> VisualEditorLoader.loadPartialFile(project, file, toolWindow)
                        WorkflowFileType.CONFIG -> VisualEditorLoader.loadFile(project, file, toolWindow)
                        else -> Unit
                    }
                }
            }
        )
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

/** Shared logic for loading workflow files into the visual editor tool window. */
private object VisualEditorLoader {
    private var schemeHandlerRegistered = false

    fun showPlaceholder(toolWindow: ToolWindow) {
        val label = JLabel(
            "<html><center>Open a workflow YAML file to see the visual editor.<br><br>" +
                "You can also right-click a YAML file tab<br>and select " +
                "<b>Open Workflow Visual Editor</b>.</center></html>",
            SwingConstants.CENTER
        )
        val panel = JPanel(BorderLayout()).apply { add(label, BorderLayout.CENTER) }
        val content = ContentFactory.getInstance().createContent(panel, "Visual Editor", false)
        toolWindow.contentManager.removeAllContents(true)
        toolWindow.contentManager.addContent(content)
    }

    /**
     * Attempts to resolve the workspace for a partial file and load the merged config.
     * Falls back to an info notification if resolution fails.
     */
    fun loadPartialFile(project: Project, file: VirtualFile, toolWindow: ToolWindow) {
        val resolved = WorkspaceResolver().resolveFromFile(file.path)
        if (resolved != null) {
            val rootVirtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(resolved.rootConfig)
            if (rootVirtualFile != null) {
                loadFile(project, rootVirtualFile, toolWindow, resolved)
                return
            }
        }
        // Resolution failed — show placeholder + info notification
        showPlaceholder(toolWindow)
        notifyPartialFile(project, file)
    }

    private fun notifyPartialFile(project: Project, file: VirtualFile) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Workflow Engine")
            ?.createNotification(
                "Partial Workflow Config",
                "\"${file.name}\" is a partial workflow config. Open the root config file for the visual editor.",
                NotificationType.INFORMATION
            )
            ?.notify(project)
    }

    fun loadFile(
        project: Project,
        file: VirtualFile,
        toolWindow: ToolWindow,
        resolved: WorkspaceResolver.ResolvedWorkspace? = null,
    ) {
        val browser = JBCefBrowser()

        // Register scheme handler AFTER browser creation ensures CEF is initialized
        ensureSchemeHandler()

        val bridge = WorkflowBridge(project, file, browser)
        if (resolved != null) bridge.resolvedWorkspace = resolved
        browser.loadURL(EditorSchemeHandlerFactory.BASE_URL)
        bridge.initialize()

        val content = ContentFactory.getInstance()
            .createContent(browser.component, file.name, false)
        content.setDisposer { bridge.dispose() }
        toolWindow.contentManager.removeAllContents(true)
        toolWindow.contentManager.addContent(content)
    }

    private fun ensureSchemeHandler() {
        if (schemeHandlerRegistered) return
        try {
            CefApp.getInstance().registerSchemeHandlerFactory(
                "https",
                EditorSchemeHandlerFactory.DOMAIN,
                EditorSchemeHandlerFactory()
            )
            schemeHandlerRegistered = true
        } catch (e: Exception) {
            // CEF not ready — will retry on next loadFile call
        }
    }
}
