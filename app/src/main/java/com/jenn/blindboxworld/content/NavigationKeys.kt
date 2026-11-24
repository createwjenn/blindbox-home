package com.jenn.blindboxworld.content

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalWindowInfo

/** A physical key can be tapped between render frames or held across multiple frames. */
internal class NavigationKeys {
    var onExit: (() -> Unit)? = null
    var onJump: (() -> Unit)? = null
    private var jumpHeld = false
    private var exitHeld = false

    private val held = mutableSetOf<Int>()
    private val pressedSinceSample = mutableSetOf<Int>()

    @Synchronized
    fun handle(code: Int, down: Boolean, repeat: Boolean = false): Boolean {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) {
            if (!down && exitHeld) { exitHeld = false; return true }
            val exit = onExit ?: return false
            if (down && !repeat && !exitHeld) { exitHeld = true; exit() }
            return true
        }
        if (code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_BUTTON_A) {
            if (!down && jumpHeld) { jumpHeld = false; return true }
            val jump = onJump ?: return false
            if (down && !repeat && !jumpHeld) { jumpHeld = true; jump() }
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Unit
            else -> return false
        }
        if (down) {
            if (!repeat && held.add(code)) pressedSinceSample.add(code)
        } else {
            held.remove(code)
        }
        return true
    }

    @Synchronized
    fun clear() {
        jumpHeld = false
        held.clear()
        pressedSinceSample.clear()
    }

    @Synchronized
    fun sample(): StickAxes? {
        if (held.isEmpty() && pressedSinceSample.isEmpty()) return null
        fun active(code: Int) = if (code in held || code in pressedSinceSample) 1f else 0f
        val axes = StickAxes(
            x = active(KeyEvent.KEYCODE_D) - active(KeyEvent.KEYCODE_A),
            forward = active(KeyEvent.KEYCODE_W) - active(KeyEvent.KEYCODE_S),
            pan = active(KeyEvent.KEYCODE_DPAD_RIGHT) - active(KeyEvent.KEYCODE_DPAD_LEFT),
            tilt = active(KeyEvent.KEYCODE_DPAD_UP) - active(KeyEvent.KEYCODE_DPAD_DOWN),
        )
        pressedSinceSample.clear()
        return axes
    }
}


/** PICO routes keyboard events to the focused attachment window before the stage Activity. */
@Composable
internal fun NavigationKeyTarget(keys: NavigationKeys?, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(keys, windowFocused) {
        if (keys != null && windowFocused) focus.requestFocus()
    }
    DisposableEffect(keys, windowFocused) {
        onDispose { if (windowFocused) keys?.clear() }
    }
    Box(
        Modifier.onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val down = native.action == KeyEvent.ACTION_DOWN
            (down || native.action == KeyEvent.ACTION_UP) &&
                keys?.handle(native.keyCode, down, native.repeatCount > 0) == true
        }.focusRequester(focus).focusable(enabled = keys != null),
    ) { content() }
}
