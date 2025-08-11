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
    val android: AndroidKDownloadFileConfiguration = AndroidKDownloadFileConfiguration(),
    val ios: IosKDownloadFileConfiguration = IosKDownloadFileConfiguration()
) {
    // Only visible when saveToDownloads is false
    val saveInCacheFiles: Boolean by lazy {
        if (saveToDownloads) {
            throw IllegalStateException("saveInCacheFiles is only available when saveToDownloads is false")
        }
        false // Default value when saveToDownloads is false
    }

    // Secondary constructor for when saveToDownloads is false
    constructor(
        noDuplicateFile: Boolean = true,
        saveToDownloads: Boolean = false,
        saveInCacheFiles: Boolean = false,
        android: AndroidKDownloadFileConfiguration = AndroidKDownloadFileConfiguration(),
        ios: IosKDownloadFileConfiguration = IosKDownloadFileConfiguration()
    ) : this(noDuplicateFile, saveToDownloads, android, ios) {
        if (saveToDownloads) {
            throw IllegalArgumentException("saveInCacheFiles can only be set when saveToDownloads is false")
        }
    }
}

data class AndroidKDownloadFileConfiguration(
    val notificationVisibility: DownloadNotificationVisibility = DownloadNotificationVisibility.Hidden,
    val title: String = "Download File",
    val description: String = "Downloading file...",
)

data class IosKDownloadFileConfiguration(
    val showLiveActivity: Boolean = false,
)