package com.gocodalone.workflow.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class ShowDslReferenceAction : AnAction("Show DSL Reference", "Open the Workflow DSL Reference panel", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow("Workflow DSL Reference")?.show()
    }
}
