package com.gocodalone.workflow.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path

class BinaryFileDownloaderTest {
    @Rule @JvmField val temporary = TemporaryFolder()

    private val url = URI("https://downloads.example.invalid/releases/wfctl")
    private val oldBytes = "known-good-binary".toByteArray()
    private val newBytes = "complete-replacement".toByteArray()

    private fun target(existing: Boolean = true): Path {
        val path = temporary.newFolder().toPath().resolve("wfctl")
        if (existing) Files.write(path, oldBytes)
        return path
    }

    private fun assertPreserved(path: Path) {
        assertArrayEquals(oldBytes, Files.readAllBytes(path))
        assertOnlyTarget(path)
    }

    private fun assertOnlyTarget(path: Path) {
        Files.list(path.parent).use { entries ->
            assertEquals(listOf("wfctl"), entries.map { it.fileName.toString() }.sorted().toList())
        }
    }

    private class Response(
        private val status: Int = 200,
        private val body: InputStream = ByteArrayInputStream(byteArrayOf(1)),
        private val length: Long = -1,
        private val location: String? = null,
        private val beforeDisconnect: () -> Unit = {},
        private val responseFailure: IOException? = null
    ) : HttpURLConnection(URI("https://downloads.example.invalid/").toURL()) {
        var disconnected = false
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() {
            disconnected = true
            beforeDisconnect()
        }
        override fun getResponseCode(): Int {
            assertEquals("connect timeout before response", 30_000, connectTimeout)
            assertEquals("read timeout before response", 120_000, readTimeout)
            assertFalse("redirects must be handled manually", instanceFollowRedirects)
            assertEquals("download User-Agent", "workflow-jetbrains", getRequestProperty("User-Agent"))
            responseFailure?.let { throw it }
            return status
        }
        override fun getInputStream() = body
        override fun getContentLengthLong() = length
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
    }

