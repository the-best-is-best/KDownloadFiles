package io.github.kdownloadfile


import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.HTMLAnchorElement
import org.w3c.fetch.RequestInit

actual fun openFile(filePath: String) {
    window.open(filePath, "_blank")
}
actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): Result<String> {
    return try {
        // Create headers
        val headers = js("new Headers()")
        headers.append("User-Agent", getUserAgent()) // Optional: not all browsers allow overriding

        // Add custom headers
        customHeaders.forEach { (key, value) ->
            headers.append(key, value)
        }

        // Build request
        val requestInit = RequestInit(
            method = "GET",
            headers = headers
        )

        val response = window.fetch(url, requestInit).await()
        if (!response.ok) {
            return Result.failure(Exception("Failed to fetch file: ${response.status}"))
        }

        val blob = response.blob().await()
        val blobUrl = js("window.URL.createObjectURL(blob)") as String

        // Download
        val anchor = window.document.createElement("a") as HTMLAnchorElement
        anchor.href = blobUrl
        anchor.download = fileName
        anchor.style.display = "none"
        window.document.body?.appendChild(anchor)
        anchor.click()

        // Clean up
        window.setTimeout({
            js("window.URL.revokeObjectURL(blobUrl)")
            anchor.remove()
        }, 2000)

        Result.success(blobUrl)
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

private fun getUserAgent(): String {
    return js("navigator.userAgent") as String
}
