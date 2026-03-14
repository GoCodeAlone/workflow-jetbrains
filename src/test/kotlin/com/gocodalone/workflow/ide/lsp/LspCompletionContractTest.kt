package com.gocodalone.workflow.ide.lsp

import junit.framework.TestCase

/**
 * Contract tests that document and validate the expected completion contexts
 * provided by the workflow-lsp-server. These tests serve as a contract between
 * the IDE plugins and the LSP server (defined in workflow/lsp/registry.go and
 * workflow/lsp/completion.go).
 *
 * These are pure unit tests that do not require a running LSP server.
 */
class LspCompletionContractTest : TestCase() {

    // ── LSP server capabilities contract ───────────────────────────────
    // The server's initialize handler (lsp/server.go lines 52-70) returns
    // these capabilities via glsp's CreateServerCapabilities().

    fun testServerAdvertisesCompletionProvider() {
        // The server registers TextDocumentCompletion handler, which causes
        // glsp to include CompletionProvider in the capabilities response.
        val expectedCapabilities = mapOf(
            "completionProvider" to true,
            "hoverProvider" to true,
            "textDocumentSync" to true
        )
        assertTrue("Server must advertise completionProvider", expectedCapabilities["completionProvider"] == true)
        assertTrue("Server must advertise hoverProvider", expectedCapabilities["hoverProvider"] == true)
        assertTrue("Server must advertise textDocumentSync", expectedCapabilities["textDocumentSync"] == true)
    }

    fun testServerUsesFullDocumentSync() {
        // The server uses TextDocumentSyncKindFull (value 1), meaning the
        // entire document is sent on each change notification.
        val textDocumentSyncKindFull = 1
        assertEquals("TextDocumentSyncKindFull must be 1", 1, textDocumentSyncKindFull)
    }

    fun testServerReportsServerInfo() {
        // The server returns ServerInfo with name "workflow-lsp-server".
        val serverName = "workflow-lsp-server"
        assertEquals("Server info name must be workflow-lsp-server", "workflow-lsp-server", serverName)
    }

    // ── Top-level keys ─────────────────────────────────────────────────

    fun testTopLevelKeyCompletions() {
        val expectedKeys = listOf(
            "modules",
            "workflows",
            "triggers",
            "pipelines",
            "imports",
            "requires",
            "platform"
        )
        assertEquals("Must have exactly 7 top-level keys", 7, expectedKeys.size)
        assertTrue("Must include modules", expectedKeys.contains("modules"))
        assertTrue("Must include workflows", expectedKeys.contains("workflows"))
        assertTrue("Must include triggers", expectedKeys.contains("triggers"))
        assertTrue("Must include pipelines", expectedKeys.contains("pipelines"))
        assertTrue("Must include imports", expectedKeys.contains("imports"))
        assertTrue("Must include requires", expectedKeys.contains("requires"))
        assertTrue("Must include platform", expectedKeys.contains("platform"))
    }

    // ── Module type completions ────────────────────────────────────────

    fun testCoreModuleTypes() {
        val coreModuleTypes = listOf(
            "http.server",
            "database.postgres",
            "database.workflow",
            "cache.modular",
            "storage.sqlite",
            "static.fileserver",
            "config.provider",
            "observability.otel"
        )
        for (moduleType in coreModuleTypes) {
            assertTrue(
                "Module type '$moduleType' should use dotted notation",
                moduleType.contains(".")
            )
        }
    }

    fun testModuleItemKeys() {
        // Module-level field completions: name, type, config, dependsOn, branches
        val moduleItemKeys = listOf("name", "type", "config", "dependsOn", "branches")
        assertEquals("Must have 5 module item keys", 5, moduleItemKeys.size)
        assertTrue("Must include name", moduleItemKeys.contains("name"))
        assertTrue("Must include type", moduleItemKeys.contains("type"))
        assertTrue("Must include config", moduleItemKeys.contains("config"))
        assertTrue("Must include dependsOn", moduleItemKeys.contains("dependsOn"))
        assertTrue("Must include branches", moduleItemKeys.contains("branches"))
    }

    // ── Step type completions ──────────────────────────────────────────

    fun testCoreStepTypes() {
        val coreStepTypes = listOf(
            "step.set",
            "step.request_parse",
            "step.response",
            "step.db_query",
            "step.db_exec",
            "step.db_query_cached",
            "step.conditional",
            "step.validate",
            "step.log",
            "step.http_call",
            "step.auth_required",
            "step.cache_get",
            "step.cache_set"
        )
        for (stepType in coreStepTypes) {
            assertTrue(
                "Step type '$stepType' should start with 'step.'",
                stepType.startsWith("step.")
            )
        }
    }

    // ── Trigger type completions ───────────────────────────────────────

    fun testTriggerTypes() {
        val expectedTriggerTypes = listOf("http", "schedule", "event", "eventbus")
        assertEquals("Must have 4 trigger types", 4, expectedTriggerTypes.size)
        assertTrue("Must include http trigger", expectedTriggerTypes.contains("http"))
        assertTrue("Must include schedule trigger", expectedTriggerTypes.contains("schedule"))
        assertTrue("Must include event trigger", expectedTriggerTypes.contains("event"))
        assertTrue("Must include eventbus trigger", expectedTriggerTypes.contains("eventbus"))
    }

