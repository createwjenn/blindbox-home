package com.jenn.blindboxworld.content

import com.google.gson.JsonParser
import com.pico.spatial.core.math.Vector3
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class WorldCourseTest {
    private fun terrain(figure: String): WorldTerrain {
        val assets = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
        val graph = JsonParser.parseReader(File(assets, "worlds/$figure/navigation.json").reader()).asJsonObject
        val nodes = graph.getAsJsonArray("nodes").map { value ->
            val row = value.asJsonArray
            WorldCell(row[0].asInt, row[1].asInt, row[2].asFloat,
                if (row[3].isJsonNull) Float.NaN else row[3].asFloat, IntArray(4) { row[4 + it].asInt })
        }
        return WorldTerrain(graph["cell"].asFloat, nodes, graph["pickups"].asJsonArray.map { it.asInt },
            graph["runSpeed"].asFloat)
    }

    private fun route(terrain: WorldTerrain, from: Int, targets: Set<Int>): List<Int> {
        val previous = IntArray(terrain.cells.size) { -1 }
        val queue = ArrayDeque<Int>()
        queue.add(from)
        previous[from] = from
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (next in targets) {
                val result = ArrayList<Int>()
                var i = next
                while (i != from) { result.add(i); i = previous[i] }
                return result.asReversed()
            }
            for (link in terrain.cells[next].links) if (link >= 0 && previous[link] == -1) {
                previous[link] = next
                queue.add(link)
            }
        }
        error("No route to a remaining target")
    }

    @Test fun tappedRoutesReachOffCentreGoalsWithoutPausingAtWaypoints() {
        for (figure in FIGURE_IDS) {
            val t = terrain(figure)
            val walker = WorldWalker(t, null)
            val route = TapRoute(t)
            for (i in t.pickupCells) {
                val c = t.cells[i]
                val hit = Vector3((c.ix + .55f) * t.cell, c.ground, (c.iz + .46f) * t.cell)
                val goal = requireNotNull(route.select(hit, walker))
                assertEquals(hit.x, goal.x, .00001f)
                assertEquals(hit.z, goal.z, .00001f)
                var frames = 0
                while (route.active && frames++ < 4000) {
                    val x = walker.x
                    val z = walker.z
                    route.advance(walker, if (frames % 3 == 0) .061f else .016f, 0f) {}
                    if (route.active) assertTrue("$figure stopped on way to $i", walker.x != x || walker.z != z)
                }
                assertFalse("$figure timed out at $i", route.active)
                assertEquals("$figure destination x at $i", hit.x, walker.x, .00002f)
                assertEquals("$figure destination z at $i", hit.z, walker.z, .00002f)
                assertEquals(i, walker.cellIndex)
            }
        }
    }

    private fun straightCourse() = WorldTerrain(1f, List(9) { i ->
        WorldCell(i, 0, 0f, Float.NaN, intArrayOf(if (i < 8) i + 1 else -1, i - 1, -1, -1))
    }, emptyList(), 8f)

    @Test fun hoveringNeverQueuesOrReplacesAWaypoint() {
        val map = straightCourse()
        val walker = WorldWalker(map, null)
        val route = TapRoute(map)
        val hit = Vector3(2.4f, 0f, .52f)
        val preview = requireNotNull(route.floorTarget(hit))
        assertFalse(route.active)
        assertEquals(preview, route.select(hit, walker))
        val first = route.legs.single()
        val distant = GroundPress(Vector3(.5f, 2f, .5f), Vector3(14f, -.15f, .5f))
        val edge = requireNotNull(route.beyondFloorTarget(distant, walker))
        assertEquals(first, route.legs.single())
        assertEquals(edge, route.selectBeyondFloor(distant, walker))
    }

    @Test fun threeClicksRunThroughBothStopsWithinTheSameFrameBudget() {
        val map = straightCourse()
        val walker = WorldWalker(map, null)
        val route = TapRoute(map)
        for (x in listOf(.55f, .60f, 4.5f)) requireNotNull(route.select(Vector3(x, 0f, .5f), walker))
        assertEquals(listOf(.5f, .55f, .60f), route.legs.map { it.start.x })
        route.advance(walker, .20f, 0f) {}
        assertEquals(1, route.legs.size)
        assertEquals(.5f + walker.pace(.85f) * .2f / WORLD_SCALE, walker.x, .0001f)

        // Even after two arrivals a fourth valid click starts a new group at the live feet.
        assertNull(route.select(Vector3(-3f, 0f, -3f), walker))
        requireNotNull(route.select(Vector3(2.5f, 0f, .5f), walker))
        assertEquals(walker.x, route.legs.single().start.x, 0f)
        assertEquals(2.5f, route.legs.single().points.last().x, 0f)
        repeat(2) { requireNotNull(route.select(Vector3(3.5f + it, 0f, .5f), walker)) }
        assertEquals(3, route.legs.size)
        repeat(100) { route.advance(walker, .1f, 0f) {} }
        assertFalse(route.active)
        assertEquals(4.5f, walker.x, .0001f)
    }

    @Test fun edgeTapsKeepTheirAlongWallCoordinateAndFarTapsContinueForward() {
        val terrain = straightCourse()
        val walker = WorldWalker(terrain, null)
        val route = TapRoute(terrain)
        val edge = requireNotNull(route.select(Vector3(2.88f, 0f, .99f), walker))
        assertEquals(2.88f, edge.x, .00001f)
        assertTrue(terrain.fitsFootprint(edge.cell,edge.x,edge.z))
        route.clear()
        val far = requireNotNull(route.selectBeyondFloor(
            GroundPress(Vector3(.5f,2f,.5f),Vector3(14f,-.15f,.5f)),walker))
        assertEquals(8, far.cell)
        repeat(150) { route.advance(walker,.073f,0f) {} }
        assertEquals(far.x,walker.x,.00002f)
        assertEquals(far.z,walker.z,.00002f)
        assertFalse(route.active)
        route.clear()
        val shallow = requireNotNull(route.selectBeyondFloor(
            GroundPress(Vector3(8f,.2f,.5f), Vector3(-1f,.2f,.5f)),walker))
        assertEquals(0,shallow.cell)
    }

    @Test fun boundaryAndCornerDestinationsRemainReachableAcrossEveryWorld() {
        val random = kotlin.random.Random(147)
        for (id in FIGURE_IDS) {
            val terrain = terrain(id)
            val walker = WorldWalker(terrain,null)
            val route = TapRoute(terrain)
            val edges = terrain.cells.indices.filter { -1 in terrain.cells[it].links }
            repeat(50) {
                val index = edges.random(random)
                val c = terrain.cells[index]
                val x = (c.ix + if (random.nextBoolean()) .999f else .001f) * terrain.cell
                val z = (c.iz + if (random.nextBoolean()) .999f else .001f) * terrain.cell
                val goal = requireNotNull(route.select(Vector3(x,terrain.surfaceHeight(index,x,z),z),walker))
                assertTrue(terrain.fitsFootprint(goal.cell,goal.x,goal.z))
                var steps = 0
                while (route.active && steps++ < 5000) route.advance(walker,.039f,0f) {}
                assertEquals("$id edge $index x",goal.x,walker.x,.00002f)
                assertEquals("$id edge $index z",goal.z,walker.z,.00002f)
            }
        }
    }

    @Test fun cameraKeepsClearanceOnSlopesWaterStackedDecksAndBeyondThePlayableEdge() {
        val map = WorldTerrain(1f, listOf(
            WorldCell(0,0,0f,Float.NaN,intArrayOf(1,-1,-1,-1)),
            WorldCell(1,0,1f,Float.NaN,intArrayOf(-1,0,-1,-1)),
            WorldCell(1,0,4f,Float.NaN,intArrayOf(-1,-1,-1,-1)),
            WorldCell(0,1,-1f,.35f,intArrayOf(-1,-1,-1,-1))
        ), emptyList(), 2f)
        fun at(x: Float,y: Float,z: Float) = map.origin + Vector3(x,y,z) * WORLD_SCALE
        val low = map.cameraEye(at(1.25f,-1f,.5f), map.actorStart)
        assertEquals(at(1.25f,.75f,.5f).y + .24f, low.y, .0001f)
        assertEquals(at(1.5f,4f,.5f).y - .24f,
            map.cameraEye(at(1.5f,6f,.5f), map.actorStart).y, .0001f)
        val water = map.cameraEye(at(.5f,-2f,1.5f), at(.5f,-1f,1.5f))
        assertEquals(at(.5f,.35f,1.5f).y + .24f, water.y, .0001f)
        val acrossWall = map.cameraEye(at(.5f,1f,1.5f), map.actorStart)
        assertTrue(acrossWall.z < at(.5f,1f,1f).z)
        val beyondEdge = map.cameraEye(at(4f,1f,.5f), map.actorStart)
        assertTrue(beyondEdge.x in at(1.5f,1f,.5f).x..at(1.58f,1f,.5f).x)
    }

    @Test fun orbitAtWorldBoundariesKeepsTheEyeInsideTheCapture() {
        for (id in FIGURE_IDS) {
            val t = terrain(id)
            for ((i,node) in t.cells.withIndex()) {
                if (-1 !in node.links || i % 7 != 0) continue
                val feet = t.origin + Vector3((node.ix+.5f)*t.cell, node.ground, (node.iz+.5f)*t.cell)*WORLD_SCALE
                for ((x,z) in listOf(3.5f to 0f, -3.5f to 0f, 0f to 3.5f, 0f to -3.5f, 2.5f to 2.5f)) {
                    val eye = t.cameraEye(feet + Vector3(x,1.4f,z), feet, i)
                    val local = (eye-t.origin) / WORLD_SCALE
                    assertNotNull("$id eye escaped at cell $i", t.hitCell(local))
                }
            }
        }
    }

    private fun follow(path: List<Int>, terrain: WorldTerrain, walker: WorldWalker, onPickup: (Int) -> Unit): Float {
        var seconds = 0f
        for (loc in path) {
            val node = terrain.cells[loc]
            val x = (node.ix + .5f) * terrain.cell
            val z = (node.iz + .5f) * terrain.cell
            var frames = 0
            while (abs(walker.x - x) > .0001f || abs(walker.z - z) > .0001f) {
                val dx = x - walker.x
                val dz = z - walker.z
                val distance = hypot(dx, dz)
                val dt = (distance * WORLD_SCALE / walker.pace(1f)).coerceAtMost(1f/72f)
                walker.advance(dx/distance, dz/distance, 1f, dt, 0f, onPickup)
                seconds += dt
                assertTrue("Blocked at cell $loc", ++frames <= 120)
            }
            assertEquals(loc, walker.cellIndex)
        }
        return seconds
    }

    @Test fun allEightCoursesFinishThroughActualContactBelowOneMinute() {
        for (figure in FIGURES) {
            val terrain = terrain(figure.id)
            assertEquals(WORLD_PICKUPS, terrain.pickupCells.distinct().size)
            val walker = WorldWalker(terrain, figure.swimNeckHeight?.let {
                it * ACTOR_HEIGHT / figure.actorRigHeight / WORLD_SCALE
            })
            val caught = mutableSetOf<Int>()
            val onPickup: (Int) -> Unit = { assertTrue(caught.add(it)) }
            walker.collect(0f, onPickup)
            var seconds = 0f
            while (caught.size < WORLD_PICKUPS) {
                val targets = terrain.pickupCells.filterIndexed { i, _ -> i !in caught }.toSet()
                val path = route(terrain, walker.cellIndex, targets)
                assertTrue("Missing collision on starting cell in ${figure.id}", path.isNotEmpty())
                seconds += follow(path, terrain, walker, onPickup)
            }
            assertTrue("${figure.id} took ${seconds}s", seconds <= 60)
            println("${figure.id}: ${caught.size} physical contacts in ${seconds}s")
        }
    }

    @Test fun holdingLeftFromTheThreeBanksEntersDeepWaterAndReturns() {
        for (id in listOf("secret-capybara-toilet", "niu-lai", "tralalero-tralala")) {
            val figure = infoOf(id)
            val terrain = terrain(id)
            if (id == "secret-capybara-toilet") assertEquals(2f, terrain.runSpeed, 0f)
            val neck = figure.swimNeckHeight!! * ACTOR_HEIGHT / figure.actorRigHeight / WORLD_SCALE
            val walker = WorldWalker(terrain, neck)
            val start = walker.x
            var frames = 0
            while (!walker.swimming || walker.waterDepth < neck) {
                walker.advance(-1f, 0f, 1f, 1f/72, 0f) { }
                assertTrue("Blocked at the bank in $id", ++frames < 4*72)
            }
            assertEquals(neck, terrain.cells[walker.cellIndex].water - walker.support, .0001f)
            frames = 0
            while (walker.x < start) {
                walker.advance(1f, 0f, 1f, 1f/72, 0f) { }
                assertTrue("Cannot climb out in $id", ++frames < 4*72)
            }
            assertFalse(walker.swimming)
        }
    }

    @Test fun everySwimmingRigCanEnterNeckDeepWaterAndClimbOut() {
        for (figure in FIGURES.filter { it.hasWater }) {
            val terrain = terrain(figure.id)
            val neck = figure.swimNeckHeight!! * ACTOR_HEIGHT / figure.actorRigHeight / WORLD_SCALE
            val walker = WorldWalker(terrain, neck)
            val deep = terrain.cells.indices.filter {
                terrain.cells[it].water - terrain.cells[it].ground >= neck + .025f
            }.toSet()
            follow(route(terrain, walker.cellIndex, deep), terrain, walker) { }
            assertTrue(figure.id, walker.swimming)
            assertEquals(neck, terrain.cells[walker.cellIndex].water - walker.support, .0001f)
            val dry = terrain.cells.indices.filter { !terrain.cells[it].water.isFinite() }.toSet()
            follow(route(terrain, walker.cellIndex, dry), terrain, walker) { }
            assertFalse(figure.id, walker.swimming)
            assertEquals(walker.ground, walker.support, .0001f)
        }
    }

    @Test fun curvedGardenBankRaisesSwimmerOntoScannedMudAndPath() {
        val map = terrain("secret-capybara-toilet")
        val walker = WorldWalker(map, .23f)
        fun index(x: Int, z: Int) = map.cells.indexOfFirst { it.ix == x && it.iz == z }
        // Along this curve the old rectangular pond placed the bed below
        // exposed mud, including (-1, 6), whose scan height is above water.
        for ((wet, mud, z) in listOf(Triple(-8, -1, 6), Triple(-24, -15, 17))) {
            follow(route(map, walker.cellIndex, setOf(index(wet, z))), map, walker) { }
            assertTrue(walker.swimming)
            var frames = 0
            while (map.cells[walker.cellIndex].ix < mud) {
                walker.advance(1f, 0f, 1f, 1/72f, 0f) { }
                assertTrue("Could not leave pond at ($mud, $z)", ++frames < 200)
            }
            assertFalse(walker.swimming)
            assertTrue("Below the photographed bank", walker.support > -.64f)
        }
    }

    @Test fun bodyKeepsClearanceAtWallsAndCannotCutAnInsideCorner() {
        // L-shaped passage, with the north-east cell obstructed.
        val map = WorldTerrain(1f, listOf(
            WorldCell(0, 0, 0f, Float.NaN, intArrayOf(1, -1, 2, -1)),
            WorldCell(1, 0, 0f, Float.NaN, intArrayOf(-1, 0, -1, -1)),
            WorldCell(0, 1, 0f, Float.NaN, intArrayOf(-1, -1, -1, 0))
        ), emptyList(), 2f)
        val w = WorldWalker(map, null)
        repeat(100) { w.advance(-1f, 0f, 1f, .25f, 0f) { } }
        assertTrue("Body overlaps the west wall", w.x >= .43f)
        assertFalse("Body overlaps blocked corner", map.fitsFootprint(0, .9f, .9f))
        assertTrue("Wall slide along the clear lane", map.fitsFootprint(0, .92f, .5f))
    }

    @Test fun rewardStepsIncludeTheExactThreshold() {
        assertEquals(10, worldRewardCoins(0L))
        for (step in 0..8) {
            val limit = PAINTING_TIME_MS + step * 10_000L
            assertEquals(10 - step, worldRewardCoins(limit))
            assertEquals(9 - step, worldRewardCoins(limit + 1))
        }
        assertEquals(1, worldRewardCoins(Long.MAX_VALUE))
    }
}
