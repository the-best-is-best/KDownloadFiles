package io.github.kdownloadfile.internal

import io.github.kdownloadfile.KDownloadProgress
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * What the platform code calls as bytes arrive. Safe to call from any thread and as often as
 * wanted: only the latest value is passed on.
 */
internal fun interface ProgressReporter {
    fun report(bytesDone: Long, totalBytes: Long?)

    companion object {
        val None = ProgressReporter { _, _ -> }
    }
}

/**
 * Runs [block] and passes the progress it reports to [onProgress] on the caller's coroutine
 * (for example the main thread), so the UI can be updated directly. Fast updates are merged,
 * and the last one (usually 100%) is always delivered once [block] succeeds.
 */
internal suspend fun <T> withProgress(
    onProgress: ((KDownloadProgress) -> Unit)?,
    block: suspend (ProgressReporter) -> T,
): T {
    if (onProgress == null) return block(ProgressReporter.None)

    val latest = MutableStateFlow<KDownloadProgress?>(null)
    var delivered: KDownloadProgress? = null
    fun deliver(progress: KDownloadProgress?) {
        if (progress != null && progress != delivered) {
            delivered = progress
            onProgress(progress)
        }
    }

    return coroutineScope {
        // Launched in the caller's context, so onProgress runs where the caller runs.
        val forwarder = launch { latest.collect(::deliver) }
        val result = try {
            block { bytesDone, totalBytes ->
                latest.value = KDownloadProgress(bytesDone, totalBytes)
            }
        } finally {
            forwarder.cancelAndJoin()
        }
        deliver(latest.value)
        result
    }
}

/** Size of each write when saving, so progress moves smoothly even for files already in memory. */
internal const val WRITE_CHUNK_SIZE = 64 * 1024

/**
 * Calls [write] for each chunk of [bytes] (offset, length) and reports the progress after each one.
 */
internal inline fun writeInChunks(
    bytes: ByteArray,
    progress: ProgressReporter,
    write: (offset: Int, length: Int) -> Unit,
) {
    val total = bytes.size.toLong()
    progress.report(0, total)
    var offset = 0
    while (offset < bytes.size) {
        val length = minOf(WRITE_CHUNK_SIZE, bytes.size - offset)
        write(offset, length)
        offset += length
        progress.report(offset.toLong(), total)
    }
}
