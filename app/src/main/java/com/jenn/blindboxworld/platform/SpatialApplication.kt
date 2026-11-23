package com.jenn.blindboxworld.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.jenn.blindboxworld.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
