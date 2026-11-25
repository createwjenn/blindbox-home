package com.jenn.blindboxworld.content

import kotlin.math.abs
import kotlin.math.hypot

/** Built into the entrance wall beside the dining table. */
internal object CornerShop {
    val at = RoomSpot(3.11f, 0f, 4.39f)
    const val yaw = 180f

    fun near(x: Float, z: Float) = hypot(x - at.x, z - at.z) <= 2.12f

    fun coversDisplay(x: Float, centerY: Float, width: Float, height: Float) =
        abs(x - at.x) < .83f + width / 2 && centerY + height / 2 > .95f && centerY - height / 2 < 2.26f
}
