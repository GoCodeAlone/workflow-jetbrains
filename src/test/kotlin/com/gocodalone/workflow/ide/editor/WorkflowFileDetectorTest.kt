package com.gocodalone.workflow.ide.editor

import com.gocodalone.workflow.ide.settings.WorkflowProjectSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WorkflowFileDetectorTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            WorkflowProjectSettings.getInstance(project).configPaths = emptyList()
        } catch (_: Exception) {
            // Service may not be available during teardown
        }
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

    fun testDetectsWfctlAndInfraRootNamesAsConfigs() {
        listOf("wfctl.yaml", "wfctl.yml", "infra.yaml", "infra.yml").forEach { fileName ->
            val file = myFixture.configureByText(fileName, "name: sample")
            assertEquals(
                "Expected $fileName to be detected as a workflow config root",
                WorkflowFileType.CONFIG,
                WorkflowFileDetector.detectFileType(project, file.virtualFile)
            )
        }
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
        WorkflowProjectSettings.getInstance(project).configPaths = listOf("**/*.yaml")
        val file = myFixture.configureByText("custom.yaml", "key: value")
        assertTrue(
            "Expected configPaths glob match to return true",
            WorkflowFileDetector.isWorkflowFile(project, file.virtualFile)
        )
    }

    fun testDetectsPartialFileWithPipelinesOnly() {
        val file = myFixture.configureByText(
            "pipelines.yaml",
            """
            pipelines:
              - name: process
                steps:
                  - type: log
                    message: hello
            """.trimIndent()
        )
        val fileType = WorkflowFileDetector.detectFileType(project, file.virtualFile)
        assertEquals(
            "Expected PARTIAL for pipelines-only YAML",
            WorkflowFileType.PARTIAL,
            fileType
        )
    }

    fun testDetectsPartialFileWithModulesOnly() {
        val file = myFixture.configureByText(
            "modules.yaml",
            """
            modules:
              - name: cache
                type: memory.cache
            """.trimIndent()
        )
        val fileType = WorkflowFileDetector.detectFileType(project, file.virtualFile)
        assertEquals(
            "Expected PARTIAL for modules-only YAML",
            WorkflowFileType.PARTIAL,
            fileType
        )
    }
}
