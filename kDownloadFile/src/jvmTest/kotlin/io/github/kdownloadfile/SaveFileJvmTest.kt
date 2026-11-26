package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SaveFileJvmTest {
    private lateinit var home: File
    private var originalHome: String? = null

    @BeforeTest
    fun setUp() {
        home = Files.createTempDirectory("kdownloadfile-test").toFile()
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", originalHome!!)
        home.deleteRecursively()
    }

    @Test
    fun savesIntoDownloadsFolder() = runBlocking {
        val path = saveFile(byteArrayOf(1, 2, 3), "report.pdf", "reports").getOrThrow()

        assertEquals(File(home, "Downloads/reports/report.pdf").absolutePath, path)
        assertContentEquals(byteArrayOf(1, 2, 3), File(path).readBytes())
    }

    @Test
    fun replacesExistingFileByDefault() = runBlocking {
        saveFile(byteArrayOf(1), "report.pdf").getOrThrow()
        val path = saveFile(byteArrayOf(2), "report.pdf").getOrThrow()

        assertEquals("report.pdf", File(path).name)
        assertContentEquals(byteArrayOf(2), File(path).readBytes())
        assertEquals(listOf("report.pdf"), File(home, "Downloads").list()!!.sorted())
    }

    @Test
    fun keepsBothFilesWhenDuplicatesAllowed() = runBlocking {
        val config = KDownloadFileConfiguration(noDuplicateFile = false)
        saveFile(byteArrayOf(1), "report.pdf", configuration = config).getOrThrow()
        val path = saveFile(byteArrayOf(2), "report.pdf", configuration = config).getOrThrow()

        assertEquals("report (1).pdf", File(path).name)
        assertEquals(
            listOf("report (1).pdf", "report.pdf"),
            File(home, "Downloads").list()!!.sorted()
        )
    }

    @Test
    fun reportsProgressWhileSaving() = runBlocking {
        val bytes = ByteArray(200 * 1024)
        val seen = mutableListOf<KDownloadProgress>()
        saveFile(bytes, "big.bin", onProgress = { seen += it }).getOrThrow()

        assertEquals(100, seen.last().percent)
        assertEquals(bytes.size.toLong(), seen.last().bytesDone)
    }

    @Test
    fun savesIntoCacheFolder() = runBlocking {
        val config = KDownloadFileConfiguration(saveToDownloads = false, saveInCacheFiles = true)
        val path = saveFile(byteArrayOf(7), "a.bin", configuration = config).getOrThrow()

        assertEquals(File(home, ".kdownloadfile/cache/a.bin").absolutePath, path)
    }
}
