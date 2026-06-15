package com.trackwrite.app.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UpdateDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadsApkAndVerifiesSha256() {
        val apkBytes = "apk bytes".toByteArray()

        withApkServer(apkBytes) { downloadUrl ->
            val downloaded = UpdateDownloader(temporaryFolder.root).download(
                candidate(downloadUrl = downloadUrl, sha256 = apkBytes.sha256()),
            )

            assertTrue(downloaded.path.contains("/updates/"))
            assertTrue(downloaded.name.endsWith(".apk"))
            assertArrayEquals(apkBytes, downloaded.readBytes())
        }
    }

    @Test
    fun deletesPartialFileWhenSha256DoesNotMatch() {
        val apkBytes = "tampered apk bytes".toByteArray()

        withApkServer(apkBytes) { downloadUrl ->
            try {
                UpdateDownloader(temporaryFolder.root).download(
                    candidate(downloadUrl = downloadUrl, sha256 = "0".repeat(64)),
                )
                fail("Expected checksum mismatch to reject the APK.")
            } catch (_: UpdateChecksumException) {
                val cachedFiles = temporaryFolder.root
                    .resolve("updates")
                    .listFiles()
                    .orEmpty()
                    .toList()
                assertTrue(cachedFiles.isEmpty())
            }
        }
    }

    private fun candidate(downloadUrl: String, sha256: String): UpdateCandidate =
        UpdateCandidate(
            metadata = UpdateMetadata(
                versionName = "v2.3",
                versionCode = 23,
                apkAssetName = "trackwrite-v2.3.apk",
                sha256 = sha256,
            ),
            apkAsset = ReleaseAsset(
                name = "trackwrite-v2.3.apk",
                downloadUrl = downloadUrl,
                sizeBytes = null,
            ),
            releasePageUrl = null,
        )

    private fun withApkServer(apkBytes: ByteArray, block: (String) -> Unit) {
        val serverSocket = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                serverSocket.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/vnd.android.package-archive\r\n" +
                        "Content-Length: ${apkBytes.size}\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                    socket.getOutputStream().use { output ->
                        output.write(responseHeaders.toByteArray(Charsets.US_ASCII))
                        output.write(apkBytes)
                    }
                }
            } catch (_: IOException) {
                // Test teardown closes the socket if the client fails before connecting.
            }
        }
        try {
            block("http://127.0.0.1:${serverSocket.localPort}/trackwrite.apk")
        } finally {
            serverSocket.close()
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    private fun ByteArray.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }
    }
}
