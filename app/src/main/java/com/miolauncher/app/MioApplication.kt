package com.miolauncher.app

import android.app.Application

class MioApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        @Volatile
        var appContext: MioApplication? = null
            private set
    }
}
