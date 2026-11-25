package com.jenn.blindboxworld.content

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hmd.HMDPose
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

internal data class LoftView(val x: Float, val y: Float, val z: Float, val heading: Float)

// The entrance slab in collection_room.py is at (-1.88, +4.28) in glTF X/Z.
internal val doorwayView = LoftView(-1.88f, 1.55f, 3.83f, -15f)
internal val plinthView = LoftView(0f, 1.35f, -.06f, 0f)

/** The 6.2 Stage converter returns metres relative to the SpatialView's origin.
 * Apply the entity transform separately so locomotion and head-locked controls use their own space. */
internal fun SpatialViewContent.localHitPosition(viewPixels: Vector3, target: Entity): Vector3? {
    val viewRoot = localSpatialCoordinateSpace
    if (viewRoot !is Entity) return null
    val inView = convertPosition(viewPixels, ViewCoordinateSpace.Local, viewRoot)
    return target.convertPositionFrom(inView, viewRoot)
}

internal fun Entity.releaseScene() {
    if (!valid) return
    enabled = false
    removeFromParent()
    destroy()
}

/** Points within a station's front approach area select a full-object view. */
internal fun inStationApproach(x: Float, z: Float, spot: RoomSpot, machine: Boolean): Boolean {
    val (across, front) = rotY(x - spot.x, z - spot.z, if (machine) -GACHA_YAW else 0f)
    return abs(across) <= (if (machine) .95f else .79f) && front in -.15f..1.95f
}

/** If the line of sight to a floor hit passes through an exhibit, stop on its viewing side. */
internal fun stationOccludesFloor(eye: RoomSpot, floor: FloorPoint, station: RoomSpot, machine: Boolean): Boolean {
    val dx = floor.x - eye.x
    val dz = floor.z - eye.z
    val squaredLength = dx * dx + dz * dz
    if (squaredLength < .01f) return false
    val t = ((station.x - eye.x) * dx + (station.z - eye.z) * dz) / squaredLength
    if (t !in 0f..1f) return false
    val cross = hypot(eye.x + dx * t - station.x, eye.z + dz * t - station.z)
    val height = eye.y * (1f - t) - station.y
    return cross <= (if (machine) .51f else .31f) && height in 0f..(if (machine) 1.83f else 1.21f)
}

/** Put the shortcut on clear floor beside the station, including after furniture is moved. */
internal fun stationPad(spot: RoomSpot, machine: Boolean, yaw: Float): LoftView? {
    val clearance = if (machine) 1.12f else .90f
    for (radius in listOf(clearance, clearance + .4f, clearance + .8f)) {
        for (offset in listOf(0f, 35f, -35f, 70f, -70f, 110f, -110f, 180f)) {
            val heading = yaw + offset
            val (dx, dz) = rotY(0f, radius, heading)
            val x = spot.x + dx
            val z = spot.z + dz
            if (RoomFloor.isWalkable(FloorPoint(x,z))) return LoftView(x, 1.55f, z, heading)
        }
    }
    return null
}

internal fun stationView(spot: RoomSpot, machine: Boolean): LoftView =
    shortcutView(if (machine) "machine" else "stand", DecorPose(spot)) ?: doorwayView

internal fun shortcutView(id: String, pose: DecorPose): LoftView? = when (id) {
    "shop" -> stationViewingSpot(pose.at, pose.yaw, 1.85f)
    "machine" -> stationViewingSpot(pose.at, GACHA_YAW + pose.yaw, 2.56f)
    "stand" -> stationViewingSpot(pose.at, pose.yaw, 2.42f)
    "record-player" -> stationViewingSpot(pose.at, pose.yaw, 1.80f)
    else -> null
}

private fun stationViewingSpot(spot: RoomSpot, heading: Float, distance: Float): LoftView? {
    // Prefer the original front. A relocated object near a wall may need an angled approach.
    for (radius in listOf(distance, distance - .18f, distance - .35f)) {
        for (offset in listOf(0f, -15f, 15f, -30f, 30f, -50f, 50f, -75f, 75f, -100f, 100f, -145f, 145f, 180f)) {
            val angle = heading + offset
            val (dx, dz) = rotY(0f, radius, angle)
            val point = FloorPoint(spot.x + dx, spot.z + dz)
            if (RoomFloor.isWalkable(point)) return LoftView(point.x, 1.55f, point.z, angle)
        }
    }
    return null
}