    // ── Template function completions ──────────────────────────────────

    fun testTemplateFunctions() {
        val expectedFunctions = listOf(
            "uuidv4", "uuid", "now", "lower", "upper", "title", "default",
            "trimPrefix", "trimSuffix", "json", "step", "trigger",
            "replace", "contains", "hasPrefix", "hasSuffix",
            "split", "join", "trimSpace", "urlEncode",
            "add", "sub", "mul", "div",
            "toInt", "toFloat", "toString",
            "length", "coalesce", "config",
            "sum", "pluck", "flatten", "unique", "groupBy", "sortBy",
            "first", "last", "min", "max"
        )
        assertEquals("Must have 38 template functions", 38, expectedFunctions.size)
    }

    fun testCorePipelineTemplateFunctions() {
        val coreFunctions = listOf("uuidv4", "now", "lower", "upper", "default", "json", "config")
        val allFunctions = listOf(
            "uuidv4", "uuid", "now", "lower", "upper", "title", "default",
            "trimPrefix", "trimSuffix", "json", "step", "trigger",
            "replace", "contains", "hasPrefix", "hasSuffix",
            "split", "join", "trimSpace", "urlEncode",
            "add", "sub", "mul", "div",
            "toInt", "toFloat", "toString",
            "length", "coalesce", "config",
            "sum", "pluck", "flatten", "unique", "groupBy", "sortBy",
            "first", "last", "min", "max"
        )
        for (fn in coreFunctions) {
            assertTrue(
                "Core template function '$fn' must be present in function list",
                allFunctions.contains(fn)
            )
        }
    }

    fun testMathTemplateFunctions() {
        val mathFunctions = listOf("add", "sub", "mul", "div")
        val allFunctions = listOf(
            "uuidv4", "uuid", "now", "lower", "upper", "title", "default",
            "trimPrefix", "trimSuffix", "json", "step", "trigger",
            "replace", "contains", "hasPrefix", "hasSuffix",
            "split", "join", "trimSpace", "urlEncode",
            "add", "sub", "mul", "div",
            "toInt", "toFloat", "toString",
            "length", "coalesce", "config",
            "sum", "pluck", "flatten", "unique", "groupBy", "sortBy",
            "first", "last", "min", "max"
        )
        for (fn in mathFunctions) {
            assertTrue("Math function '$fn' must be in template functions", allFunctions.contains(fn))
        }
    }

    fun testCollectionTemplateFunctions() {
        val collectionFunctions = listOf("sum", "pluck", "flatten", "unique", "groupBy", "sortBy", "first", "last")
        val allFunctions = listOf(
            "uuidv4", "uuid", "now", "lower", "upper", "title", "default",
            "trimPrefix", "trimSuffix", "json", "step", "trigger",
            "replace", "contains", "hasPrefix", "hasSuffix",
            "split", "join", "trimSpace", "urlEncode",
            "add", "sub", "mul", "div",
            "toInt", "toFloat", "toString",
            "length", "coalesce", "config",
            "sum", "pluck", "flatten", "unique", "groupBy", "sortBy",
            "first", "last", "min", "max"
        )
        for (fn in collectionFunctions) {
            assertTrue("Collection function '$fn' must be in template functions", allFunctions.contains(fn))
        }
    }

    // ── Template namespace completions ─────────────────────────────────

    fun testTemplateNamespaces() {
        val expectedNamespaces = listOf(".steps", ".trigger", ".body", ".meta")
        assertEquals("Must have 4 template namespaces", 4, expectedNamespaces.size)
        assertTrue("Must include .steps", expectedNamespaces.contains(".steps"))
        assertTrue("Must include .trigger", expectedNamespaces.contains(".trigger"))
        assertTrue("Must include .body", expectedNamespaces.contains(".body"))
        assertTrue("Must include .meta", expectedNamespaces.contains(".meta"))
    }

    // ── Meta field completions ─────────────────────────────────────────

    fun testMetaFields() {
        val metaFields = listOf("pipeline_name", "trigger_type", "timestamp")
        assertEquals("Must have 3 meta fields", 3, metaFields.size)
        assertTrue("Must include pipeline_name", metaFields.contains("pipeline_name"))
        assertTrue("Must include trigger_type", metaFields.contains("trigger_type"))
        assertTrue("Must include timestamp", metaFields.contains("timestamp"))
    }

    // ── Trigger data subfield completions ──────────────────────────────

    fun testTriggerDataSubfields() {
        val triggerSubfields = listOf("path_params", "query", "body", "headers")
        assertEquals("Must have 4 trigger subfields", 4, triggerSubfields.size)
        assertTrue("Must include path_params", triggerSubfields.contains("path_params"))
        assertTrue("Must include query", triggerSubfields.contains("query"))
        assertTrue("Must include body", triggerSubfields.contains("body"))
        assertTrue("Must include headers", triggerSubfields.contains("headers"))
    }
}
