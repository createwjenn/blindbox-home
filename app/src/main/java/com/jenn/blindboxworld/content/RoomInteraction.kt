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
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

@Composable
internal fun RoomMenuClose(close: () -> Unit) {
    Button(onClick=close, shape=CircleShape, contentPadding=PaddingValues(0.dp),
        modifier=Modifier.padding(bottom=12.dp).size(64.dp).spatialHoverEffect()
            .semantics { contentDescription="Close menu and step back" }) { Image(painterResource(R.drawable.room_close), null, Modifier.size(28.dp)) }
}

@Composable
internal fun ObjectTarget(title: String, open: () -> Unit) {
    Button(onClick=open, shape=CircleShape, contentPadding=PaddingValues(0.dp),
        modifier=Modifier.size(56.dp).spatialHoverEffect()
            .semantics { contentDescription="Object target: $title" }) { Text("◎") }
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

internal data class TargetRect(val left: Float,val right: Float,val bottom: Float,val top: Float) {
    fun overlap(x:Float,y:Float,r:Float): Float =
        max(0f,min(right,x+r)-max(left,x-r))*max(0f,min(top,y+r)-max(bottom,y-r))
}

/** Score above/below/left/right in view space against all visible object silhouettes. */
internal fun chooseTargetPosition(rect: TargetRect, blockers: List<TargetRect>): Pair<Float,Float> {
    val x=(rect.left+rect.right)/2;val y=(rect.bottom+rect.top)/2
    return listOf(x to rect.top+.085f, x to rect.bottom-.085f,
        rect.left-.085f to y, rect.right+.085f to y)
        .map { (a,b)->a.coerceIn(-.85f,.85f) to b.coerceIn(-.48f,.48f) }
        .minBy { (a,b)->blockers.sumOf { it.overlap(a,b,.065f).toDouble() } }
}

internal fun roomObjectTarget(state: RoomFurnishing,id: String,root: Entity,head: Entity): Vector3? {
    val rects=mutableMapOf<String,TargetRect>()
    for (key in state.placedIds) {
        val p=state.visualSpot(key);val item=state.pieces.getValue(key)
        val center=root.convertPositionTo(Vector3(p.x,p.y+(if(item.wall) 0f else item.height*.5f),p.z),head)
        if (center.z>=-.15f) continue
        val points=buildList {
            for (x in listOf(-.5f,.5f)) for(y in listOf(-.5f,.5f)) for(z in listOf(-.5f,.5f)) {
                val side=abs(state.pose(key).yaw)%180f==90f
                val point=root.convertPositionTo(Vector3(p.x+x*(if(side)item.depth else item.width),
                    p.y+(if(item.wall)0f else item.height*.5f)+y*item.height,
                    p.z+z*(if(side)item.width else item.depth)),head)
                if(point.z<-.1f) add(point)
            }
        }
        if(points.isEmpty()) continue
        rects[key]=TargetRect(points.minOf { it.x*1.35f/-it.z },points.maxOf { it.x*1.35f/-it.z },
            points.minOf { it.y*1.35f/-it.z },points.maxOf { it.y*1.35f/-it.z })
    }
    val rect=rects[id] ?: return null
    if (rect.left>.95f || rect.right<-.95f || rect.bottom>.65f || rect.top<-.65f) return null
    val (x,y)=chooseTargetPosition(rect,rects.values.toList())
    return Vector3(x,y,-1.35f)
}
