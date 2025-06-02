package io.github.kdownloadfile

import android.content.Context
import androidx.startup.Initializer

object AndroidKDownloadFile {
    internal lateinit var appContext: Context
}


internal class ApplicationContextInitializer : Initializer<Context> {
    override fun create(context: Context): Context {
        AndroidKDownloadFile.appContext = context.applicationContext
        return AndroidKDownloadFile.appContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }

}