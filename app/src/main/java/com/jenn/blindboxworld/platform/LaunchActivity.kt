package com.jenn.blindboxworld.platform

import android.view.KeyEvent
import android.view.MotionEvent
import com.jenn.blindboxworld.content.NavigationKeys
import com.jenn.blindboxworld.content.ViewZoom
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity

class LaunchActivity : SpatialLaunchActivity() {
    internal var navigationKeys: NavigationKeys? = null
        set(value) {
            field?.clear()
            field = value
        }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        if ((down || event.action == KeyEvent.ACTION_UP) &&
            navigationKeys?.handle(event.keyCode, down, event.repeatCount > 0) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) navigationKeys?.clear()
    }

    override fun onPause() {
        navigationKeys?.clear()
        super.onPause()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val ticks = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (ticks != 0f) {
                ViewZoom.scroll(ticks)
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }
}
