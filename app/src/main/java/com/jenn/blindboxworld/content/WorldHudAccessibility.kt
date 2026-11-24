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
