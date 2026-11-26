package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.triggerBrowserDownload
import org.khronos.webgl.Int8Array
import org.w3c.files.Blob

/**
 * Hands the bytes to the browser's own download, so the file appears in the browser's downloads
 * list. [folderName] and [configuration] do not apply on the web. The browser saves the file in
 * one step, so progress goes from 0 straight to 100%.
 */
internal actual suspend fun platformSaveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    progress: ProgressReporter,
): String {
    val total = bytes.size.toLong()
    progress.report(0, total)
    // A Kotlin/JS ByteArray is an Int8Array, so it can be passed to Blob without copying.
    val url = triggerBrowserDownload(Blob(arrayOf(bytes.unsafeCast<Int8Array>())), fileName)
    progress.report(total, total)
    return url
}
