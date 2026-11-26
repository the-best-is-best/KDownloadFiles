package io.github.kdownloadfile

import android.os.Environment
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.DownloadManagerTarget
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.deleteExisting
import io.github.kdownloadfile.internal.downloadWithDownloadManager
import io.github.kdownloadfile.internal.freeFileName
import io.github.kdownloadfile.internal.isNetworkAvailable
import io.github.kdownloadfile.internal.moveFile
import io.github.kdownloadfile.internal.privateFolder
import io.github.kdownloadfile.internal.publicDownloadsSubPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads with Android's DownloadManager (so the system notification can be shown).
 *
 * - Downloads folder: DownloadManager writes straight to `Download/<app name>/<folderName>`.
 * - Cache / private files: DownloadManager can only write to external storage, so the file is
 *   downloaded to the app's external files folder and then moved to `cacheDir` / `filesDir`.
 */
internal actual suspend fun platformDownloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
    progress: ProgressReporter,
): String = withContext(Dispatchers.IO) {
    val context = AndroidKDownloadFile.appContext
    if (!isNetworkAvailable(context)) throw KDownloadFileNoInternetException()

    if (configuration.saveToDownloads) {
        val relativePath = "${publicDownloadsSubPath(folderName)}/$fileName"
        if (configuration.noDuplicateFile) {
            // DownloadManager writes straight into the final place, so the old file goes first.
            deleteExisting(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    relativePath
                )
            )
        }
        // With noDuplicateFile = false DownloadManager picks a free name itself.
        val downloaded = downloadWithDownloadManager(
            context, url, customHeaders, configuration.android,
            DownloadManagerTarget.PublicDownloads(relativePath),
            progress,
        )
        return@withContext downloaded.absolutePath
    }

    val folder = privateFolder(context, configuration, folderName)
    val name = if (configuration.noDuplicateFile) fileName else freeFileName(fileName) {
        File(folder, it).exists()
    }
    val downloaded = downloadWithDownloadManager(
        context, url, customHeaders, configuration.android,
        DownloadManagerTarget.ExternalAppFiles(
            subFolder = if (configuration.saveInCacheFiles) "cache_temp" else null,
            fileName = name,
        ),
        progress,
    )
    // The old file is replaced only now that the new one has arrived.
    moveFile(downloaded, File(folder, name)).absolutePath
}
