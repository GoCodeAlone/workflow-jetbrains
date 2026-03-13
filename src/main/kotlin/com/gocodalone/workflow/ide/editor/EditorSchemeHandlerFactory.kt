package com.gocodalone.workflow.ide.editor

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.callback.CefSchemeHandlerFactory
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * CefSchemeHandlerFactory that serves plugin resources from the classloader.
 *
 * Registers on a fake HTTPS domain so JCEF can load bundled HTML/JS/CSS
 * without extracting to temp files or using jar: URLs.
 *
 * Based on the pattern from bric3/excalidraw-jetbrains-plugin and
 * docToolchain/diagrams.net-intellij-plugin.
 */
class EditorSchemeHandlerFactory : CefSchemeHandlerFactory {

    override fun create(
        browser: CefBrowser,
        frame: CefFrame,
        schemeName: String,
        request: CefRequest,
    ): CefResourceHandler {
        val uri = URI(request.url)
        val stream = EditorSchemeHandlerFactory::class.java
            .getResourceAsStream("/editor${uri.path}")
            ?.let(::BufferedInputStream)

        return ResourceHandler(uri, stream)
    }

    private class ResourceHandler(
        private val uri: URI,
        private val stream: InputStream?,
    ) : CefResourceHandler {

        override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef?) {
            response.mimeType = when {
                uri.path.endsWith(".html") -> "text/html"
                uri.path.endsWith(".js") -> "application/javascript"
                uri.path.endsWith(".css") -> "text/css"
                uri.path.endsWith(".svg") -> "image/svg+xml"
                uri.path.endsWith(".png") -> "image/png"
                uri.path.endsWith(".woff2") -> "font/woff2"
                uri.path.endsWith(".woff") -> "font/woff"
                else -> "application/octet-stream"
            }
            response.status = if (stream != null) 200 else 404
        }

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            if (stream == null) {
                bytesRead.set(0)
                return false
            }
            return try {
                val available = stream.available()
                if (available > 0) {
                    bytesRead.set(stream.read(dataOut, 0, bytesToRead.coerceAtMost(available)))
                    true
                } else {
                    bytesRead.set(0)
                    try { stream.close() } catch (_: IOException) {}
                    false
                }
            } catch (_: IOException) {
                false
            }
        }

        override fun cancel() {
            try { stream?.close() } catch (_: IOException) {}
        }
    }

    companion object {
        const val DOMAIN = "workflow-editor"
        const val BASE_URL = "https://$DOMAIN/index.html"
    }
}
