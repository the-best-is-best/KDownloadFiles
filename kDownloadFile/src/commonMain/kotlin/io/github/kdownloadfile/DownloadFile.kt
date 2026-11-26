package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration

/**
 * Downloads [url] with a single request and saves it as [fileName].
 *
 * Where the file goes depends on [configuration]; see [saveFile] for the folders.
 * On failure the [Result] holds a [KDownloadFileException]:
 * [KDownloadFileHttpException] with the status code (for example 404) or
 * [KDownloadFileNoInternetException] when the server could not be reached.
 *
 * @return the path of the saved file, which can be passed to [openFile].
 */
expect suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String? = null,
    configuration: KDownloadFileConfiguration = KDownloadFileConfiguration(),
    customHeaders: Map<String, String> = emptyMap(),
): Result<String>
