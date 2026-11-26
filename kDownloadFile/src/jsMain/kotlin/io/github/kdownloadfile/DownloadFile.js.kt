package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.triggerBrowserDownload
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response

/** The browser decides where the file goes, so [folderName] and [configuration] are not used. */
actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): Result<String> = try {
    val response = fetch(url, customHeaders)
    if (!response.ok) throw KDownloadFileHttpException(response.status.toInt())
    Result.success(triggerBrowserDownload(response.blob().await(), fileName))
} catch (e: Throwable) {
    Result.failure(e)
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