internal fun Entity.alignView(view: LoftView, pose: HMDPose?): Float {
    val yaw = (pose?.headingDegrees() ?: 0f) - view.heading
    place(0f, 0f, 0f, yaw = yaw)
    val eye = convertPositionFrom(pose?.position ?: Vector3(0f, 1.55f, 0f), null)
    val (dx, dz) = rotY(eye.x - view.x, eye.z - view.z, yaw)
    place(dx, eye.y - view.y, dz, yaw = yaw)
    return yaw
}

/** Scroll input is accumulated between render frames. The emulator can also dolly the tracked head. */
internal object ViewZoom {
    private var distance = 0f
    @Synchronized fun scroll(ticks: Float) { distance = (distance + ticks * .30f).coerceIn(-3f, 3f) }
    @Synchronized fun clear() { distance = 0f }
    @Synchronized fun advance(dt: Float): Float {
        val step = distance.coerceIn(-3.5f * dt, 3.5f * dt)
        distance -= step
        return step
    }
}

private fun axis(value: Float) = sign(value) * ((abs(value) - .16f) / .84f).coerceIn(0f, 1f)

internal enum class Interior(val x: ClosedFloatingPointRange<Float>, val y: ClosedFloatingPointRange<Float>,
                             val z: ClosedFloatingPointRange<Float>) {
    LOFT(-4.05f..3.98f, .25f..3.76f, -4.12f..4.04f),
    LANDING(-3.16f..3.16f, .25f..3.00f, -2.72f..1.80f),
}

private fun moveViewer(root: Entity, from: Vector3, to: Vector3) {
    if (abs(from.x - to.x) + abs(from.z - to.z) + abs(from.y - to.y) < .0001f) return
    val transform = root.components[TransformComponent::class.java] ?: return
    transform.setPosition(root.pos() + transform.quaternion.rotateVector(from - to))
}

/** Apply pitch about the horizontal axis of the tracked view, including when looking sideways. */
private fun viewRotation(yaw: Float, headYaw: Float, pitch: Float): Quat =
    EulerAngles(0f, headYaw, 0f).toQuat() * EulerAngles(pitch, 0f, 0f).toQuat() *
        EulerAngles(0f, yaw - headYaw, 0f).toQuat()

private const val WALK_SPEED = 1.9f
private const val TURN_SPEED = 80f

internal class RoomMotion {
    var trackedController: ControllerTrackingProvider? = null
    internal val input = CourtyardInput()

    // Position is sampled by floor actions. Only command/arrival state belongs to Compose.
    var position: FloorPoint? = null
        internal set
    var heading: Float = 0f
        private set
    var goal: FloorPoint? = null
        private set
    var lastArrival: FloorPoint? = null
        private set
    var status by mutableStateOf("Ready")
        private set

    private val path = ArrayDeque<FloorPoint>()
    private var stepX = 0f
    private var stepForward = 0f
    private var stepTurn = 0f
    private var replanned = false
    private var analogMoving = false
    val isNavigating: Boolean get() = analogMoving || goal != null
    private var reportManualStop = false

    private fun describe(point: FloorPoint) = String.format(Locale.ROOT, "%.2f, %.2f", point.x, point.z)

    // Station framing has a finite turn budget. Sampling the live view as an error
    // every frame would rotate the loft against someone looking over their shoulder.
    private var stationTurnRemaining = 0f
    val framingTurnComplete get() = stationTurnRemaining == 0f
    private var stationHeadYaw: Float? = null
    var homeFraming = false
        private set

    fun returnHome() {
        homeFraming = walkTo(doorwayView.x, doorwayView.z, doorwayView.heading)
    }

    fun finishHomeFraming() { homeFraming = false }

