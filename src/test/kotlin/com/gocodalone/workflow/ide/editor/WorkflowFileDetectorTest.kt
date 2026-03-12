package com.gocodalone.workflow.ide.editor

import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WorkflowFileDetectorTest : BasePlatformTestCase() {

    override fun tearDown() {
        WorkflowSettings.getInstance().configPaths = emptyList()
        super.tearDown()
    }

    fun testDetectsWorkflowYaml() {
        val file = myFixture.configureByText(
            "app.yaml",
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
            "Expected isWorkflowFile to return true for valid workflow YAML",
            WorkflowFileDetector.isWorkflowFile(project, file.virtualFile)
        )
    }

    fun testRejectsGenericYaml() {
        val file = myFixture.configureByText(
            "config.yaml",
            """
            database:
              host: localhost
              port: 5432
            """.trimIndent()
        )
        assertFalse(
            "Expected isWorkflowFile to return false for generic YAML",
            WorkflowFileDetector.isWorkflowFile(project, file.virtualFile)
        )
    }

    fun testRejectsNonYamlFiles() {
        val file = myFixture.configureByText("main.go", "package main")
        assertFalse(
            "Expected isWorkflowFile to return false for non-YAML file",
            WorkflowFileDetector.isWorkflowFile(project, file.virtualFile)
        )
    }

    fun testConfigPathsOverrideContentDetection() {
        // A file that doesn't match content detection but is in configPaths
        WorkflowSettings.getInstance().configPaths = listOf("*.yaml")
        val file = myFixture.configureByText("custom.yaml", "key: value")
        assertTrue(
            "Expected configPaths glob match to return true",
            WorkflowFileDetector.isWorkflowFile(project, file.virtualFile)
        )
    }
}
