package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.downloadWithLiveActivity
import io.github.kdownloadfile.internal.downloadWithUrlSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * - Without a Live Activity (the default): one `NSURLSession` request from Kotlin, so HTTP errors
 *   come back as [KDownloadFileHttpException] with their status code.
 * - With a Live Activity: the Swift `DownloadManagerInterop`, which shows the download progress.
 */
actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): Result<String> = withContext(Dispatchers.Default) {
    try {
        val path = if (configuration.ios.showLiveActivity) {
            downloadWithLiveActivity(url, fileName, folderName, configuration, customHeaders)
        } else {
            downloadWithUrlSession(url, fileName, folderName, configuration, customHeaders)
        }
        Result.success(path)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
