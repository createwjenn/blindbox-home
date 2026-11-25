package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs

internal const val STATION_RING_DIAMETER = .55f
internal const val STATION_RING_HEIGHT = .034f

/** Intersect the pointer ray with the visible pad plane. The floor input panel sits below it. */
internal fun stationRingContact(eye: RoomSpot, through: RoomSpot): FloorPoint? {
    val down = eye.y - through.y
    if (down <= 0f || eye.y <= STATION_RING_HEIGHT) return null
    val t = (eye.y - STATION_RING_HEIGHT) / down
    return FloorPoint(eye.x + (through.x - eye.x) * t, eye.z + (through.z - eye.z) * t)
}

internal fun FloorPoint.insideStationRing(x: Float, z: Float): Boolean {
    val dx = this.x - x
    val dz = this.z - z
    val radius = STATION_RING_DIAMETER / 2f
    return dx * dx + dz * dz <= radius * radius
}

/** Accept the actual floor contact in loft metres. A transient hit on a stale view plane
 * must never turn into a walk to the back wall. */
internal fun floorContact(point: RoomSpot): FloorPoint? {
    val withinFloor = abs(point.y) <= 0.07f && abs(point.x) <= 4.10f && abs(point.z) <= 4.16f
    return if (withinFloor) FloorPoint(point.x, point.z) else null
}

/** A tracked downwards ray can still reach the physical floor between physics updates. */
internal fun projectedRoomFloor(eye: RoomSpot, through: RoomSpot): FloorPoint? {
    val down = eye.y - through.y
    if (eye.y <= 0f || down < .001f) return null
    val t = eye.y / down
    return floorContact(RoomSpot(eye.x + (through.x-eye.x)*t, 0f, eye.z + (through.z-eye.z)*t))
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
