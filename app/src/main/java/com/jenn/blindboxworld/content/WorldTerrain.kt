package com.jenn.blindboxworld.content

import android.content.Context
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

internal const val WORLD_PICKUPS = 30
internal const val WORLD_SCALE = 2.4f
internal const val ACTOR_HEIGHT = .88f

/** A surface on the website's bidirectional, clearance-checked movement graph. */
internal data class WorldCell(
    val ix: Int, val iz: Int, val ground: Float, val water: Float, val links: IntArray,
)

internal class WorldTerrain(
    val cell: Float,
    val cells: List<WorldCell>,
    val pickupCells: List<Int>,
    val runSpeed: Float,
) {
    private fun center(i: Int) = (i + .5f) * cell
    val spawn = cells.first().let { Vector3(center(it.ix), it.ground, center(it.iz)) }
    // Keep the original Tung entry framing. The other captures use their own authored spawns.
    val origin = Vector3(-spawn.x * WORLD_SCALE, (.12f - spawn.y) * WORLD_SCALE, -(spawn.z + 1.08f) * WORLD_SCALE)
    private fun stage(x: Float, y: Float, z: Float) =
        Vector3(origin.x + x * WORLD_SCALE, origin.y + y * WORLD_SCALE, origin.z + z * WORLD_SCALE)
    val actorStart = stage(spawn.x, spawn.y, spawn.z)
    val pickupPositions = pickupCells.map { i ->
        val p = cells[i]
        val height = if (p.water.isFinite()) p.water + .11f else p.ground + .22f
        stage(center(p.ix), height, center(p.iz))
    }

    /** A frame advances less than one cell per axis. A missing edge is a wall, cliff, or map boundary. */
    fun nextCell(current: Int, x: Float, z: Float): Int {
        val node = cells[current]
        val dx = floor(x / cell).toInt() - node.ix
        val dz = floor(z / cell).toInt() - node.iz
        return when {
            dx == 0 && dz == 0 -> current
            dx == 1 && dz == 0 -> node.links[0]
            dx == -1 && dz == 0 -> node.links[1]
            dx == 0 && dz == 1 -> node.links[2]
            dx == 0 && dz == -1 -> node.links[3]
            else -> -1
        }
    }

    /** Exported links certify travel between cell centres. A body near an
     * unlinked cell edge or an obstructed inside corner still needs clearance. */
    fun fitsFootprint(index: Int, x: Float, z: Float): Boolean {
        val p = cells[index]
        val dx = x - center(p.ix)
        val dz = z - center(p.iz)
        val xLink = p.links[if (dx >= 0f) 0 else 1]
        val zLink = p.links[if (dz >= 0f) 2 else 3]
        // Cell centres already include the website's body-radius erosion.
        // Spend only the remaining half-cell clearance on off-centre motion.
        val margin = cell * .43f
        val edgeX = cell * .5f - abs(dx)
        val edgeZ = cell * .5f - abs(dz)
        if ((xLink < 0 && edgeX < margin) || (zLink < 0 && edgeZ < margin)) return false
        if (edgeX * edgeX + edgeZ * edgeZ >= margin * margin) return true
        if (xLink < 0 || zLink < 0) return false
        val diagonal = cells[xLink].links[if (dz >= 0f) 2 else 3]
        return diagonal >= 0 && diagonal == cells[zLink].links[if (dx >= 0f) 0 else 1]
    }

    /** Interpolate only between connected surfaces on the same deck. This
     * forms an actual ramp from the swimming depth to the top of a bank. */
    fun bedAt(index: Int, x: Float, z: Float): Float {
        val p = cells[index]
        val dx = (x - center(p.ix)) / cell
        val dz = (z - center(p.iz)) / cell
        val xi = if (dx >= 0f) 0 else 1
        val zi = if (dz >= 0f) 2 else 3
        val xn = cells.getOrNull(p.links[xi])
        val zn = cells.getOrNull(p.links[zi])
        val corner = xn?.links?.get(zi)
        val yn = if (corner != null && corner >= 0 && zn?.links?.get(xi) == corner) cells[corner].ground else p.ground
        val sx = abs(dx)
        val sz = abs(dz)
        val a = p.ground * (1f-sx) + (xn?.ground ?: p.ground) * sx
        val b = (zn?.ground ?: p.ground) * (1f-sx) + yn * sx
        return a * (1f-sz) + b * sz
    }

    companion object {
        suspend fun load(context: Context, slug: String): WorldTerrain = withContext(Dispatchers.IO) {
            val json = context.assets.open("worlds/$slug/navigation.json").bufferedReader().use { JSONObject(it.readText()) }
            val cells = json.getJSONArray("nodes").let { rows ->
                List(rows.length()) { i ->
                    val row = rows.getJSONArray(i)
                    WorldCell(row.getInt(0), row.getInt(1), row.getDouble(2).toFloat(),
                        if (row.isNull(3)) Float.NaN else row.getDouble(3).toFloat(),
                        IntArray(4) { row.getInt(4 + it) })
                }
            }
            val picks = json.getJSONArray("pickups")
            require(picks.length() == WORLD_PICKUPS) { "$slug must contain $WORLD_PICKUPS pickups" }
            val pickupCells = List(WORLD_PICKUPS) { picks.getInt(it) }
            require(pickupCells.distinct().size == WORLD_PICKUPS) { "$slug has overlapping pickup cells" }
            require(pickupCells.all { it in cells.indices }) { "$slug has an invalid pickup cell" }
            val reachable = mutableSetOf(0)
            val pending = ArrayDeque<Int>(); pending.add(0)
            while (pending.isNotEmpty()) {
                for (next in cells[pending.removeFirst()].links) {
                    require(next == -1 || next in cells.indices) { "$slug has an invalid navigation link" }
                    if (next >= 0 && reachable.add(next)) pending.add(next)
                }
            }
            require(pickupCells.all { it in reachable }) { "$slug has a pickup disconnected from the spawn" }
            WorldTerrain(json.getDouble("cell").toFloat(), cells, pickupCells,
                json.getDouble("runSpeed").toFloat())
        }
    }
}


