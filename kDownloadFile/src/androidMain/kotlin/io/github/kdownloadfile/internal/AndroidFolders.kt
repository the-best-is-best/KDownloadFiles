package io.github.kdownloadfile.internal

import android.content.Context
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import java.io.File

/** `<app name>/<folderName>`: the library's folder inside the public Downloads folder. */
internal fun publicDownloadsSubPath(folderName: String?): String =
    listOfNotNull(appName(), folderName?.takeIf { it.isNotBlank() }).joinToString("/")

/** The app-private folder for [configuration]: `cacheDir` or `filesDir`, plus [folderName]. */
internal fun privateFolder(
    context: Context,
    configuration: KDownloadFileConfiguration,
    folderName: String?,
): File {
    val base = if (configuration.saveInCacheFiles) context.cacheDir else context.filesDir
    return if (folderName.isNullOrBlank()) base else File(base, folderName)
}
