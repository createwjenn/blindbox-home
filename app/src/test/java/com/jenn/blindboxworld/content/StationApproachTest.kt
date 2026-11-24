package com.jenn.blindboxworld.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class StationApproachTest {
    @Test fun physicalFloorContactPreservesTheTappedSpotAndRejectsOffPlaneHits() {
        assertEquals(FloorPoint(.38f,-.52f), floorContact(RoomSpot(.38f,.012f,-.52f)))
        assertNull(floorContact(RoomSpot(.72f,1.14f,-3.8f)))
        assertNull(floorContact(RoomSpot(5.5f,.012f,-.52f)))
        assertNull(floorContact(RoomSpot(Float.NaN,.012f,-.52f)))
    }

    @Test fun aTapThroughTheStandFromTheDoorStopsAtTheStandWhileClearFloorRemainsReachable() {
        val door = RoomSpot(doorwayView.x, doorwayView.y, doorwayView.z)
        val stand = RoomSpot(0f, 0f, ISLAND_Z)
        assertTrue(stationOccludesFloor(door, FloorPoint(.72f, -3.8f), stand, false))
        assertFalse(stationOccludesFloor(door, FloorPoint(-1.05f, -3.5f), stand, false))
        assertFalse(stationOccludesFloor(door, FloorPoint(-1f, 1.4f), stand, false))
        val moved = RoomSpot(1.5f, 0f, -1f)
        assertTrue(stationOccludesFloor(RoomSpot(2f,1.55f,2f), FloorPoint(1.15f,-3f), moved, false))
    }

    @Test fun floorAtTheEdgeOfAChairStopsAtClearanceWithoutPassingTheTap() {
        val eye = FloorPoint(doorwayView.x, doorwayView.z)
        val tap = FloorPoint(.214f,2.262f)
        assertFalse(RoomFloor.isWalkable(tap))
        val stop = requireNotNull(RoomFloor.tapDestination(eye,tap))
        assertTrue(RoomFloor.isWalkable(stop))
        assertTrue(stop.distanceTo(tap) <= .20f)
        assertTrue(stop.distanceTo(eye) < tap.distanceTo(eye))
    }

    @Test fun listeningAreaUsesTheActualDeckPositionAndAcceptsLevelGaze() {
        val deck = RoomSpot(-.55f, .905f, -4.12f)
        assertTrue(lookingAtListeningArea(RoomSpot(-.85f,1.55f,-3.34f),0f,0f,-1f,deck))
        assertFalse(lookingAtListeningArea(RoomSpot(-.85f,1.55f,-3.34f),0f,0f,1f,deck))
        assertFalse(lookingAtListeningArea(RoomSpot(-.85f,1.55f,-1f),0f,0f,-1f,deck))
    }

    @Test fun aTapBesideAnExhibitFramesItsEntireFrontFromWalkableFloor() {
        for ((spot, machine) in listOf(RoomSpot(0f,0f,ISLAND_Z) to false, RoomSpot(GACHA_X,0f,GACHA_Z) to true)) {
            val (dx, dz) = rotY(0f, .85f, if (machine) GACHA_YAW else 0f)
            assertTrue(inStationApproach(spot.x + dx, spot.z + dz, spot, machine))
            assertFalse(inStationApproach(spot.x + 3.3f, spot.z + 2.9f, spot, machine))
            val view = stationView(spot, machine)
            assertTrue(RoomFloor.isWalkable(FloorPoint(view.x,view.z)))
            assertTrue(hypot(view.x - spot.x, view.z - spot.z) >= 2.4f)
            val (vx, vz) = rotY(0f, -1f, view.heading)
            val r = hypot(spot.x - view.x, spot.z - view.z)
            assertEquals((spot.x - view.x)/r, vx, .001f)
            assertEquals((spot.z - view.z)/r, vz, .001f)
        }
    }

    @Test fun ordinaryTapPreservesHeadingAndUserSteeringInterruptsStationTurn() {
        val m = RoomMotion()
        m.observe(FloorPoint(doorwayView.x, doorwayView.z), -15f)
        assertTrue(m.walkTo(-1.50f, 2.3f))
        assertEquals(0f, m.steerTowardStation(-15f,.02f), 0f)
        val target = stationView(RoomSpot(0f,0f,ISLAND_Z), false)
        assertTrue(m.walkTo(target.x, target.z, target.heading))
        assertTrue(m.steerTowardStation(-15f,.02f) < 0f)
        m.nudge(forward = 1f)
        assertEquals(0f, m.steerTowardStation(-14f,.02f), 0f)
    }
}
