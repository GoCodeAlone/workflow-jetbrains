package com.gocodalone.workflow.ide.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter

class WorkflowBridge(
    private val project: Project,
    private val file: VirtualFile,
    private val browser: JBCefBrowser,
) {
    /** When set, the visual editor loads merged YAML instead of the raw file content. */
    var resolvedWorkspace: WorkspaceResolver.ResolvedWorkspace? = null

    private val yamlUpdatedQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val navigateQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val schemaRequestQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val aiRequestQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val resolveFileQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val saveFilesQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val layoutQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val readyQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private var updatingFromEditor = false
    private var updatingFromWebview = false
    private var caretListener: CaretListener? = null
    private var documentListener: com.intellij.openapi.editor.event.DocumentListener? = null

    // Top-level fields the visual editor does not handle — preserved across round-trips
    private var preservedName: String? = null
    private var preservedVersion: String? = null

    fun initialize() {
        // Register JS→Kotlin message handlers
        yamlUpdatedQuery.addHandler { content ->
            handleYamlFromWebview(content)
            JBCefJSQuery.Response("")
        }

        navigateQuery.addHandler { data ->
            // Format: "filePath,line,col" (3+ parts) or legacy "line,col" (2 parts)
            val parts = data.split(",")
            when {
                parts.size >= 3 -> {
                    // Reconstruct filePath (may contain commas), line and col are last two parts
                    val col = parts.last().toIntOrNull() ?: 0
                    val line = parts[parts.size - 2].toIntOrNull() ?: 0
                    val filePath = parts.dropLast(2).joinToString(",").ifBlank { null }
                    navigateToFileAndLine(filePath, line, col)
                }
                parts.size == 2 -> navigateToLine(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
            }
            JBCefJSQuery.Response("")
        }

        schemaRequestQuery.addHandler {
            sendSchemas()
            JBCefJSQuery.Response("")
        }

        aiRequestQuery.addHandler { jsonData ->
            handleAIRequest(jsonData)
            JBCefJSQuery.Response("")
        }

        resolveFileQuery.addHandler { jsonData ->
            handleResolveFile(jsonData)
            JBCefJSQuery.Response("")
        }

        saveFilesQuery.addHandler { jsonData ->
            handleSaveFiles(jsonData)
            JBCefJSQuery.Response("")
        }

        this.layoutQuery.addHandler { layoutJson ->
            val yamlPath = file.path
            val sidecarPath = yamlPath.replace(Regex("\\.ya?ml$"), ".workflow-editor.json")
            java.io.File(sidecarPath).writeText(layoutJson)
            null
        }

        // Register this bridge so test results can be forwarded to the webview
        TestResultService.getInstance(project).activeBridge = this

        // Webview signals ready — send initial YAML + schemas
        this.readyQuery.addHandler {
            sendYamlToEditor()
            sendSchemas()
            // Discover and send plugin schemas in background
            ApplicationManager.getApplication().executeOnPooledThread {
                val plugins = PluginDiscovery.discoverPluginSchemas(project)
                if (plugins.isNotEmpty()) sendPluginSchemas(plugins)
            }
            JBCefJSQuery.Response("")
        }

        // Inject bridge functions after page load
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                injectBridge()
                // Don't send YAML/schemas here — wait for webview's ready signal
            }
        }, browser.cefBrowser)

        // Track caret position in the YAML text editor and sync to webview
        caretListener = object : CaretListener {
            override fun caretPositionChanged(e: CaretEvent) {
                val doc = e.editor.document
                val docFile = FileDocumentManager.getInstance().getFile(doc) ?: return
                val pos = e.newPosition
                val line = pos.line + 1
                val col = pos.column + 1
                if (docFile == file) {
                    browser.cefBrowser.executeJavaScript(
                        "window.onCursorMoved && window.onCursorMoved($line, $col);",
                        "", 0
                    )
                }
                // For any tracked YAML file (main or imported), notify the webview which node is at this line
                val filePath = docFile.path.replace("\\", "\\\\").replace("`", "\\`")
                browser.cefBrowser.executeJavaScript(
                    "window.onNavigateToNode && window.onNavigateToNode(`$filePath`, $line);",
                    "", 0
                )
            }
        }
        EditorFactory.getInstance().eventMulticaster.addCaretListener(caretListener!!)

        // Notify webview when an imported file's document content changes
        documentListener = object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                val changedFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (changedFile == file) return // Main file is handled by yamlUpdatedQuery round-trip
                val resolved = resolvedWorkspace ?: return
                if (!resolved.sourceMap.values.contains(changedFile.path)) return
                val content = event.document.text
                    .replace("\\", "\\\\").replace("`", "\\`").replace("\$", "\\\$")
                val escapedPath = changedFile.path
                    .replace("\\", "\\\\").replace("`", "\\`").replace("\$", "\\\$")
                browser.cefBrowser.executeJavaScript(
                    "window.onFileChanged && window.onFileChanged(`$escapedPath`, `$content`);",
                    "", 0
                )
            }
        }
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener!!)
    }

    private fun injectBridge() {
        val js = """
            window.hostBridge = {
                sendYamlUpdated: function(content) {
                    ${yamlUpdatedQuery.inject("content")}
                },
                sendNavigateToLine: function(data) {
                    ${navigateQuery.inject("data")}
                },
                sendRequestSchemas: function() {
                    ${schemaRequestQuery.inject("''")}
                },
                sendAIRequest: function(data) {
                    ${aiRequestQuery.inject("data")}
                },
                sendResolveFile: function(data) {
                    ${resolveFileQuery.inject("data")}
                },
                sendSaveFiles: function(data) {
                    ${saveFilesQuery.inject("data")}
                },
                saveLayout: function(json) {
                    ${layoutQuery.inject("json")}
                },
                sendReady: function() {
                    ${readyQuery.inject("''")}
                }
            };
            window.dispatchEvent(new Event('hostBridgeReady'));
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(js, "", 0)
    }

    fun sendYamlToEditor() {
        if (updatingFromWebview) return
        updatingFromEditor = true

        // Use merged workspace config when resolving a partial file
        val resolved = resolvedWorkspace
        if (resolved != null) {
            sendMergedConfig(resolved.mergedYaml, resolved.sourceMap, file.path)
            return
        }

        val rawContent = String(file.contentsToByteArray(), Charsets.UTF_8)

        // Preserve top-level fields the visual editor strips on round-trip
        preservedName = extractTopLevelScalar(rawContent, "name")
        preservedVersion = extractTopLevelScalar(rawContent, "version")

        val content = rawContent
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        browser.cefBrowser.executeJavaScript(
            "window.onYamlChanged && window.onYamlChanged(`$content`);",
            "", 0
        )
        updatingFromEditor = false

        val sidecarPath = file.path.replace(Regex("\\.ya?ml$"), ".workflow-editor.json")
        val sidecarFile = java.io.File(sidecarPath)
        if (sidecarFile.exists()) {
            val escaped = sidecarFile.readText()
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("\$", "\\\$")
            browser.cefBrowser.executeJavaScript("window.onLayoutLoaded && window.onLayoutLoaded(JSON.parse(`$escaped`))", "", 0)
        }
    }

    private fun handleYamlFromWebview(content: String) {
        if (updatingFromEditor) return
        updatingFromWebview = true
        val restored = injectPreservedFields(content)
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runWriteAction
                document.setText(restored)
            }
            updatingFromWebview = false
        }
    }

    /**
     * Extracts the value of a top-level scalar YAML key (e.g. `name: my-app`).
     * Returns null if the key is absent or has a non-scalar value.
     */
    internal fun extractTopLevelScalar(yaml: String, key: String): String? {
        val pattern = Regex("""^$key:\s*(.+)$""", RegexOption.MULTILINE)
        val match = pattern.find(yaml) ?: return null
        val value = match.groupValues[1].trim()
        // Exclude block/flow collection indicators
        return if (value.startsWith("{") || value.startsWith("[") || value.isEmpty()) null else value
    }

    /**
     * Injects preserved `name` and `version` fields into YAML returned from the webview.
     * If the webview already emitted the field, the existing value is replaced to avoid duplicates.
     * Fields are inserted at the top of the document.
     */
    internal fun injectPreservedFields(yaml: String): String {
        var result = yaml
        for ((key, value) in listOf("name" to preservedName, "version" to preservedVersion)) {
            if (value == null) continue
            val linePattern = Regex("""^$key:\s*.*$""", RegexOption.MULTILINE)
            result = if (linePattern.containsMatchIn(result)) {
                // Replace existing (possibly empty/wrong) value
                linePattern.replace(result, "$key: $value")
            } else {
                // Prepend missing field
                "$key: $value\n$result"
            }
        }
        return result
    }

    /** Navigate to a line in the current file. */
    private fun navigateToLine(line: Int, col: Int) {
        navigateToFileAndLine(null, line, col)
    }

    /** Navigate to a line in the specified file (or current file when filePath is null). */
    private fun navigateToFileAndLine(filePath: String?, line: Int, col: Int) {
        ApplicationManager.getApplication().invokeLater {
            val targetFile = if (filePath != null && filePath != file.path) {
                com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .findFileByPath(filePath) ?: return@invokeLater
            } else {
                file
            }
            val editors = FileEditorManager.getInstance(project).openFile(targetFile, true)
            val textEditor = editors.firstOrNull() ?: return@invokeLater
            val editor: Editor = (textEditor as? com.intellij.openapi.fileEditor.TextEditor)?.editor
                ?: return@invokeLater
            val pos = LogicalPosition(maxOf(0, line - 1), maxOf(0, col - 1))
            editor.caretModel.moveToLogicalPosition(pos)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }

    private fun sendSchemas() {
        val schemaStream = javaClass.getResourceAsStream("/schemas/workflow-config.schema.json") ?: return
        val content = schemaStream.bufferedReader().readText()
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        browser.cefBrowser.executeJavaScript(
            "window.onSchemasLoaded && window.onSchemasLoaded(JSON.parse(`$content`));",
            "", 0
        )
    }

    private fun sendPluginSchemas(plugins: List<PluginSchemaData>) {
        val gson = com.google.gson.Gson()
        val json = gson.toJson(plugins.map { p ->
            mapOf(
                "pluginName" to p.pluginName,
                "pluginIcon" to p.pluginIcon,
                "pluginColor" to p.pluginColor,
                "modules" to p.modules,
            )
        })
        val escaped = json
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        browser.cefBrowser.executeJavaScript(
            "window.onPluginSchemasLoaded && window.onPluginSchemasLoaded(JSON.parse(`$escaped`));",
            "", 0
        )
    }

    private fun handleResolveFile(jsonData: String) {
        try {
            val gson = com.google.gson.Gson()
            val request = gson.fromJson(jsonData, ResolveFileRequest::class.java)
            val parentDir = file.parent ?: run {
                sendResolveFileResponse(request.requestId, null)
                return
            }
            val targetFile = parentDir.findFileByRelativePath(request.relativePath)
            if (targetFile != null && !targetFile.isDirectory) {
                val content = String(targetFile.contentsToByteArray(), Charsets.UTF_8)
                sendResolveFileResponse(request.requestId, content)
            } else {
                sendResolveFileResponse(request.requestId, null)
            }
        } catch (_: Exception) {
            // Parse error or file read error — return null
        }
    }

    private fun sendResolveFileResponse(requestId: String, content: String?) {
        val escapedRequestId = requestId.replace("\\", "\\\\").replace("`", "\\`").replace("\$", "\\\$")
        if (content != null) {
            val escapedContent = content.replace("\\", "\\\\").replace("`", "\\`").replace("\$", "\\\$")
            browser.cefBrowser.executeJavaScript(
                "window.onResolveFileResponse && window.onResolveFileResponse(`$escapedRequestId`, `$escapedContent`);",
                "", 0
            )
        } else {
            browser.cefBrowser.executeJavaScript(
                "window.onResolveFileResponse && window.onResolveFileResponse(`$escapedRequestId`, null);",
                "", 0
            )
        }
    }

    private fun handleSaveFiles(jsonData: String) {
        val gson = com.google.gson.Gson()
        val entries = gson.fromJson(jsonData, Array<SaveFileEntry>::class.java) ?: return
        val parentDir = file.parent ?: return

        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                for (entry in entries) {
                    if (entry.path == null) {
                        // Main file — update the open document, preserving name/version
                        updatingFromWebview = true
                        val document = FileDocumentManager.getInstance().getDocument(file) ?: continue
                        document.setText(injectPreservedFields(entry.content))
                        updatingFromWebview = false
                    } else {
                        // Imported file — write relative to document directory
                        try {
                            val targetFile = parentDir.findOrCreateFile(entry.path)
                            if (targetFile != null) {
                                targetFile.setBinaryContent(entry.content.toByteArray(Charsets.UTF_8))
                            }
                        } catch (_: Exception) {
                            // Log or ignore write errors for imported files
                        }
                    }
                }
            }
        }
    }

    private data class ResolveFileRequest(
        val requestId: String,
        val relativePath: String,
    )

    private data class SaveFileEntry(
        val path: String?,
        val content: String,
    )

    private fun VirtualFile.findOrCreateFile(relativePath: String): VirtualFile? {
        val parts = relativePath.split("/")
        var current: VirtualFile = this
        for (i in 0 until parts.size - 1) {
            current = current.findChild(parts[i]) ?: current.createChildDirectory(this, parts[i])
        }
        val fileName = parts.last()
        return current.findChild(fileName) ?: current.createChildData(this, fileName)
    }

    private fun handleAIRequest(jsonData: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val gson = com.google.gson.Gson()
                val context = gson.fromJson(jsonData, AIRequestContext::class.java)

                // Try to invoke JetBrains AI Assistant
                val aiAvailable = try {
                    Class.forName("com.intellij.ai.AiService")
                    true
                } catch (_: ClassNotFoundException) {
                    false
                }

                if (aiAvailable) {
                    invokeAIAssistant(context)
                } else {
                    // Fall back: show notification suggesting AI Assistant installation
                    ApplicationManager.getApplication().invokeLater {
                        val group = com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("Workflow Engine") ?: return@invokeLater
                        group.createNotification(
                            "AI Design",
                            "Install <a href=\"https://plugins.jetbrains.com/plugin/22282-ai-assistant\">JetBrains AI Assistant</a> for AI-assisted workflow design.",
                            com.intellij.notification.NotificationType.INFORMATION
                        ).setListener(com.intellij.notification.NotificationListener.URL_OPENING_LISTENER)
                            .notify(project)
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    com.intellij.notification.NotificationGroupManager.getInstance()
                        .getNotificationGroup("Workflow Engine")
                        ?.createNotification(
                            "AI Design Failed",
                            e.message ?: "Unknown error",
                            com.intellij.notification.NotificationType.ERROR
                        )
                        ?.notify(project)
                }
            }
        }
    }

    private fun invokeAIAssistant(context: AIRequestContext) {
        try {
            val prompt = buildString {
                appendLine("You are a Workflow Engine configuration expert.")
                appendLine("Available module types: ${context.moduleTypes.joinToString(", ")}")
                appendLine("Return ONLY the complete updated YAML config. No explanations, no markdown fences.")
                appendLine()
                appendLine("Current workflow YAML:")
                appendLine("```yaml")
                appendLine(context.yaml)
                appendLine("```")
                if (context.userPrompt.isNotBlank()) {
                    appendLine()
                    appendLine("User request: ${context.userPrompt}")
                }
            }

            ApplicationManager.getApplication().invokeLater {
                // Copy to clipboard so user can paste into AI chat
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(java.awt.datatransfer.StringSelection(prompt), null)

                val notificationMessage = if (context.userPrompt.isBlank()) {
                    "Workflow context copied to clipboard. Paste into the AI chat and describe what you want."
                } else {
                    "Prompt copied to clipboard. Paste it in the AI Assistant chat."
                }

                // Try to open AI Assistant chat
                try {
                    val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                    val aiAction = actionManager.getAction("ActivateAIAssistantToolWindow")
                        ?: actionManager.getAction("AIAssistant.OpenChat")
                    if (aiAction != null) {
                        val dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                            .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                            .build()
                        val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                            aiAction, null, "", dataContext
                        )
                        aiAction.actionPerformed(event)
                    }

                    com.intellij.notification.NotificationGroupManager.getInstance()
                        .getNotificationGroup("Workflow Engine")
                        ?.createNotification(
                            "AI Design",
                            notificationMessage,
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        ?.notify(project)
                } catch (_: Exception) {
                    com.intellij.notification.NotificationGroupManager.getInstance()
                        .getNotificationGroup("Workflow Engine")
                        ?.createNotification(
                            "AI Design",
                            notificationMessage,
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        ?.notify(project)
                }
            }
        } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(WorkflowBridge::class.java)
                .warn("AI Assistant invocation failed: ${e.message}")
        }
    }

    private data class AIRequestContext(
        val yaml: String,
        val moduleTypes: List<String>,
        val userPrompt: String,
    )

    /**
     * Sends a merged workspace config to the webview, including the sourceMap so the editor
     * knows which file each node belongs to, and the activeFile currently being edited.
     */
    fun sendMergedConfig(yaml: String, sourceMap: Map<String, String>, activeFile: String) {
        val escapedYaml = yaml
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        val gson = com.google.gson.Gson()
        val escapedSourceMap = gson.toJson(sourceMap)
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        val escapedActiveFile = activeFile
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        browser.cefBrowser.executeJavaScript(
            "window.onMergedConfigLoaded && window.onMergedConfigLoaded(`$escapedYaml`, JSON.parse(`$escapedSourceMap`), `$escapedActiveFile`);",
            "", 0
        )
        updatingFromEditor = false
    }

    /**
     * Writes [content] to [filePath] on disk and refreshes the VirtualFile in the IDE.
     * Used when saving changes that belong to a partial file in a merged workspace.
     */
    fun handleSaveToFile(filePath: String, content: String) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                try {
                    val ioFile = java.io.File(filePath)
                    ioFile.writeText(content, Charsets.UTF_8)
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .refreshAndFindFileByPath(filePath)
                } catch (_: Exception) {}
            }
        }
    }

    fun sendTestResults(results: List<TestCaseResult>) {
        val gson = com.google.gson.Gson()
        val map = results.associate { r ->
            r.name to mapOf("status" to r.status.name.lowercase(), "error" to r.error)
        }
        val escaped = gson.toJson(map)
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        browser.cefBrowser.executeJavaScript(
            "window.onTestResults && window.onTestResults(JSON.parse(`$escaped`));",
            "", 0
        )
    }

    fun dispose() {
        TestResultService.getInstance(project).let { svc ->
            if (svc.activeBridge === this) svc.activeBridge = null
        }
        yamlUpdatedQuery.dispose()
        navigateQuery.dispose()
        schemaRequestQuery.dispose()
        aiRequestQuery.dispose()
        resolveFileQuery.dispose()
        saveFilesQuery.dispose()
        layoutQuery.dispose()
        readyQuery.dispose()
        caretListener?.let { EditorFactory.getInstance().eventMulticaster.removeCaretListener(it) }
        documentListener?.let { EditorFactory.getInstance().eventMulticaster.removeDocumentListener(it) }
    }
}
