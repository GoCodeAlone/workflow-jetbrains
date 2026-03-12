package com.gocodalone.workflow.ide.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter

class WorkflowBridge(
    private val project: Project,
    private val file: VirtualFile,
    private val browser: JBCefBrowser,
) {
    private val yamlUpdatedQuery = JBCefJSQuery.create(browser)
    private val navigateQuery = JBCefJSQuery.create(browser)
    private val schemaRequestQuery = JBCefJSQuery.create(browser)
    private var updatingFromEditor = false
    private var updatingFromWebview = false

    fun initialize() {
        // Register JS→Kotlin message handlers
        yamlUpdatedQuery.addHandler { content ->
            handleYamlFromWebview(content)
            JBCefJSQuery.Response("")
        }

        navigateQuery.addHandler { data ->
            val parts = data.split(",")
            if (parts.size == 2) {
                navigateToLine(parts[0].toInt(), parts[1].toInt())
            }
            JBCefJSQuery.Response("")
        }

        schemaRequestQuery.addHandler {
            sendSchemas()
            JBCefJSQuery.Response("")
        }

        // Inject bridge functions after page load
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                injectBridge()
                sendYamlToEditor()
                // Discover and send plugin schemas in background
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                    val plugins = PluginDiscovery.discoverPluginSchemas(project)
                    if (plugins.isNotEmpty()) sendPluginSchemas(plugins)
                }
            }
        }, browser.cefBrowser)
    }

    private fun injectBridge() {
        val js = """
            window.hostBridge = {
                sendYamlUpdated: function(content) {
                    ${yamlUpdatedQuery.inject("content")}
                },
                sendNavigateToLine: function(line, col) {
                    ${navigateQuery.inject("line + ',' + col")}
                },
                sendRequestSchemas: function() {
                    ${schemaRequestQuery.inject("''")}
                }
            };
            window.dispatchEvent(new Event('hostBridgeReady'));
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(js, "", 0)
    }

    fun sendYamlToEditor() {
        if (updatingFromWebview) return
        updatingFromEditor = true
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        browser.cefBrowser.executeJavaScript(
            "window.onYamlChanged && window.onYamlChanged(`$content`);",
            "", 0
        )
        updatingFromEditor = false
    }

    private fun handleYamlFromWebview(content: String) {
        if (updatingFromEditor) return
        updatingFromWebview = true
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runWriteAction
                document.setText(content)
            }
            updatingFromWebview = false
        }
    }

    private fun navigateToLine(line: Int, col: Int) {
        ApplicationManager.getApplication().invokeLater {
            val editors = FileEditorManager.getInstance(project).openFile(file, true)
            val textEditor = editors.firstOrNull() ?: return@invokeLater
            val editor: Editor = (textEditor as? com.intellij.openapi.fileEditor.TextEditor)?.editor
                ?: return@invokeLater
            val pos = LogicalPosition(line - 1, col - 1)
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

    fun dispose() {
        yamlUpdatedQuery.dispose()
        navigateQuery.dispose()
        schemaRequestQuery.dispose()
    }
}
