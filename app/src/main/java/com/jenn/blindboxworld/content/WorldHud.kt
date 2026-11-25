package com.jenn.blindboxworld.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Publish the in-world menu and its discrete input actions when play begins. */
internal class WorldHud(private val input: CourtyardInput, private val scope: CoroutineScope) {
    private var content: SpatialViewContent? = null
    private var menu: Entity? = null
    private var nudge: Job? = null

    fun mount(view: SpatialViewContent, gameMenu: Entity?) {
        content = view
        menu = gameMenu
    }

    fun show(phase: RoundPhase) {
        val playing = phase == RoundPhase.PLAYING
        val view = content
        val panel = menu
        if (playing && view != null && panel != null) {
            publishWorldMenu(view, panel, ::pulse, input::requestJump)
        }
        if (phase != RoundPhase.PLAYING) {
            nudge?.cancel()
            input.pad = StickAxes()
        }
    }

    private fun pulse(axes: StickAxes) {
        nudge?.cancel()
        nudge = scope.launch {
            input.pad = axes
            delay(155)
            input.pad = StickAxes()
        }
    }

}
