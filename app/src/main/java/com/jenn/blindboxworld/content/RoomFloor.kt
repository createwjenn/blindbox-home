package com.jenn.blindboxworld.content

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal data class FloorPoint(val x: Float, val z: Float) {
    fun distanceTo(other: FloorPoint) = hypot(x - other.x, z - other.z)
}

internal data class FloorObstacle(val x: Float, val z: Float, val width: Float, val depth: Float)

/** Loft-local metres. Imported Blender (x, y) on the ground plane becomes runtime (x, -y). */
internal object RoomFloor {
    private const val CLEARANCE = .30f
    private const val GAP = .003f
    private val walls = Rect(-3.90f, 3.90f, -3.98f, 3.98f)
    private val doorway = FloorPoint(-1.88f, 3.83f)

    private data class Rect(val left: Float, val right: Float, val back: Float, val front: Float) {
        operator fun contains(p: FloorPoint) = p.x in left..right && p.z in back..front
        fun clamp(p: FloorPoint) = FloorPoint(p.x.coerceIn(left, right), p.z.coerceIn(back, front))

        /** Fraction along a segment at which it first touches this footprint. */
        fun entry(p: FloorPoint, dx: Float, dz: Float): Float {
            if (dx == 0f && p.x !in left..right || dz == 0f && p.z !in back..front)
                return Float.POSITIVE_INFINITY
            val nearX = when { dx > 0f -> (left - p.x) / dx; dx < 0f -> (right - p.x) / dx; else -> Float.NEGATIVE_INFINITY }
            val farX = when { dx > 0f -> (right - p.x) / dx; dx < 0f -> (left - p.x) / dx; else -> Float.POSITIVE_INFINITY }
            val nearZ = when { dz > 0f -> (back - p.z) / dz; dz < 0f -> (front - p.z) / dz; else -> Float.NEGATIVE_INFINITY }
            val farZ = when { dz > 0f -> (front - p.z) / dz; dz < 0f -> (back - p.z) / dz; else -> Float.POSITIVE_INFINITY }
            val start = max(0f, max(nearX, nearZ))
            return if (start <= min(1f, min(farX, farZ))) start else Float.POSITIVE_INFINITY
        }
    }

    private fun footprint(cx: Float, cz: Float, width: Float, depth: Float) =
        Rect(cx - width / 2 - CLEARANCE, cx + width / 2 + CLEARANCE,
             cz - depth / 2 - CLEARANCE, cz + depth / 2 + CLEARANCE)

    // Freestanding objects in design/collection_room.py, plus the runtime machine/globe.
    // Low rugs and plank seams remain walkable. Wall-mounted art is outside the walking bounds.
    private val authored = buildList {
        add(footprint(0f, -1.70f, .56f, .56f))        // museum plinth
        add(footprint(-3.62f, -3.70f, 1.12f, 1.12f)) // gumball machine
        add(footprint(0f, -4.12f, 3f, .44f))         // shelf console
        for (x in listOf(-1.95f, 1.95f)) add(footprint(x, -4.15f, .32f, .38f))
        add(footprint(-2.75f, -3.95f, .40f, .40f))   // arc lamp
        add(footprint(2.14f, -1.66f, 3.06f, 1.09f)) // sofa upholstery
        add(footprint(2.10f, -.33f, 1.57f, .69f))    // glass coffee table
        add(footprint(-3.23f, .58f, .71f, .82f))     // window chair incl. arms and legs
        add(footprint(-3.09f, 1.54f, .49f, .42f))    // footstool
        add(footprint(-3.83f, -1.38f, .52f, 2.35f))  // sideboard including drawer fronts
        add(footprint(3.64f, -2.48f, .41f, .41f))    // floor lamps
        add(footprint(3.66f, 1.72f, .49f, .49f))
        add(footprint(-3.72f, 2.63f, .38f, .38f))    // planters
        add(footprint(3.72f, -3.55f, .38f, .38f))
        add(footprint(1.16f, 3.27f, 2.10f, 1.00f))   // dining top and four separate chairs
        for (x in listOf(.61f, 1.71f)) add(footprint(x, 2.61f, .45f, .44f))
        // The far chairs touch the rear walking limit. The recess enclosed between
        // them, the tabletop and the wall cannot be entered from the room.
        add(footprint(1.16f, 3.93f, 1.55f, .44f))
    }

    private var obstacles = authored

    fun setFurnishings(items: List<FloorObstacle>?) {
        val updated = items?.map { footprint(it.x,it.z,it.width,it.depth) } ?: authored
        if (updated == obstacles) return
        obstacles = updated
        corners = makeCorners()
        links = makeLinks()
    }

    fun isWalkable(p: FloorPoint) = p in walls && obstacles.none { p in it }

