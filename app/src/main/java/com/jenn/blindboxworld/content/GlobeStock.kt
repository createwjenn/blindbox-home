package com.jenn.blindboxworld.content

import androidx.compose.runtime.withFrameNanos
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

internal class GlobeStock private constructor(
    private val mix: GlobeMix,
    private val transforms: List<TransformComponent>,
) {
    companion object {
        suspend fun mount(machine: Entity): GlobeStock {
            val mix = GlobeMix()
            val prototypes = mutableMapOf<GlobeMix.Kind, Entity>()
            val transforms = mix.pieces.map { p ->
                val prototype = prototypes[p.kind]
                val entity = if (prototype == null) {
                    Entity.loadSuspend("asset://props/${p.kind.model}.glb").also { prototypes[p.kind] = it }
                } else prototype.clone(Entity.CloneOptions(true, true))
                    ?: Entity.loadSuspend("asset://props/${p.kind.model}.glb")
                machine.addChild(entity)
                entity.components[TransformComponent::class.java]!!.apply {
                    setScaleVector(Vector3(p.kind.scale,p.kind.scale,p.kind.scale))
                }
            }
            return GlobeStock(mix, transforms).also { it.render() }
        }
    }

    private fun render() {
        mix.pieces.forEachIndexed { i, p ->
            transforms[i].setPosition(Vector3(p.x, GlobeMix.CENTER_Y + p.y, p.z))
            transforms[i].setEulerAngles(EulerAngles(p.pitch,p.yaw,0f))
        }
    }

    private var moving = false

    /** The paid coin powers the jet until the crank releases the prize. */
    suspend fun animate(powered: Boolean) {
        if (powered) {
            moving = true
            mix.start()
        } else if (!moving) return
        var simulated = 0f
        var accumulator = 0f
        var last = withFrameNanos { it }
        while (powered || simulated < GlobeMix.SETTLE_SECONDS) {
            val now = withFrameNanos { it }
            accumulator += ((now-last)*1e-9f).coerceAtMost(.08f)
            last = now
            while (accumulator >= GlobeMix.DT) {
                mix.step(simulated, powered)
                simulated += GlobeMix.DT
                accumulator -= GlobeMix.DT
            }
            render()
        }
        moving = false
    }
}
