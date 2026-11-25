package com.jenn.blindboxworld.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.jenn.blindboxworld.R
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import kotlin.math.*

internal const val MENU_CLOSE_SIZE = 64
internal const val MENU_CLOSE_GAP = 12

@Composable
internal fun RoomMenuClose(close: () -> Unit) {
    Button(onClick=close, shape=CircleShape, contentPadding=PaddingValues(0.dp),
        modifier=Modifier.padding(bottom=MENU_CLOSE_GAP.dp).size(MENU_CLOSE_SIZE.dp).spatialHoverEffect()
            .semantics { contentDescription="Close menu and step back" }) { Image(painterResource(R.drawable.room_close), null, Modifier.size(28.dp)) }
}

@Composable
internal fun TravelCurtain() {
    // Black is the transition mask, not an app content surface. The room stays opaque.
    Canvas(Modifier.size(1024.dp)) { drawRect(Color.Black) }
}

/** Prefer a few steps directly away; shorten or turn the retreat if furniture blocks it. */
internal fun roomRetreat(from: FloorPoint, away: FloorPoint): FloorPoint? {
    val length=hypot(away.x,away.z).coerceAtLeast(.001f)
    for (distance in listOf(1.2f,.9f,.6f,.3f)) for (degrees in listOf(0f,30f,-30f,60f,-60f,90f,-90f,180f)) {
        val a=Math.toRadians(degrees.toDouble());val c=cos(a).toFloat();val s=sin(a).toFloat()
        val candidate=FloorPoint(from.x+(away.x*c-away.z*s)/length*distance,
            from.z+(away.x*s+away.z*c)/length*distance)
        if (RoomFloor.isWalkable(candidate) && RoomFloor.route(from,candidate).isNotEmpty()) return candidate
    }
    return null
}

/** Rendered bounds in the furniture holder's local axes, before its saved room rotation. */
internal data class DecorBounds(val center: RoomSpot, val half: RoomSpot) {
    fun nearby(eye: RoomSpot, pose: DecorPose, rotation: Float): Boolean {
        val (x,z) = rotY(eye.x-pose.at.x, eye.z-pose.at.z, -rotation)
        val dx = (abs(x-center.x)-half.x).coerceAtLeast(0f)
        val dy = (abs(eye.y-pose.at.y-center.y)-half.y).coerceAtLeast(0f)
        val dz = (abs(z-center.z)-half.z).coerceAtLeast(0f)
        return dx*dx + dy*dy + dz*dz <= 2.4f*2.4f
    }

    fun viewingSpot(eye: RoomSpot, pose: DecorPose, rotation: Float, wall: Boolean,
                    visibleFrom: (FloorPoint, RoomSpot) -> Boolean): LoftView? {
        val (cx,cz) = rotY(center.x, center.z, rotation)
        val at = RoomSpot(pose.at.x+cx, pose.at.y+center.y, pose.at.z+cz)
        val from = FloorPoint(eye.x,eye.z)
        val facing = if (wall) pose.yaw else Math.toDegrees(atan2(eye.x-at.x, eye.z-at.z).toDouble()).toFloat()
        for (offset in listOf(0f, 20f, -20f, 45f, -45f, 70f, -70f, 95f, -95f, 135f, -135f, 180f)) {
            if (wall && abs(offset)>45f) continue
            val yaw = facing+offset
            val (s,c) = rotY(0f,1f,yaw-rotation)
            val width = abs(c)*half.x+abs(s)*half.z
            val depth = abs(s)*half.x+abs(c)*half.z
            // Fit both edges and the top/bottom inside a comfortable central viewing angle.
            val radius = depth+maxOf(1.12f, (width+.46f)*1.28f, (abs(eye.y-at.y)+half.y)*1.55f)
            val (dx,dz) = rotY(0f,radius,yaw)
            val to = FloorPoint(at.x+dx,at.z+dz)
            if (RoomFloor.isWalkable(to) && visibleFrom(to,at) &&
                (from.distanceTo(to)<.015f || RoomFloor.route(from,to).isNotEmpty()))
                return LoftView(to.x,eye.y,to.z,yaw)
        }
        return null
    }
}

internal const val DECOR_MENU_WIDTH = .48f

/** Project the four footprint corners from the arrival viewpoint, then clear the
 * outermost edge by the full angular half-width of the viewer-facing card. */
internal fun decorMenuView(eye: RoomSpot, center: RoomSpot, half: RoomSpot, rotation: Float): LoftView {
    val dx = center.x - eye.x
    val dz = center.z - eye.z
    val distance = hypot(dx, dz).coerceAtLeast(.001f)
    val forwardX = dx / distance
    val forwardZ = dz / distance
    val radius = (distance * .68f).coerceIn(.55f, 1.35f)
    var left = 0f
    var right = 0f
    var top = Float.NEGATIVE_INFINITY
    for (x in listOf(-half.x, half.x)) for (z in listOf(-half.z, half.z)) {
        val (ox, oz) = rotY(x, z, rotation)
        val depth = (distance + ox * forwardX + oz * forwardZ).coerceAtLeast(.01f)
        val angle = atan2(-ox * forwardZ + oz * forwardX, depth)
        left = minOf(left, angle)
        right = maxOf(right, angle)
        top = maxOf(top, (center.y + half.y - eye.y) / depth)
    }
    val margin = atan2(DECOR_MENU_WIDTH / 2, radius) + .055f
    val candidates = listOf(right + margin, left - margin)
    val angle = candidates.sortedBy { abs(it) }.firstOrNull {
        val x = eye.x + radius * (forwardX * cos(it) - forwardZ * sin(it))
        val z = eye.z + radius * (forwardZ * cos(it) + forwardX * sin(it))
        abs(x) < 4.1f && abs(z) < 4.2f
    } ?: candidates.minBy { abs(it) }
    // A very wide, close piece can occupy both sides of the field of view.
    // Keep the controls visible in the clear space above it in that case.
    val above = abs(angle) + atan2(DECOR_MENU_WIDTH / 2, radius) > .64f && top < .3f
    val bearing = if (above) angle.coerceIn(-.36f,.36f) else angle
    val x = eye.x + radius * (forwardX * cos(bearing) - forwardZ * sin(bearing))
    val z = eye.z + radius * (forwardZ * cos(bearing) + forwardX * sin(bearing))
    val defaultY = eye.y + ((center.y - eye.y) * .35f).coerceIn(-.22f,.18f)
    val y = if (above) maxOf(defaultY, eye.y + radius * top + .32f) else defaultY
    val yaw = Math.toDegrees(atan2(eye.x-x,eye.z-z).toDouble()).toFloat()
    return LoftView(x,y,z,yaw)
}
