package com.jenn.blindboxworld.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.AccessibilityComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.setAccessibilityActions

/** Keep the handle's explicit hit area in front of the cabinet. */
internal suspend fun mountMachineCrank(mount: Entity): Entity {
    val visual = Entity.loadSuspend("asset://props/gacha_crank.glb")
    val control = Entity().apply { components.set(TransformComponent()) }
    control.setName("crank")
    control.makeTappable(Vector3(.24f, .24f, .22f), 0f)
    control.addChild(visual)
    mount.addChild(control)
    return control
}

internal fun publishMachineCrank(content: SpatialViewContent, crank: Entity, cue: Entity, stop: () -> Boolean) {
    crank.components.set(AccessibilityComponent("Crank", "Press to stop").apply { isAccessibilityElement = true })
    content.setAccessibilityActions(crank, "machine.crank-handle") {
        onClick("Press to stop") { stop() }
    }
    cue.components.set(AccessibilityComponent("Press to stop", "Crank").apply { isAccessibilityElement = true })
    content.setAccessibilityActions(cue, "machine.crank") {
        onClick("Press to stop") { stop() }
    }
}

internal fun publishTrayCue(content: SpatialViewContent, cue: Entity, openPrize: () -> Boolean) {
    cue.components.set(AccessibilityComponent("Tap to open", "Dispensed prize").apply { isAccessibilityElement = true })
    content.setAccessibilityActions(cue, "machine.dispensed-prize") {
        onClick("Tap to open") { openPrize() }
    }
}
