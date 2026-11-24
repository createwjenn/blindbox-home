package com.jenn.blindboxworld.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Intro/countdown only. Locomotion belongs to physical keyboard and VR controllers. */
internal class WorldHud(private val input: CourtyardInput, private val scope: CoroutineScope) {
    private var content: SpatialViewContent? = null
    private val counts = mutableListOf<Entity>()
    private var intro: Entity? = null
    private var menu: Entity? = null
    private var nudge: Job? = null

    suspend fun mount(view: SpatialViewContent, head: Entity, figure: String, gameMenu: Entity?) {
        content = view
        menu = gameMenu
        val welcome = Entity.loadSuspend("asset://world-hud/welcome-$figure.glb")
        welcome.place(0f, 0.055f, -1.65f)
        head.addChild(welcome)
        publishWorldWelcome(view, welcome, figure)
        intro = welcome
        for (i in 1..3) counts += Entity.loadSuspend("asset://world-hud/count-$i.glb").also {
            it.place(0f, -0.10f, -1.65f)
            head.addChild(it)
            it.enabled = i == 3
        }
    }

    fun show(phase: RoundPhase, countdown: Int) {
        val welcome = phase == RoundPhase.INTRO
        intro?.enabled = welcome
        counts.forEachIndexed { index, text -> text.enabled = welcome && index + 1 == countdown }
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
