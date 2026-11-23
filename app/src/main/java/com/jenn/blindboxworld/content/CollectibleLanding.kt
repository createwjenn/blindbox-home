package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDPose
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

/** Transfer the visible model between the unboxing stage, the viewer and the loft without a pose jump. */
internal fun carryRevealedFigure(parent: Entity, reward: Entity) {
    val position = reward.convertPositionTo(Vector3.ZERO, parent)
    val rotation = reward.convertRotationTo(Quat.identity(), parent)
    val scale = reward.convertScaleTo(Vector3.ONE, parent)
    check(parent.addChild(reward))
    reward.components[TransformComponent::class.java]?.apply {
        setPosition(position)
        setQuaternion(rotation)
        setScaleVector(scale)
    }
}

/** Move the loft along its clear-floor route while the newly opened collectible stays in the viewer's hands. */
internal suspend fun frameCollectibleStand(
    root: Entity, stand: RoomSpot, startYaw: Float, head: HMDPose?, onYaw: (Float) -> Unit
) {
    val eye = root.convertPositionFrom(head?.position ?: Vector3(0f, 1.55f, 0f), null)
    val from = FloorPoint(eye.x, eye.z)
    val target = stationView(stand, false)
    val path = listOf(from) + RoomFloor.route(from, FloorPoint(target.x, target.z))
    val lengths = path.zipWithNext { a, b -> a.distanceTo(b) }
    val distance = lengths.sum()
    val end = path.last()
    val facing = Math.toDegrees(atan2((end.x - stand.x).toDouble(), (end.z - stand.z).toDouble())).toFloat()
    val startHeading = (head?.headingDegrees() ?: 0f) - startYaw
    val turn = ((facing - startHeading + 540f) % 360f) - 180f
    // The framed exhibit includes the whole cabinet, with the figure close to eye level.
    val targetHeight = (stand.y + 1.22f).coerceIn(Interior.LOFT.y)
    val duration = (0.9f + distance * 0.42f).coerceAtMost(3.2f)
    tween(duration) { t ->
        val progress = t * t * (3f - 2f * t)
        var remaining = distance * progress
        var position = end
        for (i in lengths.indices) {
            if (remaining <= lengths[i] && lengths[i] > 0f) {
                val u = remaining / lengths[i]
                val a = path[i]
                val b = path[i + 1]
                position = FloorPoint(a.x + (b.x - a.x) * u, a.z + (b.z - a.z) * u)
                break
            }
            remaining -= lengths[i]
        }
        val view = LoftView(position.x, eye.y + (targetHeight - eye.y) * progress, position.z, startHeading + turn * progress)
        onYaw(root.alignView(view, head))
    }
}

/** Release the held collectible only after the cabinet has been framed and opened. */
internal suspend fun flyFigureToStand(root: Entity, figure: Entity, foot: Vector3, previous: Entity?) {
    carryRevealedFigure(root, figure)
    val start = figure.pos()
    val transform = checkNotNull(figure.components[TransformComponent::class.java])
    val rotation = transform.quaternion
    val scale = transform.scaleVector
    val landedScale = Vector3(BOX_SCALE, BOX_SCALE, BOX_SCALE)
    val previousTransform = previous?.components?.get(TransformComponent::class.java)
    val previousScale = previousTransform?.scaleVector
    tween(1.26f) { t ->
        val ease = t * t * (3f - 2f * t)
        val point = Vector3.lerp(start, foot, ease)
        transform.setPosition(Vector3(point.x, point.y + 0.26f * sin(PI.toFloat() * ease), point.z))
        transform.setQuaternion(Quat.slerp(rotation, Quat.identity(), ease))
        transform.setScaleVector(Vector3.lerp(scale, landedScale, ease))
        if (t > 0.69f && previousTransform != null && previousScale != null) {
            val leave = easeInOut(((t - 0.69f) / 0.31f).coerceIn(0f, 1f))
            previousTransform.setScaleVector(Vector3.lerp(previousScale, Vector3.ZERO, leave * 0.999f))
        }
    }
}
