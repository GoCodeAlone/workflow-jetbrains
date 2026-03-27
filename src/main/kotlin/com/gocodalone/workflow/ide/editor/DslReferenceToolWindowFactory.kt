package com.gocodalone.workflow.ide.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import javax.swing.JLabel

class DslReferenceToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val sections = DslReferencePanel.loadSections()
        val html = DslReferencePanel.buildHtml(sections)

        val component = try {
            val browser = JBCefBrowser()
            browser.loadHTML(html)
            browser.component
        } catch (e: Exception) {
            // JCEF not available — fall back to a label
            JLabel("DSL Reference requires JCEF support. Please use a JetBrains IDE with JCEF enabled.")
        }

        val content = ContentFactory.getInstance().createContent(component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
