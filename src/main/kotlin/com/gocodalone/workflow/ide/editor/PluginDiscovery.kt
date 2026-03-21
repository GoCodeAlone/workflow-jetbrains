package com.gocodalone.workflow.ide.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.io.File
import java.nio.file.Paths

private val LOG = Logger.getInstance(PluginDiscovery::class.java)

private const val REGISTRY_BASE =
    "https://raw.githubusercontent.com/GoCodeAlone/workflow-registry/main/plugins"
private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

data class PluginSchemaData(
    val pluginName: String,
    val pluginIcon: String?,
    val pluginColor: String?,
    val modules: Map<String, Any>,
)

/**
 * Discovers workflow plugin schemas by:
 * 1. Parsing the project's go.mod for workflow-plugin-* imports
 * 2. Fetching each plugin's manifest.json from workflow-registry
 * 3. Caching results for 24 hours in the IDE plugin cache directory
 */
object PluginDiscovery {

    fun discoverPluginSchemas(project: Project): List<PluginSchemaData> {
        val projectBase = project.basePath ?: return emptyList()
        val goModFile = File(projectBase, "go.mod")
        if (!goModFile.exists()) return emptyList()

        val goMod = goModFile.readText()
        val pluginNames = parseGoModPlugins(goMod)
        if (pluginNames.isEmpty()) return emptyList()

        val cacheDir = File(getCacheDir())
        cacheDir.mkdirs()

        return pluginNames.mapNotNull { name ->
            try {
                val cached = loadCache(cacheDir, name)
                val manifest = cached ?: fetchManifest(name)
                if (manifest != null) {
                    if (cached == null) saveCache(cacheDir, name, manifest)
                    parseManifest(name, manifest)
                } else null
            } catch (e: Exception) {
                LOG.warn("Failed to discover plugin schema for $name: ${e.message}")
                null
            }
        }
    }

    private fun parseGoModPlugins(goMod: String): List<String> {
        val regex = Regex("""github\.com/GoCodeAlone/workflow-plugin-(\w+)""")
        return regex.findAll(goMod).map { it.groupValues[1] }.distinct().toList()
    }

    private fun fetchManifest(pluginName: String): String? {
        val url = "$REGISTRY_BASE/$pluginName/manifest.json"
        val maxAttempts = 3
        val retryDelayMs = 1_000L

        repeat(maxAttempts) { attempt ->
            try {
                return HttpRequests.request(url)
                    .connectTimeout(10_000)
                    .readTimeout(10_000)
                    .readString()
            } catch (e: Exception) {
                val attemptsLeft = maxAttempts - attempt - 1
                if (attemptsLeft > 0) {
                    LOG.warn("Attempt ${attempt + 1}/$maxAttempts failed fetching manifest for $pluginName: ${e.message}. Retrying in ${retryDelayMs}ms.")
                    Thread.sleep(retryDelayMs)
                } else {
                    LOG.warn("All $maxAttempts attempts failed fetching manifest for $pluginName from $url: ${e.message}")
                }
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseManifest(pluginName: String, json: String): PluginSchemaData? {
        return try {
            val obj = com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, Any>
            PluginSchemaData(
                pluginName = (obj["name"] as? String) ?: pluginName,
                pluginIcon = obj["icon"] as? String,
                pluginColor = obj["color"] as? String,
                modules = (obj["schemas"] as? Map<String, Any>) ?: emptyMap(),
            )
        } catch (e: Exception) {
            LOG.warn("Failed to parse manifest for $pluginName: ${e.message}")
            null
        }
    }

    private fun loadCache(cacheDir: File, pluginName: String): String? {
        val cacheFile = File(cacheDir, "plugin-$pluginName.json")
        if (!cacheFile.exists()) return null
        if (System.currentTimeMillis() - cacheFile.lastModified() > CACHE_TTL_MS) return null
        return try {
            cacheFile.readText()
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCache(cacheDir: File, pluginName: String, content: String) {
        try {
            File(cacheDir, "plugin-$pluginName.json").writeText(content)
        } catch (_: Exception) {
            // Best-effort cache write
        }
    }

    private fun getCacheDir(): String {
        // Use IDE system directory for plugin caches
        val ideaDir = System.getProperty("idea.system.path")
            ?: Paths.get(System.getProperty("user.home"), ".cache", "JetBrains").toString()
        return Paths.get(ideaDir, "workflow-plugin-manifests").toString()
    }
}
