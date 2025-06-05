package io.github.kdownloadfile

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

actual fun openFile(filePath: String) {
    val context = AndroidKDownloadFile.appContext
    val externalStoragePath = Environment.getExternalStorageDirectory().path

    val file: File? = when {
        filePath.startsWith("content://") -> null
        filePath.startsWith("file://") -> File(filePath.toUri().path ?: "")
        filePath.startsWith("/storage/emulated/0/Android/data/${context.packageName}/") -> File(
            filePath
        )

        filePath.startsWith("/storage/emulated/") || filePath.startsWith(externalStoragePath) -> File(
            filePath
        )
        else -> File(context.getExternalFilesDir(null), filePath)
    }

    if (file != null && !file.exists()) {
        Log.e("OpenFile", "❌ File does not exist: ${file.absolutePath}")
        return
    }

    val uri: Uri = try {
        if (file != null) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else if (filePath.startsWith("content://")) {
            filePath.toUri()
        } else {
            throw IllegalArgumentException("Unsupported file path or URI: $filePath")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("OpenFile", "❌ Error getting URI: ${e.message}")
        return
    }

    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        val chooser = Intent.createChooser(viewIntent, "Open with").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("OpenFile", "❌ Error opening file: ${e::class.simpleName}: ${e.message}")
    }
}

actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?,
): Result<String> = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        val context = AndroidKDownloadFile.appContext // احصل على applicationContext وليس activity
        val destinationPath = if (!folderName.isNullOrEmpty()) {
            "$folderName/$fileName"
        } else {
            fileName
        }

        try {
            val request = DownloadManager.Request(url.toUri())
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationPath)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == downloadId) {
                        try {
                            context.unregisterReceiver(this)
                        } catch (ex: Exception) {
                            // Ignore
                        }

                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)

                        if (cursor != null && cursor.moveToFirst()) {
                            val status =
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val uri =
                                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                                cursor.close()
                                continuation.resume(Result.success(uri ?: ""))
                            } else {
                                val reason =
                                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                cursor.close()
                                continuation.resume(Result.failure(Exception("Download failed: reason $reason")))
                            }
                        } else {
                            cursor?.close()
                            continuation.resume(Result.failure(Exception("Cursor is empty or null.")))
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )

            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }

        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }
}
