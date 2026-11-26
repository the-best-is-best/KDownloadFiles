package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.askWhereToSave
import io.github.kdownloadfile.internal.removeChosenFile
import io.github.kdownloadfile.internal.triggerBrowserDownload
import io.github.kdownloadfile.internal.writeToFile
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import org.w3c.files.Blob
import kotlin.js.Promise

/**
 * The browser asks the user where to save ("Save as") before the download starts, then the data is
 * written there as it arrives. Browsers that cannot ask save to their Downloads folder.
 * [folderName] and [configuration] do not apply on the web: the user chooses the place.
 */
internal actual suspend fun platformDownloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
    progress: ProgressReporter,
): String {
    // Asked first: the browser opens "Save as" only right after the user's click.
    val chosenFile = askWhereToSave(fileName)

    val response = try {
        fetch(url, customHeaders).also {
            if (!it.ok) throw KDownloadFileHttpException(it.status.toInt())
        }
    } catch (e: Throwable) {
        if (chosenFile != null) removeChosenFile(chosenFile)
        throw e
    }

    if (chosenFile == null) {
        val chunks = mutableListOf<dynamic>()
        readBody(response, progress) { chunks.add(it) }
        return triggerBrowserDownload(Blob(chunks.toTypedArray()), fileName)
    }
    return writeToFile(chosenFile) { writable ->
        readBody(response, progress) { chunk ->
            writable.write(chunk).unsafeCast<Promise<Unit>>().await()
        }
    }
}

private suspend fun fetch(url: String, customHeaders: Map<String, String>): Response {
    val headers = Headers()
    customHeaders.forEach { (key, value) -> headers.append(key, value) }
    return try {
        window.fetch(url, RequestInit(method = "GET", headers = headers)).await()
    } catch (e: Throwable) {
        // fetch only rejects when no answer arrived (offline, DNS, CORS).
        throw if (!window.navigator.onLine) KDownloadFileNoInternetException(e)
        else KDownloadFileException("Could not reach the server: ${e.message}", e)
    }
}

/** Reads the body chunk by chunk (each a Uint8Array), passing each to [onChunk] and reporting progress. */
private suspend fun readBody(
    response: Response,
    progress: ProgressReporter,
    onChunk: suspend (dynamic) -> Unit
) {
    val total = response.headers.get("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
    progress.report(0, total)
    // response.body is typed `dynamic` (a ReadableStream), so getReader() is called on it directly.
    val body = response.body
    if (body == null) {
        val blob = response.blob().await()
        onChunk(blob)
        progress.report(blob.size.toLong(), blob.size.toLong())
        return
    }

    val reader = body.getReader()
    var done = 0L
    while (true) {
        val chunk = reader.read().unsafeCast<Promise<dynamic>>().await()
        if (chunk.done as Boolean) break
        onChunk(chunk.value)
        done += (chunk.value.length as Int)
        progress.report(done, total)
    }
    // Without Content-Length the size is known only at the end.
    if (total == null) progress.report(done, done)
}
