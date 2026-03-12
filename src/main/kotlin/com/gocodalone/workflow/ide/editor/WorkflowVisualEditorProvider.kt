package com.gocodalone.workflow.ide.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser

class WorkflowVisualEditorAction : AnAction("Open Visual Editor", "Open workflow visual editor", null) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!WorkflowFileDetector.isWorkflowFile(project, file)) return

        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Workflow Visual Editor") ?: return
        toolWindow.show {
            val browser = JBCefBrowser()
            val bridge = WorkflowBridge(project, file, browser)

            // Load the bundled editor HTML
            val htmlUrl = javaClass.getResource("/editor/index.html")?.toExternalForm() ?: return@show
            browser.loadURL(htmlUrl)
            bridge.initialize()

            val content = ContentFactory.getInstance()
                .createContent(browser.component, file.name, false)
            content.setDisposer { bridge.dispose() }
            toolWindow.contentManager.removeAllContents(true)
            toolWindow.contentManager.addContent(content)
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
        // Content is created dynamically when action is triggered
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
