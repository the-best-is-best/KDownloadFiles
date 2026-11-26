package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.withProgress
import kotlinx.coroutines.CancellationException

/**
 * Downloads [url] with a single request and saves it as [fileName].
 *
 * Where the file goes depends on [configuration]; see [saveFile] for the folders.
 * On failure the [Result] holds a [KDownloadFileException]:
 * [KDownloadFileHttpException] with the status code (for example 404) or
 * [KDownloadFileNoInternetException] when the server could not be reached.
 *
 * @param onProgress called while the file downloads, on the caller's coroutine (so it can update
 *   the UI directly). Not called on iOS when `ios.showLiveActivity` is on: the Live Activity shows
 *   the progress there.
 * @return the path of the saved file, which can be passed to [openFile].
 */
suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String? = null,
    configuration: KDownloadFileConfiguration = KDownloadFileConfiguration(),
    customHeaders: Map<String, String> = emptyMap(),
    onProgress: ((KDownloadProgress) -> Unit)? = null,
): Result<String> = try {
    Result.success(
        withProgress(onProgress) { progress ->
            platformDownloadFile(url, fileName, folderName, configuration, customHeaders, progress)
        }
    )
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/** Downloads and saves the file; returns its path and throws on failure. */
internal expect suspend fun platformDownloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
    progress: ProgressReporter,
): String
