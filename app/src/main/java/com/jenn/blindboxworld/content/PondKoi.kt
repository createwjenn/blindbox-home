package com.jenn.blindboxworld.content

import android.content.Context
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val KOI_FOLDER = "worlds/secret-capybara-toilet"

internal class PondKoi private constructor(private val level: Float, private val fish: List<Fish>) {
    private data class Fish(
        val x: Float, val z: Float, val radius: Float, val speed: Float, val phase: Float,
        val body: TransformComponent, val tail: TransformComponent,
    )

    // Reproduce the website's individual swimming lanes and independently hinged tail beats.
    fun update(seconds: Float) {
        fish.forEachIndexed { index, f ->
            val angle = seconds * f.speed + f.phase
            val x = f.x + cos(angle) * f.radius
            val z = f.z + sin(angle) * f.radius * .7f
            val y = level + .014f + sin(seconds * 1.6f + index) * .005f
            val yaw = atan2(-sin(angle), cos(angle) * .7f)
            val degrees = 180f / Math.PI.toFloat()
            f.body.setPosition(Vector3(x, y, z))
            f.body.setEulerAngles(EulerAngles(0f, yaw * degrees, sin(seconds * 3f + index) * .025f * degrees))
            f.tail.setPosition(Vector3(x - sin(yaw) * .16f, y, z - cos(yaw) * .16f))
            f.tail.setEulerAngles(EulerAngles(0f, (yaw + sin(seconds * 5f + index) * .35f) * degrees, 0f))
        }
    }

    companion object {
        suspend fun attach(context: Context, world: Entity): PondKoi {
            val spec = withContext(Dispatchers.IO) {
                context.assets.open("$KOI_FOLDER/koi-paths.json").bufferedReader().use { JSONObject(it.readText()) }
            }
            val paths = spec.getJSONArray("paths")
            val body = Entity.loadSuspend("asset://$KOI_FOLDER/koi-body.glb")
            val tail = Entity.loadSuspend("asset://$KOI_FOLDER/koi-tail.glb")
            suspend fun instance(template: Entity, part: String, index: Int): TransformComponent {
                val entity = if (index == 0) template else template.clone(Entity.CloneOptions(true, true))
                    ?: Entity.loadSuspend("asset://$KOI_FOLDER/koi-$part.glb")
                entity.setName("koi-$index-$part")
                world.addChild(entity)
                return requireNotNull(entity.components[TransformComponent::class.java])
            }
            val fish = List(paths.length()) { i ->
                val p = paths.getJSONObject(i)
                Fish(p.getDouble("x").toFloat(), p.getDouble("z").toFloat(), p.getDouble("radius").toFloat(),
                    p.getDouble("speed").toFloat(), p.getDouble("phase").toFloat(),
                    instance(body, "body", i), instance(tail, "tail", i))
            }
            return PondKoi(spec.getDouble("level").toFloat(), fish).also { it.update(0f) }
        }
    }
}
