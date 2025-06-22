package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration

expect fun openFile(
    filePath: String
)

expect suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String? = null,
    configuration: KDownloadFileConfiguration = KDownloadFileConfiguration(),
    customHeaders: Map<String, String> = emptyMap(),

): Result<String>
