package io.github.kdownloadfile

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class DownloadFileJvmTest {
    private lateinit var home: File
    private var originalHome: String? = null
    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"
    private val bigBody = ByteArray(300 * 1024) { it.toByte() }

    @BeforeTest
    fun setUp() {
        home = Files.createTempDirectory("kdownloadfile-test").toFile()
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", home.absolutePath)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/report.pdf") { exchange ->
                requests.incrementAndGet()
                val body = byteArrayOf(1, 2, 3)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/big.bin") { exchange ->
                // 0 = chunked transfer when the request asks for it, otherwise the real length.
                val chunked = exchange.requestURI.query == "chunked"
                exchange.sendResponseHeaders(200, if (chunked) 0 else bigBody.size.toLong())
                exchange.responseBody.use { it.write(bigBody) }
            }
            createContext("/missing.pdf") { exchange ->
                requests.incrementAndGet()
                val body = "Not found".toByteArray()
                exchange.sendResponseHeaders(404, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        System.setProperty("user.home", originalHome!!)
        home.deleteRecursively()
    }

    @Test
    fun reportsProgressOnTheCallersThread() = runBlocking {
        val callerThread = Thread.currentThread()
        val seen = mutableListOf<KDownloadProgress>()
        val path = downloadFile("$baseUrl/big.bin", "big.bin", onProgress = { progress ->
            assertEquals(callerThread, Thread.currentThread())
            seen += progress
        }).getOrThrow()

        assertEquals(bigBody.size.toLong(), File(path).length())
        assertEquals(KDownloadProgress(bigBody.size.toLong(), bigBody.size.toLong()), seen.last())
        assertEquals(100, seen.last().percent)
        assertEquals(seen.map { it.bytesDone }.sorted(), seen.map { it.bytesDone })
    }

    @Test
    fun endsAt100PercentWithoutContentLength() = runBlocking {
        val seen = mutableListOf<KDownloadProgress>()
        downloadFile(
            "$baseUrl/big.bin?chunked",
            "big.bin",
            onProgress = { seen += it }).getOrThrow()

        assertNull(seen.first().percent) // size unknown while downloading
        assertEquals(100, seen.last().percent)
    }

    @Test
    fun downloadsWithASingleRequest() = runBlocking {
        val path = downloadFile("$baseUrl/report.pdf", "report.pdf", "reports").getOrThrow()

        assertContentEquals(byteArrayOf(1, 2, 3), File(path).readBytes())
        assertEquals(1, requests.get())
    }

    @Test
    fun reportsHttpStatusAndKeepsTheOldFile() = runBlocking {
        val old = File(
            home,
            "Downloads/report.pdf"
        ).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(9)) }

        val error = downloadFile("$baseUrl/missing.pdf", "report.pdf").exceptionOrNull()

        assertIs<KDownloadFileHttpException>(error)
        assertEquals(404, error.statusCode)
        assertContentEquals(byteArrayOf(9), old.readBytes())
        assertEquals(listOf("report.pdf"), File(home, "Downloads").list()!!.toList())
    }

    @Test
    fun reportsNoInternetWhenServerCannotBeReached() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }

        val error =
            downloadFile("http://127.0.0.1:$closedPort/report.pdf", "report.pdf").exceptionOrNull()

        assertIs<KDownloadFileNoInternetException>(error)
        assertFalse(File(home, "Downloads/report.pdf").exists())
    }
}
