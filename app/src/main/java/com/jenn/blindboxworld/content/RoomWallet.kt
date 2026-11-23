package com.jenn.blindboxworld.content

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jenn.blindboxworld.R
import com.pico.spatial.ui.design.Text
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal data class WalletPayout(val before: Int, val amount: Int, val start: Long = SystemClock.elapsedRealtime())
private const val COIN_DROP_MS = 630
private const val COIN_SPACING_MS = 135

@Composable
internal fun RoomWallet(coins: Int, payout: WalletPayout?, showShop: Boolean,
                        libraryArrival: Long, onLibraryCenter: (Offset) -> Unit, openShop: () -> Unit) {
    val libraryScale = remember { Animatable(1f) }
    LaunchedEffect(libraryArrival) {
        if (libraryArrival == 0L || SystemClock.elapsedRealtime()-libraryArrival > 400L) return@LaunchedEffect
        libraryScale.snapTo(1f)
        libraryScale.animateTo(1.24f,tween(140))
        libraryScale.animateTo(1f,tween(260))
    }
    val density = LocalDensity.current.density
    var time by remember(payout) { mutableLongStateOf(0L) }
    LaunchedEffect(payout) {
        val award = payout ?: return@LaunchedEffect
        do {
            withFrameNanos { }
            time = SystemClock.elapsedRealtime() - award.start
        } while (time <= COIN_DROP_MS + award.amount * COIN_SPACING_MS)
    }
    val landed = if (time < COIN_DROP_MS) 0 else 1 + ((time - COIN_DROP_MS) / COIN_SPACING_MS).toInt()
    val shown = if (payout == null || coins != payout.before + payout.amount) coins
        else payout.before + landed.coerceAtMost(payout.amount)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (showShop) Box(Modifier.onGloballyPositioned { icon ->
            val row = icon.parentLayoutCoordinates ?: return@onGloballyPositioned
            val center = row.localPositionOf(icon, Offset(icon.size.width*.5f, icon.size.height*.5f))
            onLibraryCenter(Offset((center.x-row.size.width*.5f)/density, (row.size.height*.5f-center.y)/density))
        }) {
            Box(Modifier.graphicsLayer {
                scaleX = libraryScale.value
                scaleY = libraryScale.value
                transformOrigin = TransformOrigin(.12f,.80f)
            }) { RoomShopIcon(openShop) }
        }
        Box(Modifier.size(width = 128.dp, height = 141.dp).semantics { contentDescription = "Wallet, $shown coins" }) {
            Image(painterResource(R.drawable.room_wallet), null, Modifier.align(Alignment.BottomCenter).size(128.dp, 99.dp))
            Text(shown.toString(), Modifier.align(Alignment.BottomCenter).offset(x = (-8).dp, y = (-28).dp),
                color = Color(0xFFFFEABD), fontSize = 27.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color(0xEE321A0C), Offset(0f, 2f), 3f)))
            if (payout != null && landed < payout.amount) Canvas(Modifier.fillMaxSize()) {
                for (index in 0 until payout.amount) {
                    val t = (time - index * COIN_SPACING_MS).toFloat() / COIN_DROP_MS
                    if (t !in 0f..1f) continue
                    val x = size.width * (.42f + (1 - t) * (.32f * sin(index * 1.7f)))
                    val y = size.height * (.08f + .41f * t * t)
                    val radius = size.width * .076f
                    val w = radius * (.32f + .68f * abs(cos(t * 7f)))
                    drawOval(Color(0xFF9C5519), Offset(x - w, y - radius + 2f), Size(w * 2, radius * 2))
                    drawOval(Brush.linearGradient(listOf(Color(0xFFFFF2AA), Color(0xFFFFCA4E), Color(0xFFD38A23)),
                        Offset(x - w, y - radius), Offset(x + w, y + radius)), Offset(x - w, y - radius), Size(w * 2, radius * 2))
                    drawLine(Color(0xFFC68926), Offset(x, y - radius * .54f), Offset(x, y + radius * .54f), 1.4.dp.toPx())
                }
            }
        }
    }
}
