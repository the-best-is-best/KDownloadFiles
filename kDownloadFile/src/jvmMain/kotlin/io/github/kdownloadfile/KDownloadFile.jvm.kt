package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
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
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

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
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val client = HttpClient.newBuilder().build()

            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()

            val saveToDownloads = configuration.saveToDownloads
            val noDuplicateFile = configuration.noDuplicateFile

            // ✅ Add User-Agent
            requestBuilder.header("User-Agent", getUserAgent())

            // ✅ Add custom headers
            for ((key, value) in customHeaders) {
                requestBuilder.header(key, value)
            }

            // نحدد المجلد الرئيسي للحفظ بناءً على قيمة saveToDownloads
            val baseDir: Path = if (saveToDownloads) {
                Paths.get(System.getProperty("user.home"), "Downloads")
            } else {
                Paths.get(System.getProperty("user.home"), ".kdownloadfile")
            }

            val targetDir: Path = if (folderName.isNullOrBlank()) {
                baseDir
            } else {
                val dir = baseDir.resolve(folderName)
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir)
                }
                dir
            }

            var destination = targetDir.resolve(fileName)

            // --- التعديل يبدأ هنا ---
            if (noDuplicateFile) {
                // الحالة: noDuplicateFile = true
                // يتم حذف الملف القديم إذا كان موجودًا.
                if (Files.exists(destination)) {
                    Files.delete(destination)
                }
            } else {
                // الحالة: noDuplicateFile = false
                // يتم البحث عن اسم فريد للملف لتجنب الكتابة فوقه.
                var counter = 1
                var uniqueDestination = destination
                val fileNameWithoutExtension = destination.nameWithoutExtension
                val fileExtension = destination.extension

                while (Files.exists(uniqueDestination)) {
                    val newFileName = "${fileNameWithoutExtension} (${counter}).${fileExtension}"
                    uniqueDestination = targetDir.resolve(newFileName)
                    counter++
                }
                destination = uniqueDestination
            }
            // --- التعديل ينتهي هنا ---

            val request = requestBuilder.build()
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

private fun getUserAgent(): String {
    return System.getProperty("http.agent") ?: "KotlinJVM"
}
