package com.gocodalone.workflow.ide.lsp

import com.gocodalone.workflow.ide.WorkflowBundle
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LspServerDescriptorTest : BasePlatformTestCase() {

    // ── File pattern matching ──────────────────────────────────────────

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

    fun testIsSupportedFileForInfraRoots() {
        val descriptor = WorkflowLspServerDescriptor(project)
        listOf("infra.yaml", "infra.yml").forEach { fileName ->
            val file = myFixture.configureByText(fileName, "name: sample")
            assertTrue(
                "$fileName should be supported",
                descriptor.isSupportedFile(file.virtualFile)
            )
        }
    }

    fun testIsSupportedFileRejectsWfctlManifests() {
        val descriptor = WorkflowLspServerDescriptor(project)
        listOf("wfctl.yaml", "wfctl.yml").forEach { fileName ->
            val file = myFixture.configureByText(
                fileName,
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
                "$fileName should not be routed to the app-config Workflow LSP",
                descriptor.isSupportedFile(file.virtualFile)
            )
        }
    }

    // ── Content-based detection ────────────────────────────────────────

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

    fun testIsSupportedFileForContentWithModulesWorkflowsAndTriggers() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val content = """
            modules:
              - name: web
                type: http.server
            workflows:
              http:
                routes: []
            triggers:
              - type: http
        """.trimIndent()
        val file = myFixture.configureByText("complex.yaml", content)
        assertTrue(
            "YAML with modules: + workflows: + triggers: should be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    // ── Rejection of non-workflow files ─────────────────────────────────

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

    fun testIsSupportedFileRejectsDockerCompose() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val content = """
            version: '3.8'
            services:
              web:
                image: nginx
        """.trimIndent()
        val file = myFixture.configureByText("docker-compose.yaml", content)
        assertFalse(
            "Docker compose YAML should not be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileRejectsKubernetesManifest() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val content = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: web
        """.trimIndent()
        val file = myFixture.configureByText("deployment.yaml", content)
        assertFalse(
            "Kubernetes manifest YAML should not be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileRejectsMarkdown() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("README.md", "# Workflow\nmodules:\nworkflows:")
        assertFalse(
            "Markdown files should not be supported even with workflow keywords",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    fun testIsSupportedFileRejectsEmptyYaml() {
        val descriptor = WorkflowLspServerDescriptor(project)
        val file = myFixture.configureByText("empty.yaml", "")
        assertFalse(
            "Empty YAML files should not be supported",
            descriptor.isSupportedFile(file.virtualFile)
        )
    }

    // ── Command line construction ──────────────────────────────────────

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

    // ── WorkflowBundle file patterns ───────────────────────────────────

    fun testWorkflowFilePatternsIncludeAllKnownNames() {
        val patterns = WorkflowBundle.WORKFLOW_FILE_PATTERNS
        assertTrue("Must include workflow.yaml", patterns.contains("workflow.yaml"))
        assertTrue("Must include workflow.yml", patterns.contains("workflow.yml"))
        assertTrue("Must include app.yaml", patterns.contains("app.yaml"))
        assertTrue("Must include app.yml", patterns.contains("app.yml"))
        assertTrue("Must include infra.yaml", patterns.contains("infra.yaml"))
        assertTrue("Must include infra.yml", patterns.contains("infra.yml"))
        assertFalse("Must not include wfctl.yaml in app/LSP patterns", patterns.contains("wfctl.yaml"))
        assertFalse("Must not include wfctl.yml in app/LSP patterns", patterns.contains("wfctl.yml"))
    }

    fun testWorkflowContentKeyIsModules() {
        assertEquals(
            "Content detection key must be 'modules:'",
            "modules:",
            WorkflowBundle.WORKFLOW_CONTENT_KEY
        )
    }
}
