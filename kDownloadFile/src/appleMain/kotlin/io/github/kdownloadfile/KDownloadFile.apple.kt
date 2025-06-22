package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.native.kdownloadfile.DownloadManagerInterop
import io.github.native.kdownloadfile.FileOpener
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
actual fun openFile(filePath: String) {
    FileOpener.openFileWithFilePath(filePath)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): Result<String> = withContext(Dispatchers.Default) {
    try {
        val headersAny: Map<Any?, Any?> =
            customHeaders.entries.associate { it.key as Any? to it.value as Any? }

        val result = suspendCancellableCoroutine { continuation ->
            DownloadManagerInterop().downloadFile(
                urlString = url,
                fileName = fileName,
                folderName = folderName,
                customHeaders = headersAny,
                showLiveActivity = configuration.ios.showLiveActivity,
                completionHandler = { path, error ->
                    if (error != null) {
                        continuation.resumeWithException(Exception(error.localizedDescription))
                    } else if (path != null) {
                        continuation.resume(path)
                    } else {
                        continuation.resumeWithException(Exception("Unknown error: no path and no error"))
                    }
                }
            )
        }
        Result.success(result)
    } catch (e: Exception) {
        Result.failure(e)
    }
}