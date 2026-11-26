package io.github.kdownloadfile.configration

enum class DownloadNotificationVisibility(val rawValue: Int) {
    Visible(0),                       // أثناء التحميل فقط
    VisibleAndNotifyCompleted(1),     // أثناء التحميل وبعد الاكتمال ✅
    Hidden(2),                         // لا إشعار إطلاقًا
    NotifyOnlyOnCompletion(3);        // يظهر إشعار فقط عند الاكتمال
}

data class KDownloadFileConfiguration(
    val noDuplicateFile: Boolean = true,
    val saveToDownloads: Boolean = true,
    val saveInCacheFiles: Boolean = false,
    val android: AndroidKDownloadFileConfiguration = AndroidKDownloadFileConfiguration(),
    val ios: IosKDownloadFileConfiguration = IosKDownloadFileConfiguration(
        showLiveActivity = false
    )
) {
    init {
        if (saveToDownloads && saveInCacheFiles) {
            throw IllegalArgumentException("Cannot save to both Downloads and cache files")
        }
    }
}

data class AndroidKDownloadFileConfiguration(
    val notificationVisibility: DownloadNotificationVisibility = DownloadNotificationVisibility.Hidden,
    val title: String = "Download File",
    val description: String = "Downloading file...",
)

data class IosKDownloadFileConfiguration(
    /**
     * Shows the download as a Live Activity (iOS 16.2+). Needs the optional Swift package
     * KFileLiveActivity in the app; without it the download runs without one.
     */
    val showLiveActivity: Boolean = false,
)
