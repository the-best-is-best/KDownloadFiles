package org.company.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import io.github.kdownloadfile.configration.AndroidKDownloadFileConfiguration
import io.github.kdownloadfile.configration.DownloadNotificationVisibility
import io.github.kdownloadfile.configration.IosKDownloadFileConfiguration
import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import io.github.kdownloadfile.downloadFile
import io.github.kdownloadfile.openFile
import io.github.kpermissionnotification.NotificationPermission
import io.github.kpermissionsCore.rememberPermissionState
import kotlinx.coroutines.launch
import org.company.app.theme.AppTheme

@Preview
@Composable
fun App() = AppTheme {
    val scope = rememberCoroutineScope()
    val notificationPermissionState = rememberPermissionState(NotificationPermission)

    LaunchedEffect(Unit) {
        notificationPermissionState.launchPermissionRequest()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ElevatedButton(
            onClick = {
                scope.launch {
                        val pathRes = downloadFile(
                            url = "https://cdn.hotelnearmedanta.com/testfile.org/testfile.org-5GB.dat",
                            fileName = "re.pdf",
                            folderName = "doc",
                            configuration = KDownloadFileConfiguration(
                                saveToDownloads = true,
                                noDuplicateFile = true,
                                android = AndroidKDownloadFileConfiguration(
                                    notificationVisibility = DownloadNotificationVisibility.VisibleAndNotifyCompleted
                                ),
                                ios = IosKDownloadFileConfiguration(
                                    showLiveActivity = true
                                )
                            )
                        )
                        println("download path $pathRes")
                        pathRes.fold(
                            onSuccess = { path ->
                                println("download path $path")
                                openFile(path)
                            },
                            onFailure = { error ->
                                println("error $error")
                            }
                        )
                }
            }) {
            Text("Download")
        }

    }
}
