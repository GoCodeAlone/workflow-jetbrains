package com.gocodalone.workflow.ide.editor

import com.google.gson.Gson
import com.gocodalone.workflow.ide.WorkflowBundle

/**
 * Resolves a workspace context from any workflow YAML file (including partials).
 *
 * Walks up the directory tree to find the root config, follows imports, scans for
 * sibling partials, and produces a merged YAML with a sourceMap (node name → file path).
 */
class WorkspaceResolver {

    data class ResolvedWorkspace(
        /** Absolute path to the root config file (has both modules: and workflows:). */
        val rootConfig: String,
        /** Merged YAML combining root config and all imported/sibling partial files. */
        val mergedYaml: String,
        /** Maps named nodes (module/workflow/pipeline names) to their source file path. */
        val sourceMap: Map<String, String>,
        /** All file paths involved in the workspace (root + partials). */
        val files: List<String>,
    )

    fun resolveFromFile(filePath: String): ResolvedWorkspace? {
        val startDir = java.io.File(filePath).parentFile ?: return null
        val rootConfigFile = findRootConfig(startDir) ?: return null

        val rootContent = rootConfigFile.readText()
        val rootDir = rootConfigFile.parentFile ?: return null
        val sourceMap = mutableMapOf<String, String>()
        val files = mutableListOf(rootConfigFile.absolutePath)

        // Extract named entries defined in the root file
        extractNamedEntries(rootContent, rootConfigFile.absolutePath, sourceMap)

        // Follow explicit imports
        val importedFiles = parseImports(rootContent, rootDir)
        val importedSections = mutableMapOf<String, String>()
        for (importFile in importedFiles) {
            if (!importFile.exists() || importFile.isDirectory) continue
            val absPath = importFile.absolutePath
            if (absPath !in files) files.add(absPath)
            val importContent = importFile.readText()
            extractNamedEntries(importContent, absPath, sourceMap)
            collectSections(importContent, importedSections)
        }

        // Scan root dir for additional YAML partials not already covered
        rootDir.listFiles()
            ?.filter { f ->
                f.isFile &&
                    (f.name.endsWith(".yaml") || f.name.endsWith(".yml")) &&
                    f.absolutePath != rootConfigFile.absolutePath &&
                    f.absolutePath !in files &&
                    isPartialConfig(f)
            }
            ?.forEach { f ->
                files.add(f.absolutePath)
                val content = f.readText()
                extractNamedEntries(content, f.absolutePath, sourceMap)
                collectSections(content, importedSections)
            }

        val mergedYaml = mergeSections(rootContent, importedSections)

        return ResolvedWorkspace(
            rootConfig = rootConfigFile.absolutePath,
            mergedYaml = mergedYaml,
            sourceMap = sourceMap,
            files = files,
        )
    }

