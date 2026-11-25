package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlin.math.ceil
import kotlin.math.hypot

/** All the shoes on one waypoint leg share a mesh. Coordinates are in terrain space. */
internal class ShoeprintTrail(private val terrain: WorldTerrain) {
    private val vertices = ArrayList<Vector3>()
    private val colors = ArrayList<Color4>()
    private val indices = ArrayList<Int>()

    fun trace(leg: RouteLeg, firstSide: Int): Int {
        var start = leg.start
        val lengths = leg.points.map { end ->
            hypot(end.x - start.x, end.z - start.z).also { start = end }
        }
        val total = lengths.sum()
        var distance = 0f
        var nextStep = STRIDE * .65f
        var side = firstSide
        start = leg.start
        for ((index, end) in leg.points.withIndex()) {
            val length = lengths[index]
            if (length > 0f) {
                val dx = (end.x - start.x) / length
                val dz = (end.z - start.z) / length
                var deck = start.cell
                var lastZ = start.z
                // Follow linked decks even when a full stride crosses several cells.
                val samples = ceil(length / (terrain.cell * .4f)).toInt().coerceAtLeast(1)
                for (sample in 1..samples) {
                    val offset = length * sample / samples
                    val x = start.x + dx * offset
                    val z = start.z + dz * offset
                    while (nextStep <= distance + offset && nextStep + END_GAP <= total) {
                        val along = nextStep - distance
                        val sx = start.x + dx * along
                        val sz = start.z + dz * along
                        val localDeck = deckAt(deck, sx, lastZ, sz)
                        stamp(localDeck, sx, sz, dx, dz, side)
                        side = -side
                        nextStep += STRIDE
                    }
                    deck = deckAt(deck, x, lastZ, z)
                    lastZ = z
                }
            }
            distance += length
            start = end
        }
        return side
    }

    private fun deckAt(deck: Int, x: Float, previousZ: Float, z: Float): Int {
        val across = terrain.nextCell(deck, x, previousZ).takeIf { it >= 0 } ?: deck
        return terrain.nextCell(across, x, z).takeIf { it >= 0 } ?: across
    }

    private fun stamp(deck: Int, x: Float, z: Float, dx: Float, dz: Float, side: Int) {
        fun vertex(across: Float, forward: Float, color: Color4) {
            // Mirror the curved instep and turn each toe slightly outward.
            val lateral = side * (.031f + across + forward * .10f)
            val px = x + dx * forward - dz * lateral
            val pz = z + dz * forward + dx * lateral
            val support = deckAt(deck, px, z, pz)
            vertices += Vector3(px, terrain.surfaceHeight(support, px, pz) + .002f, pz)
            colors += color
        }

        fun sole(outline: List<Pair<Float, Float>>, centerZ: Float) {
            val base = vertices.size
            vertex(0f, centerZ, INK)
            for (scale in listOf(.85f, 1f)) {
                for ((across, forward) in outline) {
                    vertex(across * scale, centerZ + (forward - centerZ) * scale,
                        if (scale < 1f) INK else CREAM)
                }
            }
            val n = outline.size
            for (i in 0 until n) {
                val a = base + 1 + i
                val b = base + 1 + (i + 1) % n
                indices.addAll(listOf(base, a, b, a, a + n, b, b, a + n, b + n))
            }
        }
        sole(TOE, .017f)
        sole(HEEL, -.031f)
    }

    fun mesh(): MeshResource? = if (indices.isEmpty()) null else
        MeshResource.createWithMeshModel(MeshModel(vertices, indices, colors = colors), null, "waypoint-shoeprints")

    private companion object {
        const val STRIDE = .15f
        const val END_GAP = .115f // Keep the round destination marker clear.
        val INK = Color4(.075f, .046f, .037f, 1f)
        val CREAM = Color4(1f, .89f, .71f, 1f)
        val TOE = listOf(-.014f to -.009f, -.016f to .001f, -.023f to .018f,
            -.022f to .030f, -.016f to .041f, -.006f to .047f, .007f to .047f,
            .017f to .041f, .022f to .031f, .022f to .018f, .017f to .002f, .013f to -.009f)
        val HEEL = listOf(-.014f to -.018f, -.015f to -.031f, -.012f to -.039f,
            -.005f to -.044f, .006f to -.044f, .013f to -.039f, .016f to -.031f, .014f to -.018f)
    }
}
