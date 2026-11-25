package com.jenn.blindboxworld.content

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import com.pico.spatial.ui.design.ButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import kotlinx.coroutines.delay
import java.util.Locale

internal enum class RoundPhase { INTRO, PLAYING, PAUSED, FINISHED }

internal class WorldRound {
    var phase by mutableStateOf(RoundPhase.INTRO)
        private set
    private var banked = 0L
    private var started = 0L
    val elapsed get() = banked + if (phase == RoundPhase.PLAYING) SystemClock.elapsedRealtime() - started else 0L
    fun reward() = worldRewardCoins(elapsed)

    var preview by mutableStateOf(false)
    fun reset() { preview = false; banked = 0L; phase = RoundPhase.INTRO }
    fun play() {
        if (phase != RoundPhase.INTRO && phase != RoundPhase.PAUSED) return
        started = SystemClock.elapsedRealtime()
        phase = RoundPhase.PLAYING
    }
    fun stop(next: RoundPhase) {
        if (phase != RoundPhase.PLAYING) return
        banked = elapsed
        phase = next
    }
}

internal fun clockText(ms: Long) = String.format(Locale.ROOT, "%02d:%02d.%d", ms / 60000, ms / 1000 % 60, ms / 100 % 10)

private val chalk = Color(0xFFF8FAFA)

@Composable
internal fun ActionCard(width: Int = 320, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.width(width.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF404549), Color(0xFF1C2829))), shape)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun CardAction(label: String, primary: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
    val shape = RoundedCornerShape(with(LocalDensity.current) { 100f.toDp() })
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val background = when {
        pressed -> Color(0xFF566468)
        hovered -> Color(0xFF394749)
        else -> Color.Black
    }
    Button(
        onClick = action,
        enabled = enabled,
        interactionSource = interactions,
        shape = shape,
        modifier = Modifier.fillMaxWidth().clip(shape)
            .then(when {
                hovered -> Modifier.border(2.dp, Color.White, shape)
                primary -> Modifier.border(1.dp, Color(0xFF879091), shape)
                else -> Modifier
            }),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = Color.White),
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun ZoomActions() {
    CardAction("Zoom in") { ViewZoom.scroll(2f) }
    CardAction("Zoom out") { ViewZoom.scroll(-2f) }
}

@Composable
internal fun GameClock(round: WorldRound, collected: Int, total: Int) {
    var time by remember { mutableStateOf(0L) }
    LaunchedEffect(round.phase) {
        time = round.elapsed
        while (round.phase == RoundPhase.PLAYING) { time = round.elapsed; delay(80) }
    }
    Text(clockText(time), color = chalk, fontSize = 46.sp, fontWeight = FontWeight.Bold)
    Text("$collected / $total collected", color = chalk, fontSize = 22.sp)
    if (total-collected in 1..5) Text("Look for the gold markers", color = chalk, fontSize = 18.sp)
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF647070)))
}

private val resultCream = Color(0xFFFFF4DC)
private val resultGreen = Color(0xFFD9EB97)
internal const val WORLD_PANEL_DP = 800

@Composable
internal fun WorldResult(round: WorldRound, figure: String, eligible: Boolean, collected: Boolean,
                         claim: () -> Unit, restart: () -> Unit, back: () -> Unit) {
    val name = nameOf(figure)
    val dark = Color(0xFF442F32)
    val tint = when (figure) {
        "niu-lai" -> Color(0xFFE6D4B7)
        "strawberlina" -> Color(0xFFF2B3AC)
        "bananito" -> Color(0xFFEED080)
        "tung-tung-tung" -> Color(0xFFDAB79A)
        "tralalero-tralala" -> Color(0xFFAFD3D9)
        "chill-guy" -> Color(0xFFBCCD9B)
        "secret-capybara-toilet" -> Color(0xFFE4C390)
        else -> Color(0xFFF0C3C8)
    }
    val shape = RoundedCornerShape(32.dp)
    Row(Modifier.size(WORLD_PANEL_DP.dp, 480.dp).clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF654744), Color(0xFF35282C))))
        .padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.width(300.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(tint, Color(0xFFDFBDA6)))),
            contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(158.dp, 248.dp).border(9.dp, Color(0xFF805732))
                    .padding(9.dp).border(3.dp, Color(0xFFF0CA89)).padding(3.dp)
                    .background(Color(0xFF69595A)), contentAlignment = Alignment.Center) {
                    if (eligible) {
                        Image(painterResource(worldPaintingArt(figure)), "$name’s world painting",
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.foundation.Canvas(Modifier.size(43.dp)) {
                                val u = size.minDimension
                                drawArc(resultCream, 180f, 180f, false, Offset(.28f*u,.12f*u),
                                    androidx.compose.ui.geometry.Size(.44f*u,.59f*u),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(.085f*u))
                                drawRoundRect(resultCream, Offset(.14f*u,.41f*u),
                                    androidx.compose.ui.geometry.Size(.72f*u,.50f*u),
                                    androidx.compose.ui.geometry.CornerRadius(.08f*u))
                            }
                            Text("Locked", color = resultCream, fontSize = 17.sp)
                        }
                    }
                }
                Text("To unlock the painting, collect all $WORLD_PICKUPS items in ${PAINTING_TIME_MS / 1000} seconds or less.", color = dark,
                    textAlign = TextAlign.Center, fontSize = 18.sp, lineHeight = 21.sp)
                ResultButton(if (collected) "Painting collected" else "Collect painting", true,
                    enabled = eligible && !collected && !round.preview, onClick = claim)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(worldPortraitArt(figure)), name, Modifier.size(76.dp))
                Column {
                    Text(if (round.preview) "Result preview" else "World complete", color = resultGreen,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(name, color = resultCream, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column {
                Text(clockText(round.elapsed), color = resultCream, fontSize = 58.sp, fontWeight = FontWeight.Bold)
                Text("$WORLD_PICKUPS / $WORLD_PICKUPS ${infoOf(figure).collectible}", color = resultCream, fontSize = 22.sp)
                if (!round.preview) Text("+${round.reward()} coins", color = resultGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            if (collected) Text("Find this painting in your furniture collection.", color = resultCream,
                fontSize = 19.sp, lineHeight = 21.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultButton("Play again", false, onClick = restart)
                ResultButton("Return to room", false, onClick = back)
            }
        }
    }
}

@Composable
private fun ResultButton(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(100.dp), modifier = Modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) resultGreen else Color(0xFF705652),
            contentColor = if (primary) Color(0xFF422D30) else resultCream)) {
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}
