@file:OptIn(ExperimentalJsExport::class)

package io.github.kdownloadfile

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/*
 * The library's API for JavaScript / TypeScript (React etc.), generated into kdownloadfile.d.mts:
 *
 *   import { downloadFile, DownloadResult } from 'kdownloadfile'
 *
 *   const result = await downloadFile(url, 'report.pdf', p => setPercent(p.percent))
 *   if (result instanceof DownloadResult.Success) openFile(result.path)
 *   else if (result instanceof DownloadResult.HttpError && result.statusCode === 404) showNoResult()
 *
 * Every call resolves (never rejects) with a DownloadResult, so success and errors are handled in one place.
 */

private val scope = MainScope()

/** Downloads [url]; the browser saves it as [fileName]. */
@JsExport
@JsName("downloadFile")
fun downloadFileForJs(
    url: String,
    fileName: String,
    onProgress: ((DownloadProgress) -> Unit)? = null,
): Promise<DownloadResult> = scope.promise {
    downloadFile(url, fileName, onProgress = onProgress?.toKotlin()).toJs()
}

/** Saves [bytes]; the browser downloads it as [fileName]. */
@JsExport
@JsName("saveBytes")
fun saveBytesForJs(
    bytes: Uint8Array,
    fileName: String,
    onProgress: ((DownloadProgress) -> Unit)? = null,
): Promise<DownloadResult> = scope.promise {
    // Kotlin's ByteArray is an Int8Array: same memory, no copy.
    val byteArray = Int8Array(bytes.buffer, bytes.byteOffset, bytes.length).unsafeCast<ByteArray>()
    saveFile(byteArray, fileName, onProgress = onProgress?.toKotlin()).toJs()
}

/** Saves [text] as UTF-8; the browser downloads it as [fileName]. */
@JsExport
@JsName("saveText")
fun saveTextForJs(
    text: String,
    fileName: String,
    onProgress: ((DownloadProgress) -> Unit)? = null,
): Promise<DownloadResult> = scope.promise {
    saveFile(text.encodeToByteArray(), fileName, onProgress = onProgress?.toKotlin()).toJs()
}

/** Opens a [DownloadResult.Success.path] (a blob URL) in a new tab. */
@JsExport
@JsName("openFile")
fun openFileForJs(path: String) = openFile(path)

/**
 * Progress of a download or save, with plain `number`s for TypeScript.
 * [totalBytes] and [percent] are null while the size is unknown.
 */
@JsExport
class DownloadProgress internal constructor(
    val bytesDone: Double,
    val totalBytes: Double?,
    val percent: Int?,
)

/** Outcome of a call; check it with `instanceof`. */
@JsExport
sealed class DownloadResult {
    /** [path] is a blob URL that [openFileForJs] can open. */
    class Success internal constructor(val path: String) : DownloadResult()

    /** The server answered with [statusCode], for example 404. */
    class HttpError internal constructor(val statusCode: Int) : DownloadResult()

    /** The server could not be reached. */
    class NoInternet internal constructor() : DownloadResult()

    /** Anything else; [message] says what. */
    class Failed internal constructor(val message: String) : DownloadResult()
}

private fun ((DownloadProgress) -> Unit).toKotlin(): (KDownloadProgress) -> Unit = { progress ->
    this(
        DownloadProgress(
            progress.bytesDone.toDouble(),
            progress.totalBytes?.toDouble(),
            progress.percent
        )
    )
}

private fun Result<String>.toJs(): DownloadResult = fold(
    onSuccess = { path -> DownloadResult.Success(path) },
    onFailure = { error ->
        when (error) {
            is KDownloadFileHttpException -> DownloadResult.HttpError(error.statusCode)
            is KDownloadFileNoInternetException -> DownloadResult.NoInternet()
            else -> DownloadResult.Failed(error.message ?: error.toString())
        }
    },
)
