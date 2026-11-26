@file:OptIn(ExperimentalJsExport::class)

package io.github.kdownloadfile

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import kotlinx.js.JsPlainObject
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/*
 * The library's API for JavaScript / TypeScript (React etc.), generated into kdownloadfile.d.mts:
 *
 *   import { downloadFile, openFile, DownloadResult } from 'kdownloadfile'
 *
 *   // Downloads with progress, then the browser saves it (it shows in the browser's downloads list).
 *   const result = await downloadFile(url, 'report.pdf', { onProgress: setProgress })
 *   if (result instanceof DownloadResult.Success) openFile(result.path)
 *   else if (result instanceof DownloadResult.HttpError && result.statusCode === 404) showNoResult()
 *
 * Every call resolves (never rejects) with a DownloadResult, so success and errors are handled in one place.
 */

private val scope = MainScope()

/**
 * Options of [downloadFileForJs] and the save functions; a plain object in TypeScript, every field optional.
 * There is nothing about where to save: the browser saves the file like any download.
 *
 * - `headers`: extra request headers, for example `{ Authorization: 'Bearer …' }` (downloads only).
 * - `onProgress`: called while the file downloads or is written.
 */
@JsExport
@JsPlainObject
external interface FileOptions {
    /** A plain object of header names to values, for example `{ Authorization: 'Bearer …' }`. */
    val headers: dynamic
    val onProgress: ((DownloadProgress) -> Unit)?
}

/** Downloads [url]; the browser then saves it as [fileName], like any download. */
@JsExport
@JsName("downloadFile")
fun downloadFileForJs(
    url: String,
    fileName: String,
    options: FileOptions? = null
): Promise<DownloadResult> =
    scope.promise {
        downloadFile(
            url,
            fileName,
            customHeaders = headersToMap(options?.headers),
            onProgress = options?.onProgress?.toKotlin(),
        ).toJs()
    }

/** Saves [bytes]; the browser saves it as [fileName], like any download. */
@JsExport
@JsName("saveBytes")
fun saveBytesForJs(
    bytes: Uint8Array,
    fileName: String,
    options: FileOptions? = null
): Promise<DownloadResult> =
    scope.promise {
        // Kotlin's ByteArray is an Int8Array: same memory, no copy.
        val byteArray =
            Int8Array(bytes.buffer, bytes.byteOffset, bytes.length).unsafeCast<ByteArray>()
        save(byteArray, fileName, options)
    }

/** Saves [text] (UTF-8); the browser saves it as [fileName], like any download. */
@JsExport
@JsName("saveText")
fun saveTextForJs(
    text: String,
    fileName: String,
    options: FileOptions? = null
): Promise<DownloadResult> =
    scope.promise { save(text.encodeToByteArray(), fileName, options) }

/** Opens a [DownloadResult.Success.path] (a blob URL) in a new tab. */
@JsExport
@JsName("openFile")
fun openFileForJs(path: String) = openFile(path)

private suspend fun save(
    bytes: ByteArray,
    fileName: String,
    options: FileOptions?
): DownloadResult =
    saveFile(
        bytes,
        fileName,
        onProgress = options?.onProgress?.toKotlin(),
    ).toJs()

private fun headersToMap(headers: dynamic): Map<String, String> =
    if (headers == null || headers == undefined) emptyMap()
    else js("Object.keys")(headers).unsafeCast<Array<String>>()
        .associateWith { key -> headers[key].toString() }

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
