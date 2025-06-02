package io.github.kdownloadfile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

//
//actual fun saveBytes(
//    bytes: ByteArray,
//    fileName: String,
//    folderName: String
//): String {
//    try {
//        // Create the folder if it doesn't exist
//        val folder = File(folderName)
//        if (!folder.exists()) {
//            folder.mkdirs()
//        }
//
//        // Create the file object with the given folder and file name
//        val file = File(folder, fileName)
//
//        // Write the bytes to the file
//        FileOutputStream(file).use { outputStream ->
//            outputStream.write(bytes)
//        }
//
//        return file.absolutePath // Return the file path after saving
//    } catch (e: IOException) {
//        e.printStackTrace()
//        return "Error saving file: ${e.message}"
//    }
//}
//
actual fun openFile(filePath: String) {
    val file = File(filePath)
    if (file.exists()) {
        Desktop.getDesktop().open(file)
    } else {
        println("File does not exist: $filePath")
    }

}
actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?
): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val client = HttpClient.newBuilder().build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build()

            val downloadsDir = System.getProperty("user.home") + "/Downloads"
            val targetDir: Path = if (folderName.isNullOrBlank()) {
                Paths.get(downloadsDir)
            } else {
                val dir = Paths.get(downloadsDir, folderName)
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir)
                }
                dir
            }

            val destination = targetDir.resolve(fileName)

            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination))

            if (response.statusCode() == 200) {
                Result.success(destination.toAbsolutePath().toString())
            } else {
                Result.failure(Exception("Failed to download file: HTTP ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