    private fun findRootConfig(startDir: java.io.File): java.io.File? {
        var dir = startDir
        while (true) {
            // Check for .workflow.json override pointing to the root config
            val overrideFile = java.io.File(dir, ".workflow.json")
            if (overrideFile.exists()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val override = Gson().fromJson(overrideFile.readText(), Map::class.java) as Map<String, Any>
                    val configPath = override["config"] as? String
                    if (configPath != null) {
                        val candidate = java.io.File(dir, configPath)
                        if (candidate.exists()) return candidate
                    }
                } catch (_: Exception) {}
            }

            // Prefer well-known root names, then fall back to any YAML with both sections
            val yamlFiles = dir.listFiles()
                ?.filter { it.isFile && (it.name.endsWith(".yaml") || it.name.endsWith(".yml")) }
                ?.sortedWith(compareBy {
                    when (it.name) {
                        "app.yaml", "app.yml" -> 0
                        "workflow.yaml", "workflow.yml" -> 1
                        "wfctl.yaml", "wfctl.yml" -> 2
                        "infra.yaml", "infra.yml" -> 3
                        in WorkflowBundle.WORKFLOW_ROOT_FILE_NAMES -> 4
                        else -> 5
                    }
                }) ?: emptyList()

            for (candidate in yamlFiles) {
                if (isRootConfig(candidate)) return candidate
            }

            dir = dir.parentFile ?: break
        }
        return null
    }

    private fun isRootConfig(file: java.io.File): Boolean = try {
        val lines = file.bufferedReader().lineSequence().take(50).toList()
        lines.any { it.trimStart().startsWith("modules:") } &&
            lines.any { it.trimStart().startsWith("workflows:") }
    } catch (_: Exception) { false }

    private fun isPartialConfig(file: java.io.File): Boolean = try {
        val lines = file.bufferedReader().lineSequence().take(20).toList()
        lines.any { line ->
            val t = line.trimStart()
            t.startsWith("pipelines:") || t.startsWith("modules:") ||
                t.startsWith("workflows:") || t.startsWith("imports:")
        }
    } catch (_: Exception) { false }

    private fun parseImports(yaml: String, baseDir: java.io.File): List<java.io.File> {
        val imports = mutableListOf<java.io.File>()
        var inImports = false
        for (line in yaml.lineSequence()) {
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("imports:") -> inImports = true
                inImports && trimmed.startsWith("- ") -> {
                    val path = trimmed.removePrefix("- ").trim().trim('"', '\'')
                    imports.add(java.io.File(baseDir, path))
                }
                inImports && trimmed.isNotEmpty() && !trimmed.startsWith("#") &&
                    !line.startsWith(" ") && !line.startsWith("\t") -> inImports = false
            }
        }
        return imports
    }

    /**
     * Extracts named top-level entities (by `name:` key or map key under workflows:)
     * and records their source file in the sourceMap.
     */
    private fun extractNamedEntries(yaml: String, filePath: String, sourceMap: MutableMap<String, String>) {
        var currentSection: String? = null
        for (line in yaml.lineSequence()) {
            val trimmed = line.trimStart()
            when {
                !line.startsWith(" ") && !line.startsWith("\t") && trimmed.contains(":") -> {
                    currentSection = trimmed.substringBefore(":").trim()
                }
                currentSection != null && trimmed.startsWith("- name: ") -> {
                    val name = trimmed.removePrefix("- name: ").trim()
                    if (name.isNotEmpty()) sourceMap[name] = filePath
                }
                currentSection == "workflows" && line.startsWith("  ") &&
                    !line.startsWith("   ") && trimmed.contains(":") -> {
                    val name = trimmed.substringBefore(":").trim()
                    if (name.isNotEmpty()) sourceMap[name] = filePath
                }
            }
        }
    }

    /**
     * Collects top-level YAML sections (key + all indented content) from a partial file,
     * skipping `imports:`.
     */
    private fun collectSections(yaml: String, sections: MutableMap<String, String>) {
        var currentKey: String? = null
        val sectionLines = mutableMapOf<String, MutableList<String>>()

        for (line in yaml.lineSequence()) {
            val trimmed = line.trimStart()
            if (!line.startsWith(" ") && !line.startsWith("\t") &&
                trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains(":")
            ) {
                val key = trimmed.substringBefore(":").trim()
                if (key.isNotEmpty() && key != "imports") {
                    currentKey = key
                    sectionLines.getOrPut(key) { mutableListOf() }.add(line)
                } else if (key == "imports") {
                    currentKey = null
                }
            } else if (currentKey != null) {
                sectionLines.getOrPut(currentKey) { mutableListOf() }.add(line)
            }
        }

        for ((key, lines) in sectionLines) {
            sections[key] = lines.joinToString("\n")
        }
    }

    /**
     * Appends sections from imported files that are not already present in the root YAML.
     */
    private fun mergeSections(rootYaml: String, importedSections: Map<String, String>): String {
        if (importedSections.isEmpty()) return rootYaml

        val rootKeys = rootYaml.lineSequence()
            .filter { !it.startsWith(" ") && !it.startsWith("\t") && it.contains(":") }
            .map { it.substringBefore(":").trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val sb = StringBuilder(rootYaml)
        for ((key, content) in importedSections) {
            if (key !in rootKeys) {
                if (!sb.endsWith("\n")) sb.append("\n")
                sb.append(content).append("\n")
            }
        }
        return sb.toString()
    }
}
