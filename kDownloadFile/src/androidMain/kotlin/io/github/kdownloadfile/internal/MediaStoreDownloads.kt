package io.github.kdownloadfile.internal

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/**
 * Saves into `Download/<app name>/<folderName>` through MediaStore, which needs no storage permission.
 * Android may still rename the file when the old one belongs to an earlier install and cannot be removed.
 *
 * @return the path of the saved file.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal fun saveToDownloadsWithMediaStore(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    folderName: String?,
    noDuplicateFile: Boolean,
): String {
    val resolver = context.contentResolver
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val subPath = publicDownloadsSubPath(folderName)
    val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$subPath/"

    if (noDuplicateFile) {
        try {
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(fileName, relativePath)
            )
        } catch (e: SecurityException) {
            // Left by an earlier install: not ours to delete, so Android picks a new name.
        }
    }

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(fileName))
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(collection, values)
        ?: throw IOException("Could not create $fileName in Downloads")
    try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IOException("Could not write $fileName")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null
        )
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }

    // The name Android actually used, which differs from fileName when it had to add " (1)".
    val savedName =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: fileName
    return File(
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            subPath
        ),
        savedName
    ).absolutePath
}

/** The MediaStore entry of a file in Downloads, or null when there is none this app can see. */
@Suppress("DEPRECATION") // DATA is the only column that holds the full path.
internal fun findDownloadsUri(context: Context, file: File): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    return try {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(file.absolutePath),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(
                collection,
                cursor.getLong(0)
            ) else null
        }
    } catch (e: Exception) {
        null
    }
}
