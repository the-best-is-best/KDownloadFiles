package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.triggerBrowserDownload
import org.khronos.webgl.Int8Array
import org.w3c.files.Blob

/** The browser decides where the file goes, so [folderName] and [configuration] are not used. */
actual suspend fun saveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
): Result<String> = try {
    // A Kotlin/JS ByteArray is an Int8Array, so it can be passed to Blob without copying.
    Result.success(triggerBrowserDownload(Blob(arrayOf(bytes.unsafeCast<Int8Array>())), fileName))
} catch (e: Throwable) {
    Result.failure(e)
}
