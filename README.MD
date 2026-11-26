<h1 align="center">KDownloadFiles</h1><br>

<div align="center">
  <a href="https://opensource.org/licenses/Apache-2.0">
    <img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/>
  </a>
  <a href="https://android-arsenal.com/api?level=23" rel="nofollow">
    <img alt="API" src="https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat" style="max-width: 100%;">
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg?logo=android" alt="Badge Android" />
  <img src="https://img.shields.io/badge/Platform-iOS%2013%2B-lightgrey.svg?logo=apple" alt="Badge iOS" />
  <img src="https://img.shields.io/badge/Platform-JVM-8A2BE2.svg?logo=openjdk" alt="Badge JVM" />
  <img src="https://img.shields.io/badge/Platform-JS%20%2F%20TypeScript-yellow.svg?logo=javascript" alt="Badge JS" />
  <a href="https://github.com/the-best-is-best/">
    <img alt="Profile" src="https://img.shields.io/badge/github-%23181717.svg?&style=for-the-badge&logo=github&logoColor=white" height="20"/>
  </a>
</div>

> **KDownloadFile** is a **Kotlin Multiplatform** library that downloads files over HTTP (or saves
> content you already have), stores them in the right folder on each platform, and opens them with
> the system's default app.
>
> - 📥 One request per download, with the HTTP status code when it fails (for example 404)
> - 📊 Progress (`%`) while downloading and while saving
> - 💾 `saveFile` for content you downloaded yourself (your own client, auth, POST…)
> - ✅ Background downloads and notifications on **Android**
> - 🍏 **Live Activities** on **iOS 16.1+**
> - 🌐 Usable from **React / TypeScript**, with generated types

## Contents

