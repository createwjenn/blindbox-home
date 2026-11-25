package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

internal data class GroundPress(val eye: Vector3, val hit: Vector3, val margin: Boolean? = null)

// Use the rendered pickup bounds before adding its gold hint. Each bobbing collectible
// supplies a destination at its own feet, even when the ray would hit distant scenery.
internal fun Entity.mountPickupHit() {
    val bounds = getVisualBounds(this, recursive = true, enabledOnly = false)
    if (bounds.isEmpty()) return
    components.set(InteractableComponent())
    components.set(CollisionComponent(collisionShape=listOf(
        ShapeResource.createBox(bounds.size).offsetByTranslation(bounds.center)
    ), physicsMaterial=PhysicsMaterialResource()))
}

// The scanned scenery has no raycast geometry. Match the pointer surface to the
// same navigable decks and water levels used by the walker, including overpasses.
internal fun WorldTerrain.mountPickSurface(world: Entity) {
    cells.chunked(1024).forEachIndexed { chunk, group ->
        val vertices = ArrayList<Vector3>(9 * group.size)
        val triangles = ArrayList<Int>(24 * group.size)
        for ((offset, p) in group.withIndex()) {
            val index = chunk * 1024 + offset
            val base = vertices.size
            // Sample the same sloping surface as the feet. A flat tile gives
            // a different ray intersection, especially at shallow viewing angles.
            for (z in 0..2) for (x in 0..2) {
                val px = (p.ix + x * .5f) * cell
                val pz = (p.iz + z * .5f) * cell
                vertices.add(Vector3(px, surfaceHeight(index, px, pz), pz))
            }
            for (z in 0..1) for (x in 0..1) {
                val i = base + z * 3 + x
                triangles.addAll(listOf(i,i+3,i+4,i,i+4,i+1))
            }
        }
        val mesh = MeshResource.createWithMeshModel(MeshModel(vertices, triangles), null, "walk-surface-$chunk")
        world.addChild(Entity().apply {
            setName("walkSurface")
            components.set(TransformComponent())
            components.set(InteractableComponent())
            components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createStaticMesh(mesh)), physicsMaterial=PhysicsMaterialResource()))
        })
    }
    // Behind all playable decks. Forward taps just beyond the scanned floor still
    // report a pointer ray, while nearer ground and collectible colliders win.
    val minX = cells.minOf { it.ix } * cell
    val maxX = (cells.maxOf { it.ix } + 1) * cell
    val minZ = cells.minOf { it.iz } * cell
    val maxZ = (cells.maxOf { it.iz } + 1) * cell
    val bottom = cells.minOf { it.ground } - .15f
    val width = maxX - minX + 2f
    val depth = maxZ - minZ + 2f
    val height = cells.maxOf { if (it.water.isFinite()) maxOf(it.water,it.ground) else it.ground } - bottom + .18f
    val hx = width * .5f
    val hz = depth * .5f
    // Inward-facing rim catches shallow rays aimed at the distant landscape.
    // From outside the map the near wall is back-facing and leaves the ground selectable.
    val corners = listOf(Vector3(-hx,0f,-hz), Vector3(hx,0f,-hz),
        Vector3(hx,0f,hz), Vector3(-hx,0f,hz))
    val vertices = corners + corners.map { it + Vector3(0f,height,0f) }
    val indices = mutableListOf(0,2,1,0,3,2)
    for (a in corners.indices) {
        val b = (a+1) % 4
        indices.addAll(listOf(a,b,b+4,a,b+4,a+4))
    }
    val mesh = MeshResource.createWithMeshModel(MeshModel(vertices,indices),null,"world-pick-margin")
    world.addChild(Entity().apply {
        setName("walkSurfaceMargin")
        components.set(TransformComponent())
        place((minX+maxX)*.5f, bottom, (minZ+maxZ)*.5f)
        components.set(InteractableComponent())
        components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createStaticMesh(mesh)),
            physicsMaterial=PhysicsMaterialResource()))
    })

}

internal fun WorldTerrain.surfaceHeight(i: Int, x: Float, z: Float): Float {
    val bed = bedAt(i, x, z)
    val water = cells[i].water
    return if (water.isFinite()) max(bed, water) else bed
}

internal fun WorldTerrain.placeRing(ring: Entity, goal: GroundPoint) {
    val h = surfaceHeight(goal.cell, goal.x, goal.z)
    val d = cell * .08f
    fun slope(dh: Float) = Math.toDegrees(atan2(dh, 2f * d).toDouble()).toFloat()
    val pitch = slope(surfaceHeight(goal.cell, goal.x, goal.z-d) - surfaceHeight(goal.cell, goal.x, goal.z+d))
    val roll = slope(surfaceHeight(goal.cell, goal.x+d, goal.z) - surfaceHeight(goal.cell, goal.x-d, goal.z))
    ring.place(goal.x, h + .003f, goal.z, pitch = pitch, roll = roll)
}

