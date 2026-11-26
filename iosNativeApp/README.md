# KDownloadFile · Native iOS sample (SwiftUI)

An iPhone app whose screens are **real SwiftUI** (`List`, `Button`, `ProgressView`, `Label`).
Only the work comes from the Kotlin library: downloading, saving, progress and errors.

(The other iOS sample, `../iosApp`, shows the shared Compose Multiplatform screen instead.)

## Run it (on a Mac)

1. Open `iosNativeApp/iosNativeApp.xcodeproj` in **Xcode 16** or newer.
2. Pick your team in *Signing & Capabilities* (or run on a simulator).
3. Run. The first build runs Gradle to compile the Kotlin library into `kDownloadFileKit.framework`
   (build phase *Compile Kotlin Framework*: `./gradlew :kDownloadFile:embedAndSignAppleFrameworkForXcode`),
   and Xcode fetches the `KDownloadFileInterop` Swift package.

## How Swift calls the library

The library's `appleMain` has a Swift-friendly API, `KDownloadFileSwift`
(`kDownloadFile/src/appleMain/kotlin/io/github/kdownloadfile/KDownloadFileSwift.kt`):

```swift
import kDownloadFileKit

let outcome = try await KDownloadFileSwift.shared.download(
    url: "https://example.com/report.pdf",
    fileName: "report.pdf",
    folderName: "Reports",
    onProgress: { progress in
        // Called on the main thread: set SwiftUI state directly.
        self.fraction = progress.fraction          // 0.0 – 1.0 for ProgressView(value:)
        self.percent = progress.percent            // -1 while the size is unknown (isSizeKnown == false)
    }
)

switch outcome {
case let success as FileOutcome.Success:   KDownloadFileSwift.shared.open(path: success.path)
case let http as FileOutcome.HttpError:    print("HTTP \(http.statusCode)")   // e.g. 404
case is FileOutcome.NoInternet:            print("No internet")
case let failed as FileOutcome.Failed:     print(failed.message)
default:                                   break
}
```

- `download(url:fileName:folderName:headers:configuration:onProgress:)` adds headers and configuration.
- `save(data:fileName:folderName:onProgress:)` saves `Data` the app already has.
- Files go to `Documents/Downloads/<folderName>`. With `UIFileSharingEnabled` they show in the
  **Files** app under *On My iPhone › Native Demo*.

## What is where

| File | What it is |
|---|---|
| `iosNativeApp/NativeDemoApp.swift` | the `@main` app |
| `iosNativeApp/ContentView.swift` | the list of test downloads and the row view (progress bar, result) |
| `iosNativeApp/FileJob.swift` | one download's state (`idle`, `running`, `done`, `failed`) and the library call |
| `iosNativeApp/Assets.xcassets` | icon and accent color |

The project uses an Xcode 16 synchronized folder: any file added to `iosNativeApp/` is in the app,
with no project file edits.
