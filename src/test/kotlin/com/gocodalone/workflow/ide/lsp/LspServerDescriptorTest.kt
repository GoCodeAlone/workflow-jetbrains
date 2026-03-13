package com.gocodalone.workflow.ide.lsp

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LspServerDescriptorTest : BasePlatformTestCase() {

    fun testIsSupportedFileForWorkflowYaml() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("workflow.yaml", "modules:\n  - name: web")
        assertTrue(
            "workflow.yaml should be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileForWorkflowYml() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("workflow.yml", "modules:\n  - name: web")
        assertTrue(
            "workflow.yml should be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileForAppYaml() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("app.yaml", "modules:\n  - name: web")
        assertTrue(
            "app.yaml should be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileForAppYml() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("app.yml", "modules:\n  - name: web")
        assertTrue(
            "app.yml should be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileForArbitraryYamlWithModulesAndWorkflows() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val content = """
            modules:
              - name: web
                type: http.server
            workflows:
              http:
                routes: []
        """.trimIndent()
        val file = myFixture.configureByText("routes.yaml", content)
        assertTrue(
            "YAML with modules: and workflows: should be supported regardless of filename",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileForArbitraryYamlWithModulesAndPipelines() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val content = """
            modules:
              - name: web
                type: http.server
            pipelines:
              process:
                steps: []
        """.trimIndent()
        val file = myFixture.configureByText("pipeline.yaml", content)
        assertTrue(
            "YAML with modules: and pipelines: should be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileRejectsGenericYaml() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("database.yaml", "host: localhost\nport: 5432")
        assertFalse(
            "Generic YAML without workflow keys should not be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileRejectsYamlWithOnlyModules() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("random.yaml", "modules:\n  - something")
        // Has modules: but not workflows: or pipelines:, and filename is not a known pattern
        assertFalse(
            "YAML with only modules: but no workflows:/pipelines: should not be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileRejectsNonYaml() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("main.go", "package main")
        assertFalse(
            "Non-YAML files should not be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileRejectsJsonFile() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("config.json", """{"modules": []}""")
        assertFalse(
            "JSON files should not be supported even with workflow-like content",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testBuildCommandLineReturnsNonEmptyList() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val cmd = descriptor.buildCommandLine()
        assertTrue("Command line should not be empty", cmd.isNotEmpty())
    }

    fun testBuildCommandLineContainsLspServerBinary() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val cmd = descriptor.buildCommandLine()
        assertTrue(
            "First element should contain 'workflow-lsp-server', got: ${cmd[0]}",
            cmd[0].contains("workflow-lsp-server")
        )
    }

    fun testBuildCommandLineReturnsSingleElement() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val cmd = descriptor.buildCommandLine()
        assertEquals("Command line should have exactly one element", 1, cmd.size)
    }
}
