package com.jenn.blindboxworld.content

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class NavigationKeysTest {
    @Test fun jumpWorksWithoutOnScreenPadsAndDoesNotRepeatWhileHeld() {
        for (code in listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A)) {
            val keys = NavigationKeys()
            var jumps = 0
            assertFalse(keys.handle(code, true))
            keys.onJump = { jumps++ }
            assertTrue(keys.handle(code, true))
            assertTrue(keys.handle(code, true, repeat = true))
            assertEquals(1, jumps)
            keys.handle(code, false)
            keys.handle(code, true)
            assertEquals(2, jumps)
            keys.clear()
            keys.handle(code, true)
            assertEquals(3, jumps)
        }
    }

    @Test fun escapeReturnsOnceAndConsumesReleaseAfterLeavingWorld() {
        val keys = NavigationKeys()
        var exits = 0
        keys.onExit = { exits++; keys.onExit = null }
        assertTrue(keys.handle(KeyEvent.KEYCODE_ESCAPE, true))
        assertTrue(keys.handle(KeyEvent.KEYCODE_ESCAPE, false))
        assertEquals(1, exits)
        assertFalse(keys.handle(KeyEvent.KEYCODE_ESCAPE, true))
    }

    @Test fun systemBackAndControllerBDoNotRepeatExitWhileHeld() {
        for (code in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B)) {
            val keys = NavigationKeys()
            var exits = 0
            keys.onExit = { exits++ }
            assertTrue(keys.handle(code, true))
            assertTrue(keys.handle(code, true, repeat = true))
            assertTrue(keys.handle(code, false))
            assertEquals(1, exits)
        }
    }

    @Test fun movementAndOrbitCanBeHeldIndependently() {
        val keys = NavigationKeys()
        keys.handle(KeyEvent.KEYCODE_W, true)
        keys.handle(KeyEvent.KEYCODE_DPAD_UP, true)
        keys.handle(KeyEvent.KEYCODE_D, true)
        assertEquals(StickAxes(1f, 1f, tilt = 1f), keys.sample())
        keys.handle(KeyEvent.KEYCODE_W, false)
        keys.handle(KeyEvent.KEYCODE_DPAD_LEFT, true)
        assertEquals(StickAxes(1f, 0f, pan = -1f, tilt = 1f), keys.sample())
        keys.clear()
        keys.handle(KeyEvent.KEYCODE_D, true, repeat = true)
        assertNull(keys.sample())
    }

    @Test fun tapBetweenFramesProducesOneMovementSample() {
        val keys = NavigationKeys()
        keys.handle(KeyEvent.KEYCODE_A, true)
        keys.handle(KeyEvent.KEYCODE_A, false)
        assertEquals(StickAxes(-1f), keys.sample())
        assertNull(keys.sample())
        assertFalse(keys.handle(KeyEvent.KEYCODE_ENTER, true))
    }
}