    fun walkTo(x: Float, z: Float, facing: Float? = null): Boolean {
        homeFraming = false
        val from = position ?: return false
        val target = FloorPoint(x, z)
        if (!x.isFinite() || !z.isFinite() || !RoomFloor.isWalkable(target)) return false
        val distance = hypot(x - from.x, z - from.z)
        val waypoints = RoomFloor.route(from, target)
        if (distance > .015f && waypoints.isEmpty()) return false
        clearSteps()
        reportManualStop = false
        lastArrival = null
        path.clear()
        path.addAll(waypoints)
        goal = target
        stationTurnRemaining = facing?.let { signedAngle(heading - it) } ?: 0f
        stationHeadYaw = null
        ViewZoom.clear()
        replanned = false
        if (distance <= .015f) arrived(from) else status = "Walking to ${describe(target)}"
        return true
    }

    fun cancelWalk() {
        homeFraming = false
        stationTurnRemaining = 0f
        stationHeadYaw = null
        if (goal != null) status = "Walk canceled"
        path.clear()
        goal = null
        reportManualStop = false
        clearSteps()
    }

    private fun signedAngle(degrees: Float) = ((degrees % 360f + 540f) % 360f) - 180f

    fun steerTowardStation(trackedHeadYaw: Float, dt: Float): Float {
        if (stationTurnRemaining == 0f) return 0f
        val initialYaw = stationHeadYaw ?: trackedHeadYaw.also { stationHeadYaw = it }
        if (abs(signedAngle(trackedHeadYaw - initialYaw)) >= 4f) {
            stationTurnRemaining = 0f
            return 0f
        }
        val step = stationTurnRemaining.coerceIn(-TURN_SPEED * dt, TURN_SPEED * dt)
        stationTurnRemaining -= step
        return step
    }

    /** Discrete equivalents for accessible clicks and directional pad taps. */
    fun nudge(x: Float = 0f, forward: Float = 0f, turn: Float = 0f): Boolean {
        if (position == null) return false
        cancelWalk()
        status = if (turn != 0f) "Turning" else "Stepping"
        reportManualStop = true
        stepX = (stepX + x * .40f).coerceIn(-2f, 2f)
        stepForward = (stepForward + forward * .40f).coerceIn(-2f, 2f)
        stepTurn = (stepTurn + turn * 20f).coerceIn(-90f, 90f)
        return true
    }

    private fun clearSteps() { stepX = 0f; stepForward = 0f; stepTurn = 0f }

    internal fun controls(dt: Float, enableTurn: Boolean): StickAxes {
        val axes = input.sample()
        val x = axis(axes.x)
        val forward = axis(axes.forward)
        val turn = if (enableTurn) (axis(axes.turn) + axes.pan).coerceIn(-1f, 1f) else 0f
        val tilt = if (enableTurn) (axes.tilt + axis(input.zoomAxis)).coerceIn(-1f,1f) else 0f
        val zoom = 0f // The right stick controls view pitch in the room, not forward motion.
        val wheel = ViewZoom.advance(dt)
        val walking = x != 0f || forward != 0f || turn != 0f || tilt != 0f || zoom != 0f || wheel != 0f
        if (walking) {
            cancelWalk()
            if (!analogMoving) status = "Moving"
            reportManualStop = true
        }
        analogMoving = walking
        val stride = WALK_SPEED * dt
        val rotation = TURN_SPEED * dt
        val sx = stepX.coerceIn(-stride, stride)
        val sf = stepForward.coerceIn(-stride, stride)
        val st = stepTurn.coerceIn(-rotation, rotation)
        stepX -= sx; stepForward -= sf; stepTurn -= st
        val combined = (forward + zoom).coerceIn(-1f, 1f)
        val length = hypot(x, combined).coerceAtLeast(1f)
        return StickAxes(x * stride / length + sx, combined * stride / length + sf + wheel, turn * rotation + st, tilt = tilt * 55f * dt)
    }

    internal fun observe(at: FloorPoint, facing: Float) {
        position = at
        heading = facing
        if (reportManualStop && !analogMoving && stepX == 0f && stepForward == 0f && stepTurn == 0f) {
            reportManualStop = false
            status = String.format(Locale.ROOT, "At %.2f, %.2f, heading %.0f°", at.x, at.z, facing)
        }
    }