internal class WorldDestination(private val terrain: WorldTerrain, private val world: Entity) {
    private val route = TapRoute(terrain)
    var walker: WorldWalker? = null
    private val markers = List(3) { groundRing(.082f).also { world.addChild(it); it.enabled = false } }
    private data class Trail(val entity: Entity?, val nextSide: Int)
    private val trails = mutableMapOf<RouteLeg, Trail>()

    fun target(press: GroundPress, margin: Boolean): GroundPoint? {
        val actor = walker ?: return null
        return if (margin) route.beyondFloorTarget(press, actor) else route.floorTarget(press.hit)
    }

    fun select(press: GroundPress, margin: Boolean) {
        val actor = walker ?: return
        val goal = target(press, margin) ?: return
        if (route.enqueue(goal, actor)) refresh()
    }

    fun selectPickup(index: Int) {
        val actor = walker ?: return
        val goal = route.center(terrain.pickupCells[index])
        if (route.enqueue(goal, actor)) refresh()
    }

    private fun refresh() {
        val expired = trails.keys.filter { it !in route.legs }
        for (leg in expired) {
            trails.remove(leg)?.entity?.releaseScene()
        }
        for ((index, marker) in markers.withIndex()) {
            val leg = route.legs.getOrNull(index)
            marker.enabled = leg != null
            if (leg == null) continue
            val goal = leg.points.last()
            terrain.placeRing(marker, goal)
            if (leg in trails) continue
            val firstSide = route.legs.getOrNull(index - 1)?.let { trails[it]?.nextSide } ?: 1
            val footprints = ShoeprintTrail(terrain)
            val nextSide = footprints.trace(leg, firstSide)
            val entity = footprints.mesh()?.let { mesh ->
                Entity().apply {
                    components.set(TransformComponent())
                    // The SDK releases local materials with their last model. Each
                    // footprint leg owns its material for exactly that leg's lifetime.
                    val ink = glowMaterial(Color4.WHITE).apply { setCullingMode(MaterialCullingMode.NONE) }
                    components.set(ModelComponent(mesh, ink))
                    world.addChild(this)
                }
            }
            trails[leg] = Trail(entity, nextSide)
        }
    }

    fun cancel() {
        if (!route.active) return
        route.clear()
        refresh()
    }

    fun advance(walker: WorldWalker, dt: Float, lift: Float, collect: (Int) -> Unit) {
        val count = route.legs.size
        if (count == 0) return
        route.advance(walker, dt, lift, collect)
        if (count != route.legs.size) refresh()
    }
}

internal data class GroundPoint(val cell: Int, val x: Float, val z: Float)
internal class RouteLeg(val start: GroundPoint, val points: List<GroundPoint>)

internal class TapRoute(private val terrain: WorldTerrain) {
    val legs = ArrayDeque<RouteLeg>()
    private var taps = 0
    private var nextIndex = 0
    val active get() = legs.isNotEmpty()

    fun center(i: Int): GroundPoint {
        val c = terrain.cells[i]
        return GroundPoint(i, (c.ix + .5f) * terrain.cell, (c.iz + .5f) * terrain.cell)
    }

    fun select(point: Vector3, walker: WorldWalker) = floorTarget(point)?.takeIf { enqueue(it, walker) }

    fun floorTarget(point: Vector3): GroundPoint? {
        if (!point.x.isFinite() || !point.y.isFinite() || !point.z.isFinite()) return null
        val i = terrain.hitCell(point) ?: return null
        return terrain.legalPoint(i, point.x, point.z)
    }

    fun selectBeyondFloor(press: GroundPress, walker: WorldWalker) =
        beyondFloorTarget(press, walker)?.takeIf { enqueue(it, walker) }

