package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.finishSavedFile
import io.github.kdownloadfile.internal.targetUrl
import io.github.kdownloadfile.internal.throwOnError
import io.github.kdownloadfile.internal.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.writeToURL

@OptIn(ExperimentalForeignApi::class)
actual suspend fun saveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
): Result<String> = withContext(Dispatchers.Default) {
    try {
        val target = targetUrl(fileName, folderName, configuration)
        // An atomic write replaces an existing file and never leaves a partial one behind.
        throwOnError { errorPtr ->
            bytes.toNSData().writeToURL(target, NSDataWritingAtomic, errorPtr)
        }
        Result.success(finishSavedFile(target, configuration))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
