package com.depth.breathtrainer

import android.app.Application
import com.depth.breathtrainer.engine.BreathTimerEngine

/** Initializes the shared engine once per process, whichever surface (phone or car) wakes it. */
class DepthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BreathTimerEngine.init(this)
    }
}
