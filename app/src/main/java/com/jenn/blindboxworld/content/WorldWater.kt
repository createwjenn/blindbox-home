package com.jenn.blindboxworld.content

import android.content.Context
import androidx.compose.runtime.withFrameNanos
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import java.io.DataInputStream
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

internal class SwimWake {
    var x = 0f
    var z = 0f
    var level = Float.NaN
    var immersion = 0f
    var moving = false
}

internal fun hasRippleWater(figure: String) =
    figure == "secret-capybara-toilet" || figure == "niu-lai" || figure == "tralalero-tralala"

/** Transparent highlights reveal the reflection, rocks, plants and fish in the original captures. */
internal suspend fun animateWorldWater(world: Entity, context: Context, figure: String, wake: SwimWake) = coroutineScope {
    val folder = "worlds/$figure"
    val shores = context.assets.list(folder).orEmpty().filter { it.startsWith("shoreline") && it.endsWith(".bin") }
    for (name in shores) launch {
        val field = context.assets.open("$folder/$name").use { DataInputStream(it).use(::PondWaves) }
        animateWaterLayer(world, field, wake)
    }
    if (figure == "niu-lai") launch { animateWaterLayer(world, Waterfall(), wake) }
    Unit
}

private interface WaterLayer {
    val rgba: ByteArray
    fun shade(seconds: Float, wake: SwimWake)
    fun createMesh(): MeshResource
}

private suspend fun animateWaterLayer(world: Entity, field: WaterLayer, wake: SwimWake) {
    val mesh = field.createMesh()
    val material = UnlitMaterial.create(BlendingMode.TRANSPARENT)
    val surface = Entity()
    try {
        mesh.toGlobal()
        material.toGlobal()
        material.setCullingMode(MaterialCullingMode.NONE)
        material.setApplyToneMapping(false)
        material.setDepthWrite(false)
        material.setOpacity(1f)
        material.setBaseColor(Color4.WHITE)
        surface.components.set(TransformComponent())
        surface.components.set(ModelComponent(mesh, material))
        if (!world.valid || !world.addChild(surface)) return
        val start = withFrameNanos { it }
        var last = start
        while (world.valid && surface.valid) {
            val frame = withFrameNanos { it }
            if (frame - last < 50_000_000L) continue
            field.shade((frame - start) * 1e-9f, wake)
            mesh.updateRawMeshVertexBuffer(1) { it.put(field.rgba); true }
            last = frame
        }
    } finally {
        surface.releaseScene()
        material.close()
        mesh.close()
    }
}

private class PondWaves(data: DataInputStream) : WaterLayer {
    private val columns = data.readInt()
    private val rows = data.readInt()
    private val x0 = data.readFloat()
    private val z0 = data.readFloat()
    private val spacing = data.readFloat()
    private val level = data.readFloat()
    private val mask = ByteArray(columns * rows).also(data::readFully)
    private val grid = IntArray(mask.size) { -1 }
    private val count = mask.count { (it.toInt() and 255) > 1 }
    private val xyz = FloatArray(count * 3)
    private val edge = FloatArray(count)
    private val sinX = FloatArray(count)
    private val cosX = FloatArray(count)
    private val sinZ = FloatArray(count)
    private val cosZ = FloatArray(count)
    override val rgba = ByteArray(count * 4)
    private val indices: IntArray

    init {
        var vertex = 0
        for (i in mask.indices) {
            val opacity = mask[i].toInt() and 255
            if (opacity <= 1) continue
            grid[i] = vertex
            val x = x0 + (i % columns) * spacing
            val z = z0 + (i / columns) * spacing
            xyz[vertex * 3] = x
            xyz[vertex * 3 + 1] = level
            xyz[vertex * 3 + 2] = z
            edge[vertex] = opacity.toFloat()
            val phaseX = x * 19f + sin(z * 9f)
            sinX[vertex] = sin(phaseX)
            cosX[vertex] = cos(phaseX)
            sinZ[vertex] = sin(z * 16f)
            cosZ[vertex] = cos(z * 16f)
            vertex++
        }
        val faces = IntArray(count * 6)
        var n = 0
        for (i in 0 until grid.size - columns - 1) {
            if ((i+1) % columns == 0) continue
            val a = grid[i]
            val b = grid[i+columns]
            val c = grid[i+1]
            val d = grid[i+columns+1]
            if (a < 0 || b < 0 || c < 0 || d < 0) continue
            faces[n++] = a; faces[n++] = b; faces[n++] = c
            faces[n++] = c; faces[n++] = b; faces[n++] = d
        }
        indices = faces.copyOf(n)
        shade(0f, SwimWake())
    }

