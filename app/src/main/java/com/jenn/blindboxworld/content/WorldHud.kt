package com.jenn.blindboxworld.content

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Geometry-only controls. Transparent pixels of a 2D attachment cut holes in the splat pass. */
internal class WorldHud(private val input: CourtyardInput, private val scope: CoroutineScope) {
    private var content: SpatialViewContent? = null
    private val controls = mutableMapOf<String, Entity>()
    private val thumbs = mutableMapOf<String, Entity>()
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
        for ((label, x) in listOf("Walk" to -1.055f, "Jump" to -0.877f, "Turn" to -0.70f)) {
            val pad = Entity().apply { components.set(TransformComponent()) }
            pad.addChild(Entity.loadSuspend("asset://world-hud/${label.lowercase()}.glb"))
            pad.setName("worldPad:$label")
            pad.place(x, -0.695f, -1.65f)
            val diameter = if (label == "Jump") 0.12f else 0.168f
            pad.makeTappable(Vector3(diameter, diameter, 0.016f), 0f)
            if (label != "Jump") {
                val thumb = glowSphere(0.011f, Color4(1f, 0.67f, 0.18f, 1f))
                thumb.place(0f, 0.014f, 0.004f)
                pad.addChild(thumb)
                thumbs[label] = thumb
            }
            head.addChild(pad)
            pad.enabled = false
            controls[label] = pad
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
        for ((label, pad) in controls) {
            pad.enabled = playing
            if (playing) {
                // Register on the enabled target after it belongs to this SpatialView.
                content?.let { publishWorldPad(it, pad, label, ::pulse, input::requestJump) }
            } else hideWorldPad(pad)
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

    fun gestures(context: Context, active: Boolean): Modifier = if (!active) Modifier else Modifier.pointerInput(this) {
        val held = mutableMapOf<PointerId, String>()
        try {
            detectSpatialPointerEvent(context) { events ->
                var consumed = false
                for (event in events) {
                    val target = event.targetedEntity?.tagged("worldPad:")?.getName()?.removePrefix("worldPad:")
                    val label = held[event.pointerId] ?: target ?: continue
                    val pad = controls[label] ?: continue
                    if (!pad.enabled) continue
                    if (event.pressed) {
                        val first = held.put(event.pointerId, label) == null
                        nudge?.cancel()
                        if (label == "Jump") {
                            if (first) input.requestJump()
                        } else {
                            val position = event.position3D
                            val local = content?.localHitPosition(Vector3(position.x, position.y, position.z), pad) ?: continue
                            val x = local.x / 0.050f
                            val y = if (label == "Walk") (local.y - 0.014f) / 0.050f else 0f
                            val norm = hypot(x,y).coerceAtLeast(1f)
                            setAxes(label, x/norm, y/norm)
                        }
                        consumed = true
                    } else if (held.remove(event.pointerId) != null) {
                        if (label != "Jump") setAxes(label, 0f, 0f)
                        consumed = true
                    }
                }
                consumed
            }
        } finally { input.pad = StickAxes() }
    }

    private fun setAxes(label: String, x: Float, y: Float) {
        thumbs[label]?.place(0.035f*x,0.014f+0.035f*y,0.004f)
        input.pad = if (label == "Walk") input.pad.copy(x = x, forward = y) else input.pad.copy(turn = x)
    }
}
