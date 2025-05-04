package io.github.kdownloadfile

import android.app.Activity
import java.lang.ref.WeakReference

object AndroidKDownloadFile {
    internal lateinit var activity: WeakReference<Activity>


    fun init(activity: Activity) {
        this.activity = WeakReference(activity)
    }
}