    internal fun follow(from: FloorPoint, distance: Float): FloorPoint {
        val target = goal ?: return from
        var current = from
        var remaining = distance
        while (remaining > 0f && path.isNotEmpty()) {
            val waypoint = path.first()
            val dx = waypoint.x - current.x
            val dz = waypoint.z - current.z
            val length = hypot(dx, dz)
            if (length < .001f) { path.removeFirst(); continue }
            val step = minOf(length, remaining)
            val x = dx * step / length
            val z = dz * step / length
            val next = RoomFloor.step(current, x, z)
            val missed = hypot(next.x - (current.x + x), next.z - (current.z + z))
            current = next
            if (missed > .003f) {
                val replacement = if (replanned) emptyList() else RoomFloor.route(current, target)
                if (replacement.isEmpty()) cancelWalk() else {
                    path.clear()
                    path.addAll(replacement)
                    replanned = true
                }
                return current
            }
            remaining -= step
            if (step == length) { path.removeFirst(); replanned = false }
        }
        if (path.isEmpty() && hypot(current.x - target.x, current.z - target.z) <= .015f) arrived(current)
        return current
    }

    private fun arrived(at: FloorPoint) {
        path.clear()
        lastArrival = at
        goal = null
        status = "Arrived at ${describe(at)}"
    }
}

internal suspend fun navigateInterior(
    root: Entity, interior: Interior, rootYaw: () -> Float, pose: () -> HMDPose?,
    motion: RoomMotion = RoomMotion(), onYawChanged: (Float) -> Unit = {}, onStepBack: () -> Unit = {},
) {
    val provider = runCatching { ControllerTrackingProvider() }.getOrNull()
    ViewZoom.clear()
    try {
        // Physical controllers are optional on the emulator and during hand-only interaction.
        runCatching {
            motion.trackedController = provider
            provider?.addControllerActionListener(motion.input)
            provider?.start()
        }
        var previous = withFrameNanos { it }
        var lastRoot: Vector3? = null
        var lastYaw = 0f
        var pitch = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - previous) * 1e-9f).coerceIn(0f, .05f)
            previous = now
            if (!root.valid) break
            val head = pose()
            if (motion.input.takeExit() && head != null) onStepBack()
            if (head == null) {
                motion.position = null
                motion.cancelWalk()
                ViewZoom.clear()
                lastRoot = null
                continue
            }
            var yaw = rootYaw()
            val local = root.convertPositionFrom(head.position, null)
            val current = FloorPoint(local.x, local.z)
            val origin = root.pos()
            val lastOrigin = lastRoot
            val aligned = lastOrigin != null && abs(yaw - lastYaw) < .001f &&
                abs(origin.x - lastOrigin.x) < .001f && abs(origin.z - lastOrigin.z) < .001f
            val last = motion.position
            val inLoft = interior == Interior.LOFT
            // Track physical steps through the same floor obstacles; explicit camera alignments
            // establish a new starting location (door, stand, machine, or world return).
            val from = if (!inLoft) current else if (aligned && last != null) {
                RoomFloor.step(last, current.x - last.x, current.z - last.z)
            } else {
                if (last != null) motion.cancelWalk()
                RoomFloor.step(current, 0f, 0f)
            }
            val controls = motion.controls(dt, inLoft)
            val turn = controls.turn + motion.steerTowardStation(head.headingDegrees(), dt)
            val level = if (motion.homeFraming) pitch.coerceIn(-55f * dt, 55f * dt) else 0f
            if (turn != 0f || controls.tilt != 0f || level != 0f) {
                // Turn the room around the tracked eyes, preserving the viewer's local position.
                yaw = (yaw + turn) % 360f
                pitch = (pitch - controls.tilt - level).coerceIn(-55f, 65f)
                root.components[TransformComponent::class.java]?.setQuaternion(viewRotation(yaw, head.headingDegrees(), pitch))
                moveViewer(root, root.convertPositionFrom(head.position, null), local)
                onYawChanged(yaw)
            }
            val (dx, dz) = rotY(controls.x, -controls.forward, head.headingDegrees() - yaw)
            val next = when {
                !inLoft -> FloorPoint((from.x + dx).coerceIn(interior.x), (from.z + dz).coerceIn(interior.z))
                motion.goal != null -> motion.follow(from, WALK_SPEED * dt)
                dx != 0f || dz != 0f -> RoomFloor.step(from, dx, dz)
                else -> from
            }
            val height = if (motion.homeFraming)
                local.y + (doorwayView.y - local.y).coerceIn(-dt, dt) else local.y
            moveViewer(root, local, Vector3(next.x, height.coerceIn(interior.y), next.z))
            if (motion.homeFraming && motion.goal == null && pitch == 0f && abs(height - doorwayView.y) < .001f)
                motion.finishHomeFraming()
            motion.observe(next, head.headingDegrees() - yaw)
            lastYaw = yaw
            lastRoot = root.pos()
        }
    } finally {
        runCatching { provider?.removeControllerActionListener(motion.input) }
        motion.trackedController = null
        runCatching { provider?.stop() }
        motion.input.clear()
        motion.cancelWalk()
        motion.position = null
        ViewZoom.clear()
    }
}

