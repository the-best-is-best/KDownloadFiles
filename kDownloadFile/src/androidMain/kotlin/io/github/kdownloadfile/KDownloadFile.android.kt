package io.github.kdownloadfile

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

actual fun saveBytes(
    bytes: ByteArray,
    fileName: String,
    folderName: String
): String {
    val context =
        AndroidKDownloadFile.activity.get() ?: throw Exception("Activity reference is null")

    val baseFolder = Environment.DIRECTORY_DOWNLOADS

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val baseUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val existingUri = resolver.query(
            baseUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME}=?",
            arrayOf("$baseFolder/$folderName", fileName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                Uri.withAppendedPath(baseUri, id.toString())
            } else null
        }

        existingUri?.let { resolver.delete(it, null, null) }

        val contentValues = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
//            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, "$baseFolder/$folderName")
            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(baseUri, contentValues)
            ?: throw Exception("❌ Failed to create file in MediaStore")

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
            } ?: throw Exception("Failed to get OutputStream for $fileName at $uri")
            contentValues.clear()
            contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            println("✅ File saved successfully in: $baseFolder/$folderName/$fileName")
            return "$baseFolder/$folderName/$fileName"
        } catch (e: Exception) {
            println("❌ Error saving file using MediaStore: ${e.message}")
            throw Exception("Error saving file using MediaStore: ${e.message}")
        }
    } else {
        val storageDir = Environment.getExternalStoragePublicDirectory(baseFolder)
        val directory = File(storageDir, folderName)

        if (!directory.exists() && !directory.mkdirs()) {
            throw Exception("❌ Failed to create directory: ${directory.absolutePath}")
        }

        val finalFile = File(directory, fileName)
        try {
            FileOutputStream(finalFile).use { it.write(bytes) }
            println("✅ File saved successfully in: ${finalFile.absolutePath}")
            return finalFile.absolutePath
        } catch (e: Exception) {
            println("❌ Error saving file: ${e.message}")
            throw Exception("Error saving file: ${e.message}")
        }
    }
}
actual fun openFile(filePath: String) {
    val context =
        AndroidKDownloadFile.activity.get() ?: throw Exception("Activity reference is null")

    val file: File? = when {
        filePath.startsWith("content://") -> null // التعامل مع URI مباشرةً
        filePath.startsWith("/storage/emulated/") || filePath.startsWith("/sdcard/") -> File(
            filePath
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> getFileFromMediaStore(context, filePath)
        else -> File(context.getExternalFilesDir(null), filePath)
    }

    if (file != null && !file.exists()) {
        println("❌ File does not exist: ${file.absolutePath}")
        return
    }

    val uri: Uri = if (file != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }
    } else {
        Uri.parse(filePath) // إذا كان URI مباشر
    }

    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        // تحقق من نوع context هنا لتحديد ما إذا كنت بحاجة إلى FLAG_ACTIVITY_NEW_TASK
        if (context is Activity) {
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(intent, "Open with"))
        }
    } catch (e: Exception) {
        println("❌ Error opening file: ${e.message}")
    }
}

private fun getFileFromMediaStore(context: Context, filePath: String): File? {
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA)
    val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
    val selectionArgs = arrayOf(File(filePath).name)
    val cursor = context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection, selection, selectionArgs, null
    )

    cursor?.use {
        if (it.moveToFirst()) {
            val filePathColumnIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            return File(it.getString(filePathColumnIndex))
        }
    }
    return null
}