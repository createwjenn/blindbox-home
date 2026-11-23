package com.jenn.blindboxworld.content

import android.util.Log
import androidx.compose.runtime.withFrameNanos
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hmd.HMDPose
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

internal data class StickAxes(val x: Float = 0f, val forward: Float = 0f, val turn: Float = 0f, val pan: Float = 0f, val tilt: Float = 0f)

internal class CourtyardInput : ControllerTrackingProvider.ControllerActionListener {
    val keys = NavigationKeys()
    var orbitYaw = 0f
    var orbitPitch = 0f
    private val jump = java.util.concurrent.atomic.AtomicBoolean(false)
    private var aHeld = false
    fun requestJump() { jump.set(true) }
    fun takeJump() = jump.getAndSet(false)
    @Volatile private var physical = StickAxes()
    @Volatile var zoomAxis = 0f
        private set
    var pad = StickAxes()
    override fun onControllerAction(data: ControllerActionData) {
        val a = data.right?.aButtonPressed == true
        if (a && !aHeld) requestJump()
        aHeld = a
        zoomAxis = data.right?.thumbstickValue?.y ?: 0f
        val left = data.left?.thumbstickValue
        physical = StickAxes(left?.x ?: 0f, left?.y ?: 0f, data.right?.thumbstickValue?.x ?: 0f)
    }
    fun clear() { keys.clear(); jump.set(false); physical = StickAxes(); pad = StickAxes(); zoomAxis = 0f }

    fun sample(): StickAxes {
        val sticks = physical
        val padMoving = pad.x != 0f || pad.forward != 0f
        val keyboard = keys.sample()
        val movement = if (keyboard != null && (keyboard.x != 0f || keyboard.forward != 0f)) keyboard
            else if (padMoving) pad else sticks
        return StickAxes(movement.x, movement.forward, if (pad.turn != 0f) pad.turn else sticks.turn,
            pan = keyboard?.pan ?: 0f, tilt = keyboard?.tilt ?: 0f)
    }
}

internal fun HMDPose.headingDegrees() = rotation.horizontalHeading()
internal fun Entity.heading() = components[TransformComponent::class.java]?.quaternion?.horizontalHeading() ?: 0f
private fun Quat.horizontalHeading(): Float {
    val f = rotateVector(Vector3(0f, 0f, -1f))
    return Math.toDegrees(atan2(-f.x, -f.z).toDouble()).toFloat()
}

