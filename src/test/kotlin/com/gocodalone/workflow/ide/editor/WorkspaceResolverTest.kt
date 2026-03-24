package com.gocodalone.workflow.ide.editor

import junit.framework.TestCase
import java.io.File

/**
 * Tests for WorkspaceResolver.
 *
 * Uses real temp directories — no IntelliJ platform harness required.
 */
class WorkspaceResolverTest : TestCase() {

    private lateinit var tempDir: File

    override fun setUp() {
        super.setUp()
        tempDir = createTempDir("workspace-resolver-test")
    }

    override fun tearDown() {
        tempDir.deleteRecursively()
        super.tearDown()
    }

    fun testResolveFromPartialFile() {
        val rootConfig = File(tempDir, "app.yaml").apply {
            writeText(
                """
                name: my-app
                imports:
                  - pipelines.yaml
                modules:
                  - name: web
                    type: http.server
                workflows:
                  http:
                    routes: []
                """.trimIndent()
            )
        }

        val partial = File(tempDir, "pipelines.yaml").apply {
            writeText(
                """
                pipelines:
                  - name: process
                    steps:
                      - type: log
                        message: hello
                """.trimIndent()
            )
        }

        val resolver = WorkspaceResolver()
        val result = resolver.resolveFromFile(partial.absolutePath)

        assertNotNull("Expected workspace to resolve from partial file", result)
        assertEquals("Root config path should be app.yaml", rootConfig.absolutePath, result!!.rootConfig)
        assertTrue("mergedYaml should contain root modules", result.mergedYaml.contains("web"))
        assertTrue("mergedYaml should contain imported pipelines section", result.mergedYaml.contains("pipelines:"))
        assertTrue("mergedYaml should contain pipeline name", result.mergedYaml.contains("process"))
        assertTrue("files should include both paths", result.files.contains(rootConfig.absolutePath))
        assertTrue("files should include partial path", result.files.contains(partial.absolutePath))
    }

    fun testSourceMapPointsEntriesToCorrectFiles() {
        val rootConfig = File(tempDir, "app.yaml").apply {
            writeText(
                """
                modules:
                  - name: web
                    type: http.server
                workflows:
                  http:
                    routes: []
                imports:
                  - modules.yaml
                """.trimIndent()
            )
        }

        val partial = File(tempDir, "modules.yaml").apply {
            writeText(
                """
                pipelines:
                  - name: enrich
                    steps: []
                """.trimIndent()
            )
        }

        val resolver = WorkspaceResolver()
        val result = resolver.resolveFromFile(partial.absolutePath)

        assertNotNull(result)
        assertEquals("web should map to root config", rootConfig.absolutePath, result!!.sourceMap["web"])
        assertEquals("enrich should map to partial", partial.absolutePath, result.sourceMap["enrich"])
    }

    fun testReturnsNullWhenNoRootConfigFound() {
        val partial = File(tempDir, "pipelines.yaml").apply {
            writeText(
                """
                pipelines:
                  - name: process
                    steps: []
                """.trimIndent()
            )
        }

        val resolver = WorkspaceResolver()
        val result = resolver.resolveFromFile(partial.absolutePath)

        assertNull("Expected null when no root config exists in tree", result)
    }

    fun testDotWorkflowJsonOverride() {
        val subDir = File(tempDir, "sub").apply { mkdirs() }
        val rootConfig = File(tempDir, "custom-root.yaml").apply {
            writeText(
                """
                modules:
                  - name: svc
                    type: grpc.server
                workflows:
                  grpc:
                    routes: []
                """.trimIndent()
            )
        }
        File(tempDir, ".workflow.json").writeText("""{"config": "custom-root.yaml"}""")

        val partial = File(subDir, "partials.yaml").apply {
            writeText("pipelines:\n  - name: p1\n    steps: []\n")
        }

        // Walk up from sub/ will find .workflow.json in tempDir pointing to custom-root.yaml
        val resolver = WorkspaceResolver()
        val result = resolver.resolveFromFile(partial.absolutePath)

        assertNotNull("Expected workspace resolved via .workflow.json override", result)
        assertEquals(rootConfig.absolutePath, result!!.rootConfig)
    }

    fun testMergesOnlySectionsMissingFromRoot() {
        // Root already has modules: — imported modules: should NOT be appended
        val rootConfig = File(tempDir, "app.yaml").apply {
            writeText(
                """
                modules:
                  - name: web
                    type: http.server
                workflows:
                  http:
                    routes: []
                imports:
                  - extra.yaml
                """.trimIndent()
            )
        }

        File(tempDir, "extra.yaml").apply {
            writeText(
                """
                modules:
                  - name: cache
                    type: memory.cache
                """.trimIndent()
            )
        }

        val resolver = WorkspaceResolver()
        val result = resolver.resolveFromFile(rootConfig.absolutePath)

        assertNotNull(result)
        // modules: should appear exactly once (root's copy, not duplicated)
        val moduleOccurrences = result!!.mergedYaml.lines().count { it.trimStart().startsWith("modules:") }
        assertEquals("modules: section should not be duplicated", 1, moduleOccurrences)
    }
}
