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

    @Test fun cameraStopsBeforeARaisedBankAndPermitsAClearOverheadView() {
        val steps = listOf(
            WorldCell(0, 0, 0f, Float.NaN, IntArray(4) { -1 }),
            WorldCell(1, 0, .8f, Float.NaN, IntArray(4) { -1 }),
        )
        val floor = WorldTerrain(.5f, steps, emptyList(), 2f)
        val camera = WorldCameraClearance(floor)
        val focus = floor.actorStart + Vector3(0f, .44f, 0f)
        assertTrue(camera.available(focus, Vector3.RIGHT, 2f) < .6f)
        assertEquals(2f, camera.available(focus, Vector3.UP, 2f), .001f)
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
