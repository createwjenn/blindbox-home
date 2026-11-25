package com.jenn.blindboxworld.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

internal val roomStations = listOf("stand", "machine", "record-player", "shop")
internal val stationRingSize = 160.dp

/** The floor attachment uses the compositor's hover state for gaze and pinch. */
@Composable
internal fun StationRing(label: String, floorHovered: Boolean, walk: () -> Unit) {
    val input = remember { MutableInteractionSource() }
    val mouseOver by input.collectIsHoveredAsState()
    val shimmer = rememberInfiniteTransition(label = "Station glow").animateFloat(
        initialValue = .12f, targetValue = .23f,
        animationSpec = infiniteRepeatable(tween(4400, easing = LinearEasing), RepeatMode.Reverse),
        label = "Center brightness")
    Canvas(Modifier.size(stationRingSize).clip(CircleShape)
        .spatialHoverEffect { alpha(if (it.isActive || mouseOver || floorHovered) 1f else .40f) }
        .clickable(interactionSource = input, indication = null, role = Role.Button, onClickLabel = label, onClick = walk)) {
        val radius = size.minDimension * .5f
        drawCircle(Brush.radialGradient(
            0f to Color.White.copy(alpha = shimmer.value), .77f to Color.Transparent,
            .85f to Color.White.copy(alpha = .05f), .91f to Color.White.copy(alpha = .43f),
            .98f to Color.Transparent, radius = radius))
        drawCircle(Color.White, radius = radius * .91f, style = Stroke(radius * .046f))
    }
}

internal const val FLOOR_PANEL_DP = 960
internal const val FLOOR_PANEL_METERS = 8f

/** Forward compositor contacts to the same 3D hit test used by tracked pointers. */
@Composable
internal fun WalkableFloorHover(hover: (FloorPoint?, Boolean) -> Unit, tap: (FloorPoint) -> Unit) {
    val onHover by rememberUpdatedState(hover)
    val onTap by rememberUpdatedState(tap)
    fun point(x: Float, y: Float) = FloorPoint((x - .5f) * FLOOR_PANEL_METERS, (y - .5f) * FLOOR_PANEL_METERS)
    DisposableEffect(Unit) { onDispose { onHover(null, false) } }
    Canvas(Modifier.size(FLOOR_PANEL_DP.dp)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val at = event.changes.firstOrNull()?.position
                    onHover(if (event.type == PointerEventType.Exit || at == null) null
                        else point(at.x / size.width, at.y / size.height),
                        event.changes.firstOrNull()?.type == androidx.compose.ui.input.pointer.PointerType.Mouse)
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { at -> onTap(point(at.x / size.width, at.y / size.height)) }
        }) {
        drawRect(Color.White.copy(alpha = 1f / 255f))
    }
}
