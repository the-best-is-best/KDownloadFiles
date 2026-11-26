package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.targetPath
import io.github.kdownloadfile.internal.writeInChunks
import io.github.kdownloadfile.internal.writeThroughTempFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

internal actual suspend fun platformSaveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    progress: ProgressReporter,
): String = withContext(Dispatchers.IO) {
    val target = targetPath(fileName, folderName, configuration)
    writeThroughTempFile(target) { temp ->
        Files.newOutputStream(temp).use { output ->
            writeInChunks(bytes, progress) { offset, length -> output.write(bytes, offset, length) }
        }
        true
    }
    target.toAbsolutePath().toString()
}
