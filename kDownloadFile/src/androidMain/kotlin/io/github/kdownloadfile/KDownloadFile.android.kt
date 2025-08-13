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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLConnection
import kotlin.coroutines.resume

actual fun openFile(filePath: String) {
    val context = AndroidKDownloadFile.appContext
    val file = File(filePath)

    if (!file.exists()) {
        Log.e("OpenFile", "❌ File does not exist: ${file.absolutePath}")
        return
    }

    val uri: Uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        Log.e("OpenFile", "❌ Error getting URI: ${e.message}")
        return
    }

    val mimeType = getMimeType(file)

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY)
    }

    try {
        context.startActivity(
            Intent.createChooser(viewIntent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: ActivityNotFoundException) {
        Log.e("OpenFile", "❌ No Activity found to handle file: ${file.name}")
    } catch (e: Exception) {
        Log.e("OpenFile", "❌ Error opening file: ${e.message}", e)
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
        val saveInDownloadFolder = configuration.saveToDownloads
        val noDuplicateFile = configuration.noDuplicateFile
        val saveInCacheFile = configuration.saveInCacheFiles

        if (saveInDownloadFolder && saveInCacheFile) {
            continuation.resume(Result.failure(IllegalArgumentException("Cannot save to both Downloads and cache")))
            return@suspendCancellableCoroutine
        }

        if (!isNetworkAvailable(context)) {
            continuation.resume(Result.failure(Exception("❌ No internet connection.")))
            return@suspendCancellableCoroutine
        }

        if (!isDownloadableFile(url, customHeaders)) {
            continuation.resume(Result.failure(Exception("❌ File is not downloadable from server.")))
            return@suspendCancellableCoroutine
        }

        val appName = getAppName()
        val destinationPath = if (saveInDownloadFolder) {
            listOfNotNull(appName, folderName, fileName).joinToString("/")
        } else {
            listOfNotNull(folderName, fileName).joinToString("/")
        }

        try {
            if (noDuplicateFile) {
                val fileToDelete = when {
                    saveInDownloadFolder -> File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        destinationPath
                    )
                    saveInCacheFile -> File(context.cacheDir, destinationPath)
                    else -> File(context.filesDir, destinationPath)
                }

                if (fileToDelete.exists()) {
                    var deleted = fileToDelete.delete()
                    if (!deleted) {
                        try {
                            FileOutputStream(fileToDelete).use { fos ->
                                fos.channel.truncate(0)
                                fos.fd.sync()
                            }
                            deleted = fileToDelete.delete()
                        } catch (e: Exception) {
                            continuation.resume(Result.failure(e))
                            Log.e("KDownloadFile", "⚠️ Force deletion failed: ${e.message}")
                        }
                    }
                    Log.d(
                        "KDownloadFile",
                        if (deleted) "✅ Deleted: ${fileToDelete.absolutePath}" else "💥 STILL EXISTS: ${fileToDelete.absolutePath}"
                    )
                }
            }

            val request = DownloadManager.Request(url.toUri())
                .setTitle(configuration.android.title)
                .setDescription(configuration.android.description)
                .setNotificationVisibility(configuration.android.notificationVisibility.rawValue)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            when {
                saveInDownloadFolder -> request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    destinationPath
                )
                saveInCacheFile -> request.setDestinationInExternalFilesDir(
                    context,
                    "cache_temp",
                    fileName
                )
                else -> request.setDestinationInExternalFilesDir(context, null, fileName)
            }

            request.addRequestHeader("User-Agent", getUserAgent())
            for ((key, value) in customHeaders) request.addRequestHeader(key, value)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                request.setRequiresCharging(false)
                request.setRequiresDeviceIdle(false)
            }

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            if (downloadId == -1L) {
                continuation.resume(Result.failure(Exception("❌ Failed to start download: Invalid downloadId")))
                return@suspendCancellableCoroutine
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id != downloadId) return

                    try {
                        context.unregisterReceiver(this)
                    } catch (ex: Exception) {
                        // ignore
                    }

                    val query = DownloadManager.Query().setFilterById(downloadId)
                    downloadManager.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val status =
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val reason =
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            val localUriString =
                                cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    if (continuation.isActive) {
                                        val downloadedFile = File(URI.create(localUriString).path)
                                        val destFile = when {
                                            saveInDownloadFolder -> File(
                                                Environment.getExternalStoragePublicDirectory(
                                                    Environment.DIRECTORY_DOWNLOADS
                                                ), destinationPath
                                            )
                                            saveInCacheFile -> File(
                                                context.cacheDir,
                                                destinationPath
                                            )
                                            else -> File(context.filesDir, destinationPath)
                                        }

                                        destFile.parentFile?.mkdirs()

                                        val success =
                                            if (destFile == downloadedFile) true else copyFile(
                                                downloadedFile,
                                                destFile
                                            )
                                        if (success) {
                                            if (destFile != downloadedFile) downloadedFile.delete()
                                            continuation.resume(Result.success(destFile.absolutePath))
                                        } else {
                                            continuation.resume(Result.failure(Exception("❌ Failed to move downloaded file")))
                                        }
                                    }
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    if (continuation.isActive) continuation.resume(
                                        Result.failure(
                                            Exception("❌ Download failed with reason: $reason")
                                        )
                                    )
                                }
                                else -> {
                                    if (continuation.isActive) continuation.resume(
                                        Result.failure(
                                            Exception("❌ Unexpected download status: $status")
                                        )
                                    )
                                }
                            }
                        } else {
                            if (continuation.isActive) continuation.resume(
                                Result.failure(Exception("❌ DownloadManager cursor empty"))
                            )
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
                } catch (ignored: Exception) {
                }
            }

        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(Result.failure(e))
        }
    }
}


// Helper functions

private fun getMimeType(file: File): String {
    val ext = file.extension.lowercase()
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: URLConnection.guessContentTypeFromName(file.name)
        ?: "application/octet-stream"
}

private fun copyFile(sourceFile: File, destFile: File): Boolean {
    return try {
        FileInputStream(sourceFile).channel.use { source ->
            FileOutputStream(destFile).channel.use { dest ->
                dest.transferFrom(source, 0, source.size())
            }
        }
        true
    } catch (e: IOException) {
        Log.e("KDownloadFile", "❌ Error copying file: ${e.message}", e)
        false
    }
}

private fun isDownloadableFile(url: String, customHeaders: Map<String, String>?): Boolean {
    return try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Range", "bytes=0-0")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        customHeaders?.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.connect()
        val code = connection.responseCode
        connection.disconnect()
        code in 200..399
    } catch (e: Exception) {
        false
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        val netInfo = cm.activeNetworkInfo
        @Suppress("DEPRECATION")
        netInfo != null && netInfo.isConnected
    }
}

private fun getUserAgent(): String {
    val ctx = AndroidKDownloadFile.appContext
    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    val appName = ctx.applicationInfo.loadLabel(ctx.packageManager).toString()
    val version = pi.versionName ?: "1.0"
    val model = Build.MODEL ?: "Unknown"
    val manufacturer = Build.MANUFACTURER ?: "Unknown"
    val osVer = Build.VERSION.RELEASE ?: "Unknown"
    val sdkInt = Build.VERSION.SDK_INT
    return "$appName/$version (Android; $manufacturer $model; Android $osVer SDK $sdkInt)"
}


private fun getAppName(): String {
    val context = AndroidKDownloadFile.appContext
    val applicationInfo = context.applicationInfo
    val stringId = applicationInfo.labelRes
    return if (stringId == 0) {
        applicationInfo.nonLocalizedLabel.toString()
    } else {
        context.getString(stringId)
    }
}
