package com.jenn.blindboxworld.content

import com.pico.spatial.core.math.Vector3
import kotlin.math.hypot

/** Development-only inspection state. The broadcast that enables it is registered only in debuggable builds.
 * Route verification uses the real walker/collision/pickup path; it never grants items. */
internal object WorldVerification {
    var focus: Vector3? = null
    var route = false
    var active = false
    fun reset() { focus = null; route = false; active = false }
}

internal class WorldRouteReplay(private val terrain: WorldTerrain) {
    private val visited = mutableSetOf<Int>()
    private val path = ArrayDeque<Int>()
    private var target = -1
    var done = false
        private set
    fun advance(walker: WorldWalker, seconds: Float, caught: (Int) -> Unit) {
        if (done) return
        if (target < 0) {
            val remaining = terrain.pickupCells.filter { it !in visited }.toSet()
            if (remaining.isEmpty()) { done = true; return }
            val prev = IntArray(terrain.cells.size) { -1 }
            val queue = ArrayDeque<Int>(); queue.add(walker.cellIndex); prev[walker.cellIndex] = walker.cellIndex
            var found = -1
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (current in remaining) { found = current; break }
                for (next in terrain.cells[current].links) if (next >= 0 && prev[next] < 0) {
                    prev[next] = current; queue.add(next)
                }
            }
            check(found >= 0) { "Unreachable pickup cells: $remaining" }
            target = found
            val reverse = mutableListOf<Int>(); var cursor = target
            while (cursor != walker.cellIndex) { reverse.add(cursor); cursor = prev[cursor] }
            path.addAll(reverse.asReversed()); if (path.isEmpty()) path.add(target)
        }
        val next = terrain.cells[path.first()]
        val dx = (next.ix+.5f)*terrain.cell-walker.x
        val dz = (next.iz+.5f)*terrain.cell-walker.z
        val length = hypot(dx,dz)
        if (length < .0001f) {
            path.removeFirst()
            if (path.isEmpty()) { visited.add(target); target = -1 }
        } else {
            val dt = minOf(seconds, length*WORLD_SCALE/walker.pace(1f))
            walker.advance(dx/length,dz/length,1f,dt,0f,caught)
        }
    }
}
