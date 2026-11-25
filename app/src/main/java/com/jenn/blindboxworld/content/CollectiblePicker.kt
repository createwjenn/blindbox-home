package com.jenn.blindboxworld.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Color4
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import kotlinx.coroutines.awaitCancellation
import kotlin.math.atan2

internal const val PICKER_WIDTH_DP = 832
internal const val PICKER_WIDTH_METERS = 1.30f
internal const val PICKER_DISTANCE = 1.36f
private const val CARD_WIDTH = 189
private const val CARD_GAP = 12
private const val PANE_HEIGHT = 360
private const val PANE_PADDING_Y = 18
private const val HEADER_HEIGHT = 48
private const val HEADER_GAP = 12
private const val CARD_HEIGHT = 264
private const val PODIUM_CENTER = .73f
private val milk = Color(0xFFFFF8E7)
private val glassTints = listOf(Color(0xFFC492A2), Color(0xFF94BEB5), Color(0xFFAAA7CD), Color(0xFFE6BA80))

@Composable
internal fun CollectiblePicker(
    ownedIds: List<String>, displayed: String?, page: Int,
    onPage: (Int) -> Unit, onSelect: (String) -> Unit, onClose: () -> Unit,
) {
    val pageCount = (ownedIds.size + 3) / 4
    val visible = ownedIds.drop(page * 4).take(4)
    val across = ownedIds.size.coerceIn(2, 4)
    val pane = RoundedCornerShape(28.dp)
    Box(Modifier.width(PICKER_WIDTH_DP.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width((40 + across * CARD_WIDTH + (across - 1) * CARD_GAP).dp)
            .height(PANE_HEIGHT.dp).clip(pane)
            .background(Brush.verticalGradient(listOf(Color(0xCB364A4C), Color(0xB832333C))))
            .border(1.dp, Color.White.copy(alpha = .50f), pane)
            .padding(horizontal = 20.dp, vertical = PANE_PADDING_Y.dp)) {
            Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Change collectible", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = milk,
                    modifier = Modifier.weight(1f), maxLines = 1)
                if (pageCount > 1) {
                    PageArrow(false, page > 0) { onPage(page - 1) }
                    Text("${page + 1} / $pageCount", fontSize = 16.sp, color = milk,
                        modifier = Modifier.padding(horizontal = 8.dp))
                    PageArrow(true, page + 1 < pageCount) { onPage(page + 1) }
                    Spacer(Modifier.width(14.dp))
                }
                Button(onClick = onClose, modifier = Modifier.height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x80576067), contentColor = milk)) {
                    Text("Close", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(HEADER_GAP.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp, Alignment.CenterHorizontally)) {
                visible.forEachIndexed { index, id ->
                    key(id) { FigureCard(id, glassTints[index], id == displayed) { onSelect(id) } }
                }
            }
        }
    }
}

