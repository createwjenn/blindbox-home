package com.jenn.blindboxworld.content

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hand.*
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import kotlinx.coroutines.delay
import kotlin.math.*

internal val LocalHands = staticCompositionLocalOf<HandTrackingProvider?> { null }

@Composable
internal fun HandSession(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val provider = remember { HandTrackingProvider() }
    LaunchedEffect(provider) {
        val permission = "com.picovr.permission.HAND_TRACKING"
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull() ?: return@LaunchedEffect
        while (provider.supportState == DataProvider.SupportState.DEVICE_NOT_SUPPORTED) delay(1000)
        if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
            runCatching { activity.packageManager.getPermissionInfo(permission, 0) }.isSuccess)
            activity.requestPermissions(arrayOf(permission), 411)
        while (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
            provider.supportState != DataProvider.SupportState.SUPPORTED) delay(1000)
        provider.start()
        try { kotlinx.coroutines.awaitCancellation() } finally { provider.stop() }
    }
    CompositionLocalProvider(LocalHands provides provider, content = content)
}

private class ToyHand(private val root: Entity) {
    // Author at hand scale so an unposed part cannot flash as a metre-wide sphere.
    private val ball = MeshResource.createSphere(.01f)
    private fun material(color: Color4, fill: Color4) = PhysicallyBasedMaterial.create().apply {
        setBaseColor(color)
        setEmissiveColor(fill)
        setRoughness(.66f)
    }
    private val skin = material(Color4(.98f,.86f,.79f,1f), Color4(.32f,.25f,.22f,1f))
    private val yellow = material(Color4(.98f,.65f,.065f,1f), Color4(.24f,.13f,.012f,1f))
    private fun part(material: PhysicallyBasedMaterial, mesh: MeshResource = ball) = Entity().also {
        it.components.set(TransformComponent())
        it.components.set(ModelComponent(mesh, material))
        root.addChild(it)
    }.components[TransformComponent::class.java]!!
    private val palm = part(skin)
    private val cuff = part(yellow, MeshResource.createCylinder(.02f,.01f))
    private val rim = part(yellow)
    private data class Segment(val from: HandJoint.Index,val to: HandJoint.Index,val radius: Float,val piece: TransformComponent)
    private val segments = buildList {
        for ((finger,radius) in listOf("THUMB" to .014f,"INDEX" to .011f,"MIDDLE" to .012f,"RING" to .011f,"LITTLE" to .009f)) {
            val chain = HandJoint.Index.values().filter { it.name.startsWith(finger) && (finger=="THUMB" || !it.name.endsWith("METACARPAL")) }
            for ((a,b) in chain.zipWithNext()) add(Segment(a,b,radius,part(skin)))
        }
    }
    private fun alignY(unit: Vector3): Quat {
        if (unit.y < -.9999f) return Quat(Vector3(1f,0f,0f), Math.PI.toFloat())
        return Quat(unit.z, 0f, -unit.x, 1f + unit.y).normalize()
    }

    fun update(pose: HandPose?, eye: Vector3?) {
        if (pose == null || eye == null || pose.handJoints.size != 26) {
            root.enabled = false
            return
        }
        fun joint(i: HandJoint.Index) = pose[i].position
        val wrist = joint(HandJoint.Index.WRIST)
        val palmAxis = joint(HandJoint.Index.MIDDLE_PROXIMAL) - wrist
        val breadth = joint(HandJoint.Index.INDEX_PROXIMAL) - joint(HandJoint.Index.LITTLE_PROXIMAL)
        val length = palmAxis.length()
        val width = breadth.length()
        // Some simulated poses include a plausible wrist but zero or collapsed fingers.
        if (length !in .042f.. .14f || width !in .029f.. .105f ||
            pose.handJoints.any { (it.position - eye).length() !in .10f..1.65f } ||
            segments.any { (joint(it.to) - joint(it.from)).length() !in .002f.. .085f }) {
            root.enabled = false
            return
        }
        val parent = root.getParent() ?: return
        val transform = root.components[TransformComponent::class.java] ?: return
        transform.setPosition(parent.convertPositionFrom(wrist, null))
        transform.setQuaternion(parent.convertRotationFrom(Quat.identity(), null))
        val along = palmAxis / length
        val up = alignY(along)
        val across = up.conjugate().rotateVector(breadth)
        val rotation = up * Quat(Vector3(0f,1f,0f), atan2(-across.z, across.x))
        palm.setPosition(palmAxis * .53f)
        palm.setQuaternion(rotation)
        palm.setScaleVector(Vector3(width * 68f, length * 65f, width * 34f))
        cuff.setPosition(along * -.023f)
        cuff.setQuaternion(rotation)
        cuff.setScaleVector(Vector3(width * 64f, 2.4f, width * 52f))
        rim.setPosition(along * -.002f)
        rim.setQuaternion(rotation)
        rim.setScaleVector(Vector3(width * 66f, .90f, width * 54f))
        for (segment in segments) {
            val a = joint(segment.from) - wrist
            val b = joint(segment.to) - wrist
            val d = b - a
            val span = d.length()
            segment.piece.setPosition((a + b) * .5f)
            segment.piece.setQuaternion(alignY(d / span))
            segment.piece.setScaleVector(Vector3(segment.radius, span*.5f + segment.radius*.60f, segment.radius) * 100f)
        }
        root.enabled = true
    }
}

@Composable
internal fun RenderHands(scene: Entity?, hmd: HMDTrackingProvider?) {
    val provider = LocalHands.current
    LaunchedEffect(scene,provider) {
        if (scene == null || provider == null) return@LaunchedEffect
        val models = arrayOfNulls<ToyHand>(2)
        var previousData: HandTrackingData? = null
        var lastSample = 0L
        while (scene.valid) {
            withFrameNanos { }
            if (!scene.valid) break
            val data = provider.latestData
            val now = android.os.SystemClock.elapsedRealtime()
            if (data !== previousData) {
                previousData = data
                lastSample = now
            }
            val eye = hmd?.latestData?.hmdPose?.position?.takeIf { now - lastSample < 250 }
            for (side in 0..1) {
                val pose = if (side==0) data?.left else data?.right
                if (models[side] == null && pose != null) {
                    val anchor = Entity().apply { components.set(TransformComponent()); enabled = false }
                    scene.addChild(anchor)
                    models[side] = ToyHand(anchor)
                }
                models[side]?.update(pose, eye)
            }
        }
    }
}
