package com.waytogo

import android.app.Application
import com.waytogo.di.AppContainer

/** Application entry point; owns the manual DI container. */
class WayToGoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