- [Installation](#-installation)
- [Quick start](#-quick-start)
- [API](#-api)
- [Where files are saved](#-where-files-are-saved)
- [Progress](#-progress)
- [Errors](#-errors)
- [Saving content you downloaded yourself](#-saving-content-you-downloaded-yourself)
- [Configuration](#%EF%B8%8F-configuration)
- [Web: React / TypeScript](#-web-react--typescript)
- [iOS Live Activities](#-ios-live-activities)
- [Sample apps](#-sample-apps)

## 📦 Installation

[![Maven Central](https://img.shields.io/maven-central/v/io.github.the-best-is-best/kdownload-file)](https://central.sonatype.com/artifact/io.github.the-best-is-best/kdownload-file)

```kotlin
implementation("io.github.the-best-is-best:kdownload-file:2.6.0")
```

### Android: FileProvider (needed by `openFile`)

Add this inside `<application>` in your `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

and create `res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-path name="external_files" path="." />
</paths>
```

### iOS: Swift package (SPM)

Add the Swift interop package to your iOS app with **Swift Package Manager**:

<https://github.com/the-best-is-best/KDownloadFileInterop>

It provides `openFile` and the Live Activity download. (If you use version `2.3.0-rc1` of this
library, use `0.2.0-rc2` of KDownloadFileInterop.)

## 🚀 Quick start

```kotlin
@Composable
fun DownloadButton() {
    val scope = rememberCoroutineScope()
    var percent by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf("") }

    Button(onClick = {
        scope.launch {
            downloadFile(
                url = "https://your-server.com/report.pdf",
                fileName = "report.pdf",
                folderName = "Reports",
                onProgress = { percent = it.percent },
            ).fold(
                onSuccess = { path -> openFile(path) },
                onFailure = { error ->
                    message = when (error) {
                        is KDownloadFileHttpException ->
                            if (error.statusCode == 404) "File not found" else "Server error ${error.statusCode}"
                        is KDownloadFileNoInternetException -> "No internet connection"
                        else -> "Something went wrong"
                    }
                },
            )
        }
    }) {
        Text(percent?.let { "$it%" } ?: "Download")
    }
}
```

## 🔧 API

```kotlin
/** Downloads `url` with one request. Returns the saved file's path. */
suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String? = null,
    configuration: KDownloadFileConfiguration = KDownloadFileConfiguration(),
    customHeaders: Map<String, String> = emptyMap(),
    onProgress: ((KDownloadProgress) -> Unit)? = null,
): Result<String>

/** Saves bytes you already have, to the same folders as downloadFile. No network request. */
suspend fun saveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String? = null,
    configuration: KDownloadFileConfiguration = KDownloadFileConfiguration(),
    onProgress: ((KDownloadProgress) -> Unit)? = null,
): Result<String>

/** Opens a path returned by downloadFile / saveFile with the system's default app. */
fun openFile(filePath: String)
```

## 📂 Where files are saved

|         | `saveToDownloads = true` (default)                                                   | `saveInCacheFiles = true`             | neither                                    |
|---------|--------------------------------------------------------------------------------------|---------------------------------------|--------------------------------------------|
| Android | `Download/<app name>/<folderName>` (MediaStore on Android 10+, no permission needed) | `cacheDir/<folderName>`               | `filesDir/<folderName>`                    |
| iOS     | `Documents/Downloads/<folderName>`                                                   | `Library/Caches/<folderName>`         | `Library/Application Support/<folderName>` |
| Desktop | `~/Downloads/<folderName>`                                                           | `~/.kdownloadfile/cache/<folderName>` | `~/.kdownloadfile/<folderName>`            |
| Web     | the browser's own download (shows in its downloads list); the result is a blob URL  |                                       |                                            |

- With `noDuplicateFile = true` (default) an existing file with the same name is replaced;
  otherwise the new one is saved as `name (1).ext`.
- On Android 6 to 9, `saveFile` into Downloads needs the `WRITE_EXTERNAL_STORAGE` permission.

## 📊 Progress

Pass `onProgress` to `downloadFile` or `saveFile`. It is called on the coroutine you called from
(the main thread when you call from the UI), so you can update the UI directly:

```kotlin
var progress by remember { mutableStateOf<KDownloadProgress?>(null) }

scope.launch { downloadFile(url, "report.pdf", onProgress = { progress = it }) }

when (val p = progress?.percent) {
    null -> CircularProgressIndicator()                 // size not known yet
    else -> {
        LinearProgressIndicator(progress = { p / 100f })
        Text("$p%")
    }
}
```

`KDownloadProgress` has `bytesDone`, `totalBytes` (null when the server sends no `Content-Length`),
`fraction` (0.0–1.0) and `percent` (0–100). The last update is always 100% when the call succeeds.

|         | `downloadFile`                                                                          | `saveFile`                                      |
|---------|-----------------------------------------------------------------------------------------|-------------------------------------------------|
| Android | every ¼ s from DownloadManager                                                          | per 64 KB written                               |
| iOS     | every ¼ s from `NSURLSession` (not with `showLiveActivity`: the Live Activity shows it) | per 64 KB written                               |
| Desktop | per chunk received                                                                      | per 64 KB written                               |
| Web     | per chunk received                                                                      | 0% then 100% (the browser saves it in one step) |

## ❗ Errors

`downloadFile` sends a single request; there is no check request first. When it fails, the
`Result` holds one of these:

| Error                              | When                                                           |
|------------------------------------|----------------------------------------------------------------|
| `KDownloadFileHttpException`       | the server answered with an error; see `statusCode` (e.g. 404) |
| `KDownloadFileNoInternetException` | no connection, DNS failure or timeout                          |
| `KDownloadFileException`           | anything else from the library (storage full, bad URL…)        |

```kotlin
downloadFile(url, "report.pdf").onFailure { error ->
    when (error) {
        is KDownloadFileHttpException -> if (error.statusCode == 404) showNoResult() else showError()
        is KDownloadFileNoInternetException -> showNoInternet()
        else -> showError()
    }
}
```

On an HTTP error the error page is not saved as the file, and an existing file with the same name
is kept. The one exception is Android with `saveToDownloads = true` and `noDuplicateFile = true`:
DownloadManager writes straight into Downloads, so the old file is removed before the download
starts.

> On iOS this applies when `ios.showLiveActivity` is `false` (the default). With Live Activities the
> download goes through `KDownloadFileInterop`, which checks the URL with an extra request and
> reports a failure without the status code.

## 💾 Saving content you downloaded yourself

When you need your own HTTP client (auth tokens, POST, one request only, your own error handling),
download the bytes yourself and pass them to `saveFile`. It saves to the same folders as
`downloadFile` and returns a path for `openFile`:

```kotlin
suspend fun downloadReport(client: HttpClient, year: Int): String? {
    val response = client.get("InterpreterJobsReport/Download") { parameter("startYear", year) }
    if (response.status != HttpStatusCode.OK) return null // handle 404 etc. yourself

    val path = saveFile(
        bytes = response.bodyAsBytes(),
        fileName = "InterpreterJobsReport_$year-${year + 1}.pdf",
        folderName = "Reports",
    ).getOrNull() ?: return null

    openFile(path)
    return path
}
```

Because this runs in common code, the same function works on Android and iOS.

## ⚙️ Configuration

```kotlin
data class KDownloadFileConfiguration(
    val noDuplicateFile: Boolean = true,     // replace a file with the same name; false: "name (1).ext"
    val saveToDownloads: Boolean = true,     // the user-visible Downloads folder
    val saveInCacheFiles: Boolean = false,   // the app's cache (cannot be true with saveToDownloads)
    val android: AndroidKDownloadFileConfiguration = AndroidKDownloadFileConfiguration(),
    val ios: IosKDownloadFileConfiguration = IosKDownloadFileConfiguration(),
)
```

When both `saveToDownloads` and `saveInCacheFiles` are false, the file goes to the app's private
files folder.

### Android

```kotlin
data class AndroidKDownloadFileConfiguration(
    val notificationVisibility: DownloadNotificationVisibility = DownloadNotificationVisibility.Hidden,
    val title: String = "Download File",
    val description: String = "Downloading file...",
)
```

| `DownloadNotificationVisibility` | Notification                     |
|----------------------------------|----------------------------------|
| `Visible`                        | during the download only         |
| `VisibleAndNotifyCompleted`      | during the download and after it |
| `Hidden`                         | none                             |
| `NotifyOnlyOnCompletion`         | only when it completes           |

### iOS

```kotlin
data class IosKDownloadFileConfiguration(
    val showLiveActivity: Boolean = false, // Live Activity with the progress (iOS 16.1+)
)
```

## 🌐 Web: React / TypeScript

The library's JS target is a normal **JavaScript library with TypeScript types**, so a React (or any
TypeScript) app can call it directly, with no wrapper. It is the same Kotlin code as on Android and
iOS.

### How it works

```
kDownloadFile/src/commonMain     downloadFile, saveFile, progress, errors (shared with Android/iOS)
kDownloadFile/src/jsMain         fetch + browser download, and KDownloadFileWeb.kt with @JsExport
        │
        │  ./gradlew :kDownloadFile:jsBrowserProductionLibraryDistribution
        ▼
kDownloadFile/build/dist/js/productionLibrary/
        kdownloadfile.mjs        the code (ES module)
        kdownloadfile.d.mts      the TypeScript types, generated from the Kotlin code
        │
        │  import { downloadFile } from 'kdownloadfile'
        ▼
web/ (Vite + React)
```

1. **Kotlin side.** `jsMain/.../KDownloadFileWeb.kt` marks the functions and classes meant for
   JavaScript with `@JsExport`. Kotlin's `suspend` functions and `Result` do not exist in
   JavaScript,
   so these functions return a `Promise` and a `DownloadResult` class instead.
2. **Build.** The `js` target uses `binaries.library()` and `generateTypeScriptDefinitions()`, so
   Gradle writes an ES module plus a `.d.mts` file with the types.
3. **Web app.** Vite points the name `kdownloadfile` at that module (`resolve.alias` in
   `vite.config.ts`), and TypeScript at the `.d.mts` (`paths` in `tsconfig.json`). React then
   imports
   it like any npm package.
4. **In the browser** `downloadFile` uses `fetch` and reads the body chunk by chunk to report
   progress. When it is done, it hands the file to the browser's own download, so the file appears in
   the browser's **downloads list**. The result is a **blob URL** of the file (web pages never see
   real paths), which `openFile` opens in a new tab.

### The TypeScript API (generated)

```ts
function downloadFile(url: string, fileName: string, options?: FileOptions): Promise<DownloadResult>
function saveBytes(bytes: Uint8Array, fileName: string, options?: FileOptions): Promise<DownloadResult>
function saveText(text: string, fileName: string, options?: FileOptions): Promise<DownloadResult>
function openFile(path: string): void

interface FileOptions {                          // every field optional
  headers?: Record<string, string>               // e.g. { Authorization: 'Bearer …' }
  onProgress?: (p: DownloadProgress) => void
}

class DownloadProgress { bytesDone: number; totalBytes: number | null; percent: number | null }

// Every call resolves (never rejects) with one of these; check it with instanceof:
DownloadResult.Success    { path: string }        // blob URL
DownloadResult.HttpError  { statusCode: number }  // e.g. 404
DownloadResult.NoInternet
DownloadResult.Failed     { message: string }
```

`fileName` is the name the browser saves the file with.

### Using it in React

The button only calls the library; the library does the rest (downloads, reports progress, hands
the file to the browser, returns the result):

```tsx
import { useState } from 'react'
import { downloadFile, openFile, DownloadResult, type DownloadProgress } from 'kdownloadfile'

export function DownloadReportButton() {
  const [progress, setProgress] = useState<DownloadProgress | null>(null)
  const [message, setMessage] = useState('')

  async function onClick() {
    const result = await downloadFile('/api/report.pdf', 'report.pdf', { onProgress: setProgress })
    if (result instanceof DownloadResult.Success) openFile(result.path)
    else if (result instanceof DownloadResult.HttpError) setMessage(result.statusCode === 404 ? 'File not found' : 'Server error')
    else if (result instanceof DownloadResult.NoInternet) setMessage('No internet connection')
    else setMessage('Something went wrong')
  }

  return (
    <>
      <button onClick={onClick}>{progress?.percent != null ? `${progress.percent}%` : 'Download'}</button>
      {message && <p>{message}</p>}
    </>
  )
}
```

### Where files go on the web

The file goes through the browser's **own download**, like any file downloaded from a website:

- It appears in the browser's **downloads list**, where the user can open it or show it in its folder.
- It is saved where the browser saves downloads (usually the Downloads folder). If the user turned on
  "Ask where to save each file" in the browser's settings, the browser asks them; a web page cannot
  turn that on itself.
- `folderName` and `configuration` do not apply on the web.
- On an HTTP error (for example 404) nothing is downloaded; the result is `HttpError`.

### Web notes

- **CORS:** the browser can only `fetch` a file from another site if that site sends
  `Access-Control-Allow-Origin`. Your own API usually does; a random file host may not.
- **Memory:** the file is held in memory until the browser saves it, so it suits documents and
  reports more than very large files.

### Try the web sample

```bash
cd web
npm install
npm run shared   # builds the library for JS (Gradle); run again after changing Kotlin code
npm run dev      # http://localhost:5174
```

The page has buttons to download 10 MB with a percentage, download without a known size, save
20 MB, and try a 404; the files show in the browser's downloads list. The test files are served by Vite itself
(`web/testFiles.ts`), slowly, so the
percentage can be seen moving. More in [web/README.md](web/README.md).

## 🍏 iOS Live Activities

With `IosKDownloadFileConfiguration(showLiveActivity = true)` the download progress shows in the
Dynamic Island and on the Lock Screen (iOS 16.1+).

### Requirements

- 📱 Minimum iOS: **16.1**
- 🧩 A Widget Extension with a Live Activity
- 🗂️ This key in the main app's `Info.plist`:

```xml

<key>NSSupportsLiveActivities</key><true />
```

### Widget example

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
```

## 🧪 Sample apps

| Folder                       | What it shows                                                                                                              |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `sharedUI` + `androidSimple` | Compose app: a download with notification / Live Activity, and a "Progress test" section (download 10 MB, save 20 MB, 404) |
| `web`                        | React app using the library's JS build directly (see above)                                                                |
| `iosNativeApp`               | Native SwiftUI iPhone app calling the library through `KDownloadFileSwift` (see [iosNativeApp/README.md](iosNativeApp/README.md)) |

## 🛡 License

Licensed under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0).
