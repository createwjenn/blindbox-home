package com.jenn.blindboxworld.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.AccessibilityComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.ui.foundation.content.setAccessibilityActions

internal fun registerPlacedCollectible(content: SpatialViewContent, model: Entity, id: String, inspect: () -> Boolean) {
    model.components.set(AccessibilityComponent("Room collectible", nameOf(id)).apply { isAccessibilityElement = true })
    // The SDK contract scanner needs each runtime selector as a literal.
    when (id) {
        "niu-lai" -> content.setAccessibilityActions(model, "room.collectible.niu-lai") { onClick("Inspect collectible", inspect) }
        "strawberlina" -> content.setAccessibilityActions(model, "room.collectible.strawberlina") { onClick("Inspect collectible", inspect) }
        "bananito" -> content.setAccessibilityActions(model, "room.collectible.bananito") { onClick("Inspect collectible", inspect) }
        "tung-tung-tung" -> content.setAccessibilityActions(model, "room.collectible.tung-tung-tung") { onClick("Inspect collectible", inspect) }
        "tralalero-tralala" -> content.setAccessibilityActions(model, "room.collectible.tralalero-tralala") { onClick("Inspect collectible", inspect) }
        "ballerina-capuccina" -> content.setAccessibilityActions(model, "room.collectible.ballerina-capuccina") { onClick("Inspect collectible", inspect) }
        "chill-guy" -> content.setAccessibilityActions(model, "room.collectible.chill-guy") { onClick("Inspect collectible", inspect) }
        "secret-capybara-toilet" -> content.setAccessibilityActions(model, "room.collectible.secret-capybara-toilet") { onClick("Inspect collectible", inspect) }
    }
}

internal fun registerPlacementPreview(content: SpatialViewContent, model: Entity, id: String, place: () -> Boolean) {
    model.components.set(AccessibilityComponent("Placement preview", nameOf(id)).apply { isAccessibilityElement = true })
    content.setAccessibilityActions(model, "room.placement.preview") { onClick("Place here", place) }
}
