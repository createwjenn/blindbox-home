package com.jenn.blindboxworld.content

import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomFloorTest {
    private val door = FloorPoint(-1.88f, 3.83f)

    /** Play back straight route legs using the normal manual collision path at 5 cm increments. */
    private fun follow(start: FloorPoint, route: List<FloorPoint>): FloorPoint {
        var current = start
        for (destination in route) {
            val origin = current
            val frames = ceil(origin.distanceTo(destination) / .05f).toInt().coerceAtLeast(1)
            for (frame in 1..frames) {
                val t = frame.toFloat() / frames
                val x = origin.x + (destination.x - origin.x) * t
                val z = origin.z + (destination.z - origin.z) * t
                current = RoomFloor.step(current, x - current.x, z - current.z)
                assertTrue("Stuck on path to $destination", current.distanceTo(FloorPoint(x, z)) < .0001f)
                assertTrue(RoomFloor.isWalkable(current))
            }
        }
        return current
    }

    @Test fun doorReachesShelfMachineAndPreciseTap() {
        val targets = listOf(FloorPoint(-.6143f, -3.4012f), FloorPoint(-2.65f, -2.60f))
        for (target in targets) {
            val path = RoomFloor.route(door, target)
            assertEquals(target, path.last())
            assertTrue(follow(door, path).distanceTo(target) < .0001f)
        }
    }

    @Test fun tapAcrossPlinthGoesAroundItsFootprint() {
        val a = FloorPoint(0f, -.73f)
        val b = FloorPoint(0f, -2.85f)
        val path = RoomFloor.route(a, b)
        assertTrue(path.size > 1)
        assertTrue(follow(a, path).distanceTo(b) < .0001f)
        val straight = RoomFloor.step(a, 0f, -3f)
        assertTrue(straight.z > -1.245f)
        assertTrue(RoomFloor.isWalkable(straight))
        val slide = RoomFloor.step(straight, .25f, -.22f)
        assertTrue(slide.x > .24f && slide.z > -1.245f)
    }

    @Test fun secondTapRetargetsFromCurrentPosition() {
        val oldTarget = FloorPoint(1.65f, 1.58f)
        val initial = RoomFloor.route(door, oldTarget)
        assertTrue(initial.isNotEmpty())
        val first = initial.first()
        val current = FloorPoint(door.x + (first.x - door.x) * .3f, door.z + (first.z - door.z) * .3f)
        val newTarget = FloorPoint(0f, -3.18f)
        val updated = RoomFloor.route(current, newTarget)
        assertTrue(updated.isNotEmpty() && oldTarget !in updated)
        assertTrue(follow(current, updated).distanceTo(newTarget) < .0001f)
    }

    @Test fun wallsClosedAperturesAndFurnitureRejectTaps() {
        for (invalid in listOf(
            FloorPoint(-1.88f, 4.32f), FloorPoint(-4.3f, 0f), FloorPoint(-3.6f, -4.40f),
            FloorPoint(-3.62f,-3.70f), FloorPoint(1.9f,-.33f), FloorPoint(2.10f,-1.63f),
            FloorPoint(1.16f,3.27f), FloorPoint(1.29f,3.95f), FloorPoint(.61f,2.61f), FloorPoint(0f,-4.12f),
            FloorPoint(-3.22f,.65f), FloorPoint(-3.09f,1.54f), FloorPoint(Float.NaN,0f)
        )) {
            assertFalse("Obstruction accepted: $invalid", RoomFloor.isWalkable(invalid))
            assertTrue(RoomFloor.route(door, invalid).isEmpty())
        }
        assertEquals(FloorPoint(-1.88f, 3.98f), RoomFloor.step(door, 0f, 40f))
        assertTrue(RoomFloor.route(door, door).isEmpty())
    }

    @Test fun sustainedMovementMaintainsFurnitureAndWallClearance() {
        RoomFloor.setFurnishings(listOf(FloorObstacle(0f,0f,1f,1f)))
        try {
            var at=FloorPoint(0f,2f)
            repeat(300) { at=RoomFloor.step(at,0f,-.05f) }
            assertTrue("Stop at least 30 cm before the object", at.z >= .80f)
            at=FloorPoint(-2f,0f)
            repeat(300) { at=RoomFloor.step(at,-.05f,0f) }
            assertEquals(-3.90f,at.x,.001f)
        } finally { RoomFloor.setFurnishings(null) }
    }

    @Test fun legacySeatedSpawnCanLeaveFurniture() {
        val seat = FloorPoint(2.14f, -1.66f)
        val recovered = RoomFloor.step(seat, 0f, 0f)
        val path = RoomFloor.route(seat, door)
        assertEquals(recovered, path.first())
        assertTrue(RoomFloor.isWalkable(recovered))
        assertTrue(follow(recovered, path.drop(1)).distanceTo(door) < .0001f)
    }
}
