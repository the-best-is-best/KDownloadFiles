package io.github.kdownloadfile.internal

import io.github.kdownloadfile.KDownloadFileCancelledException
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.js.Promise

/*
 * On the web the browser always asks the user where to save, with its "Save as" window
 * (showSaveFilePicker, File System Access API: Chrome and Edge on desktop).
 *
 * The browser opens that window only right after a click, so it is opened *before* the download
 * starts. Browsers without the API (Firefox, Safari, phones) save to their Downloads folder instead.
 */

internal fun isSaveAsSupported(): Boolean =
    js("typeof window.showSaveFilePicker === 'function'").unsafeCast<Boolean>()

/**
 * Asks the user where to save [fileName] ("Save as"). Returns the chosen file, or null when the
 * browser cannot ask (then the caller uses the browser's own download).
 *
 * @throws KDownloadFileCancelledException when the user closes the window without choosing.
 */
internal suspend fun askWhereToSave(fileName: String): dynamic {
    if (!isSaveAsSupported()) return null
    return try {
        window.asDynamic().showSaveFilePicker(
            jsObject("suggestedName" to fileName, "startIn" to "downloads")
        ).unsafeCast<Promise<dynamic>>().await()
    } catch (e: Throwable) {
        if (e.asDynamic().name == "AbortError") throw KDownloadFileCancelledException() else throw e
    }
}

/**
 * Writes the file the user chose, through the browser's writable stream: the data goes to a
 * temporary file and only replaces the real one when [write] finishes.
 *
 * @return a blob URL of the written file, for `openFile`.
 */
internal suspend fun writeToFile(
    fileHandle: dynamic,
    write: suspend (writable: dynamic) -> Unit
): String {
    val writable = fileHandle.createWritable().unsafeCast<Promise<dynamic>>().await()
    try {
        write(writable)
        writable.close().unsafeCast<Promise<Unit>>().await()
    } catch (e: Throwable) {
        runCatching { writable.abort().unsafeCast<Promise<Unit>>().await() }
        throw e
    }
    val file = fileHandle.getFile().unsafeCast<Promise<Blob>>().await()
    return URL.createObjectURL(file)
}

/** Removes the file the "Save as" window created when the download then failed (for example a 404). */
internal suspend fun removeChosenFile(handle: dynamic) {
    if (handle.remove != undefined) runCatching {
        handle.remove().unsafeCast<Promise<Unit>>().await()
    }
}

/** A plain JS object, for the options of browser APIs. */
private fun jsObject(vararg entries: Pair<String, Any?>): dynamic {
    val result = js("{}")
    entries.forEach { (key, value) -> result[key] = value }
    return result
}
