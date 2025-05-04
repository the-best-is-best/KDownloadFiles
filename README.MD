<h1 align="center">KDownloadFiles</h1><br>

<div align="center">
<a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
<a href="https://android-arsenal.com/api?level=21" rel="nofollow">
    <img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat" style="max-width: 100%;">
</a>
<img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg?logo=android" alt="Badge Android" />
  <img src="https://img.shields.io/badge/Platform-iOS%20%2F%20macOS-lightgrey.svg?logo=apple" alt="Badge iOS" />
  <!-- <img src="https://img.shields.io/badge/Platform-JVM-8A2BE2.svg?logo=openjdk" alt="Badge JVM" />
    <img src="https://img.shields.io/badge/Platform-WASM%20%2F%20JS-yellow.svg?logo=javascript" alt="Badge JS" /> -->
<a href="https://github.com/the-best-is-best/"><img alt="Profile" src="https://img.shields.io/badge/github-%23181717.svg?&style=for-the-badge&logo=github&logoColor=white" height="20"/></a>

</div>

kdownloadfile is a simple yet effective Kotlin Multiplatform library that enables downloading files via HTTP, saving them locally, and opening them using the system’s default application — all while abstracting away platform-specific details.

## Download

[![Maven Central](https://img.shields.io/maven-central/v/io.github.the-best-is-best/kdownload-file)](https://central.sonatype.com/artifact/io.github.the-best-is-best/kdownload-file)

KDownloadFiles available on `mavenCentral()`.

```kotlin
implementation("io.github.the-best-is-best:kdownload-file:1.0.0")
```

## How to use

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.kdownloadfile.downloadFile
import io.github.kdownloadfile.openFile
import kotlinx.coroutines.launch

@Composable
fun DownloadButton() {
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            val pathRes = downloadFile(
                url = "https://firebasestorage.googleapis.com/v0/b/ecommerce-demo-48922.firebasestorage.app/o/test%2FIMG_0002.jpeg?alt=media&token=ae8223ac-b9b5-40e1-bd9a-ba869429e50f",
                fileName = "IMG_0002.jpeg",
                folderName = "firebaseStorage"
            )
            println("Download result: $pathRes")
            pathRes?.fold(
                onSuccess = { path ->
                    println("File saved at: $path")
                    openFile(path)
                },
                onFailure = { error ->
                    println("Download failed: ${error.message}")
                }
            )
        }
    }) {
        Text("Download")
    }
}


```

<br>

📂 What It Does
Downloads a file from a URL.

Saves it to a platform-specific local folder.

Opens it using the native application for that file type.

<br>

🛡 License
Licensed under the Apache 2.0 License.