    fun beyondFloorTarget(press: GroundPress, walker: WorldWalker): GroundPoint? {
        val start = routeStart(walker)
        val height = terrain.surfaceHeight(start.cell, start.x, start.z)
        val ray = press.hit - press.eye
        // Use the ray at ground level, or the low perimeter face for a near-horizontal
        // ray. Never extrapolate beyond the actual hit or back through the viewer.
        val t = if (ray.y < -.00001f) ((height - press.eye.y) / ray.y).coerceIn(0f,1f) else 1f
        val aim = press.eye + ray * t
        val dx = aim.x - start.x
        val dz = aim.z - start.z
        val length = hypot(dx,dz)
        if (length < .0001f) return null
        var best: GroundPoint? = null
        var bestDistance = Float.POSITIVE_INFINITY
        for ((i, c) in terrain.cells.withIndex()) {
            // Lower bound first; most cells are never projected.
            val ex = (abs((c.ix + .5f) * terrain.cell - aim.x) - .5f * terrain.cell).coerceAtLeast(0f)
            val ez = (abs((c.iz + .5f) * terrain.cell - aim.z) - .5f * terrain.cell).coerceAtLeast(0f)
            if (ex*ex + ez*ez >= bestDistance) continue
            val goal = terrain.legalPoint(i, aim.x, aim.z)
            val along = ((goal.x-start.x)*dx + (goal.z-start.z)*dz) / length
            if (along < .0001f) continue
            val dy = terrain.surfaceHeight(i,goal.x,goal.z) - height
            val score = (goal.x-aim.x)*(goal.x-aim.x) + (goal.z-aim.z)*(goal.z-aim.z) + dy*dy
            if (score < bestDistance) { bestDistance = score; best = goal }
        }
        return best
    }

    private fun routeStart(walker: WorldWalker) = if (taps == 3 || legs.isEmpty())
        GroundPoint(walker.cellIndex, walker.x, walker.z) else legs.last().points.last()

    fun enqueue(goal: GroundPoint, walker: WorldWalker): Boolean {
        val replace = taps == 3
        val start = routeStart(walker)
        val points = plan(start, goal)
        if (points.isEmpty()) return false
        if (replace) clear()
        legs.addLast(RouteLeg(start, points))
        taps++
        return true
    }

    fun clear() {
        legs.clear()
        taps = 0
        nextIndex = 0
    }

    // Check the same axis order, deck links and footprint as WorldWalker.
    private fun clearSegment(a: GroundPoint, b: GroundPoint): Boolean {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val pieces = ceil(hypot(dx, dz) / (terrain.cell * .1f)).toInt().coerceAtLeast(1)
        var previousZ = a.z
        var i = a.cell
        for (step in 1..pieces) {
            val f = step.toFloat() / pieces
            val x = a.x + dx * f
            val z = a.z + dz * f
            i = terrain.nextCell(i, x, previousZ)
            if (i < 0 || !terrain.fitsFootprint(i, x, previousZ)) return false
            i = terrain.nextCell(i, x, z)
            if (i < 0 || !terrain.fitsFootprint(i, x, z)) return false
            previousZ = z
        }
        return i == b.cell
    }

    private fun plan(start: GroundPoint, goal: GroundPoint): List<GroundPoint> {
        val path = mutableListOf<GroundPoint>()
        if (clearSegment(start, goal)) {
            return listOf(goal)
        }
        val previous = IntArray(terrain.cells.size) { -1 }
        val queue = ArrayDeque<Int>()
        queue.add(start.cell)
        previous[start.cell] = start.cell
        while (queue.isNotEmpty() && previous[goal.cell] < 0) {
            val i = queue.removeFirst()
            for (n in terrain.cells[i].links) if (n >= 0 && previous[n] < 0) {
                previous[n] = i
                queue.add(n)
            }
        }
        if (previous[goal.cell] < 0) return emptyList()
        val waypoints = ArrayDeque<GroundPoint>()
        waypoints.add(goal)
        var cursor = goal.cell
        while (cursor != start.cell) {
            waypoints.addFirst(center(cursor))
            cursor = previous[cursor]
        }
        waypoints.addFirst(center(start.cell))
        var anchor = start
        var last = start
        for (point in waypoints) {
            if (!clearSegment(anchor, point)) {
                path.add(last)
                anchor = last
            }
            last = point
        }
        path.add(goal)
        return path
    }

    fun advance(walker: WorldWalker, dt: Float, lift: Float, collect: (Int) -> Unit): Boolean {
        var remaining = dt.coerceIn(0f, .25f)
        while (remaining > 0f && active) {
            val leg = legs.first()
            val next = leg.points[nextIndex]
            val dx = next.x - walker.x
            val dz = next.z - walker.z
            val length = hypot(dx, dz)
            if (length < .00001f) {
                nextIndex++
                if (nextIndex == leg.points.size) {
                    legs.removeFirst()
                    nextIndex = 0
                    if (legs.isEmpty()) taps = 0
                }
                continue
            }
            val speed = walker.pace(.85f) / WORLD_SCALE
            val step = minOf(remaining, length / speed, terrain.cell * .1f / speed)
            val oldX = walker.x
            val oldZ = walker.z
            walker.advance(dx / length, dz / length, .85f, step, lift, collect)
            remaining -= step
            if (walker.x == oldX && walker.z == oldZ) {
                // A sub-ULP tail after a waypoint cannot move a Float coordinate.
                // Keep the route and spend the next frame's full movement budget.
                if (step * speed < .00001f) break
                clear()
                break
            }
        }
        return active
    }
}
