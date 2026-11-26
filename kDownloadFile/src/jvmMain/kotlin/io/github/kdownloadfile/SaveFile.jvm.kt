package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.targetPath
import io.github.kdownloadfile.internal.writeThroughTempFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

actual suspend fun saveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val target = targetPath(fileName, folderName, configuration)
        writeThroughTempFile(target) { temp -> Files.write(temp, bytes); true }
        Result.success(target.toAbsolutePath().toString())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
