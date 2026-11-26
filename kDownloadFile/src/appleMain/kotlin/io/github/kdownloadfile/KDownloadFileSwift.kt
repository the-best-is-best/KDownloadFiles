package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * The library's API for Swift (SwiftUI / UIKit apps), with types Swift reads easily:
 *
 * ```swift
 * import kDownloadFileKit
 *
 * let outcome = try await KDownloadFileSwift.shared.download(
 *     url: url, fileName: "report.pdf", folderName: "Reports",
 *     onProgress: { progress in self.percent = progress.percent }
 * )
 * if let success = outcome as? FileOutcome.Success { KDownloadFileSwift.shared.open(path: success.path) }
 * else if let error = outcome as? FileOutcome.HttpError, error.statusCode == 404 { showNoResult() }
 * ```
 *
 * Every call returns a [FileOutcome] (it only throws when the Swift task is cancelled), and
 * `onProgress` is called on the main thread, so SwiftUI state can be set directly.
 */
object KDownloadFileSwift {

    /** Downloads [url] into `Documents/Downloads/<folderName>` (see [downloadFile]). */
    suspend fun download(
        url: String,
        fileName: String,
        folderName: String?,
        onProgress: ((FileProgress) -> Unit)?,
    ): FileOutcome = withContext(Dispatchers.Main) {
        downloadFile(url, fileName, folderName, onProgress = onProgress?.toKotlin()).toOutcome()
    }

    /** Like [download], with custom headers (for example `["Authorization": "Bearer …"]`) and configuration. */
    suspend fun download(
        url: String,
        fileName: String,
        folderName: String?,
        headers: Map<String, String>,
        configuration: KDownloadFileConfiguration,
        onProgress: ((FileProgress) -> Unit)?,
    ): FileOutcome = withContext(Dispatchers.Main) {
        downloadFile(url, fileName, folderName, configuration, headers, onProgress?.toKotlin()).toOutcome()
    }

    /** Saves [data] you already have (for example from your own URLSession call), like [saveFile]. */
    suspend fun save(
        data: NSData,
        fileName: String,
        folderName: String?,
        onProgress: ((FileProgress) -> Unit)?,
    ): FileOutcome = withContext(Dispatchers.Main) {
        saveFile(data.toByteArray(), fileName, folderName, onProgress = onProgress?.toKotlin()).toOutcome()
    }

    /** Shows the file with the system preview. */
    fun open(path: String) = openFile(path)
}

/**
 * Progress of a download or save. When the size is unknown ([isSizeKnown] false),
 * [totalBytes] and [percent] are -1 and [fraction] is 0: show a spinner instead of a percentage.
 */
class FileProgress internal constructor(
    val bytesDone: Long,
    val totalBytes: Long,
    val percent: Int,
    /** 0.0 to 1.0, for `ProgressView(value:)`. */
    val fraction: Double,
    val isSizeKnown: Boolean,
)

/** Outcome of a call; check it with `as?` / `is` in Swift. */
sealed class FileOutcome {
    /** [path] can be passed to [KDownloadFileSwift.open]. */
    class Success internal constructor(val path: String) : FileOutcome()

    /** The server answered with [statusCode], for example 404. */
    class HttpError internal constructor(val statusCode: Int) : FileOutcome()

    /** The server could not be reached. */
    class NoInternet internal constructor() : FileOutcome()

    /** Anything else; [message] says what. */
    class Failed internal constructor(val message: String) : FileOutcome()
}

private fun ((FileProgress) -> Unit).toKotlin(): (KDownloadProgress) -> Unit = { progress ->
    val known = progress.percent != null
    this(
        FileProgress(
            bytesDone = progress.bytesDone,
            totalBytes = if (known) progress.totalBytes ?: -1 else -1,
            percent = progress.percent ?: -1,
            fraction = progress.fraction?.toDouble() ?: 0.0,
            isSizeKnown = known,
        )
    )
}

private fun Result<String>.toOutcome(): FileOutcome = fold(
    onSuccess = { path -> FileOutcome.Success(path) },
    onFailure = { error ->
        when (error) {
            is KDownloadFileHttpException -> FileOutcome.HttpError(error.statusCode)
            is KDownloadFileNoInternetException -> FileOutcome.NoInternet()
            else -> FileOutcome.Failed(error.message ?: error.toString())
        }
    },
)

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val result = ByteArray(size)
    if (size > 0) result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return result
}
