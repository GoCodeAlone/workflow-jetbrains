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
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter

class WorkflowBridge(
    private val project: Project,
    private val file: VirtualFile,
    private val browser: JBCefBrowser,
) {
    private val yamlUpdatedQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val navigateQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val schemaRequestQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val aiRequestQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
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

        aiRequestQuery.addHandler { jsonData ->
            handleAIRequest(jsonData)
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
                },
                sendAIRequest: function(data) {
                    ${aiRequestQuery.inject("data")}
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
                appendLine()
                appendLine("User request: ${context.userPrompt}")
            }

            ApplicationManager.getApplication().invokeLater {
                // Copy to clipboard so user can paste into AI chat
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(java.awt.datatransfer.StringSelection(prompt), null)

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

                        com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("Workflow Engine")
                            ?.createNotification(
                                "AI Design",
                                "Prompt copied to clipboard. Paste it in the AI Assistant chat.",
                                com.intellij.notification.NotificationType.INFORMATION
                            )
                            ?.notify(project)
                    } else {
                        com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("Workflow Engine")
                            ?.createNotification(
                                "AI Design",
                                "Prompt copied to clipboard. Open your AI assistant and paste to get help designing your workflow.",
                                com.intellij.notification.NotificationType.INFORMATION
                            )
                            ?.notify(project)
                    }
                } catch (_: Exception) {
                    com.intellij.notification.NotificationGroupManager.getInstance()
                        .getNotificationGroup("Workflow Engine")
                        ?.createNotification(
                            "AI Design",
                            "Prompt copied to clipboard. Open your AI assistant and paste to get help.",
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

    fun dispose() {
        yamlUpdatedQuery.dispose()
        navigateQuery.dispose()
        schemaRequestQuery.dispose()
        aiRequestQuery.dispose()
    }
}
