package io.github.kdownloadfile

import kotlinx.browser.window

/** Opens the URL returned by [downloadFile] or [saveFile] in a new tab. */
actual fun openFile(filePath: String) {
    window.open(filePath, "_blank")
}
