package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.askWhereToSave
import io.github.kdownloadfile.internal.triggerBrowserDownload
import io.github.kdownloadfile.internal.writeInChunks
import io.github.kdownloadfile.internal.writeToFile
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.w3c.files.Blob
import kotlin.js.Promise

/**
 * The browser asks the user where to save ("Save as"), then the file is written there in 64 KB
 * chunks with progress. Browsers that cannot ask save to their Downloads folder in one step.
 * [folderName] and [configuration] do not apply on the web: the user chooses the place.
 */
internal actual suspend fun platformSaveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    progress: ProgressReporter,
): String {
    // A Kotlin/JS ByteArray is an Int8Array, so it can be used without copying.
    val array = bytes.unsafeCast<Int8Array>()
    val chosenFile = askWhereToSave(fileName)
    if (chosenFile == null) {
        val total = bytes.size.toLong()
        progress.report(0, total)
        val url = triggerBrowserDownload(Blob(arrayOf(array)), fileName)
        progress.report(total, total)
        return url
    }
    return writeToFile(chosenFile) { writable ->
        writeInChunks(bytes, progress) { offset, length ->
            writable.write(array.subarray(offset, offset + length)).unsafeCast<Promise<Unit>>()
                .await()
        }
    }
}
