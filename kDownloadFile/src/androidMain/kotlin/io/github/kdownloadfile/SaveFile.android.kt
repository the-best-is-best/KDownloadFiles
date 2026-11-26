package io.github.kdownloadfile

import android.os.Build
import android.os.Environment
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.privateFolder
import io.github.kdownloadfile.internal.publicDownloadsSubPath
import io.github.kdownloadfile.internal.saveToDownloadsWithMediaStore
import io.github.kdownloadfile.internal.writeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual suspend fun platformSaveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    progress: ProgressReporter,
): String = withContext(Dispatchers.IO) {
    val context = AndroidKDownloadFile.appContext
    when {
        // Android 10+: through MediaStore, no storage permission needed.
        configuration.saveToDownloads && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            saveToDownloadsWithMediaStore(
                context,
                bytes,
                fileName,
                folderName,
                configuration.noDuplicateFile,
                progress
            )

        // Android 6 to 9: written directly, needs the WRITE_EXTERNAL_STORAGE permission.
        configuration.saveToDownloads -> writeFile(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                publicDownloadsSubPath(folderName)
            ),
            bytes, fileName, configuration.noDuplicateFile, progress
        )

        else -> writeFile(
            privateFolder(context, configuration, folderName),
            bytes,
            fileName,
            configuration.noDuplicateFile,
            progress
        )
    }
}