    /** Visible wood beside furniture can be inside the viewer's clearance margin.
     * Back off toward the viewer just enough to stand there, without overshooting the tap. */
    fun tapDestination(from: FloorPoint, hit: FloorPoint): FloorPoint? {
        if (isWalkable(hit)) return hit
        val distance = hit.distanceTo(from)
        if (!distance.isFinite() || distance < .001f) return null
        val dx = (from.x - hit.x) / distance
        val dz = (from.z - hit.z) / distance
        for (i in 1..16) {
            val offset = minOf(i * .02f, distance)
            val point = FloorPoint(hit.x + dx * offset, hit.z + dz * offset)
            if (isWalkable(point)) return point
        }
        return null
    }

    private fun firstHit(a: FloorPoint, b: FloorPoint): Float {
        val dx = b.x - a.x
        val dz = b.z - a.z
        return obstacles.minOfOrNull { it.entry(a, dx, dz) } ?: Float.POSITIVE_INFINITY
    }

    private fun clear(a: FloorPoint, b: FloorPoint) = !firstHit(a, b).isFinite()

    /** Recover a legacy location on furniture to the closest point just outside its footprint. */
    private fun recover(from: FloorPoint): FloorPoint {
        if (!from.x.isFinite() || !from.z.isFinite()) return doorway
        val p = walls.clamp(from)
        if (isWalkable(p)) return p
        // Adjacent or overlapping objects can obstruct a single box's closest face.
        val xs = listOf(p.x) + obstacles.flatMap { listOf(it.left - GAP, it.right + GAP) }
        val zs = listOf(p.z) + obstacles.flatMap { listOf(it.back - GAP, it.front + GAP) }
        var best = doorway
        var nearest = p.distanceTo(best)
        for (x in xs) for (z in zs) {
            val candidate = FloorPoint(x, z)
            val distance = p.distanceTo(candidate)
            if (distance < nearest && isWalkable(candidate)) { best = candidate; nearest = distance }
        }
        return best
    }

    private fun sweep(from: FloorPoint, to: FloorPoint): FloorPoint {
        val hit = firstHit(from, to)
        if (!hit.isFinite()) return to
        val distance = from.distanceTo(to)
        val t = (hit - GAP / distance.coerceAtLeast(GAP)).coerceAtLeast(0f)
        return FloorPoint(from.x + (to.x - from.x) * t, from.z + (to.z - from.z) * t)
    }

    /** Swept displacement, followed by free-axis sliding when a diagonal meets furniture. */
    fun step(from: FloorPoint, dx: Float, dz: Float): FloorPoint {
        val start = if (isWalkable(from)) from else recover(from)
        if (!dx.isFinite() || !dz.isFinite()) return start
        val end = walls.clamp(FloorPoint(start.x + dx, start.z + dz))
        val contact = sweep(start, end)
        if (contact == end) return end
        val alongX = sweep(contact, FloorPoint(end.x, contact.z))
        return sweep(alongX, FloorPoint(alongX.x, end.z))
    }

    // Precompute visibility between the expanded furniture corners. Direct clear-floor taps
    // bypass the graph entirely. Routes around furniture retain the exact, unquantized tap.
    private fun makeCorners() = obstacles.flatMap { box ->
        listOf(box.left - GAP, box.right + GAP).flatMap { x ->
            listOf(box.back - GAP, box.front + GAP).map { z -> FloorPoint(x, z) }
        }
    }.distinct().filter(::isWalkable)
    private var corners = makeCorners()
    private fun makeLinks() = corners.mapIndexed { i, from ->
        corners.indices.filter { j -> i != j && clear(from, corners[j]) }
    }

    private var links = makeLinks()

    fun route(from: FloorPoint, to: FloorPoint): List<FloorPoint> {
        if (!isWalkable(to)) return emptyList()
        val start = if (isWalkable(from)) from else recover(from)
        val recovery = if (from == start) emptyList() else listOf(start)
        if (to == start) return recovery
        if (clear(start, to)) return recovery + to

        val distance = FloatArray(corners.size) { Float.POSITIVE_INFINITY }
        val parent = IntArray(corners.size) { -1 }
        val settled = BooleanArray(corners.size)
        val seesGoal = BooleanArray(corners.size)
        for (i in corners.indices) {
            if (clear(start, corners[i])) distance[i] = start.distanceTo(corners[i])
            seesGoal[i] = clear(corners[i], to)
        }
        // A* with exact Euclidean edge cost and a straight-line lower bound.
        while (true) {
            var best = -1
            var bestEstimate = Float.POSITIVE_INFINITY
            for (i in corners.indices) if (!settled[i]) {
                val estimate = distance[i] + corners[i].distanceTo(to)
                if (estimate < bestEstimate) { best = i; bestEstimate = estimate }
            }
            if (best < 0) return emptyList()
            if (seesGoal[best]) {
                val path = mutableListOf(to)
                var i = best
                while (i >= 0) {
                    val corner = corners[i]
                    if (corner != start && corner != path.last()) path.add(corner)
                    i = parent[i]
                }
                return recovery + path.asReversed()
            }
            settled[best] = true
            for (i in links[best]) {
                val via = distance[best] + corners[best].distanceTo(corners[i])
                if (via < distance[i]) { distance[i] = via; parent[i] = best }
            }
        }
    }
}
