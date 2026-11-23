package com.jenn.blindboxworld.content

import android.content.Context
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

internal data class OutlinePoint(val u: Float, val v: Float)

internal class PlacementProfiles(context: Context) {
    private val profiles: Map<String, List<OutlinePoint>> =
        context.assets.open("furniture/footprints.json").bufferedReader().use { reader ->
            val doc = JSONObject(reader.readText())
            doc.keys().asSequence().associateWith { id ->
                val loop = doc.getJSONArray(id)
                List(loop.length()) { index ->
                    val p = loop.getJSONArray(index)
                    OutlinePoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                }
            }
        }

    fun mesh(id: String, wall: Boolean, scale: Float = 1f): MeshResource {
        val loop = profiles.getValue(id)
        val width = (loop.maxOf { it.u } - loop.minOf { it.u }) * scale
        val depth = (loop.maxOf { it.v } - loop.minOf { it.v }) * scale
        val radius = (min(width, depth) * .04f).coerceIn(.0018f, .007f)
        val sides = 6
        val positions = ArrayList<Vector3>(loop.size * sides)
        val indices = ArrayList<Int>(loop.size * sides * 6)

        fun outward(a: OutlinePoint, b: OutlinePoint): OutlinePoint {
            val du = b.u - a.u
            val dv = b.v - a.v
            val length = hypot(du, dv).coerceAtLeast(.000001f)
            return OutlinePoint(dv / length, -du / length)
        }

        for (i in loop.indices) {
            val p = loop[i]
            val next = (i + 1) % loop.size
            val before = outward(loop[(i+loop.size-1) % loop.size], p)
            val after = outward(p, loop[next])
            val du = before.u + after.u
            val dv = before.v + after.v
            val length = hypot(du, dv).coerceAtLeast(.000001f)
            // Bisect each corner and join adjoining segments into one continuous stroke.
            val nx = du / length
            val ny = dv / length
            val miter = radius / (nx * after.u + ny * after.v).coerceAtLeast(.5f)
            for (j in 0 until sides) {
                val angle = 2.0 * PI * j / sides
                val along = miter * cos(angle).toFloat()
                val lift = radius * sin(angle).toFloat()
                val u = p.u * scale + nx * along
                val v = p.v * scale + ny * along
                positions.add(if (wall) Vector3(u, v, lift) else Vector3(u, lift, v))
                val a = i * sides + j
                val b = next * sides + j
                val c = next * sides + (j + 1) % sides
                val d = i * sides + (j + 1) % sides
                indices.addAll(listOf(a, b, c, a, c, d))
            }
        }
        return MeshResource.createWithMeshModel(MeshModel(positions, indices), null, "room-outline-${id.replace(':', '-')}")
    }
}

internal fun placementOutline(mesh: MeshResource, allowed: Boolean = true): Entity {
    val color = if (allowed) Color4(.58f, 1f, .68f, 1f) else Color4(1f, .32f, .25f, 1f)
    val material = glowMaterial(color).apply { setCullingMode(MaterialCullingMode.NONE) }
    return Entity().apply {
        components.set(TransformComponent())
        components.set(ModelComponent(mesh, material))
    }
}