    override fun shade(seconds: Float, wake: SwimWake) {
        // Separate the spatial and time phases to evaluate the website's two waves
        // with four trig calls per frame, regardless of the number of pond vertices.
        val sx = sin(seconds * 1.3f); val cx = cos(seconds * 1.3f)
        val sz = sin(seconds * .9f); val cz = cos(seconds * .9f)
        val immersed = wake.immersion > 0f && abs(level - wake.level) < .02f
        val phase = seconds * (if (wake.moving) .85f else .4f)
        for (i in edge.indices) {
            val wave = ((sinX[i] * cx + cosX[i] * sx) * (sinZ[i] * cz - cosZ[i] * sz)).coerceAtLeast(0f)
            val square = wave * wave
            val fourth = square * square
            val shine = fourth * fourth * fourth
            var ring = 0f
            if (immersed) {
                val dx = xyz[i * 3] - wake.x
                val dz = xyz[i * 3 + 2] - wake.z
                val distanceSquared = dx * dx + dz * dz
                if (distanceSquared in .012f..0.37f) {
                    val distance = sqrt(distanceSquared)
                    val age = (distance - .16f) / .4f
                    if (age in 0f..1f) {
                        val cycle = (age - phase) * 3f
                        val line = abs(cycle - kotlin.math.round(cycle))
                        ring = (1f - line * 2.8f).coerceAtLeast(0f) * (1f - age) * wake.immersion
                    }
                }
            }
            val k = i * 4
            rgba[k] = (5 + 95 * shine + 60 * ring).toInt().toByte()
            rgba[k+1] = (32 + 115 * shine + 92 * ring).toInt().toByte()
            rgba[k+2] = (43 + 108 * shine + 92 * ring).toInt().toByte()
            rgba[k+3] = (edge[i] * (.19f + shine * .21f + ring * .34f)).toInt().toByte()
        }
    }

    override fun createMesh(): MeshResource {
        val extentX = (columns - 1) * spacing * .5f
        val extentZ = (rows - 1) * spacing * .5f
        val bounds = BoundingBox(Vector3(x0 + extentX, level, z0 + extentZ), Vector3(extentX, .01f, extentZ))
        return createWaterMesh(xyz, rgba, indices, bounds)
    }
}

/** Animated streaks aligned to the scanned waterfall in Niu Lai's original world-waterfall.js. */
private class Waterfall : WaterLayer {
    private val columns = 65
    private val rows = 73
    private val xyz = FloatArray(columns * rows * 3)
    private val edge = FloatArray(columns * rows)
    override val rgba = ByteArray(columns * rows * 4)
    private val indices = IntArray((columns - 1) * (rows - 1) * 6)

    init {
        fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        var triangle = 0
        for (j in 0 until rows) for (i in 0 until columns) {
            val k = j * columns + i
            val u = i.toFloat() / (columns - 1)
            val v = j.toFloat() / (rows - 1)
            val x = (u - .5f) * 1.25f
            xyz[3*k] = -4.5f + cos(.15f) * x
            xyz[3*k+1] = -.15f + (v - .5f) * 4.1f
            xyz[3*k+2] = -21.5f - sin(.15f) * x
            edge[k] = smooth(u/.14f) * smooth((1f-u)/.14f) * smooth(v/.08f) * smooth((1f-v)/.08f)
            if (i + 1 < columns && j + 1 < rows) {
                indices[triangle++] = k; indices[triangle++] = k + 1; indices[triangle++] = k + columns
                indices[triangle++] = k + 1; indices[triangle++] = k + columns + 1; indices[triangle++] = k + columns
            }
            rgba[4*k] = 171.toByte()
            rgba[4*k+1] = 220.toByte()
            rgba[4*k+2] = 255.toByte()
        }
        shade(0f, SwimWake())
    }

    override fun shade(seconds: Float, wake: SwimWake) {
        for (j in 0 until rows) {
            val v = j.toFloat() / (rows - 1)
            val bend = sin(v * 9f + seconds) * 1.1f
            for (i in 0 until columns) {
                val u = i.toFloat() / (columns - 1)
                val k = j * columns + i
                val stripe = .5f + .5f * sin(u * 61f + bend)
                val flow = .5f + .5f * sin(v * 38f + seconds * 8f + u * 19f)
                rgba[4*k+3] = (89f * edge[k] * stripe * stripe * stripe * stripe *
                    (.35f + .65f * flow * flow * flow)).toInt().toByte()
            }
        }
    }

    override fun createMesh() = createWaterMesh(xyz, rgba, indices,
        BoundingBox(Vector3(-4.5f, -.15f, -21.5f), Vector3(.65f, 2.1f, .12f)))
}

private fun createWaterMesh(xyz: FloatArray, rgba: ByteArray, indices: IntArray, bounds: BoundingBox): MeshResource {
    return RawMeshDescriptorBuilder().use { builder ->
        builder.streamLayout(RawMeshStreamLayout(0, 0, 12))
            .streamLayout(RawMeshStreamLayout(1, 0, 4))
            .streamAttribute(RawMeshStreamAttribute(MeshVertexAttribute.POSITION, MeshVertexAttributeType.FLOAT3))
            .streamAttribute(RawMeshStreamAttribute(MeshVertexAttribute.COLOR, MeshVertexAttributeType.UBYTE4, true, 0, 1))
            .vertexCount(xyz.size / 3).indexCount(indices.size).indexType(MeshIndexSizeType.UNSIGNED_INT)
            .isDynamic(true).boundingBox(bounds).addSubMesh(0, indices.size, 0, "Water surface").build().use { desc ->
                RawMesh.create(desc).use { raw ->
                    check(raw.populateVertexBuffer(0) { it.asFloatBuffer().put(xyz); true })
                    check(raw.populateVertexBuffer(1) { it.put(rgba); true })
                    check(raw.populateIndicesBuffer { it.asIntBuffer().put(indices); true })
                    MeshResource.createWithRawMesh(raw, "Water ripples")
                }
            }
    }
}
