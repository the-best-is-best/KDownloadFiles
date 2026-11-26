package io.github.kdownloadfile

import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.WRITE_CHUNK_SIZE
import io.github.kdownloadfile.internal.withProgress
import io.github.kdownloadfile.internal.writeInChunks
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KDownloadProgressTest {
    @Test
    fun percentFromBytes() {
        assertEquals(50, KDownloadProgress(50, 100).percent)
        assertEquals(0.25f, KDownloadProgress(1, 4).fraction)
    }

    @Test
    fun unknownTotalHasNoPercent() {
        assertNull(KDownloadProgress(10, null).percent)
        assertNull(KDownloadProgress(10, 0).percent)
    }

    @Test
    fun percentNeverGoesAbove100() {
        assertEquals(100, KDownloadProgress(150, 100).percent)
    }

    @Test
    fun lastProgressIsAlwaysDelivered() = runTest {
        val seen = mutableListOf<KDownloadProgress>()
        val result = withProgress({ seen += it }) { reporter ->
            reporter.report(10, 100)
            reporter.report(100, 100)
            "done"
        }
        assertEquals("done", result)
        assertEquals(KDownloadProgress(100, 100), seen.last())
    }

    @Test
    fun writesInChunksAndReportsEachOne() {
        val bytes = ByteArray(WRITE_CHUNK_SIZE * 2 + 10)
        val reports = mutableListOf<Long>()
        val chunks = mutableListOf<Int>()
        writeInChunks(
            bytes,
            ProgressReporter { done, _ -> reports += done }) { _, length -> chunks += length }

        assertEquals(listOf(WRITE_CHUNK_SIZE, WRITE_CHUNK_SIZE, 10), chunks)
        assertEquals(
            listOf(
                0L,
                WRITE_CHUNK_SIZE.toLong(),
                WRITE_CHUNK_SIZE * 2L,
                bytes.size.toLong()
            ), reports
        )
    }
}
