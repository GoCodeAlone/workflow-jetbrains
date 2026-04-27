package com.gocodalone.workflow.ide.livetemplate

import com.gocodalone.workflow.ide.editor.WorkflowFileDetector
import com.gocodalone.workflow.ide.editor.WorkflowFileType
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

/**
 * Live template context that restricts workflow templates to Workflow config files.
 */
class WorkflowLiveTemplateContext : TemplateContextType("WORKFLOW_YAML") {

    override fun getPresentableName(): String = "Workflow YAML"

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file
        val virtualFile = file.virtualFile ?: return false
        return WorkflowFileDetector.detectFileType(file.project, virtualFile) in setOf(
            WorkflowFileType.CONFIG,
            WorkflowFileType.PARTIAL,
            WorkflowFileType.TEST
        )
    }
}
