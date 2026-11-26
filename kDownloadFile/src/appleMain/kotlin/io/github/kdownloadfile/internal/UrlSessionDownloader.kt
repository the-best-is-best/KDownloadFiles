package io.github.kdownloadfile.internal

import io.github.kdownloadfile.KDownloadFileException
import io.github.kdownloadfile.KDownloadFileHttpException
import io.github.kdownloadfile.KDownloadFileNoInternetException
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLSession
import platform.Foundation.downloadTaskWithRequest
import platform.Foundation.setValue

/**
 * Downloads [url] with a single request and saves it like `saveFile` does.
 *
 * Unlike the Swift `DownloadManagerInterop`, this sends no probe request first and checks the
 * HTTP status of the download itself, so a 404 comes back as [KDownloadFileHttpException]
 * instead of being saved as the file.
 *
 * @return the path of the saved file.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun downloadWithUrlSession(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): String {
    val nsUrl = NSURL.URLWithString(url) ?: throw KDownloadFileException("Invalid URL: $url")

    val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
        setValue(userAgent(), forHTTPHeaderField = "User-Agent")
        customHeaders.forEach { (key, value) -> setValue(value, forHTTPHeaderField = key) }
    }

    return suspendCancellableCoroutine { continuation ->
        val task =
            NSURLSession.sharedSession.downloadTaskWithRequest(request) { location, response, error ->
                // The downloaded file is deleted when this block returns, so it is moved here.
                val result = when {
                    error != null -> Result.failure(error.toDownloadException())
                    else -> {
                        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                        when {
                            status !in 200..299 -> Result.failure(KDownloadFileHttpException(status))
                            location == null -> Result.failure(KDownloadFileException("The download has no file"))
                            else -> runCatching {
                                moveIntoPlace(
                                    location,
                                    fileName,
                                    folderName,
                                    configuration
                                )
                            }
                        }
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun moveIntoPlace(
    location: NSURL,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
): String {
    val fileManager = NSFileManager.defaultManager
    val target = targetUrl(fileName, folderName, configuration)
    // Only now that the new file has arrived is the old one removed (noDuplicateFile).
    target.path?.takeIf(fileManager::fileExistsAtPath)?.let { path ->
        throwOnError { errorPtr -> fileManager.removeItemAtPath(path, errorPtr) }
    }
    throwOnError { errorPtr -> fileManager.moveItemAtURL(location, target, errorPtr) }
    return finishSavedFile(target, configuration)
}

private fun NSError.toDownloadException(): KDownloadFileException {
    val offline = domain == NSURLErrorDomain && code in setOf(
        NSURLErrorNotConnectedToInternet,
        NSURLErrorNetworkConnectionLost,
        NSURLErrorCannotFindHost,
        NSURLErrorCannotConnectToHost,
        NSURLErrorDNSLookupFailed,
        NSURLErrorTimedOut,
        NSURLErrorDataNotAllowed,
        NSURLErrorInternationalRoamingOff,
    )
    return when {
        offline -> KDownloadFileNoInternetException(Exception(localizedDescription))
        domain == NSURLErrorDomain && code == NSURLErrorCancelled -> KDownloadFileException("Download cancelled")
        else -> KDownloadFileException(localizedDescription)
    }
}

private fun userAgent(): String {
    val info = NSBundle.mainBundle.infoDictionary
    val appName = info?.get("CFBundleName") as? String ?: "App"
    val version = info?.get("CFBundleShortVersionString") as? String ?: "1.0"
    return "$appName/$version (iOS; ${NSProcessInfo.processInfo.operatingSystemVersionString})"
}
