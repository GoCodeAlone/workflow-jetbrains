package com.gocodalone.workflow.ide

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Stages and validates a release binary before publishing it to the cache. */
internal class BinaryFileDownloader(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val makeExecutable: (File) -> Boolean = { it.setExecutable(true, false) && it.canExecute() },
    private val replace: (Path, Path) -> Unit = { staged, destination ->
        // Do not fall back to a copy or a non-atomic move that could damage the cache.
        Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
) {
    fun download(url: URI, destination: Path, executable: Boolean) {
        val target = destination.toAbsolutePath()
        Files.createDirectories(target.parent)
        val staged = Files.createTempFile(target.parent, ".${target.fileName}.", ".download")
        try {
            transfer(url, staged)
            if (executable && !makeExecutable(staged.toFile())) {
                throw IOException("Could not make downloaded binary executable")
            }
            // All fallible preparation, stream closing and disconnects happen first.
            // Successful atomic replacement is the commit point; nothing follows it.
            replace(staged, target)
        } catch (failure: Throwable) {
            try {
                Files.deleteIfExists(staged)
            } catch (cleanup: Throwable) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    private fun transfer(url: URI, staged: Path) {
        var current = url
        // Retain the existing limit of five HTTP requests, including redirects.
        repeat(5) {
            if (!current.scheme.equals("https", ignoreCase = true)) {
                throw IOException("Binary downloads require HTTPS")
            }
            val connection = openConnection(current.toURL())
            var failure: Throwable? = null
            try {
                connection.setRequestProperty("User-Agent", "workflow-jetbrains")
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000

                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")?.takeIf { it.isNotBlank() }
                        ?: throw IOException("Redirect with no Location header")
                    current = try {
                        current.resolve(location)
                    } catch (invalid: IllegalArgumentException) {
                        throw IOException("Invalid redirect Location", invalid)
                    }
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Download failed: HTTP $code")
                }

                val expected = connection.contentLengthLong
                val copied = connection.inputStream.use { input ->
                    Files.newOutputStream(staged).use { output -> input.copyTo(output) }
                }
                if (copied == 0L || (expected >= 0 && copied != expected)) {
                    throw IOException("Incomplete binary download: received $copied bytes, expected $expected")
                }
                return
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                try {
                    connection.disconnect()
                } catch (cleanup: Throwable) {
                    failure?.addSuppressed(cleanup) ?: throw cleanup
                }
            }
        }
        throw IOException("Too many redirects while downloading binary")
    }
}
