package com.gocodalone.workflow.ide.livetemplate

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WorkflowLiveTemplateContextTest : BasePlatformTestCase() {

    fun testContextAppliesToWorkflowRootYaml() {
        val context = WorkflowLiveTemplateContext()
        val file = myFixture.configureByText(
            "workflow.yaml",
            """
            modules:
              - name: web
                type: http.server
            workflows:
              http:
                routes: []
            """.trimIndent()
        )

        assertTrue(
            "Workflow live templates should apply to workflow root YAML files",
            context.isInContext(TemplateActionContext.expanding(file, 0))
        )
    }

    fun testContextRejectsWfctlManifest() {
        val context = WorkflowLiveTemplateContext()
        val file = myFixture.configureByText(
            "wfctl.yaml",
            """
            plugins:
              - name: auth
                source: registry.example.com/workflow-plugin-auth
            registries:
              - name: default
                url: https://registry.example.com
            """.trimIndent()
        )

        assertFalse(
            "Workflow live templates should not apply to wfctl manifests",
            context.isInContext(TemplateActionContext.expanding(file, 0))
        )
    }

    fun testContextRejectsGenericYaml() {
        val context = WorkflowLiveTemplateContext()
        val file = myFixture.configureByText(
            "database.yaml",
            """
            host: localhost
            port: 5432
            """.trimIndent()
        )

        assertFalse(
            "Workflow live templates should not apply to generic YAML files",
            context.isInContext(TemplateActionContext.expanding(file, 0))
        )
    }

    fun testLiveTemplateXmlUsesWorkflowContextInsteadOfGlobalYaml() {
        val templates = javaClass.getResourceAsStream("/liveTemplates/workflow.xml")!!
            .bufferedReader()
            .readText()

        assertTrue(
            "Workflow live templates should be bound to the Workflow YAML context",
            templates.contains("""<option name="WORKFLOW_YAML" value="true"""")
        )
        assertFalse(
            "Workflow live templates should not be bound to every YAML file",
            templates.contains("""<option name="YAML" value="true"""")
        )
    }
}
