package io.github.kdownloadfile.internal

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.kdownloadfile.KDownloadFileException
import io.github.kdownloadfile.KDownloadFileHttpException
import io.github.kdownloadfile.KDownloadFileNoInternetException
import io.github.kdownloadfile.configration.AndroidKDownloadFileConfiguration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where DownloadManager writes the file. */
internal sealed interface DownloadManagerTarget {
    /** `Download/<relativePath>` in shared storage. */
    data class PublicDownloads(val relativePath: String) : DownloadManagerTarget

    /** The app's external files folder (`Android/data/<package>/files/<subFolder>`). */
    data class ExternalAppFiles(val subFolder: String?, val fileName: String) :
        DownloadManagerTarget
}

/**
 * Downloads [url] with DownloadManager and waits until it is done, reporting the bytes received
 * to [progress] while waiting.
 *
 * @return the file DownloadManager wrote.
 * @throws KDownloadFileHttpException when the server answers with an error such as 404.
 */
internal suspend fun downloadWithDownloadManager(
    context: Context,
    url: String,
    headers: Map<String, String>,
    notification: AndroidKDownloadFileConfiguration,
    target: DownloadManagerTarget,
    progress: ProgressReporter,
): File {
    val request = DownloadManager.Request(url.toUri())
        .setTitle(notification.title)
        .setDescription(notification.description)
        .setNotificationVisibility(notification.notificationVisibility.rawValue)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
        .addRequestHeader("User-Agent", userAgent())
    headers.forEach { (key, value) -> request.addRequestHeader(key, value) }
    when (target) {
        is DownloadManagerTarget.PublicDownloads ->
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                target.relativePath
            )

        is DownloadManagerTarget.ExternalAppFiles ->
            request.setDestinationInExternalFilesDir(context, target.subFolder, target.fileName)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        request.setRequiresCharging(false)
        request.setRequiresDeviceIdle(false)
    }

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = AtomicLong(-1L)
    return coroutineScope {
        // DownloadManager has no progress callback, so it is asked a few times a second.
        val poller = launch {
            while (isActive) {
                val id = downloadId.get()
                if (id != -1L) reportProgress(downloadManager, id, progress)
                delay(PROGRESS_POLL_MILLIS)
            }
        }
        val file = try {
            awaitDownload(context, downloadManager, request) { downloadId.set(it) }
        } finally {
            poller.cancel()
        }
        // The last poll may have come just before the end, so ask once more now that it is done.
        reportProgress(downloadManager, downloadId.get(), progress)
        file
    }
}

private const val PROGRESS_POLL_MILLIS = 250L

/** Enqueues [request] and waits for DownloadManager's "download complete" broadcast. */
private suspend fun awaitDownload(
    context: Context,
    downloadManager: DownloadManager,
    request: DownloadManager.Request,
    onEnqueued: (Long) -> Unit,
): File = suspendCancellableCoroutine { continuation ->
    // Written here, read on the main thread by the receiver.
    val downloadId = AtomicLong(-1L)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id == -1L || id != downloadId.get()) return
            runCatching { context.unregisterReceiver(this) }
            if (!continuation.isActive) return
            runCatching { finishedDownload(downloadManager, id) }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        }
    }

    // Registered before enqueue, so a very fast download cannot finish before we listen.
    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        ContextCompat.RECEIVER_EXPORTED
    )
    continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

    val id = downloadManager.enqueue(request)
    downloadId.set(id)
    if (id == -1L) {
        runCatching { context.unregisterReceiver(receiver) }
        continuation.resumeWithException(KDownloadFileException("Failed to start download"))
    } else {
        onEnqueued(id)
    }
}

/** Reports the bytes received so far; the total is -1 in DownloadManager until the server sends it. */
private fun reportProgress(
    downloadManager: DownloadManager,
    downloadId: Long,
    progress: ProgressReporter
) {
    downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
        if (!cursor.moveToFirst()) return
        val done =
            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total =
            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        progress.report(done.coerceAtLeast(0), total.takeIf { it > 0 })
    }
}

/** Reads the result of a finished download: its file, or the error it failed with. */
private fun finishedDownload(downloadManager: DownloadManager, downloadId: Long): File {
    val query = DownloadManager.Query().setFilterById(downloadId)
    downloadManager.query(query)?.use { cursor ->
        if (!cursor.moveToFirst()) throw KDownloadFileException("Download not found")
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        return when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val localUri =
                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                File(URI.create(localUri).path)
            }

            DownloadManager.STATUS_FAILED -> throw downloadFailure(reason)
            else -> throw KDownloadFileException("Unexpected download status: $status")
        }
    }
    throw KDownloadFileException("Download not found")
}

/**
 * For an HTTP error DownloadManager puts the status code itself in COLUMN_REASON;
 * its own ERROR_* codes start at 1000.
 */
private fun downloadFailure(reason: Int): KDownloadFileException = when (reason) {
    in 100..999 -> KDownloadFileHttpException(reason)
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> KDownloadFileException("Not enough storage space")
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> KDownloadFileException("No external storage found")
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> KDownloadFileException("The file already exists")
    DownloadManager.ERROR_FILE_ERROR -> KDownloadFileException("Could not write the file")
    DownloadManager.ERROR_HTTP_DATA_ERROR -> KDownloadFileNoInternetException()
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> KDownloadFileException("Too many redirects")
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> KDownloadFileException("Unexpected answer from the server")
    else -> KDownloadFileException("Download failed with reason: $reason")
}
