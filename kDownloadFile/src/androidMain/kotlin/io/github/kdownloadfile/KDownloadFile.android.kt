package io.github.kdownloadfile

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
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

    val mimeType = when {
        file != null -> getMimeType(file)
        else -> context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        val chooser = Intent.createChooser(viewIntent, "Open with").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this file type", Toast.LENGTH_SHORT).show()
        Log.e("OpenFile", "❌ No Activity found to handle file: ${file?.name}")
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("OpenFile", "❌ Error opening file: ${e::class.simpleName}: ${e.message}")
    }
}

private fun getMimeType(file: File): String {
    return when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        "ppt", "pptx" -> "application/vnd.ms-powerpoint"
        "txt" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        else -> URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
    }
}

actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): Result<String> = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->

        val context = AndroidKDownloadFile.appContext

        // Check if device has active internet connection
        if (!isNetworkAvailable(context)) {
            continuation.resume(Result.failure(Exception("❌ No internet connection.")))
            return@suspendCancellableCoroutine
        }


        // Check if the server supports downloading this file
        if (!isDownloadableFile(url, customHeaders)) {
            continuation.resume(Result.failure(Exception("❌ File is not downloadable from server.")))
            return@suspendCancellableCoroutine
        }

        val destinationPath = listOfNotNull(folderName, fileName).joinToString("/")
        val androidConfig = configuration.android

        try {
            val request = DownloadManager.Request(url.trim().toUri())
                .setTitle(androidConfig.title)
                .setDescription(androidConfig.description)
                .setNotificationVisibility(androidConfig.notificationVisibility.rawValue)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationPath)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .addRequestHeader("User-Agent", getUserAgent())

            // Add custom headers if provided
            for ((key, value) in customHeaders) {
                request.addRequestHeader(key, value)
            }

            // Optional constraints for newer Android versions
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

            // Listen for the download complete broadcast
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id != downloadId) return

                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {
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

                            DownloadManager.STATUS_FAILED -> {
                                val reason =
                                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                cursor.close()
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(Exception("❌ Download failed (reason=$reason)")))
                                }
                            }

                            else -> {
                                cursor.close()
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(Exception("❌ Unexpected download status: $status")))
                                }
                            }
                        }
                    } else {
                        cursor?.close()
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("❌ Failed to access download status.")))
                        }
                    }
                }
            }

            // Register the broadcast receiver for download complete
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )

            // Cleanup on coroutine cancellation
            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }

        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
        }
    }
}

private fun isDownloadableFile(url: String, customHeaders: Map<String, String>?): Boolean {
    return try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Range", "bytes=0-0") // safer than HEAD
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        customHeaders?.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        connection.connect()
        val code = connection.responseCode
        connection.disconnect()
        code in 200..399
    } catch (e: Exception) {
        false
    }

}

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        @Suppress("DEPRECATION")
        networkInfo != null && networkInfo.isConnected
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


