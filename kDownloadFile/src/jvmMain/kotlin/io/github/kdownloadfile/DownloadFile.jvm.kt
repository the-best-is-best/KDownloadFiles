package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.internal.targetPath
import io.github.kdownloadfile.internal.writeThroughTempFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException

actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
    customHeaders: Map<String, String>,
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val request = HttpRequest.newBuilder(URI.create(url)).GET()
            .header("User-Agent", System.getProperty("http.agent") ?: "KotlinJVM")
            .apply { customHeaders.forEach { (key, value) -> header(key, value) } }
            .build()
        val target = targetPath(fileName, folderName, configuration)

        var statusCode = 0
        // The body goes to a temporary file, so an error page never replaces the old file.
        writeThroughTempFile(target) { temp ->
            statusCode = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofFile(temp))
                .statusCode()
            statusCode in 200..299
        }
        if (statusCode !in 200..299) throw KDownloadFileHttpException(statusCode)
        Result.success(target.toAbsolutePath().toString())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ConnectException) {
        Result.failure(KDownloadFileNoInternetException(e))
    } catch (e: UnknownHostException) {
        Result.failure(KDownloadFileNoInternetException(e))
    } catch (e: HttpTimeoutException) {
        Result.failure(KDownloadFileNoInternetException(e))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