/** Shared by the controller and route replay. Collision checks every crossed part of a frame. */
internal class WorldWalker(private val terrain: WorldTerrain, private val neckDepth: Float?) {
    var x = terrain.spawn.x
        private set
    var z = terrain.spawn.z
        private set
    var cellIndex = 0
        private set
    private val acquired = BooleanArray(terrain.pickupCells.size)
    private val surface get() = terrain.cells[cellIndex]
    val ground get() = terrain.bedAt(cellIndex, x, z)
    val waterDepth get() = surface.water - ground
    val swimming get() = waterDepth > .06f
    val support get() = if (swimming && neckDepth != null) maxOf(ground, surface.water - neckDepth) else ground

    fun pace(strength: Float): Float {
        // Preserve a slow precision range; use full deflection to run a large world course.
        val sprint = ((strength - .48f) / .52f).coerceIn(0f, 1f)
        return strength * (.90f + (terrain.runSpeed - .90f) * sprint * sprint) *
            if (swimming) .72f else 1f
    }

    fun advance(dx: Float, dz: Float, strength: Float, seconds: Float, lift: Float, caught: (Int) -> Unit) {
        val duration = seconds.coerceIn(0f, .25f)
        val pieces = ceil(terrain.runSpeed * duration / (WORLD_SCALE * terrain.cell * .3f)).toInt().coerceAtLeast(1)
        repeat(pieces) {
            val step = pace(strength) * duration / pieces / WORLD_SCALE
            val nextX = x + dx * step
            val cx = terrain.nextCell(cellIndex, nextX, z)
            if (cx >= 0 && terrain.fitsFootprint(cx, nextX, z)) { x = nextX; cellIndex = cx }
            val nextZ = z + dz * step
            val cz = terrain.nextCell(cellIndex, x, nextZ)
            if (cz >= 0 && terrain.fitsFootprint(cz, x, nextZ)) { z = nextZ; cellIndex = cz }
            collect(lift, caught)
        }
    }

    fun collect(lift: Float, caught: (Int) -> Unit) {
        val px = terrain.origin.x + x * WORLD_SCALE
        val pz = terrain.origin.z + z * WORLD_SCALE
        val feet = terrain.origin.y + support * WORLD_SCALE + lift
        for (i in terrain.pickupPositions.indices) {
            if (acquired[i]) continue
            val item = terrain.pickupPositions[i]
            val dx = item.x - px
            val dz = item.z - pz
            if (dx*dx + dz*dz <= .56f*.56f && item.y >= feet - .13f && item.y <= feet + ACTOR_HEIGHT + .18f) {
                acquired[i] = true
                caught(i)
            }
        }
    }
}
