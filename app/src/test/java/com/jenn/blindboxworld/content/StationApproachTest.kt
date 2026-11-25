package com.jenn.blindboxworld.content

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class StationApproachTest {
    @Test fun solidPadOwnsItsDiscFromEitherFloorInputHeight() {
        val eye = RoomSpot(1f, 1.55f, 3f)
        for (height in listOf(0f, .018f, STATION_RING_HEIGHT)) {
            for ((x,z,inside) in listOf(Triple(0f,0f,true), Triple(.27f,0f,true),
                Triple(.28f,0f,false), Triple(.22f,.22f,false))) {
                val t = (eye.y-height)/(eye.y-STATION_RING_HEIGHT)
                val contact = RoomSpot(eye.x+(x-eye.x)*t, height, eye.z+(z-eye.z)*t)
                assertEquals(inside, requireNotNull(stationRingContact(eye,contact)).insideStationRing(0f,0f))
            }
        }
    }

    @Test fun shopWindowIsReachableAndTheOldStandFootprintIsClear() {
        val entrance = FloorPoint(doorwayView.x, doorwayView.z)
        val view = requireNotNull(shortcutView("shop", DecorPose(CornerShop.at, CornerShop.yaw)))
        val counter = FloorPoint(view.x, view.z)
        assertTrue(RoomFloor.isWalkable(FloorPoint(3.44f, 3.73f)))
        assertFalse(CornerShop.near(entrance.x, entrance.z))
        assertTrue(CornerShop.near(counter.x, counter.z))
        assertFalse(RoomFloor.isWalkable(FloorPoint(CornerShop.at.x, CornerShop.at.z)))
        assertEquals(counter, RoomFloor.route(entrance, counter).last())
    }

    @Test fun hoverRayFindsClearFloorWhenThereIsNoPhysicsContact() {
        val eye = RoomSpot(1f,1.5f,2f)
        assertEquals(FloorPoint(1f,-1f), projectedRoomFloor(eye,RoomSpot(1f,1f,1f)))
        assertNull(projectedRoomFloor(eye,RoomSpot(1f,1.5f,1f)))
        assertNull(projectedRoomFloor(eye,RoomSpot(1f,2f,1f)))
        assertNull(projectedRoomFloor(eye,RoomSpot(1f,1.4f,0f)))
    }

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

    @Test fun floorShortcutsArriveAtReachableViewsBeyondTheMarkers() {
        val stations = mapOf(
            "machine" to RoomSpot(GACHA_X, 0f, GACHA_Z),
            "stand" to RoomSpot(0f, 0f, ISLAND_Z),
            "record-player" to RoomSpot(-.55f, .905f, -4.12f)
        )
        for ((id, spot) in stations) {
            val view = requireNotNull(shortcutView(id, DecorPose(spot)))
            val pad = requireNotNull(stationPad(spot, id == "machine", 0f))
            val stop = FloorPoint(view.x, view.z)
            val center = FloorPoint(spot.x, spot.z)
            assertTrue(stop.distanceTo(center) > FloorPoint(pad.x, pad.z).distanceTo(center) + .6f)
            assertTrue(RoomFloor.route(FloorPoint(doorwayView.x, doorwayView.z), stop).isNotEmpty())
            val (dx, dz) = rotY(0f, -1f, view.heading)
            assertEquals((spot.x - stop.x) / stop.distanceTo(center), dx, .001f)
            assertEquals((spot.z - stop.z) / stop.distanceTo(center), dz, .001f)
            if (id == "record-player") assertTrue(lookingAtListeningArea(
                RoomSpot(view.x, view.y, view.z), dx, 0f, dz, spot))
        }
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

    @Test fun turningYourHeadDuringAnApproachReleasesStationFacingButKeepsWalking() {
        val motion = RoomMotion()
        motion.observe(FloorPoint(doorwayView.x, doorwayView.z), -15f)
        assertTrue(motion.walkTo(-1.50f, 2.3f, 90f))
        assertTrue(motion.steerTowardStation(179f, .02f) < 0f)
        assertTrue(motion.steerTowardStation(-179f, .02f) < 0f)
        assertEquals(0f, motion.steerTowardStation(-170f, .02f), 0f)
        assertEquals(0f, motion.steerTowardStation(179f, .02f), 0f)
        assertNotNull(motion.goal)
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
