<h1 align="center">KDownloadFiles</h1><br>

<div align="center">
  <a href="https://opensource.org/licenses/Apache-2.0">
    <img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/>
  </a>
  <a href="https://android-arsenal.com/api?level=21" rel="nofollow">
    <img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat" style="max-width: 100%;">
  </a>
  <a href="">
    <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg?logo=android" alt="Badge Android" />
    <img src="https://img.shields.io/badge/Platform-iOS%2013%2B-lightgrey.svg?logo=apple" alt="Badge iOS" />
  </a>
  <!--
  <img src="https://img.shields.io/badge/Platform-JVM-8A2BE2.svg?logo=openjdk" alt="Badge JVM" />
  <img src="https://img.shields.io/badge/Platform-WASM%20%2F%20JS-yellow.svg?logo=javascript" alt="Badge JS" />
  -->
  <a href="">
    <img src="https://img.shields.io/badge/Platform-JS-yellow.svg?logo=javascript" alt="Badge JS" />
  </a>
  <a href="https://github.com/the-best-is-best/">
    <img alt="Profile" src="https://img.shields.io/badge/github-%23181717.svg?&style=for-the-badge&logo=github&logoColor=white" height="20"/>
  </a>
</div>

> **KDownloadFile** is a simple yet powerful **Kotlin Multiplatform** library that enables
> downloading files over HTTP with support for custom headers, saving them locally, and opening them
> using the system’s default app — all while abstracting away platform-specific details.
>
> ✅ Supports background downloads on **Android**  
> 🍏 Supports **Live Activities** on **iOS 16.1+** to reflect download progress  
> 🌍 Built for **KMP**, with clean API and platform-safe design

## 📦 Download

[![Maven Central](https://img.shields.io/maven-central/v/io.github.the-best-is-best/kdownload-file)](https://central.sonatype.com/artifact/io.github.the-best-is-best/kdownload-file)

```kotlin
implementation("io.github.the-best-is-best:kdownload-file:2.5.0")
````


## If used version 2.3.0-rc1

* need use version 0.2.0-rc2 of KDownloadFileInterop


## ⚙️ Android File Provider Configuration

To ensure proper file access and sharing in Android, follow these steps:

### 1 Add this to your `AndroidManifest.xml` inside `<application>`

```xml
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider" android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
````

### 2 Create `res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-path name="external_files" path="." />
</paths>
```

## 🍏 iOS Installation (SPM)

To use **KDownloadFile**, you must add the Swift interop library to your iOS targets.

Use **Swift Package Manager** and add the following:

<https://github.com/the-best-is-best/KDownloadFileInterop>

## 🚀 How to Use

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
                url = "https://your-server.com/file.jpg",
                fileName = "file.jpg",
                folderName = "downloads"
            )

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
````

## 📂 What It Does

- ✅ Downloads a file from a URL
- 📀 Saves it to a platform-specific local folder
- 📂 Opens it using the native application for that file type

## 🔧 API Overview

```kotlin
expect suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String? = null,
    configuration: KDownloadFileConfiguration = KDownloadFileConfiguration(),
    customHeaders: Map<String, String> = emptyMap()
): Result<String>

expect fun openFile(
    filePath: String
)
````

## ⚙️ Configuration

```kotlin
data class KDownloadFileConfiguration(
    val android: AndroidKDownloadFileConfiguration = AndroidKDownloadFileConfiguration(),
    val ios: IosKDownloadFileConfiguration = IosKDownloadFileConfiguration(),
)
````

### 1 Android

```kotlin
data class AndroidKDownloadFileConfiguration(
    val notificationVisibility: DownloadNotificationVisibility = DownloadNotificationVisibility.Hidden,
    val title: String = "Download File",
    val description: String = "Downloading file..."
)
````

#### DownloadNotificationVisibility options

| Enum                      | Description                             |
|---------------------------|-----------------------------------------|
| Visible                   | Shows notification during download only |
| VisibleAndNotifyCompleted | Shows notification during and after ✅   |
| Hidden                    | No notification at all                  |
| NotifyOnlyOnCompletion    | Only notify when download completes     |

### 2 IOS

```kotlin
data class IosKDownloadFileConfiguration(
    val showLiveActivity: Boolean = false
)
```

- `showLiveActivity = true` enables iOS Live Activities (iOS 16.1+)

## 🍏 iOS Live Activities Support

KDownloadFiles supports **Live Activities** on iOS 16.1+.  
This shows real-time download progress in the Dynamic Island and Lock Screen.

### ⚠️ Requirements

- 📱 Minimum iOS: **16.1**
- 🧹 You must create a Widget Extension with Live Activity type.
- 🗾️ Add this key to your `Info.plist` of the main iOS app:

```xml

<key>NSSupportsLiveActivities</key><true />
````

```swift
import ActivityKit
import WidgetKit
import SwiftUI
import KDownloadFileInterop

struct Downloader_WidgetLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: DownloadAttributes.self) { context in
            LiveActivityView(
                fileName: context.attributes.fileName,
                progress: context.state.progress,
                status: context.state.status
            )
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "arrow.down.circle.fill")
                        .foregroundColor(.blue)
                        .font(.title2)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(String(format: "%.0f%%", context.state.progress * 100))
                        .fontWeight(.semibold)
                        .foregroundColor(.blue)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(context.attributes.fileName)
                            .bold()
                            .font(.headline)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        ProgressView(value: context.state.progress)
                            .progressViewStyle(LinearProgressViewStyle(tint: .blue))
                        Text(context.state.status)
                            .font(.caption2)
                            .foregroundColor(.gray)
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                Image(systemName: "arrow.down.circle.fill")
                    .foregroundColor(.blue)
            } compactTrailing: {
                Text(String(format: "%.0f%%", context.state.progress * 100))
                    .fontWeight(.bold)
                    .foregroundColor(.blue)
            } minimal: {
                Image(systemName: "arrow.down.circle.fill")
                    .foregroundColor(.blue)
            }
            .keylineTint(Color.blue)
        }
    }
}

struct LiveActivityView: View {
    let fileName: String
    let progress: Double
    let status: String

    var body: some View {
        VStack(spacing: 12) {
            Text("📁 \(fileName)")
                .font(.headline)
                .lineLimit(1)
                .truncationMode(.middle)
                .foregroundColor(.white)

            ProgressView(value: progress)
                .progressViewStyle(LinearProgressViewStyle(tint: .white))
                .scaleEffect(x: 1, y: 3, anchor: .center)
                .background(Color.white.opacity(0.3))
                .cornerRadius(4)

            Text(status)
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(LinearGradient(
                    gradient: Gradient(colors: [Color.blue.opacity(0.8), Color.cyan]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ))
        )
        .padding(8)
    }
}
````

```md
## 🛡 License

Licensed under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0)
```
