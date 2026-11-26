package io.github.kdownloadfile

import io.github.native.kdownloadfile.FileOpener
import kotlinx.cinterop.ExperimentalForeignApi

/** Shows the file with the system preview (Swift `FileOpener` from KDownloadFileInterop). */
@OptIn(ExperimentalForeignApi::class)
actual fun openFile(filePath: String) {
    FileOpener.openFileWithFilePath(filePath)
}