/** Follow translation from a fixed tracking-space anchor. Head motion never orbits the world.
 * Shorten the camera boom at capture boundaries while preserving tracked head rotation. */
internal suspend fun followWorldActor(root: Entity, garden: Entity, actor: Entity, terrain: WorldTerrain, pose: () -> HMDPose?,
                                      input: CourtyardInput, actorCell: () -> Int?) {
    val gardenTransform = garden.components[TransformComponent::class.java] ?: return
    ViewZoom.clear()
    var anchor: Vector3? = null
    var entryYaw = 0f
    var cameraYaw = input.orbitYaw
    var distance = 2.35f
    var previous = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val dt = ((now - previous) * 1e-9f).coerceIn(0f, .05f)
        previous = now
        val entry = pose() ?: continue
        if (anchor == null) {
            anchor = entry.position
            entryYaw = entry.headingDegrees()
        }
        distance = (distance - axis(input.zoomAxis) * 2f * dt - ViewZoom.advance(dt)).coerceIn(1.25f, 3.5f)
        val inspected = WorldVerification.focus
        val feet = inspected ?: actor.pos()
        val focus = inspected ?: (feet + Vector3(0f, ACTOR_HEIGHT * .5f, 0f))
        val deck = if (inspected == null) actorCell() else null
        val (dx, dz) = rotY(0f, -distance, entryYaw)
        val desired = root.convertPositionFrom(anchor + Vector3(dx, 0f, dz), null)
        // The tracked offset includes physical leaning and zoom, even with a stationary actor.
        val offset = root.convertPositionFrom(entry.position, null) - desired
        val pitch = 16f - input.orbitPitch
        fun rotation(yaw: Float) = EulerAngles(pitch, yaw, 0f).toQuat()
        fun clearance(yaw: Float): Float {
            val eye = focus + rotation(yaw).conjugate().rotateVector(offset)
            return (terrain.cameraEye(eye, feet, deck) - focus).length()
        }
        // When a wall would push the lens into the actor, favour the closest
        // open orbit angle. Retain the previous side around symmetrical corners.
        fun angleDelta(to: Float, from: Float) = ((to - from + 180f).mod(360f)) - 180f
        val preferred = input.orbitYaw
        val minimum = minOf(1.55f, offset.length() * .9f)
        var aim = preferred
        var best = clearance(preferred)
        if (best < minimum) {
            for (step in 1..12) {
                for (side in listOf(-1, 1)) {
                    val candidate = preferred + side * step * 15f
                    val score = minOf(minimum, clearance(candidate)) -
                        (.001f * step * 15f + .0015f * abs(angleDelta(candidate,cameraYaw)))
                    if (score > best) { best = score; aim = candidate }
                }
            }
        }
        cameraYaw = (cameraYaw + angleDelta(aim,cameraYaw).coerceIn(-85f*dt,85f*dt)) % 360f
        val orientation = rotation(cameraYaw)
        gardenTransform.setQuaternion(orientation)
        val eye = focus + orientation.conjugate().rotateVector(offset)
        val safe = terrain.cameraEye(eye, feet, deck)
        moveViewer(root, desired, garden.convertPositionTo(focus + (safe - eye), root))
    }
}