    @Test fun replacesOnlyAfterTransferAndDisconnectComplete() {
        val path = target()
        val response = Response(
            body = object : ByteArrayInputStream(newBytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    assertArrayEquals(oldBytes, Files.readAllBytes(path))
                    return super.read(buffer, offset, length)
                }
            },
            length = newBytes.size.toLong(),
            beforeDisconnect = { assertArrayEquals(oldBytes, Files.readAllBytes(path)) }
        )
        BinaryFileDownloader(openConnection = { response }).download(url, path, executable = false)
        assertArrayEquals(newBytes, Files.readAllBytes(path))
        assertTrue(response.disconnected)
        assertOnlyTarget(path)
    }

    @Test fun acceptsNonemptyUnknownLengthAndPreparesExecutableBeforeReplacement() {
        val path = target()
        val response = Response(body = ByteArrayInputStream(newBytes))
        var prepared = false
        BinaryFileDownloader(
            openConnection = { response },
            makeExecutable = { staged ->
                assertTrue(response.disconnected)
                assertArrayEquals(oldBytes, Files.readAllBytes(path))
                assertArrayEquals(newBytes, staged.readBytes())
                prepared = true
                true
            }
        ).download(url, path, executable = true)
        assertTrue(prepared)
        assertArrayEquals(newBytes, Files.readAllBytes(path))
        assertOnlyTarget(path)
    }

    @Test fun interruptedTransferPreservesCache() {
        val path = target()
        val input = object : InputStream() {
            var reads = 0
            override fun read(): Int = if (reads++ < 2) 1 else throw IOException("interrupted body")
        }
        val response = Response(body = input, length = 20)
        val error = assertThrows(IOException::class.java) {
            BinaryFileDownloader(openConnection = { response }).download(url, path, executable = false)
        }
        assertEquals("interrupted body", error.message)
        assertTrue(response.disconnected)
        assertPreserved(path)
    }

    @Test fun emptyBodiesAndKnownLengthMismatchesPreserveCache() {
        for ((bytes, length) in listOf(
            byteArrayOf() to 0L, byteArrayOf() to -1L,
            newBytes to (newBytes.size + 1L), newBytes to (newBytes.size - 1L)
        )) {
            val path = target()
            val response = Response(body = ByteArrayInputStream(bytes), length = length)
            assertThrows(IOException::class.java) {
                BinaryFileDownloader(openConnection = { response }).download(url, path, executable = false)
            }
            assertTrue(response.disconnected)
            assertPreserved(path)
        }
    }

    @Test fun httpErrorsAndResponseFailuresPreserveCache() {
        for (response in listOf(Response(status = 404), Response(status = 500),
            Response(status = 206), Response(responseFailure = IOException("connect failed")))) {
            val path = target()
            assertThrows(IOException::class.java) {
                BinaryFileDownloader(openConnection = { response }).download(url, path, executable = false)
            }
            assertTrue(response.disconnected)
            assertPreserved(path)
        }
    }

    @Test fun inputCloseAndDisconnectFailuresHappenBeforeReplacement() {
        val closeFailure = object : ByteArrayInputStream(newBytes) {
            override fun close() { throw IOException("close failed") }
        }
        for (response in listOf(Response(body = closeFailure), Response(
            body = ByteArrayInputStream(newBytes), beforeDisconnect = { throw IOException("disconnect failed") }
        ))) {
            val path = target()
            assertThrows(IOException::class.java) {
                BinaryFileDownloader(openConnection = { response }).download(url, path, executable = false)
            }
            assertTrue(response.disconnected)
            assertPreserved(path)
        }
    }

    @Test fun disconnectFailureDoesNotHideOriginalFailure() {
        val path = target()
        val response = Response(responseFailure = IOException("response failed"),
            beforeDisconnect = { throw IOException("disconnect failed") })
        val error = assertThrows(IOException::class.java) {
            BinaryFileDownloader(openConnection = { response }).download(url, path, executable = false)
        }
        assertEquals("response failed", error.message)
        assertEquals(listOf("disconnect failed"), error.suppressed.map { it.message })
        assertPreserved(path)
    }

    @Test fun connectionFactoryFailurePreservesCache() {
        val path = target()
        assertThrows(IOException::class.java) {
            BinaryFileDownloader(openConnection = { throw IOException("could not open connection") })
                .download(url, path, executable = false)
        }
        assertPreserved(path)
    }

    @Test fun successfulFirstInstallPreparesExecutableAndLeavesNoStagingFile() {
        val path = target(existing = false)
        val executable = !System.getProperty("os.name").lowercase().contains("windows")
        BinaryFileDownloader(openConnection = { Response(body = ByteArrayInputStream(newBytes)) })
            .download(url, path, executable)
        assertArrayEquals(newBytes, Files.readAllBytes(path))
        if (executable) assertTrue(path.toFile().canExecute())
        assertOnlyTarget(path)
    }

    @Test fun redirectExhaustionFailsAfterFiveRequests() {
        val path = target()
        val responses = mutableListOf<Response>()
        assertThrows(IOException::class.java) {
            BinaryFileDownloader(openConnection = {
                Response(status = 302, location = "/again").also { responses.add(it) }
            }).download(url, path, executable = false)
        }
        assertEquals(5, responses.size)
        assertTrue(responses.all { it.disconnected })
        assertPreserved(path)
    }

    @Test fun resolvesRelativeRedirectAndAcceptsLastPermittedResponse() {
        val path = target()
        val requests = mutableListOf<String>()
        val responses = mutableListOf<Response>()
        BinaryFileDownloader(openConnection = { next ->
            requests.add(next.toString())
            val response = if (requests.size < 5) Response(status = 302, location = "asset")
                else Response(body = ByteArrayInputStream(newBytes), length = newBytes.size.toLong())
            responses.add(response)
            response
        }).download(url, path, executable = false)
        assertEquals(5, requests.size)
        assertEquals("https://downloads.example.invalid/releases/asset", requests[1])
        assertTrue(responses.all { it.disconnected })
        assertArrayEquals(newBytes, Files.readAllBytes(path))
        assertOnlyTarget(path)
    }

    @Test fun supportsStandardRedirectCodes() {
        for (code in listOf(301, 302, 303, 307, 308)) {
            val path = target()
            var requests = 0
            BinaryFileDownloader(openConnection = {
                if (requests++ == 0) Response(status = code, location = "/asset")
                else Response(body = ByteArrayInputStream(newBytes))
            }).download(url, path, executable = false)
            assertEquals(2, requests)
            assertArrayEquals(newBytes, Files.readAllBytes(path))
            assertOnlyTarget(path)
        }
    }

    @Test fun invalidLocationsAndHttpsDowngradePreserveCache() {
        for (location in listOf(null, "", "  ", "https://[", "http://downloads.example.invalid/asset")) {
            val path = target()
            var requests = 0
            val response = Response(status = 302, location = location)
            assertThrows(IOException::class.java) {
                BinaryFileDownloader(openConnection = { requests++; response })
                    .download(url, path, executable = false)
            }
            assertEquals(1, requests)
            assertTrue(response.disconnected)
            assertPreserved(path)
        }
    }

    @Test fun executablePreparationFailuresPreserveCache() {
        for (throws in listOf(false, true)) {
            val path = target()
            assertThrows(IOException::class.java) {
                BinaryFileDownloader(
                    openConnection = { Response(body = ByteArrayInputStream(newBytes)) },
                    makeExecutable = { if (throws) throw IOException("chmod failed") else false }
                ).download(url, path, executable = true)
            }
            assertPreserved(path)
        }
    }

    @Test fun atomicReplacementFailuresPreserveCacheWithoutFallback() {
        for (failure in listOf(
            AtomicMoveNotSupportedException("staged", "wfctl", "unsupported"), AccessDeniedException("wfctl")
        )) {
            val path = target()
            var attempts = 0
            assertThrows(IOException::class.java) {
                BinaryFileDownloader(
                    openConnection = { Response(body = ByteArrayInputStream(newBytes)) },
                    replace = { staged, destination ->
                        attempts++
                        assertEquals(path.parent, staged.parent)
                        assertEquals(path, destination)
                        assertArrayEquals(newBytes, Files.readAllBytes(staged))
                        throw failure
                    }
                ).download(url, path, executable = false)
            }
            assertEquals(1, attempts)
            assertPreserved(path)
        }
    }

    @Test fun failedFirstInstallDoesNotLeaveACachedBinary() {
        val path = target(existing = false)
        assertThrows(IOException::class.java) {
            BinaryFileDownloader(openConnection = { Response(status = 404) })
                .download(url, path, executable = false)
        }
        assertFalse(Files.exists(path))
        Files.list(path.parent).use { assertEquals(0L, it.count()) }
    }
}
