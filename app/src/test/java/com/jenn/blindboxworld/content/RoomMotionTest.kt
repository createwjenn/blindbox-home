package com.jenn.blindboxworld.content

import org.junit.Assert.*
import org.junit.Test

class RoomMotionTest {
    @Test fun routedWalkMaintainsAFrameSpeedLimitAndReportsItsActualEndpoint() {
        val start = FloorPoint(0f, -.73f)
        val target = FloorPoint(0f, -2.85f)
        val motion = RoomMotion()
        motion.observe(start, 0f)
        assertTrue(motion.walkTo(target.x, target.z))
        var current = start
        repeat(600) {
            val next = motion.follow(current, .025f)
            assertTrue(current.distanceTo(next) <= .0251f)
            assertTrue(RoomFloor.isWalkable(next))
            motion.observe(next, 0f)
            current = next
        }
        assertTrue(current.distanceTo(target) < .002f)
        assertEquals(current, motion.lastArrival)
        assertNull(motion.goal)
        assertTrue(motion.status.startsWith("Arrived at"))
    }

    @Test fun manualDeflectionCancelsTheRouteWithoutReportingAnArrival() {
        val motion = RoomMotion()
        motion.observe(FloorPoint(0f, -.73f), 0f)
        assertTrue(motion.walkTo(0f, -2.85f))
        motion.input.pad = StickAxes(turn = 1f)
        val delta = motion.controls(.02f, enableTurn = true)
        assertTrue(delta.turn > 0f)
        assertNull(motion.goal)
        assertNull(motion.lastArrival)
    }

    @Test fun aDiscreteTurnCompletesInFramesWithoutAddingATranslation() {
        val start = FloorPoint(-1.88f, 3.83f)
        val motion = RoomMotion()
        motion.observe(start, 0f)
        assertTrue(motion.nudge(turn = -1f))
        var angle = 0f
        repeat(100) {
            val delta = motion.controls(.01f, enableTurn = true)
            assertEquals(0f, delta.x, 0f)
            assertEquals(0f, delta.forward, 0f)
            angle += delta.turn
            motion.observe(start, -angle)
        }
        assertEquals(-20f, angle, .001f)
        assertEquals(start, motion.position)
        assertTrue(motion.status.contains("heading 20°"))
    }
}
