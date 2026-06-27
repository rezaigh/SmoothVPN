package com.smoothvpn

import android.app.Application
import com.smoothvpn.data.ProfileRepository

class App : Application() {
    val repository: ProfileRepository by lazy { ProfileRepository(this) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
