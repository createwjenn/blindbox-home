package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlin.math.hypot

/** Nearby missing navigation edges become a short, glowing fence. It never moves the camera. */
internal class WorldBoundary(private val terrain: WorldTerrain, parent: Entity) {
    private val root = Entity().apply { components.set(TransformComponent()); parent.addChild(this) }
    private val mesh = MeshResource.createBox(Vector3(terrain.cell * WORLD_SCALE, .025f, .025f), 0f)
    private val material = glowMaterial(Color4(.42f, .95f, .90f, 1f))
    private val rails = List(96) {
        Entity().apply {
            components.set(TransformComponent()); components.set(ModelComponent(mesh, material))
            root.addChild(this); enabled = false
        }
    }
    private var lastCell = -1
    private var edges = emptyList<Pair<Vector3, Float>>()
    fun update(cell: Int, x: Float, z: Float) {
        if (cell != lastCell) {
            lastCell = cell
            val nearby = mutableSetOf(cell)
            var frontier = setOf(cell)
            repeat(5) {
                frontier = frontier.flatMap { terrain.cells[it].links.toList() }
                    .filter { it >= 0 && nearby.add(it) }.toSet()
            }
            edges = nearby.flatMap { index ->
                val p = terrain.cells[index]
                p.links.indices.filter { p.links[it] < 0 }.map { direction ->
                    val ex = (p.ix + .5f) * terrain.cell + when (direction) { 0 -> .5f; 1 -> -.5f; else -> 0f } * terrain.cell
                    val ez = (p.iz + .5f) * terrain.cell + when (direction) { 2 -> .5f; 3 -> -.5f; else -> 0f } * terrain.cell
                    Pair(Vector3(ex, if (p.water.isFinite()) maxOf(p.ground, p.water) else p.ground, ez),
                        if (direction < 2) 90f else 0f)
                }
            }
        }
        var n = 0
        for ((p, yaw) in edges.sortedBy { hypot(it.first.x-x, it.first.z-z) }) {
            val range = hypot(p.x-x, p.z-z) * WORLD_SCALE
            if (range >= 1.25f || n + 2 > rails.size) continue
            val alpha = ((1.25f-range)/.65f).coerceIn(0f, 1f)
            for (height in listOf(.12f, .38f)) {
                rails[n++].apply {
                    enabled = true
                    place(terrain.origin.x+p.x*WORLD_SCALE, terrain.origin.y+p.y*WORLD_SCALE+height,
                        terrain.origin.z+p.z*WORLD_SCALE, yaw=yaw)
                    setOpacity(alpha)
                }
            }
        }
        while (n < rails.size) rails[n++].enabled = false
    }
    fun close() { root.releaseScene(); mesh.close(); material.close() }
}
