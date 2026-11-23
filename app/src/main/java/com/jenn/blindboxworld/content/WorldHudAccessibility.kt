package com.jenn.blindboxworld.content

import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.AccessibilityComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.ui.foundation.content.setAccessibilityActions

internal fun publishWorldWelcome(content: SpatialViewContent, welcome: Entity, figure: String) {
    val instructions = nameOf(figure) + ". Collect 30 " + infoOf(figure).collectible
    welcome.components.set(AccessibilityComponent("World introduction", instructions).apply { isAccessibilityElement = true })
    content.setAccessibilityActions(welcome, "world.welcome") {}
}

internal fun hideWorldPad(pad: Entity) {
    pad.components[AccessibilityComponent::class.java]?.isAccessibilityElement = false
}

internal fun publishWorldPad(content: SpatialViewContent, pad: Entity, label: String, move: (StickAxes) -> Unit, jump: () -> Unit) {
    when (label) {
        "Walk" -> publishWorldWalk(content, pad, move)
        "Turn" -> publishWorldTurn(content, pad, move)
        "Jump" -> publishWorldJump(content, pad, jump)
    }
}

private fun publishWorldWalk(content: SpatialViewContent, pad: Entity, move: (StickAxes) -> Unit) {
    pad.components.set(AccessibilityComponent("Walk", "Move in this world").apply { isAccessibilityElement = true })
    content.setAccessibilityActions(pad, "world.walk") {
        onClick("Step forward") { move(StickAxes(forward = 1f)); true }
        customActions = listOf(
            CustomAccessibilityAction("Step backward") { move(StickAxes(forward = -1f)); true },
            CustomAccessibilityAction("Step left") { move(StickAxes(x = -1f)); true },
            CustomAccessibilityAction("Step right") { move(StickAxes(x = 1f)); true })
    }
}

private fun publishWorldTurn(content: SpatialViewContent, pad: Entity, move: (StickAxes) -> Unit) {
    pad.components.set(AccessibilityComponent("Turn", "Turn the view").apply { isAccessibilityElement = true })
    content.setAccessibilityActions(pad, "world.turn") {
        onClick("Turn right") { move(StickAxes(turn = 1f)); true }
        customActions = listOf(CustomAccessibilityAction("Turn left") { move(StickAxes(turn = -1f)); true })
    }
}

private fun publishWorldJump(content: SpatialViewContent, pad: Entity, jump: () -> Unit) {
    pad.components.set(AccessibilityComponent("Jump", "Jump over the ground").apply { isAccessibilityElement = true })
    content.setAccessibilityActions(pad, "world.jump") {
        onClick("Jump") { jump(); true }
    }
}


internal fun publishWorldMenu(content: SpatialViewContent, menu: Entity, step: (StickAxes) -> Unit, jump: () -> Unit) {
    menu.components.set(AccessibilityComponent("World controls", "Move, turn, or jump").apply { isAccessibilityElement = true })
    content.setAccessibilityActions(menu, "world.controls") {
        onClick("Step forward") { step(StickAxes(forward = 1f)); true }
        customActions = listOf(
            CustomAccessibilityAction("Step forward") { step(StickAxes(forward = 1f)); true },
            CustomAccessibilityAction("Step backward") { step(StickAxes(forward = -1f)); true },
            CustomAccessibilityAction("Step left") { step(StickAxes(x = -1f)); true },
            CustomAccessibilityAction("Step right") { step(StickAxes(x = 1f)); true },
            CustomAccessibilityAction("Turn left") { step(StickAxes(turn = -1f)); true },
            CustomAccessibilityAction("Turn right") { step(StickAxes(turn = 1f)); true },
            CustomAccessibilityAction("Jump") { jump(); true })
    }
}
