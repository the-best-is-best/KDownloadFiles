package io.github.kdownloadfile.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.webkit.MimeTypeMap
import io.github.kdownloadfile.AndroidKDownloadFile
import java.net.URLConnection

/** The app's label, used as its folder name inside Downloads. */
internal fun appName(): String {
    val context = AndroidKDownloadFile.appContext
    val applicationInfo = context.applicationInfo
    val stringId = applicationInfo.labelRes
    return if (stringId == 0) {
        applicationInfo.nonLocalizedLabel.toString()
    } else {
        context.getString(stringId)
    }
}

/** For example `MyApp/1.2 (Android; Google Pixel 8; Android 15 SDK 35)`. */
internal fun userAgent(): String {
    val context = AndroidKDownloadFile.appContext
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val label = context.applicationInfo.loadLabel(context.packageManager).toString()
    val version = packageInfo.versionName ?: "1.0"
    return "$label/$version (Android; ${Build.MANUFACTURER} ${Build.MODEL}; " +
            "Android ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT})"
}

internal fun mimeTypeOf(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: URLConnection.guessContentTypeFromName(fileName)
        ?: "application/octet-stream"
}

internal fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        cm.activeNetworkInfo?.isConnected == true
    }
}
