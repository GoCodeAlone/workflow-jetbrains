package com.gocodalone.workflow.ide.editor

import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.FileSystems

class WorkflowFileDetector {
    companion object {
        fun isWorkflowFile(project: Project, file: VirtualFile): Boolean {
            // Layer 1: explicit configPaths from settings
            val settings = WorkflowSettings.getInstance()
            val configPaths = settings.configPaths
            if (configPaths.isNotEmpty()) {
                val projectBase = project.basePath ?: return false
                val relativePath = file.path.removePrefix("$projectBase/")
                for (pattern in configPaths) {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                    if (matcher.matches(java.nio.file.Path.of(relativePath))) return true
                }
            }

            // Layer 2: content detection
            if (!file.name.endsWith(".yaml") && !file.name.endsWith(".yml")) return false
            try {
                val content = String(file.contentsToByteArray(), Charsets.UTF_8)
                val lines = content.lineSequence().take(50).toList()
                val hasModules = lines.any { it.trimStart().startsWith("modules:") }
                val hasWorkflows = lines.any { it.trimStart().startsWith("workflows:") }
                return hasModules && hasWorkflows
            } catch (_: Exception) {
                return false
            }
        }
    }
}
