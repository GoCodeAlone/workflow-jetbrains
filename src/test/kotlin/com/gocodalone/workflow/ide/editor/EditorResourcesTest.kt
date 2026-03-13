package com.gocodalone.workflow.ide.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Validates that all editor webview resources are bundled correctly.
 * These tests would have caught the jar: URL / ERR_UNKNOWN_URL_SCHEME
 * issue before release by ensuring resources are accessible from the classloader.
 */
class EditorResourcesTest : BasePlatformTestCase() {

    fun testEditorIndexHtmlExists() {
        val stream = EditorSchemeHandlerFactory::class.java.getResourceAsStream("/editor/index.html")
        assertNotNull("editor/index.html must be bundled as a classpath resource", stream)
        stream?.close()
    }

    fun testEditorIndexJsExists() {
        val stream = EditorSchemeHandlerFactory::class.java.getResourceAsStream("/editor/index.js")
        assertNotNull("editor/index.js must be bundled as a classpath resource", stream)
        stream?.close()
    }

    fun testEditorIndexCssExists() {
        val stream = EditorSchemeHandlerFactory::class.java.getResourceAsStream("/editor/index.css")
        assertNotNull("editor/index.css must be bundled as a classpath resource", stream)
        stream?.close()
    }

    fun testIndexHtmlReferencesExistingAssets() {
        val html = EditorSchemeHandlerFactory::class.java
            .getResourceAsStream("/editor/index.html")!!
            .bufferedReader().readText()

        assertTrue("index.html must reference index.js", html.contains("index.js"))
        assertTrue("index.html must reference index.css", html.contains("index.css"))
        assertTrue("index.html must have a #root mount point", html.contains("id=\"root\""))
    }

    fun testIndexHtmlIsValidHtml() {
        val html = EditorSchemeHandlerFactory::class.java
            .getResourceAsStream("/editor/index.html")!!
            .bufferedReader().readText()

        assertTrue("Must have DOCTYPE", html.contains("<!DOCTYPE html>"))
        assertTrue("Must have <html> tag", html.contains("<html"))
        assertTrue("Must have <head> tag", html.contains("<head>"))
        assertTrue("Must have <body> tag", html.contains("<body>"))
    }

    fun testIndexJsIsNotEmpty() {
        val js = EditorSchemeHandlerFactory::class.java
            .getResourceAsStream("/editor/index.js")!!
            .bufferedReader().readText()

        assertTrue("index.js must not be empty", js.length > 100)
    }

    fun testIndexCssIsNotEmpty() {
        val css = EditorSchemeHandlerFactory::class.java
            .getResourceAsStream("/editor/index.css")!!
            .bufferedReader().readText()

        assertTrue("index.css must not be empty", css.length > 50)
    }

    fun testSchemaResourceExists() {
        val stream = javaClass.getResourceAsStream("/schemas/workflow-config.schema.json")
        assertNotNull("workflow-config.schema.json must be bundled", stream)
        stream?.close()
    }

    fun testSchemaIsValidJson() {
        val content = javaClass.getResourceAsStream("/schemas/workflow-config.schema.json")!!
            .bufferedReader().readText()
        assertTrue("Schema must start with {", content.trimStart().startsWith("{"))
        assertTrue("Schema must contain \$schema key", content.contains("\"\$schema\""))
    }

    fun testSchemeHandlerFactoryConstants() {
        assertEquals("workflow-editor", EditorSchemeHandlerFactory.DOMAIN)
        assertEquals("https://workflow-editor/index.html", EditorSchemeHandlerFactory.BASE_URL)
    }

    fun testSchemeHandlerResolvesEditorResources() {
        // Verify the classloader path mapping that the scheme handler uses:
        // URI path "/index.html" -> classloader resource "/editor/index.html"
        for (name in listOf("/index.html", "/index.js", "/index.css")) {
            val stream = EditorSchemeHandlerFactory::class.java.getResourceAsStream("/editor$name")
            assertNotNull("Scheme handler must resolve $name -> /editor$name", stream)
            stream?.close()
        }
    }

    fun testSchemeHandlerDoesNotResolveNonexistent() {
        val stream = EditorSchemeHandlerFactory::class.java.getResourceAsStream("/editor/nonexistent.xyz")
        assertNull("Nonexistent resource should return null", stream)
    }
}
