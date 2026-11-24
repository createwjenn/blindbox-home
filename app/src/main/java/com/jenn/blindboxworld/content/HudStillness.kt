package com.jenn.blindboxworld.content

/** Ignore tracking jitter, hide promptly, and wait for a settled view before returning. */
internal class HudStillness {
    var alpha = 1f
        private set
    private var quietSeconds = .8f
    fun advance(seconds: Float, moving: Boolean, interacting: Boolean): Float {
        quietSeconds = if (moving) 0f else quietSeconds + seconds
        val target = if (interacting || quietSeconds >= .8f) 1f else 0f
        val step = seconds / if (target > alpha) .25f else .18f
        alpha = if (target > alpha) minOf(target, alpha + step) else maxOf(target, alpha - step)
        return alpha
    }
}
