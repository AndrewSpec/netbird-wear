package io.netbird.wear

import android.app.Application
import io.netbird.wear.data.StateStore

class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StateStore.init(this)
    }
}
