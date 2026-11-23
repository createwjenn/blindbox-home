package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs

/** Accept the actual floor contact in loft metres. A transient hit on a stale view plane
 * must never turn into a walk to the back wall. */
internal fun floorContact(point: RoomSpot): FloorPoint? {
    val withinFloor = abs(point.y) <= 0.07f && abs(point.x) <= 4.10f && abs(point.z) <= 4.16f
    return if (withinFloor) FloorPoint(point.x, point.z) else null
}

internal fun stationFloorTarget(machine: Boolean) = Entity().apply {
    setName(if (machine) "stationFloor:machine" else "stationFloor:stand")
    components.set(TransformComponent())
    val width = if (machine) 1.90f else 1.58f
    components.set(CollisionComponent(
        collisionShape = listOf(ShapeResource.createBox(Vector3(width, 0.012f, 2.10f))
            .offsetByTranslation(Vector3(0f, 0.026f, 0.90f))),
        physicsMaterial = PhysicsMaterialResource()
    ))
    components.set(InteractableComponent())
}
