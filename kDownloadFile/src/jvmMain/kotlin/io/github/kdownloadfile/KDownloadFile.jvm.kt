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

            // ✅ Add User-Agent
            requestBuilder.header("User-Agent", getUserAgent())

            // ✅ Add custom headers
            for ((key, value) in customHeaders) {
                requestBuilder.header(key, value)
            }

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
