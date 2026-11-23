package com.jenn.blindboxworld.content

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.body.BodyJoint
import com.pico.spatial.tracking.body.BodyTrackingProvider
import com.pico.spatial.tracking.eye.EyeTrackingProvider
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDPose
import kotlin.math.sqrt

/** Read transient tracking poses only while the player is positioning an item. */
internal class PlacementPointer(private val motion: RoomMotion) {
    private val hand = HandTrackingProvider()
    private val eyes = EyeTrackingProvider()
    private val body = BodyTrackingProvider
    private val sessions = mutableListOf<DataProvider<*>>()
    private val forward = Vector3(0f, 0f, -1f)

    private val optionalSensors = mapOf<DataProvider<*>, String>(
        hand to "com.picovr.permission.HAND_TRACKING",
        eyes to "com.picovr.permission.EYE_TRACKING",
    )
    private var activity: Activity? = null
    var canEnableSensors by mutableStateOf(false)
        private set

    private fun startOnce(provider: DataProvider<*>) {
        if (provider !in sessions && runCatching { provider.start() }.isSuccess) sessions.add(provider)
    }

    fun refresh(host: Activity?) {
        activity = host
        var canEnable = false
        for ((provider, permission) in optionalSensors) {
            if (provider in sessions || provider.supportState == DataProvider.SupportState.DEVICE_NOT_SUPPORTED) continue
            if (host?.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) startOnce(provider)
            else if (host != null) canEnable = true
        }
        canEnableSensors = canEnable
        if (body.supportState == DataProvider.SupportState.SUPPORTED) startOnce(body)
    }

    fun enableSensors() {
        val host = activity ?: return
        val permissions = mutableListOf<String>()
        for ((provider, permission) in optionalSensors) {
            if (provider in sessions || provider.supportState == DataProvider.SupportState.DEVICE_NOT_SUPPORTED) continue
            // Some ROMs broker tracking consent in a spatial system panel instead of PackageManager.
            if (runCatching { host.packageManager.getPermissionInfo(permission, 0) }.isSuccess &&
                host.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) permissions.add(permission)
            else startOnce(provider)
        }
        if (permissions.isNotEmpty()) host.requestPermissions(permissions.toTypedArray(), 410)
    }

    fun stop() {
        sessions.forEach { runCatching { it.stop() } }
        sessions.clear()
        activity = null
        canEnableSensors = false
    }

    private fun aim(position: Vector3, rotation: Quat): Pair<Vector3, Vector3>? {
        val d = rotation.rotateVector(forward)
        if (!position.x.isFinite() || !position.y.isFinite() || !position.z.isFinite() ||
            !(d.x*d.x + d.y*d.y + d.z*d.z in .90f..1.1f)) return null
        return position to d
    }

    fun ray(head: HMDPose): Pair<Vector3, Vector3> {
        hand.latestData?.let { h ->
            val pointing = listOfNotNull(h.rightAim, h.leftAim).filter { it.isReady }.maxByOrNull { it.aimValue }
            pointing?.aimPose?.let { aim(it.position, it.rotation) }?.let { return it }
        }
        motion.trackedController?.latestData?.let { c ->
            (c.right ?: c.left)?.let { aim(it.position, it.rotation) }?.let { return it }
        }
        body.latestData?.bodyPose?.bodyJoints?.let { joints ->
            // Only interpret an extended arm as intent. Relaxing it restores gaze aiming.
            fun arm(shoulder: BodyJoint.Index, elbow: BodyJoint.Index, wrist: BodyJoint.Index): Pair<Vector3, Vector3>? {
                val s = joints.firstOrNull { it.index == shoulder }?.position ?: return null
                val e = joints.firstOrNull { it.index == elbow }?.position ?: return null
                val w = joints.firstOrNull { it.index == wrist }?.position ?: return null
                val x = w.x-e.x; val y = w.y-e.y; val z = w.z-e.z
                val reachX = w.x-s.x; val reachZ = w.z-s.z
                val length = sqrt(x*x+y*y+z*z)
                if (reachX*reachX + reachZ*reachZ < .35f*.35f || length !in 0.12f..0.48f) return null
                return w to Vector3(x/length,y/length,z/length)
            }
            arm(BodyJoint.Index.RIGHT_SHOULDER,BodyJoint.Index.RIGHT_ELBOW,BodyJoint.Index.RIGHT_WRIST)?.let { return it }
            arm(BodyJoint.Index.LEFT_SHOULDER,BodyJoint.Index.LEFT_ELBOW,BodyJoint.Index.LEFT_WRIST)?.let { return it }
        }
        eyes.latestData?.eyePose?.let { aim(it.position, it.rotation) }?.let { return it }
        return head.position to head.rotation.rotateVector(forward)
    }
}

@Composable
internal fun rememberPlacementPointer(active: Boolean, motion: RoomMotion): PlacementPointer {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    }
    val pointer = remember(motion) { PlacementPointer(motion) }
    LaunchedEffect(pointer, active, activity) {
        while (active) {
            pointer.refresh(activity)
            delay(750)
        }
    }
    DisposableEffect(pointer, active) {
        onDispose { if (active) pointer.stop() }
    }
    return pointer
}
