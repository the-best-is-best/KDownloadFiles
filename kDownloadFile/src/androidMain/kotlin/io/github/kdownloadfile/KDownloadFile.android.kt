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
import android.webkit.MimeTypeMap
import android.widget.Toast
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


/**
 * Opens a downloaded file using a viewer application.
 *
 * This function handles files from both public storage (e.g., Downloads) and the app's cache directory.
 * It uses `FileProvider` to create a secure content URI, preventing `FileUriExposedException`
 * on modern Android versions.
 *
 * @param filePath The absolute path of the file to open. This should be a clean path string, not a `file://` URI.
 */
actual fun openFile(filePath: String) {
    val context = AndroidKDownloadFile.appContext

    // Create the File object from the absolute path.
    val file = File(filePath)

    if (!file.exists()) {
        Log.e("OpenFile", "❌ File does not exist: ${file.absolutePath}")
        Toast.makeText(context, "File does not exist.", Toast.LENGTH_SHORT).show()
        return
    }

    val uri: Uri = try {
        // Use FileProvider for a secure URI. This is the correct and only way
        // to share files with other apps on modern Android.
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("OpenFile", "❌ Error getting URI: ${e.message}")
        Toast.makeText(context, "Cannot open file due to URI error", Toast.LENGTH_SHORT).show()
        return
    }

    // Get the MIME type of the file.
    val mimeType = getMimeType(file)

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    }

    try {
        // Use a chooser to let the user select the app to open the file.
        val chooser = Intent.createChooser(viewIntent, "Open with").apply {
            // This flag is crucial for starting an activity from a non-Activity context.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this file type", Toast.LENGTH_SHORT).show()
        Log.e("OpenFile", "❌ No Activity found to handle file: ${file.name}")
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("OpenFile", "❌ Error opening file: ${e::class.simpleName}: ${e.message}")
    }
}

/**
 * Downloads a file to the specified location (public downloads or app cache).
 *
 * This function uses the `DownloadManager` for robust, background downloads.
 * It handles the movement of files to the cache directory and correctly returns
 * the absolute path of the final file location.
 *
 * @return A `Result<String>` containing the absolute path of the downloaded file on success,
 * or an `Exception` on failure.
 */
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

        // Validate configuration
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

        val destinationPath = listOfNotNull(folderName, fileName).joinToString("/")
        val androidConfig = configuration.android

        try {
            // Handle file deletion for noDuplicateFile
            if (noDuplicateFile) {
                val fileToDelete = when {
                    saveInDownloadFolder -> {
                        File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            destinationPath
                        )
                    }
                    saveInCacheFile -> File(context.cacheDir, destinationPath)
                    else -> File(context.filesDir, destinationPath)
                }

                if (fileToDelete.exists()) {
                    // 1. First try normal deletion
                    var deleted = fileToDelete.delete()

                    // 2. If failed, force deletion
                    if (!deleted) {
                        try {
                            // Clear file contents first
                            FileOutputStream(fileToDelete).use { fos ->
                                fos.channel.truncate(0)
                                fos.fd.sync() // Force sync to disk
                            }
                            deleted = fileToDelete.delete()
                        } catch (e: Exception) {
                            Log.e("KDownloadFile", "⚠️ Force deletion failed: ${e.message}")
                        }
                    }

                    // 3. Final verification
                    when {
                        deleted && !fileToDelete.exists() ->
                            Log.d("KDownloadFile", "✅ Deleted: ${fileToDelete.absolutePath}")

                        fileToDelete.exists() ->
                            Log.w("KDownloadFile", "💥 STILL EXISTS: ${fileToDelete.absolutePath}")

                        else ->
                            Log.w("KDownloadFile", "⚠️ Deletion status unclear")
                    }
                }
            }

            val request = DownloadManager.Request(url.trim().toUri())
                .setTitle(androidConfig.title)
                .setDescription(androidConfig.description)
                .setNotificationVisibility(androidConfig.notificationVisibility.rawValue)

            // Set destination based on configuration
            when {
                saveInDownloadFolder -> {
                    request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        destinationPath
                    )
                }

                saveInCacheFile -> {
                    // For cache, we download to a temp location first
                    request.setDestinationInExternalFilesDir(context, "cache_temp", fileName)
                }

                else -> {
                    // For persistent app storage
                    request.setDestinationInExternalFilesDir(context, null, fileName)
                }
            }

            // Rest of your request setup...
            request.setAllowedOverMetered(true)
            request.setAllowedOverRoaming(true)
            request.addRequestHeader("User-Agent", getUserAgent())

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
                    } catch (_: Exception) {
                    }

                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursorQuery = downloadManager.query(query)

                    cursorQuery.use { cursor ->
                        if (cursor != null && cursor.moveToFirst()) {
                            val status =
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            val localUriString =
                                cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    if (continuation.isActive) {
                                        val downloadedFile = File(URI.create(localUriString).path)

                                        when {
                                            saveInDownloadFolder -> {
                                                continuation.resume(
                                                    Result.success(
                                                        File(
                                                            Environment.getExternalStoragePublicDirectory(
                                                                Environment.DIRECTORY_DOWNLOADS
                                                            ),
                                                            destinationPath
                                                        ).absolutePath
                                                    )
                                                )
                                            }

                                            saveInCacheFile -> {
                                                // Move to cache directory
                                                val cacheFile =
                                                    File(context.cacheDir, destinationPath)
                                                cacheFile.parentFile?.mkdirs()
                                                if (copyFile(downloadedFile, cacheFile)) {
                                                    downloadedFile.delete()
                                                    continuation.resume(Result.success(cacheFile.absolutePath))
                                                } else {
                                                    continuation.resume(Result.failure(Exception("❌ Failed to move file to cache")))
                                                }
                                            }

                                            else -> {
                                                // Move to persistent app storage
                                                val appFile =
                                                    File(context.filesDir, destinationPath)
                                                appFile.parentFile?.mkdirs()
                                                if (copyFile(downloadedFile, appFile)) {
                                                    downloadedFile.delete()
                                                    continuation.resume(Result.success(appFile.absolutePath))
                                                } else {
                                                    continuation.resume(Result.failure(Exception("❌ Failed to move file to app storage")))
                                                }
                                            }
                                        }
                                    }
                                }
                                // ... rest of your status handling
                            }
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
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
        }
    }
}

// Helper function to determine the MIME type of a file
private fun getMimeType(file: File): String {
    val extension = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: URLConnection.guessContentTypeFromName(file.name)
        ?: "application/octet-stream"
}

// Helper function to robustly copy a file
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

// Helper to check if a file is downloadable by checking the HTTP headers
private fun isDownloadableFile(url: String, customHeaders: Map<String, String>?): Boolean {
    return try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Range", "bytes=0-0")
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

// Helper to check for network connectivity
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

// Helper to generate a custom User-Agent string
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