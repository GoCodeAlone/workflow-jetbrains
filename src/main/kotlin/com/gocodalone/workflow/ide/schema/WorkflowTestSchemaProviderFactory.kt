package com.gocodalone.workflow.ide.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

class WorkflowTestSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(WorkflowTestSchemaProvider())
    }
}

class WorkflowTestSchemaProvider : JsonSchemaFileProvider {

    override fun getName(): String = "Workflow Test Config"

    override fun isAvailable(file: VirtualFile): Boolean {
        val name = file.name
        return (name.endsWith("_test.yaml") || name.endsWith("_test.yml"))
    }

    override fun getSchemaFile(): VirtualFile? {
        val resource = javaClass.classLoader.getResource("schemas/workflow-test.schema.json")
            ?: return null
        return com.intellij.openapi.vfs.VfsUtil.findFileByURL(resource)
    }

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

    override fun isUserVisible(): Boolean = true
}