@Composable
private fun PageArrow(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = if (forward) "Next page" else "Previous page"
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.height(42.dp).semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0x80576067), contentColor = milk)) {
        Canvas(Modifier.size(16.dp)) {
            val x = if (forward) .35f else .65f
            val tip = Offset(size.width * (1f - x), size.height * .5f)
            val color = if (enabled) milk else milk.copy(alpha = .4f)
            drawLine(color, Offset(size.width * x, size.height * .16f), tip, 2.6.dp.toPx(), StrokeCap.Round)
            drawLine(color, tip, Offset(size.width * x, size.height * .84f), 2.6.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun FigureCard(id: String, tint: Color, active: Boolean, select: () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    Box(Modifier.size(CARD_WIDTH.dp, CARD_HEIGHT.dp).clip(shape)
        .background(Brush.verticalGradient(listOf(tint.copy(alpha = .65f), tint.copy(alpha = .26f), Color(0xBF252C35))))
        .border(if (active) 2.5.dp else 1.dp, if (active) Color(0xFFE0EFB2) else milk.copy(alpha = .61f), shape)
        .spatialHoverEffect()
        .selectable(selected = active, role = Role.Button, onClick = select)
        .semantics(mergeDescendants = true) {
            contentDescription = nameOf(id)
            stateDescription = if (active) "On display" else "Owned"
        }) {
        Canvas(Modifier.fillMaxSize()) {
            drawOval(tint.copy(alpha = .50f), Offset(size.width * .11f, size.height * .703f),
                Size(size.width * .78f, size.height * .078f))
            drawOval(milk.copy(alpha = .35f), Offset(size.width * .14f, size.height * (PODIUM_CENTER - .034f)),
                Size(size.width * .72f, size.height * .068f))
        }
        Row(Modifier.align(Alignment.TopCenter).padding(top = 11.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (active) "✓  On display" else infoOf(id).rarity.label,
                color = if (active) Color(0xFF34452E) else milk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(if (active) Color(0xFFDBEDAA) else Color(0x80415156), RoundedCornerShape(20.dp))
                    .padding(horizontal = 11.dp, vertical = 4.dp))
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(51.dp).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center) {
            Text(nameOf(id), fontSize = 19.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = milk,
                maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun CollectiblePickerPreview(foreground: RoomHudForeground?, ids: List<String>, outfits: Map<String, Set<String>>) {
    LaunchedEffect(foreground, ids, outfits) {
        if (foreground == null || ids.isEmpty()) return@LaunchedEffect
        val group = Entity().apply { components.set(TransformComponent()) }
        var previewLight: Entity? = null
        try {
            val units = PICKER_WIDTH_METERS / PICKER_WIDTH_DP
            val panelCenter = (PANE_HEIGHT + MENU_CLOSE_SIZE + MENU_CLOSE_GAP) * .5f
            val podiumY = panelCenter - MENU_CLOSE_SIZE - MENU_CLOSE_GAP - PANE_PADDING_Y -
                HEADER_HEIGHT - HEADER_GAP - CARD_HEIGHT * PODIUM_CENTER
            group.place(0f, 0f, -PICKER_DISTANCE)
            val lamp = Entity().apply {
                components.set(TransformComponent())
                components.set(PointLightComponent(Color4(1f, .95f, .88f, 1f), 30f, 1.25f))
                place(-.10f, .38f, .42f)
            }
            group.addChild(lamp)
            foreground.addPreviewLight(lamp, 30f)
            previewLight = lamp
            foreground.modalLayer.addChild(group)
            ids.forEachIndexed { index, id ->
                val x = (index - (ids.size - 1) / 2f) * (CARD_WIDTH + CARD_GAP) * units
                val model = loadDressedFigure(id, outfits[id].orEmpty())
                foreground.sortPreview(model)
                val podium = Vector3(x, podiumY * units, 0f)
                val eye = foreground.eyeIn(group)
                val yaw = Math.toDegrees(atan2(eye.x - x, eye.z).toDouble()).toFloat()
                val slot = wearableHolder()
                group.addChild(slot)
                slot.addChild(model)
                model.place(0f, 0f, 0f, yaw = yaw)
                val bounds = model.getVisualBounds(slot, recursive = true, enabledOnly = false)
                if (!bounds.isEmpty()) {
                    val size = bounds.size
                    val scale = minOf(146f * units / size.y, (CARD_WIDTH - 28) * units / size.x)
                    // Bring the whole mesh in front of the panel along the eye-to-podium ray.
                    val offset = size.z * scale * .5f + .004f
                    val t = eye.z / (eye.z + offset)
                    val foot = Vector3.lerp(eye, podium, t)
                    val fit = scale * t
                    slot.place(foot.x - bounds.center.x * fit, foot.y - (bounds.center.y - size.y * .5f) * fit,
                        foot.z - bounds.center.z * fit, fit)
                } else slot.place(x, podium.y, .08f, 139f * units)
            }
            awaitCancellation()
        } finally {
            previewLight?.let(foreground::removePreviewLight)
            group.releaseScene()
        }
    }
}
