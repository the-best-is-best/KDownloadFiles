package io.github.kdownloadfile.internal

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob

/**
 * Lets the browser download [blob] as [fileName].
 *
 * @return a blob URL for the file. It stays valid until the page is closed, so it can be
 * passed to `openFile`.
 */
internal fun triggerBrowserDownload(blob: Blob, fileName: String): String {
    val blobUrl = URL.createObjectURL(blob)
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = blobUrl
    anchor.download = fileName
    anchor.style.display = "none"
    document.body?.appendChild(anchor)
    anchor.click()
    window.setTimeout({ anchor.remove() }, 0)
    return blobUrl
}
