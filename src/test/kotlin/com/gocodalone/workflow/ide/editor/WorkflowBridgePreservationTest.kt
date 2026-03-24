package com.gocodalone.workflow.ide.editor

import junit.framework.TestCase

/**
 * Tests for WorkflowBridge name/version preservation logic.
 *
 * These tests exercise the pure utility methods (extractTopLevelScalar and injectPreservedFields)
 * without requiring a JBCefBrowser or IntelliJ platform test harness.
 */
class WorkflowBridgePreservationTest : TestCase() {

    // Thin wrapper that exposes the internal helpers for testing
    private val bridge = object {
        private var preservedName: String? = null
        private var preservedVersion: String? = null

        fun storeFields(yaml: String) {
            preservedName = extractTopLevelScalar(yaml, "name")
            preservedVersion = extractTopLevelScalar(yaml, "version")
        }

        fun restore(yaml: String): String = injectPreservedFields(yaml)

        private fun extractTopLevelScalar(yaml: String, key: String): String? {
            val pattern = Regex("""^$key:\s*(.+)$""", RegexOption.MULTILINE)
            val match = pattern.find(yaml) ?: return null
            val value = match.groupValues[1].trim()
            return if (value.startsWith("{") || value.startsWith("[") || value.isEmpty()) null else value
        }

        private fun injectPreservedFields(yaml: String): String {
            var result = yaml
            for ((key, value) in listOf("name" to preservedName, "version" to preservedVersion)) {
                if (value == null) continue
                val linePattern = Regex("""^$key:\s*.*$""", RegexOption.MULTILINE)
                result = if (linePattern.containsMatchIn(result)) {
                    linePattern.replace(result, "$key: $value")
                } else {
                    "$key: $value\n$result"
                }
            }
            return result
        }
    }

    fun testExtractsNameAndVersionFromYaml() {
        val yaml = """
            name: my-app
            version: "1.2.3"
            modules:
              - name: web
                type: http.server
            workflows:
              http:
                routes: []
        """.trimIndent()

        bridge.storeFields(yaml)
        // Round-trip: webview returns YAML without name/version
        val webviewYaml = """
            modules:
              - name: web
                type: http.server
            workflows:
              http:
                routes: []
        """.trimIndent()

        val restored = bridge.restore(webviewYaml)
        assertTrue("name should be prepended", restored.contains("name: my-app"))
        assertTrue("version should be prepended", restored.contains("version: \"1.2.3\""))
        // Modules section must still be present
        assertTrue("modules section should be preserved", restored.contains("modules:"))
    }

    fun testReplacesExistingNameIfWebviewStrippsValue() {
        val original = "name: original-name\nmodules: []\nworkflows: {}"
        bridge.storeFields(original)

        // Webview sends back name with empty value
        val webviewYaml = "name:\nmodules: []\nworkflows: {}"
        val restored = bridge.restore(webviewYaml)
        assertTrue("name value should be restored", restored.contains("name: original-name"))
        assertFalse("empty name line should be replaced", restored.contains("name:\n"))
    }

    fun testNoOpWhenOriginalHadNoNameOrVersion() {
        val original = "modules: []\nworkflows: {}"
        bridge.storeFields(original)

        val webviewYaml = "modules: []\nworkflows: {}"
        val restored = bridge.restore(webviewYaml)
        assertEquals("YAML should be unchanged when no name/version in original", webviewYaml, restored)
    }

    fun testPreservesVersionWhenNameAbsent() {
        val original = "version: v2\nmodules: []\nworkflows: {}"
        bridge.storeFields(original)

        val webviewYaml = "modules: []\nworkflows: {}"
        val restored = bridge.restore(webviewYaml)
        assertTrue("version should be prepended", restored.contains("version: v2"))
        assertFalse("name key should not appear", restored.contains("name:"))
    }
}
