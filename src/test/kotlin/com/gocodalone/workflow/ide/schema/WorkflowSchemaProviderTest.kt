package com.gocodalone.workflow.ide.schema

import com.gocodalone.workflow.ide.settings.WorkflowProjectSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WorkflowSchemaProviderTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            WorkflowProjectSettings.getInstance(project).configPaths = emptyList()
        } catch (_: Exception) {}
        super.tearDown()
    }

    fun testSchemaFileIsResolvable() {
        val provider = WorkflowSchemaProvider(project)
        val schemaFile = provider.schemaFile
        assertNotNull("Schema file must be resolvable from classpath", schemaFile)
    }

    fun testSchemaAppliesToWorkflowYaml() {
        val file = myFixture.configureByText("workflow.yaml", "modules: []")
        val provider = WorkflowSchemaProvider(project)
        assertTrue(
            "Schema should apply to workflow.yaml",
            provider.isAvailable(file.virtualFile)
        )
    }

    fun testSchemaAppliesToAppYaml() {
        val file = myFixture.configureByText("app.yaml", "modules: []")
        val provider = WorkflowSchemaProvider(project)
        assertTrue(
            "Schema should apply to app.yaml",
            provider.isAvailable(file.virtualFile)
        )
    }

    fun testSchemaAppliesToInfraRoots() {
        val provider = WorkflowSchemaProvider(project)
        listOf("infra.yaml", "infra.yml").forEach { fileName ->
            val file = myFixture.configureByText(fileName, "name: sample")
            assertTrue(
                "Schema should apply to $fileName",
                provider.isAvailable(file.virtualFile)
            )
        }
    }

    fun testSchemaAppliesToWorkflowSuffixPatterns() {
        val provider = WorkflowSchemaProvider(project)
        listOf("orders-workflow.yaml", "billing-workflow.yml").forEach { fileName ->
            val file = myFixture.configureByText(fileName, "name: sample")
            assertTrue(
                "Schema should apply to $fileName",
                provider.isAvailable(file.virtualFile)
            )
        }
    }

    fun testSchemaDoesNotApplyToWfctlManifests() {
        val provider = WorkflowSchemaProvider(project)
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
                "Workflow app schema should not apply to wfctl manifest $fileName",
                provider.isAvailable(file.virtualFile)
            )
        }
    }

    fun testSchemaDoesNotApplyToGenericYaml() {
        val file = myFixture.configureByText("database.yaml", "host: localhost")
        val provider = WorkflowSchemaProvider(project)
        assertFalse(
            "Schema should not apply to generic YAML without workflow keys",
            provider.isAvailable(file.virtualFile)
        )
    }

    fun testSchemaDoesNotApplyToNonYaml() {
        val file = myFixture.configureByText("main.go", "package main")
        val provider = WorkflowSchemaProvider(project)
        assertFalse(
            "Schema should not apply to non-YAML files",
            provider.isAvailable(file.virtualFile)
        )
    }

    fun testSchemaAppliesToContentMatchedYamlWithArbitraryName() {
        val file = myFixture.configureByText(
            "routes.yaml",
            """
            modules:
              - name: web
                type: http.server
            workflows:
              http:
                routes: []
            """.trimIndent()
        )
        val provider = WorkflowSchemaProvider(project)
        assertTrue(
            "Schema should apply to any YAML with modules: content, regardless of filename",
            provider.isAvailable(file.virtualFile)
        )
    }

    fun testSchemaAppliesToConfigPathsMatch() {
        WorkflowProjectSettings.getInstance(project).configPaths = listOf("config/**/*.yaml")
        // BasePlatformTestCase files are created in a temp dir, so we test with a
        // pattern that matches any .yaml file
        WorkflowProjectSettings.getInstance(project).configPaths = listOf("**/*.yaml")
        val file = myFixture.configureByText("custom.yaml", "key: value")
        val provider = WorkflowSchemaProvider(project)
        assertTrue(
            "Schema should apply when file matches configPaths glob",
            provider.isAvailable(file.virtualFile)
        )
    }
}
