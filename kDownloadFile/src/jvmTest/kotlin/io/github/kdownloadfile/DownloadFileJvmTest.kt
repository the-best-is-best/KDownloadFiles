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

class DownloadFileJvmTest {
    private lateinit var home: File
    private var originalHome: String? = null
    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

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