/** The character, scenery, pickups and water share the same capture frame. */
internal suspend fun driveWorldActor(
    input: CourtyardInput, actor: Entity, terrain: WorldTerrain, figure: FigureInfo,
    garden: Entity, wearables: WorldWearables?, wake: SwimWake, headPose: () -> HMDPose?, canDrive: () -> Boolean,
    onPickup: (Int) -> Unit,
) {
    val playback = mutableListOf<AnimationPlaybackController>()
    val provider = ControllerTrackingProvider()
    val neckDepth = figure.swimNeckHeight?.let { it * ACTOR_HEIGHT / figure.actorRigHeight / WORLD_SCALE }
    val walker = WorldWalker(terrain, neckDepth)
    input.orbitYaw = 0f
    input.orbitPitch = 0f
    var swimTime = 0f
    var footY = walker.support
    var lift = 0f
    var velocity = 0f
    var facing = 0f
    var turnReady = true
    var currentMotion = ""
    try {
        // Bind the visible rig during the welcome countdown so play starts with controls ready.
        var meshes = actor.findSkinnedMeshEntity(true)
        for (attempt in 0 until 30) {
            if (meshes.isNotEmpty()) break
            kotlinx.coroutines.delay(100)
            meshes = actor.findSkinnedMeshEntity(true)
        }
        val rigs = meshes.map { mesh ->
            val clips = mesh.getAnimationResources().associateBy { it.getName() }
            Log.i("WorldControls", "Rig clips: ${clips.keys}")
            mesh to clips
        }
        val swimMasks = if (figure.id in setOf("secret-capybara-toilet", "niu-lai", "tralalero-tralala"))
            meshes.flatMap { it.components[ModelComponent::class.java]?.materials?.filterIsInstance<PhysicallyBasedMaterial>().orEmpty() }
        else emptyList()
        var lastMask = -1
        provider.addControllerActionListener(input)
        val tracking = provider.start()
        Log.i("WorldControls", "Controller tracking=$tracking, skinned meshes=${rigs.size}, waterline materials=${swimMasks.size}")
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val frameSeconds = (now - previous) * 1e-9f
            val dt = frameSeconds.coerceIn(0f, .25f)
            previous = now
            val axes = if (canDrive()) input.sample() else StickAxes()
            val head = headPose()
            if (abs(axes.turn) < .35f) turnReady = true
            val snap = if (turnReady && abs(axes.turn) > .68f) {
                turnReady = false
                30f * sign(axes.turn)
            } else 0f
            input.orbitYaw = (input.orbitYaw + snap + axes.pan * 80f * dt) % 360f
            input.orbitPitch = (input.orbitPitch + axes.tilt * 55f * dt).coerceIn(-45f, 35f)
            val magnitude = hypot(axes.x, axes.forward)
            val strength = ((magnitude - .16f) / .84f).coerceIn(0f, 1f)
            var speed = 0f
            if (strength > 0f && dt > 0f) {
                val heading = if (head == null) -input.orbitYaw
                    else garden.convertRotationFrom(head.rotation, null).horizontalHeading()
                val (dx, dz) = rotY(axes.x / magnitude, -axes.forward / magnitude, heading)
                val oldX = walker.x
                val oldZ = walker.z
                walker.advance(dx, dz, strength, dt, lift, onPickup)
                speed = hypot(walker.x - oldX, walker.z - oldZ) * WORLD_SCALE / dt
                if (speed > .01f) {
                    val target = Math.toDegrees(atan2(walker.x - oldX, walker.z - oldZ).toDouble()).toFloat()
                    val delta = ((target - facing + 540f) % 360f) - 180f
                    facing = (facing + delta.coerceIn(-500f * dt, 500f * dt)) % 360f
                }
            }
            val swimming = walker.swimming
            val immersion = if (swimming && neckDepth != null) (walker.waterDepth / neckDepth).coerceIn(0f, 1f) else 0f
            val support = walker.support
            if (swimming) { lift = 0f; velocity = 0f; swimTime += dt }
            val wasAirborne = lift > 0f
            if (input.takeJump() && canDrive() && !wasAirborne && !swimming) velocity = 3.4f
            if (canDrive() && (wasAirborne || velocity > 0f)) {
                lift = (lift + velocity * dt - 4.9f * dt * dt).coerceAtLeast(0f)
                velocity = if (lift == 0f) 0f else velocity - 9.8f * dt
            }
            if (canDrive() && (lift != 0f || speed == 0f)) walker.collect(lift, onPickup)
            if (swimMasks.isNotEmpty()) {
                // Preserve a little colour beneath the surface. Follow the rendered body's
                // eased descent at the bank, including its return to shore.
                val submerged = if (swimming) (walker.ground + walker.waterDepth - footY) * WORLD_SCALE else 0f
                val cutoff = ((submerged - .035f).coerceAtLeast(0f) / ACTOR_HEIGHT * 254f).toInt().coerceIn(0, 254)
                if (cutoff != lastMask) {
                    swimMasks.forEach { it.setOpacityThreshold(cutoff / 255f) }
                    lastMask = cutoff
                }
            }
            val moving = speed > .01f
            wake.x = walker.x
            wake.z = walker.z
            wake.level = terrain.cells[walker.cellIndex].water
            wake.immersion = immersion
            wake.moving = moving
            val motion = when {
                swimming -> if (moving) "Swim" else "Float"
                lift > 0f -> "Jump"
                speed >= .80f -> "Run"
                moving -> "Walk"
                else -> "Idle"
            }
            val motionChanged = motion != currentMotion
            if (motionChanged) {
                for (player in playback) { player.stop(); player.close() }
                playback.clear()
                for ((mesh, clips) in rigs) {
                    val clip = when (motion) {
                        "Idle", "Float", "Swim" -> clips["Walk"]
                        "Jump" -> clips["Jump"] ?: clips["Run"]
                        else -> clips[motion]
                    } ?: continue
                    val player = mesh.playAnimation(clip)
                    if (motion == "Idle" || motion == "Float") { player.setTime(0f); player.pause() }
                    playback += player
                }
                Log.d("WorldControls", "Motion=$motion players=${playback.size}")
                currentMotion = motion
            }
            val rate = if (moving && motion != "Jump") {
                val cadence = when (motion) { "Swim" -> .55f; "Run" -> speed / 1.15f; else -> speed / .32f }
                cadence.coerceIn(.45f, 2f)
            } else 1f
            if (moving && motion != "Jump") playback.forEach { it.setSpeed(rate) }
            if (playback.isNotEmpty()) wearables?.pose(motion, motionChanged, frameSeconds, rate)
            val origin = terrain.origin
            if (moving || swimming || wasAirborne || lift > 0f || abs(support - footY) > .001f) {
                // Stay on top of a rising bank. Smoothing is only needed on descent.
                footY = maxOf(support, footY + (support - footY) * (dt * 14f).coerceAtMost(1f))
                val bob = immersion * sin(swimTime * 3.4f) * .015f
                actor.place(origin.x + walker.x * WORLD_SCALE, origin.y + footY * WORLD_SCALE + lift + bob,
                    origin.z + walker.z * WORLD_SCALE, ACTOR_HEIGHT / figure.actorRigHeight, yaw = facing,
                    pitch = if (moving) immersion * 11f else 0f, roll = immersion * sin(swimTime * 2.4f) * 3f)

            }
        }
    } finally {
        provider.removeControllerActionListener(input)
        provider.stop()
        input.clear()
        for (clip in playback) clip.close()
    }
}
