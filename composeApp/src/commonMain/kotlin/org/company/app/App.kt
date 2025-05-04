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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdownloadfile.downloadFile
import io.github.kdownloadfile.openFile
import kotlinx.coroutines.launch
import org.company.app.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
internal fun App() = AppTheme {
    val scope = rememberCoroutineScope()
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
                        url = "https://firebasestorage.googleapis.com/v0/b/ecommerce-demo-48922.firebasestorage.app/o/test%2FIMG_0002.jpeg?alt=media&token=ae8223ac-b9b5-40e1-bd9a-ba869429e50f",
                        fileName = "2FIMG_0002.jpeg",
                        folderName = "firebaseStorage"
                    )
                    println("download path $pathRes")
                    pathRes?.fold(
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
