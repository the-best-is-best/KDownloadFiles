package io.github.kdownloadfile

import android.os.Build
import android.os.Environment
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.privateFolder
import io.github.kdownloadfile.internal.publicDownloadsSubPath
import io.github.kdownloadfile.internal.saveToDownloadsWithMediaStore
import io.github.kdownloadfile.internal.writeFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun saveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val context = AndroidKDownloadFile.appContext
        val path = when {
            // Android 10+: through MediaStore, no storage permission needed.
            configuration.saveToDownloads && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                saveToDownloadsWithMediaStore(
                    context,
                    bytes,
                    fileName,
                    folderName,
                    configuration.noDuplicateFile
                )

            // Android 6 to 9: written directly, needs the WRITE_EXTERNAL_STORAGE permission.
            configuration.saveToDownloads -> writeFile(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    publicDownloadsSubPath(folderName)
                ),
                bytes, fileName, configuration.noDuplicateFile
            )

            else -> writeFile(
                privateFolder(context, configuration, folderName),
                bytes,
                fileName,
                configuration.noDuplicateFile
            )
        }
        Result.success(path)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
