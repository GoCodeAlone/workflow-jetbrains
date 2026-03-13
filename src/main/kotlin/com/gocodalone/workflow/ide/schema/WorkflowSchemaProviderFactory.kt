package com.gocodalone.workflow.ide.schema

import com.gocodalone.workflow.ide.WorkflowBundle
import com.gocodalone.workflow.ide.settings.WorkflowProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import java.nio.file.FileSystems

class WorkflowSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(WorkflowSchemaProvider(project))
    }
}

class WorkflowSchemaProvider(private val project: Project) : JsonSchemaFileProvider {

    override fun getName(): String = "Workflow Engine Config"

    override fun isAvailable(file: VirtualFile): Boolean {
        if (!file.name.endsWith(".yaml") && !file.name.endsWith(".yml")) return false

        // Check explicit configPaths from project settings
        val projectSettings = WorkflowProjectSettings.getInstance(project)
        val configPaths = projectSettings.configPaths
        if (configPaths.isNotEmpty()) {
            val projectBase = project.basePath
            if (projectBase != null) {
                val relativePath = file.path.removePrefix("$projectBase/")
                for (pattern in configPaths) {
                    try {
                        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                        if (matcher.matches(java.nio.file.Path.of(relativePath))) return true
                    } catch (_: Exception) {
                        // Invalid glob pattern, skip
                    }
                }
            }
        }

        // Match by common workflow config file names
        val name = file.name
        if (name == "workflow.yaml" || name == "workflow.yml" ||
            name == "app.yaml" || name == "app.yml"
        ) {
            return true
        }

        // Content-based: any YAML with modules: key
        try {
            val text = String(file.contentsToByteArray(), Charsets.UTF_8)
            if (text.contains(WorkflowBundle.WORKFLOW_CONTENT_KEY)) {
                return true
            }
        } catch (_: Exception) {
            // Ignore read errors
        }
        return false
    }

    override fun getSchemaFile(): VirtualFile? {
        val resource = javaClass.classLoader.getResource("schemas/workflow-config.schema.json")
            ?: return null
        return com.intellij.openapi.vfs.VfsUtil.findFileByURL(resource)
    }

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

    override fun isUserVisible(): Boolean = true
}
