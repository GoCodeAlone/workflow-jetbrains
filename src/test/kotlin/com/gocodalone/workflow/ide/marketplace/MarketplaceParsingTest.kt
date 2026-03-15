package com.gocodalone.workflow.ide.marketplace

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarketplaceParsingTest : BasePlatformTestCase() {

    fun testParseRegistryIndexValidJson() {
        val json = """[{"name":"plugin-a","version":"1.0","tier":"core"},{"name":"plugin-b","version":"0.1","tier":"community"}]"""
        val result = parseRegistryIndex(json)
        assertEquals(2, result.size)
        assertEquals("plugin-a", result[0]["name"])
        assertEquals("plugin-b", result[1]["name"])
    }

    fun testParseRegistryIndexEmptyArray() {
        val result = parseRegistryIndex("[]")
        assertTrue(result.isEmpty())
    }

    fun testParseRegistryIndexNullJson() {
        val result = parseRegistryIndex("null")
        assertTrue(result.isEmpty())
    }

    fun testParseRegistryIndexEmptyString() {
        val result = parseRegistryIndex("")
        assertTrue(result.isEmpty())
    }

    fun testParseRegistryIndexBlankString() {
        val result = parseRegistryIndex("   ")
        assertTrue(result.isEmpty())
    }

    fun testFilterPublicPluginsExcludesPrivateBoolean() {
        val plugins = listOf(
            mapOf<String, Any>("name" to "public-plugin", "private" to false),
            mapOf<String, Any>("name" to "private-plugin", "private" to true),
        )
        val result = filterPublicPlugins(plugins)
        assertEquals(1, result.size)
        assertEquals("public-plugin", result[0]["name"])
    }

    fun testFilterPublicPluginsExcludesPrivateString() {
        val plugins = listOf(
            mapOf<String, Any>("name" to "public-plugin"),
            mapOf<String, Any>("name" to "private-plugin", "private" to "true"),
            mapOf<String, Any>("name" to "also-private", "private" to "TRUE"),
        )
        val result = filterPublicPlugins(plugins)
        assertEquals(1, result.size)
        assertEquals("public-plugin", result[0]["name"])
    }

    fun testFilterPublicPluginsIncludesWhenFieldMissing() {
        val plugins = listOf(
            mapOf<String, Any>("name" to "no-private-field"),
        )
        val result = filterPublicPlugins(plugins)
        assertEquals(1, result.size)
    }

    fun testFilterPublicPluginsEmptyList() {
        val result = filterPublicPlugins(emptyList())
        assertTrue(result.isEmpty())
    }

    fun testParseRegistryIndexMissingFields() {
        val json = """[{"name":"minimal"}]"""
        val result = parseRegistryIndex(json)
        assertEquals(1, result.size)
        assertEquals("minimal", result[0]["name"])
        assertNull(result[0]["version"])
    }
}
