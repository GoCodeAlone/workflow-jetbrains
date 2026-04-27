package com.gocodalone.workflow.ide.editor

import com.gocodalone.workflow.ide.WorkflowBundle
import com.gocodalone.workflow.ide.settings.WorkflowProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.FileSystems

enum class WorkflowFileType { CONFIG, PARTIAL, TEST, FEATURE, WFCTL_MANIFEST }

class WorkflowFileDetector {
    companion object {

        fun detectFileType(project: Project, file: VirtualFile): WorkflowFileType? {
            // Feature files
            if (file.name.endsWith(".feature")) return WorkflowFileType.FEATURE

            if (!file.name.endsWith(".yaml") && !file.name.endsWith(".yml")) return null
            if (file.name in WorkflowBundle.WFCTL_MANIFEST_FILE_NAMES) return WorkflowFileType.WFCTL_MANIFEST

            // Layer 1: explicit configPaths always treated as CONFIG
            val projectSettings = WorkflowProjectSettings.getInstance(project)
            val configPaths = projectSettings.configPaths
            if (configPaths.isNotEmpty()) {
                val projectBase = project.basePath
                if (projectBase != null) {
                    val relativePath = file.path.removePrefix("$projectBase/")
                    for (pattern in configPaths) {
                        try {
                            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                            if (matcher.matches(java.nio.file.Path.of(relativePath))) return WorkflowFileType.CONFIG
                        } catch (_: Exception) {}
                    }
                }
            }

            // Layer 2: documented workflow config names and patterns
            if (WorkflowBundle.isWorkflowConfigFileName(file.name)) return WorkflowFileType.CONFIG

            // Layer 3: content-based tiered detection
            val content = try {
                String(file.contentsToByteArray(), Charsets.UTF_8)
            } catch (_: Exception) {
                return null
            }
            val lines = content.lineSequence().take(50).toList()

            // Full config: has both modules: and workflows:
            val hasModules = lines.any { it.trimStart().startsWith("modules:") }
            val hasWorkflows = lines.any { it.trimStart().startsWith("workflows:") }
            if (hasModules && hasWorkflows) return WorkflowFileType.CONFIG

            // Test file: _test.yaml suffix with tests: or config: key
            if ((file.name.endsWith("_test.yaml") || file.name.endsWith("_test.yml")) &&
                (content.contains("tests:") || content.contains("config:"))) {
                return WorkflowFileType.TEST
            }

            // Partial config: any workflow-relevant top-level key
            if (lines.any { line ->
                val t = line.trimStart()
                t.startsWith("pipelines:") || t.startsWith("modules:") ||
                    t.startsWith("workflows:") || t.startsWith("imports:")
            }) return WorkflowFileType.PARTIAL

            return null
        }

        fun isWorkflowFile(project: Project, file: VirtualFile): Boolean {
            val type = detectFileType(project, file)
            return type == WorkflowFileType.CONFIG || type == WorkflowFileType.PARTIAL
        }

        fun isTestFile(project: Project, file: VirtualFile): Boolean =
            detectFileType(project, file) == WorkflowFileType.TEST

        fun isFeatureFile(file: VirtualFile): Boolean =
            file.name.endsWith(".feature")
    }
}
