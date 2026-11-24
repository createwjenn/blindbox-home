package com.jenn.blindboxworld.content

import org.junit.Assert.*
import org.junit.Test

class HudStillnessTest {
    @Test fun motionHidesAndBriefStopsDoNotFlashControls() {
        val hud = HudStillness()
        repeat(3) { hud.advance(.08f, true, false) }
        assertEquals(0f, hud.alpha, .001f)
        repeat(8) { hud.advance(.08f, false, false) }
        assertEquals(0f, hud.alpha, .001f)
        hud.advance(.08f, true, false)
        repeat(8) { hud.advance(.08f, false, false) }
        assertEquals(0f, hud.alpha, .001f)
        repeat(6) { hud.advance(.08f, false, false) }
        assertEquals(1f, hud.alpha, .001f)
    }

    @Test fun heldControlStaysVisibleUntilReleasedEvenWhileMoving() {
        val hud = HudStillness()
        repeat(20) { hud.advance(.08f, true, true) }
        assertEquals(1f, hud.alpha, .001f)
        repeat(3) { hud.advance(.08f, true, false) }
        assertEquals(0f, hud.alpha, .001f)
    }

    @Test fun openingMenuRestoresHiddenControls() {
        val hud = HudStillness()
        repeat(3) { hud.advance(.08f, true, false) }
        repeat(4) { hud.advance(.08f, true, true) }
        assertEquals(1f, hud.alpha, .001f)
    }
}
