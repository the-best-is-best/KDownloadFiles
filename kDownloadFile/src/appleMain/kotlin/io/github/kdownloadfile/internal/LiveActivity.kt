package io.github.kdownloadfile.internal

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSClassFromString
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSUUID
import platform.darwin.NSObject
import platform.darwin.NSObjectMeta

/*
 * Optional Live Activity, shown by the Swift package KFileLiveActivity when the app includes it.
 *
 * The library does not link to that package (no cinterop): it looks the Swift class up by name at
 * run time. Without the package the class is not found and downloads simply run without a Live
 * Activity. The names below are the contract with the Swift side and must not change.
 */

/** The Swift class: `@objc(KFileLiveActivity) public final class KFileLiveActivity: NSObject`. */
private const val CLASS_NAME = "KFileLiveActivity"

/** Its methods, each taking one NSDictionary with the keys below. */
private const val START = "start:"
private const val UPDATE = "update:"
private const val END = "end:"

private const val KEY_ID = "id"
private const val KEY_FILE_NAME = "fileName"
private const val KEY_KIND = "kind"          // "download" or "upload"
private const val KEY_PROGRESS = "progress"  // 0.0 – 1.0, or -1 when the size is unknown
private const val KEY_STATUS = "status"      // text shown on the Live Activity
private const val KEY_SUCCESS = "success"    // end only: true or false

/** One Live Activity for one transfer; every call does nothing when the Swift package is missing. */
internal class LiveActivity private constructor(private val target: NSObject, private val id: String) {

    private var lastPercent = -1

    /** Updates the Live Activity when the whole percentage changes (iOS limits how often it can update). */
    fun update(bytesDone: Long, totalBytes: Long?) {
        val percent = if (totalBytes != null && totalBytes > 0) (bytesDone * 100 / totalBytes).toInt() else -1
        if (percent == lastPercent) return
        lastPercent = percent
        send(
            target, UPDATE,
            mapOf(
                KEY_ID to id,
                KEY_PROGRESS to (if (percent >= 0) percent / 100.0 else -1.0),
                KEY_STATUS to (if (percent >= 0) "$percent%" else "Downloading…"),
            ),
        )
    }

    fun end(success: Boolean, status: String) {
        send(target, END, mapOf(KEY_ID to id, KEY_SUCCESS to success, KEY_STATUS to status))
    }

    companion object {
        /** Starts a Live Activity, or returns null when the app does not include KFileLiveActivity. */
        fun start(fileName: String, kind: String): LiveActivity? {
            val target = swiftObject ?: return null
            val activity = LiveActivity(target, NSUUID().UUIDString)
            send(
                target, START,
                mapOf(
                    KEY_ID to activity.id,
                    KEY_FILE_NAME to fileName,
                    KEY_KIND to kind,
                    KEY_STATUS to "Starting…",
                ),
            )
            return activity
        }

        /** One instance of the Swift class, created on first use; null when the class does not exist. */
        @OptIn(BetaInteropApi::class)
        private val swiftObject: NSObject? by lazy {
            val swiftClass = NSClassFromString(CLASS_NAME) as? NSObjectMeta ?: return@lazy null
            swiftClass.new()
        }

        @OptIn(ExperimentalForeignApi::class)
        private fun send(target: NSObject, method: String, info: Map<String, Any>) {
            val selector = NSSelectorFromString(method)
            if (target.respondsToSelector(selector)) target.performSelector(selector, withObject = info)
        }
    }
}
