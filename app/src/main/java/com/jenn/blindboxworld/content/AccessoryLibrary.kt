package com.jenn.blindboxworld.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

// The preview entity is positioned in front of the left inset of this fixed-size attachment.
internal const val LIBRARY_WIDTH_DP = 772
internal const val LIBRARY_WIDTH_METERS = 1.12f
internal const val LIBRARY_DISTANCE = 1.28f
private const val PAGE_SIZE = 6
private val ink = Color(0xFF422D30)
private val cardCream = Color(0xFFFFF4DC)
private val softCream = Color(0xFFFFE7CE)
private val pistachio = Color(0xFFD9EB97)
private val moss = Color(0xFF495E34)

@Composable
internal fun AccessoryLibrary(
    figureId: String,
    unlocked: Set<String>,
    wearing: Set<String>,
    onTry: (Accessory) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    // Acquired pieces stay together and retain their catalogue order within each group.
    val items = remember(unlocked) { ACCESSORIES.sortedBy { it.id !in unlocked } }
    var page by remember(figureId) { mutableIntStateOf(0) }
    val pages = (items.size + PAGE_SIZE - 1) / PAGE_SIZE
    val visible = items.drop(page * PAGE_SIZE).take(PAGE_SIZE)
    val panelShape = RoundedCornerShape(32.dp)

    Row(
        Modifier.size(LIBRARY_WIDTH_DP.dp, 472.dp).clip(panelShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF624441), Color(0xFF34272B))))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FigureBackdrop(figureId, Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Accessory library", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = cardCream)
                Text("${unlocked.size} / ${items.size}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = pistachio, modifier = Modifier.semantics { stateDescription = "${unlocked.size} accessories unlocked" })
            }
            Text("Discover new pieces in regular gumballs.", fontSize = 16.sp, color = softCream)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in visible.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (item in row) key(item.id) {
                            AccessoryTile(item, item.id in unlocked, item.id in wearing) { onTry(item) }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                LibraryButton("Previous", enabled = page > 0) { page-- }
                Text("${page + 1} of $pages", fontSize = 15.sp, color = softCream)
                LibraryButton("Next", enabled = page + 1 < pages) { page++ }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LibraryButton("Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
                LibraryButton("Save outfit", modifier = Modifier.weight(1.55f), primary = true, onClick = onSave)
            }
        }
    }
}

@Composable
private fun FigureBackdrop(id: String, modifier: Modifier) {
    val blush = Color(0xFFF2C6AE)
    Box(modifier.clip(RoundedCornerShape(22.dp))
        .background(Brush.verticalGradient(listOf(blush, Color(0xFFEED9C0), Color(0xFFCA999A))))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Soft studio arch and the two edges of a ceramic turntable behind the spatial figure.
            drawRoundRect(Color.White.copy(alpha = .24f), Offset(.11f*w,.06f*h),
                Size(.78f*w,.66f*h), CornerRadius(.39f*w))
            drawOval(Color(0xFFA77877).copy(alpha = .45f), Offset(.16f*w,.665f*h), Size(.68f*w,.088f*h))
            drawOval(Color(0xFFB85F68), Offset(.12f*w,.637f*h), Size(.76f*w,.096f*h))
            drawOval(Color(0xFFFFECDD), Offset(.12f*w,.624f*h), Size(.76f*w,.090f*h))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
            Text(infoOf(id).rarity.label.uppercase(), fontSize = 13.sp, letterSpacing = 1.7.sp,
                fontWeight = FontWeight.Bold, color = Color(0xFF69474D))
            Text(nameOf(id), fontSize = 23.sp, lineHeight = 24.sp, maxLines = 2,
                fontWeight = FontWeight.Bold, color = ink)
        }
    }
}

@Composable
private fun AccessoryTile(item: Accessory, unlocked: Boolean, wearing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    val surface = if (unlocked) cardCream else Color(0xFFE2D2C4)
    val state = when {
        !unlocked -> "Locked"
        wearing -> "Equipped"
        else -> "Available"
    }
    Box(Modifier.size(120.dp).clip(shape).background(surface)
        .then(if (wearing) Modifier.border(3.dp, pistachio, shape) else Modifier)
        .then(if (unlocked) Modifier.spatialHoverEffect() else Modifier)
        .toggleable(value = wearing, enabled = unlocked, role = Role.Checkbox, onValueChange = { onClick() })
        .semantics(mergeDescendants = true) { stateDescription = state }) {
        Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(item.thumbnail), contentDescription = null,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                colorFilter = if (unlocked) null else ColorFilter.tint(Color(0xFF978680)))
            Box(Modifier.fillMaxWidth().height(39.dp), contentAlignment = Alignment.Center) {
                Text(item.name, color = ink, fontSize = 13.sp, lineHeight = 15.sp,
                    textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, maxLines = 2, softWrap = true,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        if (wearing || !unlocked) {
            Canvas(Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)
                .background(if (wearing) moss else Color(0xFF74615E), CircleShape)) {
                val u = size.minDimension
                if (wearing) {
                    drawLine(Color.White, Offset(.23f*u,.50f*u), Offset(.43f*u,.68f*u), .095f*u, StrokeCap.Round)
                    drawLine(Color.White, Offset(.43f*u,.68f*u), Offset(.77f*u,.32f*u), .095f*u, StrokeCap.Round)
                } else {
                    drawArc(Color.White, 180f, 180f, false, Offset(.34f*u,.23f*u), Size(.32f*u,.45f*u),
                        style = Stroke(.085f*u))
                    drawRoundRect(Color.White, Offset(.27f*u,.45f*u), Size(.46f*u,.34f*u), CornerRadius(.065f*u))
                }
            }
        }
    }
}

@Composable
private fun LibraryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
                          primary: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) pistachio else Color(0xFF705652),
            contentColor = if (primary) ink else cardCream,
        )) {
        Text(label, fontSize = 16.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
    }
}
