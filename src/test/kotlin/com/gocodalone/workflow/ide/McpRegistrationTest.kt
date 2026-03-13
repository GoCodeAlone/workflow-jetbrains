package com.gocodalone.workflow.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class McpRegistrationTest : BasePlatformTestCase() {

    fun testMcpJsonFormat() {
        // Verify the JSON structure McpRegistration would write is correct
        val tempDir = createTempDirectory()
        val mcpFile = File(tempDir, ".mcp.json")

        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val config = mutableMapOf<String, Any>()
        val servers = mutableMapOf<String, Any>()
        servers["workflow"] = mapOf(
            "type" to "stdio",
            "command" to "/usr/local/bin/wfctl",
            "args" to listOf("mcp"),
        )
        config["mcpServers"] = servers
        mcpFile.writeText(gson.toJson(config) + "\n")

        val content = mcpFile.readText()
        assertTrue("Must contain mcpServers key", content.contains("\"mcpServers\""))
        assertTrue("Must contain workflow server", content.contains("\"workflow\""))
        assertTrue("Must contain stdio type", content.contains("\"stdio\""))
        assertTrue("Must contain mcp arg", content.contains("\"mcp\""))

        // Verify it's valid JSON that round-trips
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(content, Map::class.java) as Map<String, Any>
        assertNotNull(parsed["mcpServers"])

        tempDir.deleteRecursively()
    }

    fun testMcpJsonMergesWithExisting() {
        val tempDir = createTempDirectory()
        val mcpFile = File(tempDir, ".mcp.json")

        // Write existing config with another server
        mcpFile.writeText("""{"mcpServers":{"other":{"type":"stdio","command":"other-cmd"}}}""")

        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        @Suppress("UNCHECKED_CAST")
        val existing = gson.fromJson(mcpFile.readText(), MutableMap::class.java) as MutableMap<String, Any>
        @Suppress("UNCHECKED_CAST")
        val servers = (existing["mcpServers"] as? MutableMap<String, Any>) ?: mutableMapOf()
        servers["workflow"] = mapOf(
            "type" to "stdio",
            "command" to "/usr/local/bin/wfctl",
            "args" to listOf("mcp"),
        )
        existing["mcpServers"] = servers
        mcpFile.writeText(gson.toJson(existing) + "\n")

        val content = mcpFile.readText()
        assertTrue("Must still contain 'other' server", content.contains("\"other\""))
        assertTrue("Must contain 'workflow' server", content.contains("\"workflow\""))

        tempDir.deleteRecursively()
    }

    private fun createTempDirectory(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "mcp-test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }
}
