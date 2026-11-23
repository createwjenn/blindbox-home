package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.SortAsUIElementComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Distance along the cone from the eye to the four corners of an attachment. */
internal class HudOccluder(val x: Float, val y: Float, val z: Float, val w: Float, val h: Float, val d: Float) {
    fun entry(eye: Vector3, end: Vector3, halfDiagonal: Float): Float {
        var near = 0f
        var far = 1f
        fun clip(distance: Float, speed: Float): Boolean {
            if (abs(speed) < .00001f) return distance <= 0f
            val t = distance / speed
            if (speed > 0f) near = maxOf(near, t) else far = minOf(far, t)
            return near <= far
        }
        fun axis(origin: Float, destination: Float, center: Float, half: Float): Boolean {
            val delta = destination - origin
            return clip(center-half-origin, delta+halfDiagonal) && clip(origin-center-half, halfDiagonal-delta)
        }
        return if (axis(eye.x,end.x,x,w) && axis(eye.y,end.y,y,h) && axis(eye.z,end.z,z,d)) near else 1f
    }
}

internal fun roomHudOccluders(furnishing: RoomFurnishing, placed: Collection<RoomSpot>): List<HudOccluder> = buildList {
    // The loft walls, floor and ceiling also intercept rays, especially when turning beside a window.
    for (x in listOf(-4.26f,4.26f)) add(HudOccluder(x,2f,0f,.045f,2.25f,4.55f))
    for (z in listOf(-4.46f,4.46f)) add(HudOccluder(0f,2f,z,4.40f,2.25f,.045f))
    add(HudOccluder(0f,-.05f,0f,4.40f,.05f,4.55f))
    add(HudOccluder(0f,4.12f,0f,4.40f,.06f,4.55f))
    for (id in furnishing.placedIds) {
        val piece = furnishing.pieces.getValue(id)
        val pose = furnishing.visualPose(id)
        val p = pose.at
        val radians = Math.toRadians(pose.yaw.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        val hh = piece.height * .5f
        add(HudOccluder(p.x, p.y + if (piece.wall) 0f else hh, p.z,
            (c*piece.width + s*piece.depth)*.5f, hh, (s*piece.width + c*piece.depth)*.5f))
    }
    for (p in placed) add(HudOccluder(p.x,p.y+.13f,p.z,.13f,.13f,.13f))
}

/** The foreground and original head anchors have the same pose. Scaling from the eye
 * preserves the angular size and location of both the pixels and native hit surface. */
internal class RoomHudForeground(private val head: Entity, private val room: Entity) {
    val layer = Entity().apply { components.set(TransformComponent()); head.addChild(this) }
    val modalLayer = Entity().apply { components.set(TransformComponent()); layer.addChild(this) }
    val actionAnchor = Entity().apply { components.set(TransformComponent()); head.addChild(this) }
    private val zero = Vector3(0f,0f,0f)
    private var lastScale = 1f
    private var lastCheck = 0L
    private val previewLights = mutableMapOf<Entity, Float>()
    private val modalSize = .76f
    private val modalRise = .12f
    private var modalDepth = 0f

    fun fitModal(distance: Float) {
        modalDepth = distance
        modalLayer.place(0f,modalRise,-distance*(1f-modalSize),modalSize)
    }

    fun addPreviewLight(entity: Entity, nominal: Float) {
        previewLights[entity] = nominal
        entity.components[PointLightComponent::class.java]?.intensity = nominal * lastScale * lastScale * modalSize * modalSize
    }

    fun removePreviewLight(entity: Entity) { previewLights.remove(entity) }

    fun sortPreview(model: Entity) {
        if (model.components[ModelComponent::class.java] != null) {
            model.components.set(SortAsUIElementComponent(distanceBias = .035f))
        }
        model.getChildren().forEach(::sortPreview)
    }

    fun update(frame: Long, blockers: List<HudOccluder>, wallet: Entity?, pads: Entity?, panel: Entity?,
               modal: Boolean) {
        val anchorTransform = actionAnchor.components[TransformComponent::class.java] ?: return
        val physical = panel?.components?.get(TransformComponent::class.java)
        val parent = actionAnchor.getParent() ?: return
        if (panel?.enabled == true && physical != null) {
            physical.setPosition(parent.convertPositionTo(anchorTransform.position, head))
            physical.setQuaternion(parent.convertRotationTo(anchorTransform.quaternion, head))
            physical.setScaleVector(parent.convertScaleTo(anchorTransform.scaleVector, head))
        }
        // The tracked pose still updates every frame. Geometry checks only need normal head-motion cadence.
        if (frame - lastCheck < 24_000_000L) return
        lastCheck = frame
        val eye = head.convertPositionTo(zero,room)
        var scale = 1f
        fun allow(target: Vector3, radius: Float) {
            val end = head.convertPositionTo(target,room)
            for (bounds in blockers) {
                val distance = bounds.entry(eye,end,radius)
                if (distance < 1f) scale = min(scale,(distance * .92f).coerceAtLeast(.055f))
            }
        }
        if (wallet?.enabled == true) allow(wallet.pos(), .23f)
        if (pads?.enabled == true) allow(pads.pos(), .24f)
        if (panel?.enabled == true && physical != null) {
            val p = physical.position
            if (modal) allow(Vector3(p.x*modalSize,p.y*modalSize+modalRise,
                p.z*modalSize-modalDepth*(1f-modalSize)), .83f*modalSize)
            else allow(p, .38f)
        }
        if (abs(lastScale-scale) < .003f) return
        layer.components[TransformComponent::class.java]?.setScaleVector(Vector3(scale,scale,scale))
        for ((lamp, intensity) in previewLights) if (lamp.valid) {
            lamp.components[PointLightComponent::class.java]?.intensity = intensity * scale * scale * modalSize * modalSize
        }
        lastScale = scale
    }
}
