package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.downloadWithLiveActivity
import io.github.kdownloadfile.internal.downloadWithUrlSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * - Without a Live Activity (the default): one `NSURLSession` request from Kotlin, so HTTP errors
 *   come back as [KDownloadFileHttpException] with their status code, and progress is reported.
 * - With a Live Activity: the Swift `DownloadManagerInterop`, which shows the progress itself.
 */
internal actual suspend fun platformDownloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
    progress: ProgressReporter,
): String = withContext(Dispatchers.Default) {
    if (configuration.ios.showLiveActivity) {
        downloadWithLiveActivity(url, fileName, folderName, configuration, customHeaders)
    } else {
        downloadWithUrlSession(url, fileName, folderName, configuration, customHeaders, progress)
    }
}
