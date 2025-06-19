package io.github.kdownloadfile

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
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
    customHeaders: Map<String, String>,
): Result<String> = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine<Result<String>> { continuation ->

        val context = AndroidKDownloadFile.appContext
        val destinationPath = if (!folderName.isNullOrEmpty()) {
            "$folderName/$fileName"
        } else {
            fileName
        }

        try {
            val request = DownloadManager.Request(url.trim().toUri())
                .setTitle(fileName)
                .setDescription("Downloading")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationPath)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .addRequestHeader("User-Agent", getUserAgent())

            for ((key, value) in customHeaders) {
                request.addRequestHeader(key, value)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                request.setRequiresCharging(false)
                request.setRequiresDeviceIdle(false)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            if (downloadId == -1L) {
                continuation.resume(Result.failure(Exception("❌ Failed to start download: Invalid downloadId (-1L)")))
                return@suspendCancellableCoroutine
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id != downloadId) return

                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // Ignore unregister exception
                    }

                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)

                    if (cursor != null && cursor.moveToFirst()) {
                        val status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uri =
                                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                                cursor.close()
                                if (continuation.isActive) {
                                    continuation.resume(Result.success(uri ?: ""))
                                }
                            }

                            DownloadManager.STATUS_FAILED, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> {
                                val reason =
                                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                cursor.close()
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(Exception("Download failed or interrupted: status=$status reason=$reason")))
                                }
                            }

                            else -> {
                                cursor.close()
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(Exception("Unknown download status: $status")))
                                }
                            }
                        }
                    } else {
                        cursor?.close()
                        if (continuation.isActive) {
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
                    // ignore
                }
            }

        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
        }
    }
}


private fun getUserAgent(): String {
    val ctx = AndroidKDownloadFile.appContext
    val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    val appName = ctx.applicationInfo.loadLabel(ctx.packageManager).toString()
    val version = packageInfo.versionName ?: "1.0"

    val model = Build.MODEL ?: "Unknown"
    val manufacturer = Build.MANUFACTURER ?: "Unknown"
    val osVersion = Build.VERSION.RELEASE ?: "Unknown"
    val sdkInt = Build.VERSION.SDK_INT

    return "$appName/$version " +
            "(Android; $manufacturer $model; Android $osVersion SDK $sdkInt)"

}


