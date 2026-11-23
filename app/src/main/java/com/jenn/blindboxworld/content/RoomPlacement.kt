package com.jenn.blindboxworld.content

import kotlin.math.abs

/** The foot of a collectible, in metres relative to the authored loft. */
internal data class RoomSpot(val x: Float, val y: Float, val z: Float) {
    fun inside() = x in -4.16f..4.16f && y in 0f..3.9f && z in -4.34f..4.34f

    fun encode() = "$x,$y,$z"

    companion object {
        fun decode(value: String?): RoomSpot? {
            val parts = value?.split(',') ?: return null
            if (parts.size != 3) return null
            val (x, y, z) = parts.map { it.toFloatOrNull() ?: return null }
            return RoomSpot(x, y, z).takeIf { it.inside() }
        }
    }

    fun moved(dx: Float, dy: Float, dz: Float) = RoomSpot(
        (x + dx).coerceIn(-4.16f, 4.16f),
        (y + dy).coerceIn(0f, 3.9f),
        (z + dz).coerceIn(-4.34f, 4.34f),
    )
}

internal data class PlacementSurface(
    val x: ClosedFloatingPointRange<Float>, val z: ClosedFloatingPointRange<Float>, val y: Float, val id: String = "",
)

// Coordinates of the supporting surfaces in design/collection_room.py, in the runtime's Y-up frame.
private val surfaces = listOf(
    PlacementSurface(-4.16f..4.16f, -4.34f..4.34f, .002f),
    PlacementSurface(-1.45f..1.45f, -4.29f..-3.95f, .905f),
    PlacementSurface(-1.45f..1.45f, -4.29f..-3.95f, .416f),
    PlacementSurface(-1.45f..1.45f, -4.29f..-3.95f, .106f),
    PlacementSurface(1.34f..2.86f, -.65f..-.01f, .412f),
    PlacementSurface(.15f..2.17f, 2.81f..3.73f, .785f),
    PlacementSurface(-4.05f..-3.63f, -2.51f..-.25f, .621f),
    PlacementSurface(-3.51f..-2.93f, 0.38f..0.92f, .431f),
    PlacementSurface(-3.30f..-2.88f, 1.36f..1.72f, .331f),
)

/** First top surface intersected by the actual pointing ray, including the room floor. */
internal fun supportingPose(eye: RoomSpot, dx: Float, dy: Float, dz: Float, furniture: List<PlacementSurface> = surfaces.drop(1)): DecorPose? {
    if (dy >= -.0001f) return null
    var nearest = Float.POSITIVE_INFINITY
    var pose: DecorPose? = null
    for (surface in listOf(surfaces.first()) + furniture) {
        val t = (surface.y - eye.y) / dy
        if (t <= 0f || t >= nearest) continue
        val x = eye.x + dx * t
        val z = eye.z + dz * t
        if (x in surface.x && z in surface.z) {
            nearest = t
            pose = DecorPose(RoomSpot(x, surface.y, z), support = surface.id)
        }
    }
    return pose
}

/** Center the frame on the ray and align its back face against the nearest solid elevation. */
internal fun wallPose(eye: RoomSpot, dx: Float, dy: Float, dz: Float, depth: Float): DecorPose? {
    var nearest = Float.POSITIVE_INFINITY
    var result: DecorPose? = null
    fun consider(t: Float, yaw: Float) {
        if (!t.isFinite() || t <= 0f || t >= nearest) return
        val p = RoomSpot(eye.x + t*dx, eye.y + t*dy, eye.z + t*dz)
        if (p.x !in -4.28f..4.28f || p.y !in 0f..4.06f || p.z !in -4.4f..4.4f) return
        if (yaw == 0f && p.x < -2.89f) return // Window beside the shelf wall.
        nearest = t
        result = DecorPose(p, yaw)
    }
    if (dx > .0001f) consider((4.28f - depth/2 - eye.x)/dx, -90f)
    if (dz > .0001f) consider((4.39f - depth/2 - eye.z)/dz, 180f)
    if (dz < -.0001f) consider((-4.39f + depth/2 - eye.z)/dz, 0f)
    return result
}

internal fun freeShelfSpot(occupied: Collection<RoomSpot>): RoomSpot? =
    SHELF_SLOTS.asSequence().map { RoomSpot(it.x, it.y, it.z) }.firstOrNull { slot ->
        occupied.none { abs(it.x - slot.x) < .15f && abs(it.y - slot.y) < .11f && abs(it.z - slot.z) < .14f }
    }

/** Preserve the selected figure's room spot for the outgoing figure when switching the display. */
internal fun Map<String, RoomSpot>.switchDisplay(previous: String?, selected: String): Map<String, RoomSpot> {
    val destination = this[selected] ?: return this
    if (previous == selected) return this
    val remaining = this - selected
    return if (previous == null) remaining else remaining + (previous to destination)
}
