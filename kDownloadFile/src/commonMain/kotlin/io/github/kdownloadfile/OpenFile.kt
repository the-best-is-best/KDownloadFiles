package io.github.kdownloadfile

/** Opens the file at [filePath] (as returned by [downloadFile] or [saveFile]) in the app the system picks. */
expect fun openFile(
    filePath: String
)
