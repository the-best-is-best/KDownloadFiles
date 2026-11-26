package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.ProgressReporter
import io.github.kdownloadfile.internal.WRITE_CHUNK_SIZE
import io.github.kdownloadfile.internal.targetPath
import io.github.kdownloadfile.internal.writeThroughTempFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.file.Files

internal actual suspend fun platformDownloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
    progress: ProgressReporter,
): String = withContext(Dispatchers.IO) {
    val request = HttpRequest.newBuilder(URI.create(url)).GET()
        .header("User-Agent", System.getProperty("http.agent") ?: "KotlinJVM")
        .apply { customHeaders.forEach { (key, value) -> header(key, value) } }
        .build()

    val response = try {
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream())
    } catch (e: ConnectException) {
        throw KDownloadFileNoInternetException(e)
    } catch (e: UnknownHostException) {
        throw KDownloadFileNoInternetException(e)
    } catch (e: HttpTimeoutException) {
        throw KDownloadFileNoInternetException(e)
    }

    response.body().use { body ->
        val status = response.statusCode()
        if (status !in 200..299) throw KDownloadFileHttpException(status)

        val total =
            response.headers().firstValueAsLong("Content-Length").orElse(-1L).takeIf { it > 0 }
        val target = targetPath(fileName, folderName, configuration)
        // The body goes to a temporary file, so a broken download never replaces the old file.
        writeThroughTempFile(target) { temp ->
            Files.newOutputStream(temp).use { output ->
                val buffer = ByteArray(WRITE_CHUNK_SIZE)
                var done = 0L
                progress.report(0, total)
                while (true) {
                    ensureActive()
                    val read = body.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    progress.report(done, total)
                }
                // Without Content-Length the size is known only at the end.
                if (total == null) progress.report(done, done)
            }
            true
        }
        target.toAbsolutePath().toString()
    }
}
