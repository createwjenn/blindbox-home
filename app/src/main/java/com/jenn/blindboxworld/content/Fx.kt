package com.jenn.blindboxworld.content

import android.util.Log
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.ParticleComponent
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.particle.ParticleBlendingMode
import com.pico.spatial.core.ecs.particle.ParticleColorGradient
import com.pico.spatial.core.ecs.particle.ParticleColorVaryingProperty
import com.pico.spatial.core.ecs.particle.ParticleEmitterShape
import com.pico.spatial.core.ecs.particle.ParticleEmitterShapeType
import com.pico.spatial.core.ecs.particle.ParticleFloatVaryingProperty
import com.pico.spatial.core.ecs.particle.ParticleIntVaryingProperty
import com.pico.spatial.core.ecs.particle.ParticleVaryingPropertyType
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/* ---------- transforms ---------- */

fun Entity.place(x: Float, y: Float, z: Float, scale: Float = 1f, yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f) {
    components[TransformComponent::class.java]?.apply {
        setPosition(Vector3(x, y, z))
        setScaleVector(Vector3(scale, scale, scale))
        setEulerAngles(EulerAngles(pitch, yaw, roll))
    }
}

fun Entity.pos(): Vector3 = components[TransformComponent::class.java]?.position ?: Vector3(0f, 0f, 0f)

/** Walks up the parent chain to find the first entity whose name starts with [prefix]. */
fun Entity.tagged(prefix: String): Entity? {
    var e: Entity? = this
    while (e != null) { if (e.getName().startsWith(prefix)) return e; e = e.getParent() }
    return null
}

/** Look + pinch target: collider + interactable + system hover highlight. Shape is in entity-local units. */
fun Entity.makeTappable(size: Vector3, centerY: Float) {
    components.set(CollisionComponent(
        collisionShape = listOf(ShapeResource.createBox(size).offsetByTranslation(Vector3(0f, centerY, 0f))),
        physicsMaterial = PhysicsMaterialResource()))
    components.set(InteractableComponent())
    components.set(HoverEffectComponent())
}

/* ---------- simple emissive geometry ---------- */

fun glowMaterial(color: Color4, additive: Boolean = false) =
    UnlitMaterial.create(if (additive) BlendingMode.ADD else BlendingMode.OPAQUE).apply {
        setBaseColor(color); setApplyToneMapping(false)
    }

fun glowSphere(radius: Float, color: Color4, additive: Boolean = false): Entity = Entity().apply {
    components.set(TransformComponent())
    components.set(ModelComponent(MeshResource.createSphere(radius), glowMaterial(color, additive)))
}

internal fun machineHighlight(radius: Float): Entity = Entity().apply {
    components.set(TransformComponent())
    components.set(ModelComponent(MeshResource.createTorus(radius, radius - .010f),
        glowMaterial(Color4(1f, .51f, .035f, 1f))))
}

fun warmLight(): Entity = Entity().apply {
    components.set(TransformComponent())
    components.set(PointLightComponent(Color4(1f, 0.72f, 0.38f, 1f), 0f, 0.9f))
}

fun Entity.setLight(intensity: Float, color: Color4? = null) {
    components[PointLightComponent::class.java]?.apply { this.intensity = intensity; color?.let { this.color = it } }
}

fun Entity.setOpacity(a: Float) {
    val c = components[OpacityControllerComponent::class.java]
    if (c == null) components.set(OpacityControllerComponent(a)) else c.opacity = a
}

/* ---------- reveal burst ---------- */

/** GPU particle burst in the rarity colour (ParticleComponent, SDK 6.2.8 mainSimulation API). */
fun particleBurst(color: Color4, count: Int): Entity? = try {
    Entity().apply {
        components.set(TransformComponent())
        components.set(ParticleComponent().apply {
            isEmitting = true
            isLoop = false
            emitterShape = ParticleEmitterShape(type = ParticleEmitterShapeType.SPHERE)
            birthRateFull = ParticleFloatVaryingProperty(ParticleVaryingPropertyType.CONSTANT, 0f, 0f)
            mainBurstCount = ParticleIntVaryingProperty(ParticleVaryingPropertyType.RANDOM, count, count + count / 3)
            mainBurstTime = 0f
            mainBurstCycles = 1
            mainSimulation.apply {
                startColor = ParticleColorVaryingProperty(ParticleVaryingPropertyType.RANDOM, color, Color4.WHITE)
                startSize = ParticleFloatVaryingProperty(ParticleVaryingPropertyType.RANDOM, 0.012f, 0.03f)
                startSpeed = ParticleFloatVaryingProperty(ParticleVaryingPropertyType.RANDOM, 0.6f, 1.6f)
                lifetime = ParticleFloatVaryingProperty(ParticleVaryingPropertyType.RANDOM, 0.9f, 1.8f)
                emitSpreadingAngle = 360f
                isMotionEnabled = true
                acceleration = Vector3(0f, -0.9f, 0f)
                isColorGradientEnabled = true
                colorGradient = ParticleColorGradient(startColor = color, endColor = Color4(color.red, color.green, color.blue, 0f))
                material.blendingMode = ParticleBlendingMode.ADDITIVE
            }
        })
    }
} catch (e: Exception) {
    Log.w("BlindboxFx", "particles unavailable, confetti only", e); null
}

/** Mesh confetti fallback/companion: small emissive orbs tweened outward with gravity. */
class Confetti(color: Color4, n: Int, private val origin: Vector3) {
    val root = Entity().apply { components.set(TransformComponent()) }
    private val bits = List(n) { i ->
        val c = if (i % 3 == 0) Color4.WHITE else color
        val e = glowSphere(if (i % 4 == 0) 0.014f else 0.009f, c)
        root.addChild(e)
        val a = i * 2.39996f
        val up = 0.35f + (i % 5) * 0.12f
        e to Vector3(cos(a) * 0.55f, 0.9f + up, sin(a) * 0.55f)
    }

    init { root.place(origin.x, origin.y, origin.z) }

    suspend fun play(seconds: Float = 1.4f) {
        tween(seconds) { k ->
            val t = k * seconds
            for ((e, v) in bits) e.place(v.x * t, v.y * t - 1.4f * t * t, v.z * t, 0.3f + (1f - k))
        }
        root.enabled = false
    }
}

/** Light beam: tall additive cylinder that flares then fades. */
fun beam(color: Color4): Entity = Entity().apply {
    components.set(TransformComponent())
    components.set(ModelComponent(MeshResource.createCylinder(1.6f, 0.11f),
        glowMaterial(Color4(color.red * 0.6f, color.green * 0.6f, color.blue * 0.6f, 1f), additive = true)))
}

/** Wall-clock tween: runs [step] with t in 0..1 over [seconds], independent of frame rate. */
suspend fun tween(seconds: Float, step: (Float) -> Unit) {
    val start = android.os.SystemClock.uptimeMillis()
    while (true) {
        val t = ((android.os.SystemClock.uptimeMillis() - start) / (seconds * 1000f)).coerceAtMost(1f)
        step(t)
        if (t >= 1f) break
        delay(16)
    }
}

fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
fun easeInOut(t: Float) = if (t < .5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2
