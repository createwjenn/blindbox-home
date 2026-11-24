package com.jenn.blindboxworld.content

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text

@Composable
internal fun RoomStickPanel(motion: RoomMotion) {
    val input = motion.input
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val walk = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Walk"
            role = Role.Button
            onClick(label = "Step forward") { motion.nudge(forward = 1f) }
            customActions = listOf(
                CustomAccessibilityAction("Step backward") { motion.nudge(forward = -1f) },
                CustomAccessibilityAction("Step left") { motion.nudge(x = -1f) },
                CustomAccessibilityAction("Step right") { motion.nudge(x = 1f) },
            )
        }
        StickPad("Walk", false, walk, held = { input.padHeld = it }, tap = { x, y -> motion.nudge(x = x, forward = y) }) { x, y ->
            input.pad = input.pad.copy(x = x, forward = y)
        }
        val turn = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Turn"
            role = Role.Button
            onClick(label = "Turn right") { motion.nudge(turn = 1f) }
            customActions = listOf(CustomAccessibilityAction("Turn left") { motion.nudge(turn = -1f) })
        }
        StickPad("Turn", true, turn, held = { input.padHeld = it }, tap = { x, _ -> motion.nudge(turn = x) }) { x, _ ->
            input.pad = input.pad.copy(turn = x)
        }
    }
}

@Composable
private fun StickPad(
    label: String, horizontal: Boolean, modifier: Modifier,
    held: (Boolean) -> Unit, tap: (Float, Float) -> Unit, change: (Float, Float) -> Unit,
) {
    var thumb by remember { mutableStateOf(Offset.Zero) }
    Column(modifier.pointerInput(label) {
        fun point(position: Offset): Offset {
            val radius = size.width * .5f
            if (position.y > size.width) return Offset.Zero // The label is also a button.
            val offset = Offset((position.x - radius) / radius,
                if (horizontal) 0f else (position.y - radius) / radius)
            return offset / offset.getDistance().coerceAtLeast(1f)
        }
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                held(true)
                thumb = point(down.position)
                // In the room a brief directional tap takes one step. A drag or sustained
                // deflection drives the same continuous axes as a controller thumbstick.
                var continuous = false
                var dragged = false
                val primary = if (horizontal) Offset(1f, 0f) else Offset(0f, -1f)
                val holdUntil = SystemClock.uptimeMillis() + 140L
                while (true) {
                    val event = if (continuous) awaitPointerEvent() else {
                        val remaining = holdUntil - SystemClock.uptimeMillis()
                        if (remaining > 0) withTimeoutOrNull(remaining) { awaitPointerEvent() } else null
                    }
                    if (event == null) {
                        continuous = true
                        if (!dragged && thumb.getDistance() <= .16f) thumb = primary
                        change(thumb.x, -thumb.y)
                        continue
                    }
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    pointer.consume()
                    if (!pointer.pressed) {
                        if (!continuous) {
                            val direction = if (thumb.getDistance() <= .16f) primary else thumb / thumb.getDistance()
                            tap(direction.x, -direction.y)
                        }
                        break
                    }
                    if ((pointer.position - down.position).getDistance() > size.width * .06f) dragged = true
                    thumb = point(pointer.position)
                    if (continuous && !dragged && thumb.getDistance() <= .16f) thumb = primary
                    if (continuous) change(thumb.x, -thumb.y)
                }
                held(false)
                thumb = Offset.Zero
                change(0f, 0f)
            }
        } finally {
            held(false)
            thumb = Offset.Zero
            change(0f, 0f)
        }
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(86.dp)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF594E46), r)
            drawLine(Color.LightGray, center - Offset(r * .80f, 0f), center + Offset(r * .80f, 0f), 2f)
            if (!horizontal) drawLine(Color.LightGray, center - Offset(0f, r * .80f), center + Offset(0f, r * .80f), 2f)
            drawCircle(Gold, r * .19f, center + thumb * (r * .79f))
        }
        Text(label, fontSize = 18.sp, color = Cream)
    }
}
