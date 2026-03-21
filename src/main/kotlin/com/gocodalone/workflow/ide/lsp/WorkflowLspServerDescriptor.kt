package com.gocodalone.workflow.ide.lsp

import com.gocodalone.workflow.ide.BinaryDownloader
import com.gocodalone.workflow.ide.WorkflowBundle
import com.gocodalone.workflow.ide.settings.WorkflowProjectSettings
import com.gocodalone.workflow.ide.settings.WorkflowSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.FileSystems

/**
 * Describes the workflow-lsp-server configuration including command line and file associations.
 *
 * This descriptor is used by LSP4IJ to determine:
 * - Which files to send to the LSP server
 * - How to start the LSP server process
 * - The working directory for the server
 *
 * Note: LSP4IJ also uses the static file-name patterns declared in
 * META-INF/workflow-lsp4ij.xml (`fileNamePatternMapping`) to activate the server.
 * Those patterns are hardcoded to the common workflow config names and cannot
 * dynamically reflect per-project `configPaths` glob settings. `isSupportedFile`
 * is the authoritative runtime check and covers both configPaths globs and
 * content-based detection as a fallback.
 */
class WorkflowLspServerDescriptor(project: Project) {

    val project: Project = project

    /**
     * Determines if the LSP server should handle the given file.
     *
     * Matching priority:
     * 1. Project-level `configPaths` glob patterns (from WorkflowProjectSettings)
     * 2. Common workflow config file names (workflow.yaml, app.yaml, etc.)
     * 3. Content-based detection (modules: + workflows:/pipelines: in first 50 lines)
     */
    fun isSupportedFile(file: VirtualFile): Boolean {
        val name = file.name
        if (!name.endsWith(".yaml") && !name.endsWith(".yml")) {
            return false
        }

        // Layer 1: project configPaths glob patterns (mirrors WorkflowFileDetector)
        val projectSettings = WorkflowProjectSettings.getInstance(project)
        val configPaths = projectSettings.configPaths
        if (configPaths.isNotEmpty()) {
            val projectBase = project.basePath
            if (projectBase != null) {
                val relativePath = file.path.removePrefix("$projectBase/")
                for (pattern in configPaths) {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                    if (matcher.matches(java.nio.file.Path.of(relativePath))) return true
                }
            }
        }

        // Layer 2: common workflow config names
        if (name == "workflow.yaml" || name == "workflow.yml" ||
            name == "app.yaml" || name == "app.yml"
        ) {
            return true
        }

        // Layer 3: content-based detection
        return try {
            val text = String(file.contentsToByteArray(), Charsets.UTF_8)
            text.contains("modules:") && (text.contains("workflows:") || text.contains("pipelines:"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds the command line to start the LSP server.
     * Uses the shared resolution: settings → PATH → downloaded cache.
     */
    fun buildCommandLine(): List<String> {
        val settings = WorkflowSettings.getInstance()
        val resolved = BinaryDownloader.resolveFromPathOrCache(
            WorkflowBundle.LSP_SERVER_BINARY,
            settings.lspServerPath
        )
        val binaryPath = resolved ?: WorkflowBundle.LSP_SERVER_BINARY
        return listOf(binaryPath)
    }
}
