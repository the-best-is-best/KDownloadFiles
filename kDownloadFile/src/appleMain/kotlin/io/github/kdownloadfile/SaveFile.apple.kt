package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.finishSavedFile
import io.github.kdownloadfile.internal.targetUrl
import io.github.kdownloadfile.internal.writeInChunksToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun platformSaveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    progress: ProgressReporter,
): String = withContext(Dispatchers.Default) {
    val target = targetUrl(fileName, folderName, configuration)
    writeInChunksToFile(bytes, target, progress)
    finishSavedFile(target, configuration)
}
