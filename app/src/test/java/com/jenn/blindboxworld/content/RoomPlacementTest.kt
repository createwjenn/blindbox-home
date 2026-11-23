package com.jenn.blindboxworld.content

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class RoomPlacementTest {
    @Test fun switchingTheDisplayPreservesOtherCollectiblesAndTheirSpots() {
        val farShelf = RoomSpot(.55f, .416f, -4.12f)
        val nearShelf = RoomSpot(-.55f, .416f, -4.12f)
        val placements = mapOf("chosen" to farShelf, "other" to nearShelf)
        assertEquals(mapOf("previous" to farShelf, "other" to nearShelf),
            placements.switchDisplay("previous", "chosen"))
        assertEquals(mapOf("other" to nearShelf), placements.switchDisplay(null, "chosen"))
        assertEquals(placements, placements.switchDisplay("previous", "stored"))
        assertEquals(placements, placements.switchDisplay("previous", "previous"))
    }

    @Test fun storageRequiresExplicitPlacementAndPreservesShelfHeight() {
        assertNull(RoomSpot.decode(null))
        for (slot in SHELF_SLOTS) {
            val chosen = RoomSpot(slot.x, slot.y, slot.z)
            assertEquals(chosen, RoomSpot.decode(chosen.encode()))
        }
        assertNull(RoomSpot.decode("0,0"))
        assertNull(RoomSpot.decode("0,NaN,0"))
        assertNull(RoomSpot.decode("20,0,0"))
    }

    @Test fun shelfShortcutSkipsOccupiedSlots() {
        val chosen = mutableListOf<RoomSpot>()
        repeat(SHELF_SLOTS.size) {
            val next = requireNotNull(freeShelfSpot(chosen))
            assertFalse(next in chosen)
            chosen.add(next)
        }
        assertNull(freeShelfSpot(chosen))
    }

    @Test fun aimHitsEachShelfTierAndTheFloor() {
        for (height in listOf(.905f, .416f, .106f)) {
            val spot = requireNotNull(supportingPose(RoomSpot(1.3f, height + .24f, -3.64f), 0f, -.5f, -1f)).at
            assertEquals(height, spot.y, .0001f)
            assertEquals(-4.12f, spot.z, .0001f)
        }
        assertEquals(.002f, requireNotNull(supportingPose(RoomSpot(0f,1.6f,2f),0f,-1f,0f)).at.y, .0001f)
    }

    @Test fun pointingAcrossTheRoomReachesTheActualFloorAndMissesDoNotFloat() {
        val eye = RoomSpot(0f, 1.6f, 3f)
        assertNull(supportingPose(eye, 0f, 0f, -1f))
        assertNull(supportingPose(eye, 0f, -.01f, -1f))
        val farFloor = requireNotNull(supportingPose(eye, 0f, -1.598f, -6f, emptyList()))
        assertEquals(-3f, farFloor.at.z, .0001f)
    }

    @Test fun aPaintingFacesIntoTheRoomAndRestsFlushOnThePointedWall() {
        val eye = RoomSpot(0f, 1.6f, 0f)
        val east = requireNotNull(wallPose(eye, 1f, .1f, .2f, .08f))
        assertEquals(-90f, east.yaw, .001f)
        assertEquals(4.28f, east.at.x + .04f, .001f)
        assertEquals(1.6f + east.at.x*.1f, east.at.y, .001f)
        assertEquals(east.at.x*.2f, east.at.z, .001f)
        val across = requireNotNull(wallPose(eye, 0f, .05f, 1f, .04f))
        assertEquals(180f, across.yaw, .001f)
        assertEquals(4.39f, across.at.z + .02f, .001f)
        assertNull(wallPose(eye, -1f, 0f, 0f, .04f))
        assertNull(wallPose(eye, .2f, 1f, 0f, .04f))
    }

    @Test fun previewAndCommittedRoomUseTheSamePaintingAndFurnitureTransforms() {
        val storage = mutableMapOf<String, String>()
        val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { self, method, args ->
            when (method.name) {
                "putString" -> { storage[args[0] as String] = args[1] as String; self }
                "apply" -> null
                else -> error("Unexpected editor call ${method.name}")
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> storage[args[0]] ?: args[1]
                "getStringSet" -> args[1]
                "edit" -> editor
                else -> error("Unexpected preference call ${method.name}")
            }
        } as SharedPreferences
        val room = RoomFurnishing(emptyList(), preferences)
        val eye = RoomSpot(0f,1.8f,0f)
        fun place(id: String, dx: Float, dy: Float, dz: Float): DecorPose {
            room.begin(id)
            room.updateAim(eye, dx, dy, dz)
            val preview = room.visualPose(id)
            assertTrue(room.visibleAtAim(id))
            assertEquals(requireNotNull(room.target).pose, preview)
            assertTrue(room.putDown())
            assertEquals(preview, room.visualPose(id))
            assertEquals(preview, RoomFurnishing(emptyList(), preferences).pose(id))
            return preview
        }
        val frame = place("unboxing-print", 0f,0f,1f)
        assertEquals(180f, frame.yaw, .001f)
        assertEquals(4.39f - .02f, frame.at.z, .001f)
        val stand = place("stand", -.9f,-1.798f,.5f)
        assertEquals(.002f, stand.at.y, .001f)
        room.begin("unboxing-print")
        room.updateAim(eye, -1f,0f,0f)
        assertFalse(room.visibleAtAim("unboxing-print"))
        assertFalse(room.putDown())
        room.cancel()
        assertEquals(frame, room.visualPose("unboxing-print"))
    }

    @Test fun arbitraryDraggedPositionsStayInsideAndSurviveReload() {
        val shifted = RoomSpot(.6f, 1.22f, 1.48f).moved(-1.33f, .42f, .18f)
        assertEquals(shifted, RoomSpot.decode(shifted.encode()))
        assertTrue(shifted.moved(99f,99f,99f).inside())
        assertTrue(shifted.moved(-99f,-99f,-99f).inside())
    }
}
