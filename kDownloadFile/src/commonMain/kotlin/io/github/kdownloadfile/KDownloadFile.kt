package io.github.kdownloadfile

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

expect fun openFile(
    filePath: String
)

expect suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String? = null,
    customHeaders: Map<String, String> = emptyMap()
): Result<String>


@OptIn(ExperimentalTime::class)
internal fun generateHashedFileName(originalName: String): String {
    val dotIndex = originalName.lastIndexOf('.')
    val namePart = if (dotIndex != -1) originalName.substring(0, dotIndex) else originalName
    val extPart = if (dotIndex != -1) originalName.substring(dotIndex) else ""

    // استخدم timestamp كهاش بسيط
    val hash = Clock.System.now().toEpochMilliseconds()
    return "${namePart}_$hash$extPart"
}
