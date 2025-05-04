package io.github.kdownloadfile

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

expect fun openFile(
    filePath: String
)

expect fun saveBytes(
    bytes: ByteArray,
    fileName: String,
    folderName: String,
): String


suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String,
    parameters: List<Pair<String, String>>? = null
): Result<String>? {
    return try {
        withContext(Dispatchers.Default) {
            val client = HttpClient()
            val response = client.request {
                url(url)
                parameters?.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
            val path = saveBytes(
                response.bodyAsBytes(),
                fileName,
                folderName,
            )
            Result.success(path)
        }
    } catch (e: Exception) {
        return Result.failure(e)
    }
}