package com.jenn.blindboxworld.content

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class NavigationKeysTest {
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
