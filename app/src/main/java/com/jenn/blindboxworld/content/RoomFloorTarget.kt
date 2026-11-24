package com.jenn.blindboxworld.content

import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.AccessibilityComponent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Color4
import com.pico.spatial.ui.foundation.content.setAccessibilityActions

internal fun roomFloorTarget(content: SpatialViewContent): Entity {
    val floor = Entity().apply {
        setName("loftFloor")
        components.set(TransformComponent())
        components.set(CollisionComponent(
            collisionShape = listOf(ShapeResource.createBox(Vector3(8.2f, 0.024f, 8.32f))),
            physicsMaterial = PhysicsMaterialResource()
        ))
        components.set(InteractableComponent())
    }
    // Hit surface is retained only for furniture placement, never for locomotion.
    floor.components.set(AccessibilityComponent("Furniture placement surface", "").apply {
        isAccessibilityElement = false
    })
    content.setAccessibilityActions(floor, "room.placement-floor") { }
    floor.enabled = false
    return floor
}

internal fun roomDestinationMarker() = Entity().apply {
    components.set(TransformComponent())
    components.set(ModelComponent(
        MeshResource.createTorus(0.105f, 0.008f),
        glowMaterial(Color4(1f, 0.83f, 0.44f, 1f))
    ))
    enabled = false
}

/** Interaction-only wall planes expose precise mouse and pinch locations during placement. */
internal fun placementWallTarget() = Entity().apply {
    setName("placementWalls")
    components.set(TransformComponent())
    fun slab(size: Vector3, at: Vector3) = ShapeResource.createBox(size).offsetByTranslation(at)
    components.set(CollisionComponent(collisionShape = listOf(
        slab(Vector3(.04f,4.06f,8.8f), Vector3(4.30f,2.03f,0f)),
        slab(Vector3(8.56f,4.06f,.04f), Vector3(0f,2.03f,4.41f)),
        slab(Vector3(8.56f,4.06f,.04f), Vector3(0f,2.03f,-4.41f)),
    ), physicsMaterial = PhysicsMaterialResource()))
    components.set(InteractableComponent())
}
