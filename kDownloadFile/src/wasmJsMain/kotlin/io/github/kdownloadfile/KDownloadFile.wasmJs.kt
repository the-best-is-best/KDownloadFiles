package io.github.kdownloadfile


actual fun openFile(filePath: String) {
}

actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?
): Result<String> {
    TODO("Not yet implemented")
}