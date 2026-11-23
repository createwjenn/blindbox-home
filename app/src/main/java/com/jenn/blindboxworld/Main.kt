package com.jenn.blindboxworld

import com.jenn.blindboxworld.content.BlindboxExperience
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                BlindboxExperience()
            }
        }
    }
