package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Latch the last visible ray on pinch-down, before the gesture shifts hand aim. */
internal class GroundHover {
    private var press: GroundPress? = null
    private var updated = 0L
    fun show(value: GroundPress) { press = value; updated = android.os.SystemClock.uptimeMillis() }
    fun current() = press?.takeIf { android.os.SystemClock.uptimeMillis() - updated < 100 }
    fun clear() { press = null }
}

/** Flat white rim, feathered halo and faint center, like the loft's station pads.
 * Visual geometry only, so a pointer always reaches the surface underneath. */
internal fun groundRing(radius: Float, dashed: Boolean = false): Entity {
    val points = ArrayList<Vector3>()
    val colors = ArrayList<Color4>()
    val triangles = ArrayList<Int>()
    val radii = if (dashed) listOf(.88f,1f) else listOf(0f, .77f, .86f, .89f, .935f, .98f, 1f)
    val opacity = if (dashed) listOf(1f,1f) else listOf(.13f, 0f, .06f, .96f, .96f, .06f, 0f)
    val slices = 96
    for (i in 0..slices) {
        val angle = 2.0 * PI * i / slices
        for (j in radii.indices) {
            points += Vector3(radius * radii[j] * cos(angle).toFloat(), 0f, radius * radii[j] * sin(angle).toFloat())
            colors += Color4(1f, 1f, 1f, opacity[j])
            if (i == slices || j == radii.lastIndex || (dashed && i % 6 >= 4)) continue
            val a = i * radii.size + j
            val b = a + radii.size
            triangles.addAll(listOf(a, b, b+1, a, b+1, a+1))
        }
    }
    val mesh = MeshResource.createWithMeshModel(MeshModel(points, triangles, colors = colors), null,
        if (dashed) "ground-preview" else "ground-destination")
    val material = UnlitMaterial.create(if (dashed) BlendingMode.OPAQUE else BlendingMode.TRANSPARENT).apply {
        setBaseColor(Color4.WHITE); setApplyToneMapping(false); setCullingMode(MaterialCullingMode.NONE)
    }
    return Entity().apply {
        components.set(TransformComponent())
        components.set(ModelComponent(mesh, material))
        enabled = false
    }
}
