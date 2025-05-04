package io.github.kdownloadfile


import kotlinx.browser.document
import org.khronos.webgl.Uint8Array
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.json

actual fun saveBytes(
    bytes: ByteArray,
    fileName: String,
    folderName: String
): String {
    val uint8Array = js("new Uint8Array(bytes)") as Uint8Array
    val blob = Blob(arrayOf(uint8Array), BlobPropertyBag(type = "application/octet-stream"))

    val url = URL.createObjectURL(blob)
    val a = document.createElement("a") as org.w3c.dom.HTMLAnchorElement
    a.href = url
    a.download = fileName
    document.body?.appendChild(a)
    a.click()
    document.body?.removeChild(a)
    URL.revokeObjectURL(url)

    return "Download triggered for $fileName"
}
