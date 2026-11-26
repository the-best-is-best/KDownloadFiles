package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.LiveActivity
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.downloadWithUrlSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Always one `NSURLSession` request from Kotlin: HTTP errors come back as [KDownloadFileHttpException]
 * with their status code, and progress is reported.
 *
 * With `ios.showLiveActivity` the progress is also shown as a Live Activity, when the app includes
 * the optional Swift package KFileLiveActivity (without it the download runs without one).
 */
internal actual suspend fun platformDownloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
    progress: ProgressReporter,
): String = withContext(Dispatchers.Default) {
    val liveActivity = if (configuration.ios.showLiveActivity) LiveActivity.start(fileName, kind = "download") else null
    val reporter = if (liveActivity == null) progress else ProgressReporter { bytesDone, totalBytes ->
        progress.report(bytesDone, totalBytes)
        liveActivity.update(bytesDone, totalBytes)
    }
    try {
        downloadWithUrlSession(url, fileName, folderName, configuration, customHeaders, reporter).also {
            liveActivity?.end(success = true, status = "Done")
        }
    } catch (e: CancellationException) {
        liveActivity?.end(success = false, status = "Cancelled")
        throw e
    } catch (e: Throwable) {
        liveActivity?.end(
            success = false,
            status = when (e) {
                is KDownloadFileHttpException -> if (e.statusCode == 404) "File not found" else "Error ${e.statusCode}"
                is KDownloadFileNoInternetException -> "No internet connection"
                else -> "Failed"
            },
        )
        throw e
    }
}
