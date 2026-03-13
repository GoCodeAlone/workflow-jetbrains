package com.gocodalone.workflow.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class BinaryDownloaderTest : BasePlatformTestCase() {

    fun testGetPlatformSuffixReturnsValidValue() {
        val suffix = BinaryDownloader.getPlatformSuffix()
        val validSuffixes = listOf(
            "darwin-arm64", "darwin-amd64",
            "linux-arm64", "linux-amd64",
            "windows-amd64"
        )
        assertTrue(
            "getPlatformSuffix() returned '$suffix', expected one of $validSuffixes",
            suffix in validSuffixes
        )
    }

    fun testGetPlatformSuffixMatchesRunningOS() {
        val suffix = BinaryDownloader.getPlatformSuffix()
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("mac") || osName.contains("darwin") ->
                assertTrue("On macOS, suffix should start with darwin-", suffix.startsWith("darwin-"))
            osName.contains("linux") ->
                assertTrue("On Linux, suffix should start with linux-", suffix.startsWith("linux-"))
            osName.contains("windows") ->
                assertTrue("On Windows, suffix should start with windows-", suffix.startsWith("windows-"))
        }
    }

    fun testGetDefaultBinaryPathIncludesPlatformSuffix() {
        val path = BinaryDownloader.getDefaultBinaryPath("wfctl")
        val suffix = BinaryDownloader.getPlatformSuffix()
        assertTrue(
            "Default binary path should include platform suffix '$suffix', got: $path",
            path.contains(suffix)
        )
    }

    fun testGetDefaultBinaryPathEndsWithBinaryName() {
        val path = BinaryDownloader.getDefaultBinaryPath("wfctl")
        if (BinaryDownloader.isWindows()) {
            assertTrue("On Windows, path should end with wfctl.exe", path.endsWith("wfctl.exe"))
        } else {
            assertTrue("Path should end with /wfctl", path.endsWith("/wfctl"))
        }
    }

    fun testGetDefaultBinaryPathForLspServer() {
        val path = BinaryDownloader.getDefaultBinaryPath("workflow-lsp-server")
        assertTrue(
            "LSP server path should contain workflow-lsp-server",
            path.contains("workflow-lsp-server")
        )
    }

    fun testGetDefaultBinaryPathContainsPluginDir() {
        val path = BinaryDownloader.getDefaultBinaryPath("wfctl")
        assertTrue(
            "Default binary path should contain 'workflow-engine/bin/', got: $path",
            path.contains("workflow-engine/bin/")
        )
    }

    fun testIsDownloadedReturnsFalseForNonexistentBinary() {
        assertFalse(
            "isDownloaded should return false for a binary that doesn't exist",
            BinaryDownloader.isDownloaded("nonexistent-fake-binary-xyz")
        )
    }

    fun testResolveFromPathOrCacheWithValidSettingsPath() {
        val tmpFile = File.createTempFile("wfctl-test", "")
        tmpFile.setExecutable(true)
        try {
            val resolved = BinaryDownloader.resolveFromPathOrCache("wfctl", tmpFile.absolutePath)
            assertEquals(
                "Should return the settings path when file exists and is executable",
                tmpFile.absolutePath,
                resolved
            )
        } finally {
            tmpFile.delete()
        }
    }

    fun testResolveFromPathOrCacheWithInvalidSettingsPath() {
        val resolved = BinaryDownloader.resolveFromPathOrCache(
            "wfctl",
            "/nonexistent/path/to/wfctl"
        )
        // Should fall through to PATH check, not return the invalid settings path
        assertFalse(
            "Should not return a non-existent settings path",
            resolved == "/nonexistent/path/to/wfctl"
        )
    }

    fun testResolveFromPathOrCacheWithBlankSettings() {
        val resolved = BinaryDownloader.resolveFromPathOrCache("wfctl", "")
        // If wfctl is on PATH, we get a path. If not, may be null.
        // Either way, it should not throw
        if (resolved != null) {
            assertTrue("Resolved path should exist", File(resolved).exists())
            assertTrue("Resolved path should be executable", File(resolved).canExecute())
        }
    }

    fun testResolveFromPathOrCacheFindsSystemBinary() {
        // Test with a binary we know exists on the system
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            return // Skip on Windows -- ls may not exist
        }
        val resolved = BinaryDownloader.resolveFromPathOrCache("ls", "")
        assertNotNull("Should find 'ls' on system PATH", resolved)
        assertTrue("ls should exist at resolved path", File(resolved!!).exists())
    }

    fun testResolveFromPathOrCacheReturnsNullForMissingBinary() {
        val resolved = BinaryDownloader.resolveFromPathOrCache(
            "completely-nonexistent-binary-name-xyz-123",
            ""
        )
        assertNull("Should return null when binary not found anywhere", resolved)
    }

    fun testResolveFromPathOrCacheSettingsPathTakesPriority() {
        val tmpFile = File.createTempFile("wfctl-test-priority", "")
        tmpFile.setExecutable(true)
        try {
            val resolved = BinaryDownloader.resolveFromPathOrCache("ls", tmpFile.absolutePath)
            assertEquals(
                "Settings path should take priority over PATH",
                tmpFile.absolutePath,
                resolved
            )
        } finally {
            tmpFile.delete()
        }
    }

    fun testResolveFromPathOrCacheNonExecutableSettingsPathFallsThrough() {
        val tmpFile = File.createTempFile("wfctl-test-noexec", "")
        tmpFile.setExecutable(false)
        try {
            val resolved = BinaryDownloader.resolveFromPathOrCache("wfctl", tmpFile.absolutePath)
            // Should NOT return the non-executable settings path
            assertFalse(
                "Non-executable settings path should be skipped",
                resolved == tmpFile.absolutePath
            )
        } finally {
            tmpFile.delete()
        }
    }

    fun testIsWindowsMatchesPlatform() {
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("windows")) {
            assertTrue("isWindows() should return true on Windows", BinaryDownloader.isWindows())
        } else {
            assertFalse("isWindows() should return false on non-Windows", BinaryDownloader.isWindows())
        }
    }

    fun testGetDefaultBinaryPathWindowsExeSuffix() {
        // Verify the .exe logic is consistent with isWindows()
        val path = BinaryDownloader.getDefaultBinaryPath("wfctl")
        if (BinaryDownloader.isWindows()) {
            assertTrue("On Windows, binary path should end with .exe", path.endsWith(".exe"))
        } else {
            assertFalse("On non-Windows, binary path should not end with .exe", path.endsWith(".exe"))
        }
    }
}
