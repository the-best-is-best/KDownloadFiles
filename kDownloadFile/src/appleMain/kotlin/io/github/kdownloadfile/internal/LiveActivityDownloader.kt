package io.github.kdownloadfile.internal

import io.github.kdownloadfile.KDownloadFileException
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.native.kdownloadfile.DownloadManagerInterop
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Downloads through the Swift `DownloadManagerInterop` (KDownloadFileInterop), which shows the
 * progress as a Live Activity.
 *
 * The Swift side checks the URL with an extra request first and does not report HTTP status
 * codes, so failures come back as a plain [KDownloadFileException].
 *
 * @return the path of the saved file.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun downloadWithLiveActivity(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): String {
    val headers: Map<Any?, Any?> =
        customHeaders.entries.associate { it.key as Any? to it.value as Any? }
    return suspendCancellableCoroutine { continuation ->
        DownloadManagerInterop().downloadFile(
            urlString = url,
            fileName = fileName,
            folderName = folderName,
            customHeaders = headers,
            saveInCacheFiles = configuration.saveInCacheFiles,
            showLiveActivity = configuration.ios.showLiveActivity,
            saveToDownloads = configuration.saveToDownloads,
            noDuplicateFile = configuration.noDuplicateFile,
            completionHandler = { path, error ->
                when {
                    error != null ->
                        continuation.resumeWithException(KDownloadFileException(error.localizedDescription))

                    path != null -> continuation.resume(path)
                    else -> continuation.resumeWithException(KDownloadFileException("No path and no error"))
                }
            }
        )
    }
}
