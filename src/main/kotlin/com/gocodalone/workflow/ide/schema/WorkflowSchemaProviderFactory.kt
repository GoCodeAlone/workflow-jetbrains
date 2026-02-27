package com.gocodalone.workflow.ide.schema

import com.gocodalone.workflow.ide.WorkflowBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

class WorkflowSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(WorkflowSchemaProvider(project))
    }
}

class WorkflowSchemaProvider(private val project: Project) : JsonSchemaFileProvider {

    override fun getName(): String = "Workflow Engine Config"

    override fun isAvailable(file: VirtualFile): Boolean {
        // Match by common workflow config file names
        val name = file.name
        if (name == "workflow.yaml" || name == "workflow.yml" ||
            name == "app.yaml" || name == "app.yml"
        ) {
            return true
        }
        // Also match files with workflow-related name patterns
        if ((name.endsWith(".yaml") || name.endsWith(".yml")) &&
            (name.contains("workflow") || name.contains("app"))
        ) {
            // Check content for the modules: key as a heuristic
            try {
                val text = String(file.contentsToByteArray(), Charsets.UTF_8)
                if (text.contains(WorkflowBundle.WORKFLOW_CONTENT_KEY)) {
                    return true
                }
            } catch (_: Exception) {
                // Ignore read errors
            }
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
