package io.github.kdownloadfile


import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.HTMLAnchorElement

actual fun openFile(filePath: String) {
    window.open(filePath, "_blank")
}

actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?
): Result<String> {
    return try {
        val response = window.fetch(url).await()
        if (!response.ok) {
            return Result.failure(Exception("Failed to fetch file: ${response.status}"))
        }

        val blob = response.blob().await()

        val blobUrl = js("window.URL.createObjectURL(blob)") as String

        val anchor = window.document.createElement("a") as HTMLAnchorElement
        anchor.href = blobUrl

        anchor.download = fileName

        anchor.style.display = "none"
        window.document.body?.appendChild(anchor)
        anchor.click()

        window.setTimeout({
            js("window.URL.revokeObjectURL(blobUrl)")
            anchor.remove()
        }, 2000)

        Result.success(blobUrl)
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
