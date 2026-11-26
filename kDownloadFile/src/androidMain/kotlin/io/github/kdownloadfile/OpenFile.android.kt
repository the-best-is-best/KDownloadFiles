package io.github.kdownloadfile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import io.github.kdownloadfile.internal.findDownloadsUri
import io.github.kdownloadfile.internal.mimeTypeOf
import java.io.File

private const val TAG = "OpenFile"

actual fun openFile(filePath: String) {
    val context = AndroidKDownloadFile.appContext
    val file = File(filePath)

    // On Android 10 a file this app saved to Downloads through MediaStore (see saveFile) cannot be
    // read through its path, so it is opened through its MediaStore entry instead.
    val uri: Uri = if (file.exists()) {
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting URI: ${e.message}")
            return
        }
    } else {
        findDownloadsUri(context, file) ?: run {
            Log.e(TAG, "❌ File does not exist: ${file.absolutePath}")
            return
        }
    }

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeTypeOf(file.name))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY)
    }

    try {
        context.startActivity(
            Intent.createChooser(viewIntent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "❌ No Activity found to handle file: ${file.name}")
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error opening file: ${e.message}", e)
    }
}
