package com.gocodalone.workflow.ide.livetemplate

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import org.jetbrains.yaml.YAMLLanguage

/**
 * Live template context that restricts workflow templates to YAML files.
 */
class WorkflowLiveTemplateContext : TemplateContextType("WORKFLOW_YAML") {

    override fun getPresentableName(): String = "Workflow YAML"

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file
        return file.language.isKindOf(YAMLLanguage.INSTANCE)
    }
}
