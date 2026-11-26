package org.company.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdownloadfile.KDownloadFileHttpException
import io.github.kdownloadfile.KDownloadFileNoInternetException
import io.github.kdownloadfile.KDownloadProgress
import io.github.kdownloadfile.downloadFile
import io.github.kdownloadfile.saveFile
import kotlinx.coroutines.launch

/** A 10 MB test file that sends Content-Length, so the percentage is known. */
private const val TEST_FILE_URL = "https://proof.ovh.net/files/10Mb.dat"
private const val MISSING_FILE_URL = "https://proof.ovh.net/files/this-file-does-not-exist.dat"
private const val SAVE_TEST_SIZE = 20 * 1024 * 1024

/** Buttons to try the download and save progress, and the 404 error. */
@Composable
fun ProgressDemo(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<KDownloadProgress?>(null) }
    var message by remember { mutableStateOf("") }

    fun run(label: String, action: suspend ((KDownloadProgress) -> Unit) -> Result<String>) {
        scope.launch {
            working = true
            progress = null
            message = "$label…"
            message = action { progress = it }.fold(
                onSuccess = { path -> "$label done:\n$path" },
                onFailure = { error -> "$label failed: ${error.describe()}" },
            )
            working = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Progress test", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !working, onClick = {
                run("Download") { onProgress ->
                    downloadFile(
                        TEST_FILE_URL,
                        "progress-test.dat",
                        "test",
                        onProgress = onProgress
                    )
                }
            }) { Text("Download 10 MB") }

            Button(enabled = !working, onClick = {
                run("Save") { onProgress ->
                    saveFile(
                        ByteArray(SAVE_TEST_SIZE),
                        "save-test.dat",
                        "test",
                        onProgress = onProgress
                    )
                }
            }) { Text("Save 20 MB") }
        }

        OutlinedButton(enabled = !working, onClick = {
            run("404 test") { onProgress ->
                downloadFile(MISSING_FILE_URL, "missing.dat", "test", onProgress = onProgress)
            }
        }) { Text("Try a missing file (404)") }

        ProgressView(working, progress)

        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProgressView(working: Boolean, progress: KDownloadProgress?) {
    val percent = progress?.percent
    when {
        // Size not known yet (or the server sent no Content-Length): spinner without a number.
        working && percent == null -> CircularProgressIndicator()

        percent != null -> Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("$percent%  (${progress.bytesDone.toMegabytes()} / ${progress.totalBytes?.toMegabytes()} MB)")
        }
    }
}

private fun Long.toMegabytes(): String {
    val tenths = this * 10 / (1024 * 1024)
    return "${tenths / 10}.${tenths % 10}"
}

private fun Throwable.describe(): String = when (this) {
    is KDownloadFileHttpException -> "server answered $statusCode" + if (statusCode == 404) " (file not found)" else ""
    is KDownloadFileNoInternetException -> "no internet connection"
    else -> message ?: toString()
}
