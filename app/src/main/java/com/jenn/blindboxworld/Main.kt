package com.jenn.blindboxworld

import com.jenn.blindboxworld.content.BlindboxExperience
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

// PICO can mount another default-container composition when the app is reopened.
// Only the newest composition owns the room; retiring the old subtree invokes its
// existing entity, attachment, tracking, and coroutine cleanup paths.
private var activeExperience by mutableStateOf<Any?>(null)

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            val session = remember { Any() }
            DisposableEffect(session) {
                activeExperience = session
                onDispose {
                    if (activeExperience === session) activeExperience = null
                }
            }
            if (activeExperience === session) {
                PicoTheme {
                    com.jenn.blindboxworld.content.HandSession { BlindboxExperience() }
                }
            }
        }
    }
