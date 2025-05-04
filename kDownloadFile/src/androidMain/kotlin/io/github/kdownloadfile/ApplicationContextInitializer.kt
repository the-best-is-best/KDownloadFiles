package io.github.kdownloadfile


import android.content.Context
import androidx.startup.Initializer

internal lateinit var applicationContext: Context

class ApplicationContextInitializer : Initializer<Context> {
    override fun create(context: Context): Context = context.also {
        applicationContext = it.applicationContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}