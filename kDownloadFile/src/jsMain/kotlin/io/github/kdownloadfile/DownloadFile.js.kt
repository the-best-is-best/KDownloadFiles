package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.triggerBrowserDownload
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import org.w3c.files.Blob
import kotlin.js.Promise

/**
 * Downloads with `fetch` (so progress can be reported), then hands the file to the browser's own
 * download: it appears in the browser's downloads list and goes where the browser saves files.
 * [folderName] and [configuration] do not apply on the web.
 */
internal actual suspend fun platformDownloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
    progress: ProgressReporter,
): String {
    val response = fetch(url, customHeaders)
    if (!response.ok) throw KDownloadFileHttpException(response.status.toInt())
    return triggerBrowserDownload(readBody(response, progress), fileName)
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

/** Reads the body chunk by chunk, so the bytes received can be reported. */
private suspend fun readBody(response: Response, progress: ProgressReporter): Blob {
    val total = response.headers.get("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
    // response.body is typed `dynamic` (a ReadableStream), so getReader() is called on it directly.
    val body = response.body ?: return response.blob().await()

    val reader = body.getReader()
    val chunks = mutableListOf<dynamic>()
    var done = 0L
    progress.report(0, total)
    while (true) {
        val chunk = reader.read().unsafeCast<Promise<dynamic>>().await()
        if (chunk.done as Boolean) break
        chunks.add(chunk.value)
        done += (chunk.value.length as Int)
        progress.report(done, total)
    }
    // Without Content-Length the size is known only at the end.
    if (total == null) progress.report(done, done)
    return Blob(chunks.toTypedArray())
}
