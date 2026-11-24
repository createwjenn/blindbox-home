package com.jenn.blindboxworld.content

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.AccessibilityComponent
import com.pico.spatial.ui.foundation.content.setAccessibilityActions
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import com.pico.spatial.ui.input.pointer.position3d
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialScaleGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.HandController
import com.pico.spatial.ui.platform.HandControllerHapticType
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalHandControllerHaptic
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Flow: plinth (museum vitrine) → gachapon in the corner (coin starts blower → crank releases capsule)
 * → pick up + shake → opening at the machine → figure floats to the plinth and the vitrine closes over it.
 */
private enum class Room { PLINTH, MACHINE, OPENING, REVEAL, WORLD }
private enum class Step { COIN, CRANK, DROP, BOX }
private enum class Scene { HOME, WORLD }
private enum class RoomCard { CLOCK, ZOOM, STAND, PLACED, PLACING, ACCESSORIES, PICKER, MACHINE, SHOP, DECOR, CARRY, HIDDEN }
private val Room.scene get() = when (this) {
    Room.PLINTH, Room.MACHINE, Room.OPENING, Room.REVEAL -> Scene.HOME
    Room.WORLD -> Scene.WORLD
}

/* ---------- gachapon geometry (root-space) ---------- */

/** Rotates (x, z) about Y by [deg] (same convention as entity yaw: +Z turns toward +X). */
internal fun rotY(x: Float, z: Float, deg: Float): Pair<Float, Float> {
    val a = Math.toRadians(deg.toDouble()); val c = cos(a).toFloat(); val s = sin(a).toFloat()
    return Pair(x * c + z * s, -x * s + z * c)
}
private fun gLocal(x: Float, y: Float, z: Float): Vector3 { val (rx, rz) = rotY(x, z, GACHA_YAW); return Vector3(rx, y, rz) }
private fun gLocal(l: Local, dz: Float = 0f, dy: Float = 0f) = gLocal(l.x, l.y + dy, l.z + dz)
private fun dist(a: Vector3, b: Vector3) = kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))

/** Mutable scene handles (entity refs and per-frame gesture bookkeeping; not Compose state). */
private class Refs {
    var root: Entity? = null
    var box: Entity? = null
    var light: Entity? = null
    var island: Entity? = null
    var islandId: String? = null
    var figYaw = 0f
    var settled = true
    var vitrine: Entity? = null
    var standMount: Entity? = null
    var curtain: Entity? = null
    private val travelMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun blackTravel(move: () -> Unit) {
        travelMutex.lock()
        val mask = curtain
        try {
            mask?.enabled = true
            for (f in 0..8) { mask?.setOpacity(f/8f); delay(20) }
            move()
            delay(40)
            for (f in 8 downTo 0) { mask?.setOpacity(f/8f); delay(20) }
        } finally { mask?.takeIf { it.valid }?.enabled = false; travelMutex.unlock() }
    }
    var presentationHead: Entity? = null
    var libraryPanel: Entity? = null
    var libraryCenter: Vector3? = null
    var machineMount: Entity? = null
    var prizePreparation: kotlinx.coroutines.Deferred<PrizePackage?>? = null
    var crankCue: Entity? = null
    var trayCue: Entity? = null
    var prizeGlow: Entity? = null
    var machine: Entity? = null
    var crank: Entity? = null
    var crankGlow: Entity? = null
    var slot: Entity? = null
    var chute: Entity? = null
    var coin: Entity? = null
    var stock: GlobeStock? = null
    var plus: Entity? = null
    val sparks = arrayOfNulls<Entity>(WORLD_PICKUPS)
    /** Root yaw: 0 at the plinth; turned + shifted so the machine stands in front of the player at the machine. */
    var rootYaw = 0f
    var rootAtMachine = false
    var holding = false
    var dropping = false
    var coinDrag = false
    var cox = 0f; var coy = 0f; var coz = 0f
    var crankAngle = 0f
    var ox = 0f; var oy = 0f; var oz = 0f
    var strokeX = 0f; var strokeY = 0f
    var dirX = 0; var dirY = 0
    var lastRev = 0L
    var lastRattle = 0L
    fun reset() {
        prizePreparation?.cancel()
        prizePreparation = null
        libraryPanel = null
        libraryCenter = null
        curtain = null; presentationHead = null; machineMount = null; crankCue = null; trayCue = null; prizeGlow = null
        root = null; box = null; light = null; island = null; islandId = null; vitrine = null; standMount = null; machine = null
        crank = null; crankGlow = null; slot = null; chute = null; coin = null; stock = null; plus = null
        sparks.fill(null); holding = false; dropping = false; coinDrag = false; settled = true
        rootYaw = 0f; rootAtMachine = false; ox = 0f; oy = 0f; oz = 0f
    }
}

private const val LIGHT_MAX = 2600f
private val RarityWarm = Color4(1f, 0.72f, 0.38f, 1f)
internal val PanelBack = Color(0xD91E1916) // design-style: fixed-figma-color brown glass of the room status pill
internal val Cream = Color(0xFFFFF4E6) // design-style: fixed-figma-color cream lettering on the room action cards
internal val Gold = Color(0xFFFFD45C) // design-style: fixed-figma-color gold control accents in the room HUD

@Composable
fun HomeStage(hmd: HMDTrackingProvider?, entryThreshold: Float = 0f, onExitDoor: () -> Unit = {}, onRoomReady: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("blindbox", Context.MODE_PRIVATE).also(::restoreUnboxPoster)
    val furnishing = remember(prefs) { RoomFurnishing(context,prefs) }
    val view = LocalView.current
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    val haptic = LocalHandControllerHaptic.current
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    val sfx = remember { Sfx() }
    DisposableEffect(sfx) { onDispose { sfx.release() } }
    var entryMask by remember { mutableStateOf<Entity?>(null) }
    LaunchedEffect(entryThreshold,entryMask) { entryMask?.enabled=entryThreshold>0f }
    var returnView by remember { mutableStateOf(doorwayView) }

    val unboxedAt = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>().apply {
        for (id in FIGURE_IDS) if (prefs.contains("unboxed.$id")) put(id, prefs.getLong("unboxed.$id", 0L))
    } }
    var owned by remember { mutableStateOf(prefs.getStringSet("figures", emptySet()).orEmpty().toSet()) }
    var unlockedAccessories by remember { mutableStateOf(libraryUnlocks(
        prefs.getStringSet("accessories", emptySet()).orEmpty()
    )) }
    var equippedAccessories by remember { mutableStateOf(owned.associateWith { id ->
        wearableOutfit(prefs.getStringSet("outfit.$id", emptySet()).orEmpty(), unlockedAccessories)
    }) }

    fun saveAccessories(figureId: String, accessoryIds: Set<String>) {
        if (figureId !in owned) return
        val outfit = wearableOutfit(accessoryIds, unlockedAccessories)
        prefs.edit().putStringSet("outfit.$figureId", outfit).apply()
        equippedAccessories = equippedAccessories + (figureId to outfit)
    }
    var placements by remember { mutableStateOf(owned.mapNotNull { id ->
        RoomSpot.decode(prefs.getString("placement.$id", null))?.let { id to it }
    }.toMap()) }
    var inspecting by remember { mutableStateOf<String?>(null) }
    var placing by remember { mutableStateOf<String?>(null) }
    var stars by remember { mutableStateOf(prefs.getStringSet("stars", emptySet()).orEmpty().toSet()) }
    // Testing balance resets once per launch; returning from a world keeps this session's earnings.
    var coins by remember { mutableIntStateOf(START_COINS) }
    LaunchedEffect(Unit) { prefs.edit().putInt("coins", START_COINS).apply() }
    var walletPayout by remember { mutableStateOf<WalletPayout?>(null) }
    var freeDay by remember { mutableStateOf(prefs.getString("freeDay", "") ?: "") }
    var displayed by remember { mutableStateOf(
        prefs.getString("displayed", null)?.takeIf { it in owned && it !in placements }
            ?: FIGURE_IDS.firstOrNull { it in owned && it !in placements }
    ) }
    var room by remember { mutableStateOf(Room.PLINTH) }
    var pendingPrize by remember {
        mutableStateOf(DrawPrize.restore(prefs.getString("pendingPrize", null)))
    }
    var step by remember { mutableStateOf(when {
        pendingPrize == null -> Step.COIN
        prefs.getBoolean("prizeReleased", false) -> Step.BOX
        else -> Step.CRANK
    }) }
    var holdingUi by remember { mutableStateOf(false) }
    var figure by remember { mutableStateOf(displayed ?: FIGURE_IDS.first()) }
    var paintingWins by remember { mutableStateOf(prefs.getStringSet("worldPaintingWins", emptySet()).orEmpty().toSet()) }
    var worldPaintings by remember { mutableStateOf(prefs.getStringSet("worldPaintings", emptySet()).orEmpty().toSet()) }
    val round = remember { WorldRound() }
    var roundId by remember { mutableIntStateOf(0) }
    var caught by remember { mutableStateOf(emptySet<Int>()) }
    var lastAccessoryMessage by remember { mutableStateOf<String?>(null) }
    var plusText by remember { mutableStateOf("+1 coin") }
    val meter: MutableFloatState = remember { mutableFloatStateOf(0f) }
    val refs = remember { Refs() }
    val roomMotion = remember { RoomMotion() }
    var reducedMotion by remember { mutableStateOf(prefs.getBoolean("reducedMotion", true)) }
    val ownedIds by rememberUpdatedState(remember(owned) { FIGURE_IDS.filter { it in owned } })
    val roomNow by rememberUpdatedState(room)
    val stepNow by rememberUpdatedState(step)
    val coinsNow by rememberUpdatedState(coins)
    val today = LocalDate.now().toString()
    val freeCoinReady = coins == 0 && freeDay != today

    var dismissedMachine by remember { mutableStateOf(false) }
    var openingBackdrop by remember { mutableStateOf(false) }
    var accessoryFlying by remember { mutableStateOf(false) }
    var libraryArrival by remember { mutableStateOf(0L) }

    fun machinePoint(local: Local): Vector3 {
        val at = furnishing.visualSpot("machine")
        val p = gLocal(local)
        return Vector3(at.x + p.x, at.y + p.y, at.z + p.z)
    }

    fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }

    fun buzz(strong: Boolean = false) {
        view.performHapticFeedback(if (strong) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
        runCatching {
            val type = if (strong) HandControllerHapticType.Press else HandControllerHapticType.Step
            haptic.feedback(type, HandController.Right)
            if (strong) haptic.feedback(type, HandController.Left)
        }
    }

    fun setCoins(n: Int) { coins = n; prefs.edit().putInt("coins", n).apply() }

    fun awardWorldCoins(amount: Int) {
        walletPayout = WalletPayout(coins, amount)
        setCoins(coins + amount)
    }

    /** World-space "+1 coin" that floats up and fades where the panel layout put it. */
    fun showPlus(text: String = "+1 coin") {
        plusText = text
        val p = refs.plus ?: return
        val base = p.pos(); val yaw = p.yawDeg()
        scope.launch {
            p.enabled = true
            tween(1.4f) { t -> p.place(base.x, base.y + 0.18f * easeOut(t), base.z, 1.0f, yaw = yaw); p.setOpacity(if (t < .7f) 1f else (1f - t) / .3f) }
            p.enabled = false
            p.place(base.x, base.y, base.z, 1.0f, yaw = yaw)
        }
    }

    /** Converts a world-space drag delta to root space (the root is yawed while at the machine). */
    fun toRoot(dx: Float, dz: Float) = rotY(dx, dz, -refs.rootYaw)

    fun setRootView(atMachine: Boolean) {
        val root = refs.root ?: return
        refs.rootAtMachine = atMachine
        val pose = hmd?.latestData?.hmdPose
        // Stand on the machine's viewing side, where the whole globe and its card fit.
        val target = if (atMachine) stationView(furnishing.spot("machine"), true) else stationView(furnishing.spot("stand"), false)
        refs.rootYaw = root.alignView(target, pose)
    }

    fun boxCenter(): Vector3 = refs.box?.let { b -> val p = b.pos(); Vector3(p.x, p.y + BOX_GLB_HEIGHT * b.scaleF() * .5f, p.z) }
        ?: gLocal(TRAY_REST, dy = .1f)

    fun moveBox(x: Float, y: Float, z: Float, scale: Float = TRAY_BOX_SCALE, yaw: Float = 0f, roll: Float = 0f) {
        refs.box?.place(x, y, z, scale, yaw = GACHA_YAW + yaw, roll = roll)
        refs.light?.place(x, y + BOX_GLB_HEIGHT * scale * .5f, z)
    }

    fun spawnCoin() {
        val coin = refs.coin ?: return
        if (step != Step.COIN) return // The insertion animation owns it until it disappears into the slot.
        if (room != Room.MACHINE || coins <= 0) { coin.enabled = false; return }
        if (coin.enabled) return
        refs.cox = 0f; refs.coy = 0f; refs.coz = 0f
        coin.enabled = true
        val p = gLocal(COIN_SLOT, dz = .15f)
        coin.place(p.x, p.y, p.z, 1.6f, yaw = GACHA_YAW, pitch = 90f)
        sfx.play(coin, "coin_spawn", 0.7f)
    }

    /** Short fade, then the scene moves so the machine (or the plinth) is in front of you; the head never moves. */
    fun travel(toMachine: Boolean) {
        roomMotion.cancelWalk()
        if (toMachine) dismissedMachine = false
        scope.launch {
            val root = refs.root ?: return@launch
            if (toMachine || refs.rootAtMachine) {
                sfx.play(root, "whoosh", 0.5f)
                refs.blackTravel { setRootView(toMachine) }
            }
            room = if (toMachine) Room.MACHINE else if (roomNow == Room.MACHINE) Room.PLINTH else roomNow
            if (toMachine) spawnCoin()
        }
    }

    /** Whoosh + fade the current scene out, then switch scene. */
    fun go(next: Room, before: () -> Unit = {}) {
        roomMotion.cancelWalk()
        scope.launch {
            val root = refs.root
            if (root != null && next.scene != roomNow.scene) {
                if (roomNow.scene == Scene.HOME) hmd?.latestData?.hmdPose?.let { head ->
                    val p = root.convertPositionFrom(head.position, null)
                    returnView = LoftView(p.x, p.y, p.z, head.headingDegrees() - refs.rootYaw)
                }
                sfx.play(root, "whoosh", 0.7f)
                for (f in 10 downTo 0) { root.setOpacity(f / 10f); delay(22) }
            }
            before()
            room = next
        }
    }

    fun grantFreeCoin() {
        if (coins > 0 || freeDay == today) return
        freeDay = today
        coins += 1
        prefs.edit().putString("freeDay", today).putInt("coins", coins).apply()
        refs.slot?.let { sfx.play(it, "coin_spawn") }
        buzz(strong = true)
        showPlus("+1 free coin")
    }

    fun insertCoin() {
        if (room != Room.MACHINE || step != Step.COIN) return
        val slot = refs.slot ?: return
        if (refs.stock == null) return
        if (coins <= 0) { sfx.play(slot, "no_coin"); buzz(); return }
        val prize = DrawPrize.pick(owned, unlockedAccessories)
        pendingPrize = prize
        lastAccessoryMessage = null
        coins -= 1
        prefs.edit().putInt("coins", coins).putString("pendingPrize", prize.saveKey)
            .putBoolean("prizeReleased", false).apply()
        step = Step.CRANK
        val coin = refs.coin
        scope.launch {
            if (coin != null && coin.enabled) {
                val a = coin.pos(); val b = gLocal(COIN_SLOT, dz = .01f)
                tween(0.35f) { t ->
                    val e = easeInOut(t)
                    coin.place(a.x + (b.x - a.x) * e, a.y + (b.y - a.y) * e + sin(t * 3.14f) * .03f, a.z + (b.z - a.z) * e,
                        1.6f * (1f - .5f * t), yaw = GACHA_YAW + 90f * e, pitch = 90f)
                }
                coin.enabled = false
            }
            sfx.play(slot, "coin_insert")
            buzz(strong = true)
        }
    }

    fun placeCrank() { val p = gLocal(CRANK_AXLE); refs.crank?.place(p.x, p.y, p.z, yaw = GACHA_YAW, roll = Math.toDegrees(refs.crankAngle.toDouble()).toFloat()) }

    fun startDraw() {
        if (step != Step.CRANK) return
        step = Step.DROP
        prefs.edit().putBoolean("prizeReleased", true).apply()
        buzz(strong = true)
    }

    suspend fun mountPrize(): Entity {
        val prize = checkNotNull(pendingPrize)
        val shell = when (prize) {
            // The authored root centres the box; the tray holder places its mesh from the feet.
            is DrawPrize.Figure -> Entity.loadSuspend("asset://box.glb").apply { place(0f,0f,0f) }
            is DrawPrize.Wearable -> Entity.loadSuspend("asset://props/${prize.shell}.glb").apply { place(0f,.49f,0f,.47f) }
        }
        val holder = Entity().apply {
            components.set(TransformComponent())
            setName("box")
            makeTappable(Vector3(.98f, .98f, .98f), .49f)
            addChild(shell)
            sfx.makeObjectSource(this)
        }
        refs.machineMount!!.addChild(holder)
        refs.box = holder
        refs.prizePreparation?.cancel()
        refs.prizePreparation = scope.async {
            try { preparePrizePackage(holder, prize) }
            catch (failure: com.pico.spatial.core.ecs.resource.ResourceLoadingException) {
                Log.w("BlindboxGacha", "Preload deferred until opening", failure)
                null
            }
        }
        return holder
    }

    fun stopCrank() {
        if (room != Room.MACHINE || step != Step.CRANK) return
        refs.crank?.let { sfx.play(it, "crank_click") }
        startDraw()
    }

    /** Lifts or lowers the glass vitrine over the plinth. */
    suspend fun vitrine(up: Boolean, seconds: Float = 0.9f) {
        val v = refs.vitrine ?: return
        sfx.play(v, "vitrine_slide", 0.8f)
        val from = v.pos().y; val to = ISLAND_Y + if (up) VITRINE_LIFT else 0f
        tween(seconds) { t -> v.place(0f, from + (to - from) * easeInOut(t), ISLAND_Z) }
    }

    suspend fun displayFigure(fig: Entity, id: String) {
        val root = checkNotNull(refs.root)
        val stand = furnishing.spot("stand")
        val foot = Vector3(stand.x, stand.y + EXHIBIT_FOOT_Y, stand.z)
        val previous = refs.island
        val previousId = displayed
        val previousPlacement = placements[id]
        var landed = false
        refs.settled = false
        refs.rootAtMachine = false
        dismissedMachine = true
        roomMotion.cancelWalk()
        try {
            sfx.play(fig, "whoosh", 0.56f)
            if (reducedMotion) {
                refs.blackTravel { refs.rootYaw = root.alignView(stationView(stand, false), hmd?.latestData?.hmdPose) }
            } else frameCollectibleStand(root, stand, refs.rootYaw, hmd?.latestData?.hmdPose) { refs.rootYaw = it }
            Log.i("BlindboxGacha", "Stand framed for $id")
            vitrine(true, 0.52f)
            flyFigureToStand(root, fig, foot, previous)
            previous?.releaseScene()
            refs.island = fig
            refs.islandId = id
            refs.figYaw = 0f

            val timestamp = System.currentTimeMillis()
            owned = owned + id
            unboxedAt[id] = timestamp
            val editor = prefs.edit().putStringSet("figures", owned).putLong("unboxed.$id", timestamp)
                .putString("displayed", id).remove("placement.$id").remove("pendingPrize").remove("prizeReleased")
            if (previousId != null && previousPlacement != null && previousId != id) {
                editor.putString("placement.$previousId", previousPlacement.encode())
            }
            editor.apply()
            placements = placements.switchDisplay(previousId, id)
            displayed = id
            pendingPrize = null
            landed = true
            Log.i("BlindboxGacha", "Landed $id on display, replacing ${previousId ?: "empty stand"}")

            val burst = Confetti(infoOf(id).rarity.color, 14, Vector3.ZERO)
            burst.root.place(foot.x, foot.y + 0.025f, foot.z, 0.56f)
            root.addChild(burst.root)
            try {
                sfx.play(fig, "spark_catch", 0.48f)
                buzz()
                kotlinx.coroutines.coroutineScope {
                    launch { burst.play(0.78f) }
                    vitrine(false, 0.86f)
                }
            } finally {
                burst.root.releaseScene()
            }
        } finally {
            if (!landed) {
                fig.releaseScene()
                if (previous?.valid == true && root.valid) {
                    previous.place(foot.x, foot.y, foot.z, BOX_SCALE, yaw = refs.figYaw)
                }
            }
            refs.settled = true
        }
    }

    suspend fun openingSequence(prize: DrawPrize) {
        val box = refs.box ?: return
        val root = refs.root ?: return
        val head = refs.presentationHead ?: return
        val library = refs.libraryPanel
        if (prize is DrawPrize.Wearable && (library == null || !library.valid)) {
            room = Room.MACHINE
            return
        }
        fun retainDraw(failure: com.pico.spatial.core.ecs.resource.ResourceLoadingException) {
            box.enabled = true
            box.components.get(AccessibilityComponent::class.java)?.value = "Ready to open"
            room = Room.MACHINE
            Log.e("BlindboxGacha", "Could not open the package; its paid draw is retained", failure)
        }
        val fig = try {
            when (prize) {
                is DrawPrize.Figure -> loadDressedFigure(prize.id, equippedAccessories[prize.id].orEmpty())
                is DrawPrize.Wearable -> loadProp("asset://${prize.item.modelPath}")
            }
        } catch (failure: com.pico.spatial.core.ecs.resource.ResourceLoadingException) {
            retainDraw(failure)
            return
        }
        try {
            val prepared = refs.prizePreparation?.await() ?: preparePrizePackage(box, prize)
            box.enabled = false
            openingBackdrop = true
            presentPrize(head, prize, prepared, fig, sfx, pulse = { buzz(strong = true) }) { stage, reward ->
                if (prize is DrawPrize.Wearable && library != null) {
                    accessoryFlying = true
                    openingBackdrop = false
                    library.enabled = true
                    // Bottom-aligned 92 dp icon, 3 dp gap and 128 x 141 dp wallet. The measured
                    // centre supersedes this initial layout before drawing the first travel frame.
                    val initialCenter = Vector3(converter.dpToLength((-65.5f).dp,LengthUnit.Meters),
                        converter.dpToLength((-24.5f).dp,LengthUnit.Meters),.001f)
                    androidx.compose.runtime.withFrameNanos { }
                    flyAccessoryToLibrary(reward,{
                        library.convertPositionTo(refs.libraryCenter ?: initialCenter,stage)
                    },sfx)
                } else {
                    carryRevealedFigure(head,reward)
                }
            }
        } catch (failure: com.pico.spatial.core.ecs.resource.ResourceLoadingException) {
            fig.releaseScene()
            retainDraw(failure)
            return
        } finally {
            openingBackdrop = false
            accessoryFlying = false
        }
        box.releaseScene()
        refs.box = null
        refs.prizePreparation = null
        meter.floatValue = 0f
        val editor = prefs.edit().remove("pendingPrize").remove("prizeReleased")
        if (prize is DrawPrize.Wearable) {
            lastAccessoryMessage = if (prize.item.id in unlockedAccessories) "${prize.item.name} · Already in collection"
                else "${prize.item.name} unlocked"
            unlockedAccessories = unlockedAccessories + prize.item.id
            editor.putStringSet("accessories", unlockedAccessories).apply()
            libraryArrival = SystemClock.elapsedRealtime()
            buzz()
            pendingPrize = null
            fig.releaseScene()
            step = Step.COIN
            room = Room.MACHINE
            return
        }
        displayFigure(fig, (prize as DrawPrize.Figure).id)
        step = Step.COIN
        room = Room.REVEAL
    }

    fun openBox() {
        if (room != Room.MACHINE || step != Step.BOX || refs.box == null) return
        val prize = pendingPrize ?: return
        if (prize is DrawPrize.Figure) figure = prize.id
        refs.holding = false; holdingUi = false
        room = Room.OPENING
        refs.box?.components?.get(AccessibilityComponent::class.java)?.value = "Opening"
        scope.launch { openingSequence(prize) }
    }

    fun registerDispensedPrize(content: SpatialViewContent, parcel: Entity) {
        val kind = if (pendingPrize is DrawPrize.Figure) "Slop blind box" else "Accessory capsule"
        parcel.components.set(AccessibilityComponent("Dispensed prize", "$kind, ready to open").apply { isAccessibilityElement = true })
        content.setAccessibilityActions(parcel, "machine.tray-object") {
            onClick("Tap to open") {
                if (step != Step.BOX || room != Room.MACHINE || refs.box != parcel) false
                else { openBox(); true }
            }
        }
    }

    fun holdPoint() = gLocal(0f, 1.1f, 0.55f)

    fun dropBox() {
        val box = refs.box ?: return
        scope.launch {
            refs.dropping = true
            try {
                val p = box.pos(); val b = gLocal(TRAY_REST)
                val frames = 14
                for (f in 1..frames) {
                    if (refs.holding || roomNow != Room.MACHINE) return@launch
                    val t = f / frames.toFloat()
                    moveBox(p.x + (b.x - p.x) * t, p.y + (b.y - p.y) * t * t, p.z + (b.z - p.z) * t)
                    delay(22)
                }
                sfx.play(box, "rattle_${(1..3).random()}", 0.5f)
                buzz()
                for (f in 0..6) { moveBox(b.x, b.y + sin(f / 6f * 3.14f) * 0.015f, b.z); delay(22) }
                refs.ox = 0f; refs.oy = 0f; refs.oz = 0f
            } finally { refs.dropping = false }
        }
    }

    fun onShakeReversal(speed: Float) {
        val box = refs.box ?: return
        val now = SystemClock.uptimeMillis()
        meter.floatValue = (meter.floatValue + (speed / 1.6f).coerceIn(0.06f, 0.2f)).coerceAtMost(1f)
        if (now - refs.lastRattle > 85) {
            refs.lastRattle = now
            sfx.play(box, "rattle_${(1..3).random()}", (0.5f + speed / 3f).coerceAtMost(1f))
            buzz()
        }
        if (meter.floatValue >= 1f) openBox()
    }

    fun catchSpark(i: Int) {
        if (room != Room.WORLD || round.phase != RoundPhase.PLAYING || i in caught) return
        val spark = refs.sparks[i]
        caught = caught + i
        buzz(strong = caught.size == WORLD_PICKUPS)
        if (spark != null) {
            sfx.play(spark, "spark_catch")
            spark.enabled = false
        }
        if (caught.size == WORLD_PICKUPS && WorldVerification.active) {
            round.stop(RoundPhase.FINISHED)
            round.preview = true
            Log.i("WorldVerification", "complete $figure contacts=${caught.size}")
            return
        }
        if (caught.size == WORLD_PICKUPS) {
            stars = stars + figure; prefs.edit().putStringSet("stars", stars).apply()
            round.stop(RoundPhase.FINISHED)
            awardWorldCoins(round.reward())
            if (round.elapsed <= PAINTING_TIME_MS && figure !in paintingWins) {
                paintingWins = paintingWins + figure
                prefs.edit().putStringSet("worldPaintingWins", paintingWins).apply()
            }
        }
    }

    val scene = room.scene
    val recordMusic = rememberRoomRecordPlayer(playingInRoom = scene == Scene.HOME)
    val sky = rememberLoftSky(scene != Scene.WORLD) ?: return
    val latestSky by rememberUpdatedState(sky)
    key(scene, if (scene == Scene.WORLD) figure else null) {
        val exhibit = displayed
        val placedModels = remember { mutableMapOf<String, Entity>() }
        var sceneDisposed by remember { mutableStateOf(false) }
        var ownedScene by remember { mutableStateOf<Entity?>(null) }
        var exitDoorPanel by remember { mutableStateOf<Entity?>(null) }
        var card by remember { mutableStateOf(RoomCard.CLOCK) }
        var wardrobeId by remember { mutableStateOf<String?>(null) }
        var draftOutfit by remember { mutableStateOf(emptySet<String>()) }
        var pickerPage by remember { mutableIntStateOf(-1) }
        var zoomMenu by remember { mutableStateOf(false) }
        var roomContent by remember { mutableStateOf<SpatialViewContent?>(null) }
        var floorTarget by remember { mutableStateOf<Entity?>(null) }
        var destinationMarker by remember { mutableStateOf<Entity?>(null) }
        var walletPanel by remember { mutableStateOf<Entity?>(null) }
        var roomForeground by remember { mutableStateOf<RoomHudForeground?>(null) }
        val hudOccluders by remember { androidx.compose.runtime.derivedStateOf {
            if (scene == Scene.HOME) roomHudOccluders(furnishing, placements.values) else emptyList()
        } }
        var roomPads by remember { mutableStateOf<Entity?>(null) }
        var roomPanel by remember { mutableStateOf<Entity?>(null) }
        val menuPointerHeld = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        val movingItem = placing != null || furnishing.carrying != null
        val placementPointer = rememberPlacementPointer(movingItem, roomMotion)
        var figureTarget by remember { mutableStateOf<RoomSpot?>(null) }
        // A pressed desktop pointer supplies its own hit coordinates until the drag ends.
        var dragPoint by remember { mutableStateOf<Vector3?>(null) }
        var hoverPoint by remember(placing, furnishing.carrying) { mutableStateOf<Vector3?>(null) }
        fun aimPlacement(pointInView: Vector3? = null) {
            val root = refs.root ?: return
            val head = hmd?.latestData?.hmdPose ?: return
            val (origin, direction) = placementPointer.ray(head)
            val eye = root.convertPositionFrom(origin, null)
            val point = if (pointInView != null) {
                roomContent?.localHitPosition(pointInView, root) ?: return
            } else root.convertPositionFrom(origin + direction, null)
            val panel = roomPanel
            if (panel != null && panel.enabled && (card == RoomCard.CARRY || card == RoomCard.PLACING)) {
                // Keep the last preview still while the player points at its confirmation button.
                val start = panel.convertPositionFrom(origin, null)
                val end = panel.convertPositionFrom(point, root)
                val dz = end.z - start.z
                if (abs(dz) > .0001f) {
                    val t = -start.z / dz
                    val halfWidth = converter.dpToLength(160.dp, LengthUnit.Meters)
                    val halfHeight = converter.dpToLength(130.dp, LengthUnit.Meters)
                    if (t > 0f && abs(start.x + t * (end.x - start.x)) <= halfWidth &&
                        abs(start.y + t * (end.y - start.y)) <= halfHeight) return
                }
            }
            val ray = point - eye
            val localEye = RoomSpot(eye.x, eye.y, eye.z)
            if (placing != null) {
                figureTarget = supportingPose(localEye, ray.x, ray.y, ray.z, furnishing.surfaces())?.at
            } else furnishing.updateAim(localEye, ray.x, ray.y, ray.z)
        }
        var proximityItem by remember { mutableStateOf<String?>(null) }
        var ignoredItem by remember { mutableStateOf<String?>(null) }
        var dismissedAt by remember { mutableStateOf<FloorPoint?>(null) }
        var hudRestoreUntil by remember { mutableStateOf(0L) }
        var targetPanel by remember { mutableStateOf<Entity?>(null) }
        var focusedDecor by remember { mutableStateOf<String?>(null) }
        fun showDecorTarget(id: String): Boolean {
            if (id !in furnishing.placedIds || movingItem || (furnishing.shopOpen || wardrobeId != null || pickerPage >= 0)) return false
            dismissedAt = null
            ignoredItem = null
            focusedDecor = id
            return true
        }


        fun selectDecor(id: String): Boolean {
            if (roomNow == Room.OPENING || placing != null || furnishing.carrying != null || furnishing.shopOpen || wardrobeId != null || pickerPage >= 0) return false
            if (id !in furnishing.placedIds) return false
            inspecting=null; zoomMenu=false; ignoredItem=null; dismissedAt=null
            furnishing.selected=id
            buzz()
            return true
        }
        fun finishDecor() {
            val id=furnishing.carrying ?: return
            val supported=furnishing.surfaces().filter { it.id==id }
            var shifts=emptyMap<String,RoomSpot>()
            val placed=furnishing.putDown { edit,before,after ->
                shifts=placements.filterValues { p -> supported.any { p.x in it.x && p.z in it.z && abs(p.y-it.y)<.03f } }
                    .mapValues { (_,p) -> RoomSpot(p.x+after.x-before.x,p.y+after.y-before.y,p.z+after.z-before.z) }
                shifts.forEach { (k,v)->edit.putString("placement.$k",v.encode()) }
            }
            if(!placed) return
            if(shifts.isNotEmpty()) placements=placements+shifts
            ignoredItem=id; buzz()
        }

        fun modalOpen() = wardrobeId != null || pickerPage >= 0 || furnishing.shopOpen
        fun canNavigate() = scene == Scene.HOME && !modalOpen() && roomNow != Room.OPENING &&
            !holdingUi && !refs.coinDrag

        fun closeRoomMenu() {
            val id = furnishing.selected ?: when (card) {
                RoomCard.MACHINE -> "machine"
                RoomCard.STAND -> "stand"
                RoomCard.DECOR -> proximityItem
                else -> null
            }
            val root = refs.root
            val head = hmd?.latestData?.hmdPose
            val eye = if (root != null && head != null) root.convertPositionFrom(head.position,null) else null
            val from = eye?.let { FloorPoint(it.x,it.z) }
            val objectAt = id?.let { furnishing.spot(it) }
            val forward = head?.rotation?.rotateVector(Vector3(0f,0f,-1f))
            val back = if (objectAt != null && from != null) FloorPoint(from.x-objectAt.x,from.z-objectAt.z)
                else forward?.let { val d=toRoot(it.x,it.z); FloorPoint(-d.first,-d.second) } ?: FloorPoint(0f,1f)
            val destination = from?.let { roomRetreat(it,back) }
            roomMotion.cancelWalk()
            furnishing.cancel(); furnishing.closeShop()
            wardrobeId=null; pickerPage=-1; placing=null; figureTarget=null; inspecting=null; focusedDecor=null
            zoomMenu=false; ignoredItem=id; dismissedMachine=true; refs.rootAtMachine=false
            if (room == Room.MACHINE) room=Room.PLINTH
            refs.coin?.enabled=false
            card=RoomCard.CLOCK
            dismissedAt=destination ?: from
            hudRestoreUntil=SystemClock.uptimeMillis()+2500L
            if (destination != null) roomMotion.walkTo(destination.x, destination.z)
            buzz()
        }

        fun returnToEntrance() {
            closeRoomMenu()
            roomMotion.cancelWalk()
            dismissedAt=FloorPoint(doorwayView.x,doorwayView.z)
            hudRestoreUntil=SystemClock.uptimeMillis()+2500L
            roomMotion.walkTo(doorwayView.x,doorwayView.z)
        }

        fun closePicker() {
            pickerPage = -1
            card = if (inspecting != null) RoomCard.PLACED else RoomCard.STAND
        }

        fun selectCollectible(id: String) {
            if (pickerPage < 0 || id !in ownedIds) return
            val previous = displayed
            if (id != previous) {
                val spot = placements[id]
                // A placed selection trades locations with the stand. A stored selection leaves the room alone.
                val editor = prefs.edit().putString("displayed", id).remove("placement.$id")
                if (previous != null) {
                    if (spot == null) editor.remove("placement.$previous")
                    else editor.putString("placement.$previous", spot.encode())
                }
                editor.apply()
                placedModels[id]?.enabled = false
                refs.island?.enabled = false
                refs.figYaw = 0f
                placements = placements.switchDisplay(previous, id)
                displayed = id
                if (inspecting != null) setRootView(false)
                inspecting = null
                buzz()
            }
            closePicker()
        }

        fun selectPlaced(id: String): Boolean {
            if (modalOpen() || furnishing.carrying != null || placing != null || id !in placements || room == Room.OPENING || room == Room.MACHINE) return false
            furnishing.selected=null
            inspecting = id
            buzz()
            return true
        }

        fun beginPlacement(id: String) {
            if (id !in owned || placing != null || furnishing.carrying != null) return
            figureTarget = null
            furnishing.selected = null
            placing = id
        }

        fun finishPlacement(spot: RoomSpot) {
            val id = placing ?: return
            if (!spot.inside() || id !in owned) return
            placements = placements + (id to spot)
            val editor = prefs.edit().putString("placement.$id", spot.encode())
            if (displayed == id) {
                displayed = FIGURE_IDS.firstOrNull { it in owned && it !in placements }
                editor.putString("displayed", displayed)
            }
            editor.apply()
            placing = null
            inspecting = null
            buzz()
        }

        fun backToRoom(): Boolean {
            if (room != Room.WORLD) return false
            WorldVerification.reset()
            round.stop(RoundPhase.PAUSED)
            go(Room.PLINTH)
            return true
        }

        fun enterWorld(id: String) {
            if (id !in owned) return
            inspecting = null
            furnishing.selected=null
            go(Room.WORLD) { figure = id; caught = emptySet(); round.reset() }
        }

        val ready = remember { mutableStateOf(false) }
        var shell by remember { mutableStateOf<Entity?>(null) }
        var shellLook by remember { mutableStateOf("") }
        var courtyard by remember { mutableStateOf<Entity?>(null) }
        val swimWake = remember(figure) { SwimWake() }
        var rippleWorld by remember { mutableStateOf<Entity?>(null) }
        var worldActor by remember { mutableStateOf<Entity?>(null) }
        var pondKoi by remember { mutableStateOf<PondKoi?>(null) }
        var worldWearables by remember { mutableStateOf<WorldWearables?>(null) }
        var terrain by remember { mutableStateOf<WorldTerrain?>(null) }
        var worldSkyEntity by remember { mutableStateOf<Entity?>(null) }
        val sticks = remember { CourtyardInput() }
        val keyInput = when {
            scene == Scene.WORLD -> sticks
            !ready.value -> null
            canNavigate() -> roomMotion.input
            else -> null
        }
        val currentKeys by rememberUpdatedState(keyInput?.keys)
        val activity = remember(context) {
            generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<com.jenn.blindboxworld.platform.LaunchActivity>().firstOrNull()
        }
        DisposableEffect(activity, keyInput) {
            activity?.navigationKeys = keyInput?.keys
            onDispose { activity?.navigationKeys = null }
        }
        val worldHud = remember { WorldHud(sticks, scope) }
        var hud by remember { mutableStateOf<Entity?>(null) }
        var nearMachine by remember { mutableStateOf(false) }
        val hudPanels = remember { mutableMapOf<String, Entity>() }
        val pickupHints = remember { mutableListOf<Entity>() }
        DisposableEffect(currentKeys, scene) {
            if (scene == Scene.WORLD) {
                currentKeys?.onExit = { backToRoom() }
                currentKeys?.onJump = { sticks.requestJump() }
            }
            onDispose { currentKeys?.onExit = null; currentKeys?.onJump = null }
        }
        fun restart() {
            sticks.clear()
            round.reset()
            caught = emptySet()
            refs.sparks.filterNotNull().forEach { it.enabled = true }
            courtyard?.place(0f, 0f, 0f)
            terrain?.actorStart?.let { p ->
                worldActor?.place(p.x, p.y, p.z, ACTOR_HEIGHT / infoOf(figure).actorRigHeight)
            }
            roundId++
            round.play()
        }
        DisposableEffect(Unit) { onDispose {
            sceneDisposed = true
            roomMotion.cancelWalk()
            sfx.stopAmbient()
            worldSkyEntity?.releaseScene()
            hud?.releaseScene()
            ownedScene?.releaseScene()
            ownedScene = null
            refs.reset()
        } }

        // Debug hook (debuggable builds only) to drive the flow on the emulator:
        // adb shell am broadcast -a com.jenn.blindboxworld.DEBUG -p com.jenn.blindboxworld --es cmd machine|coin|crank|lift|shake|open|plinth
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) DisposableEffect(Unit) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: android.content.Intent) {
                    when (i.getStringExtra("cmd")) {
                        "verify-curtain" -> { refs.curtain?.let { it.setOpacity(1f); it.enabled=i.getBooleanExtra("visible",true) } }
                        "verify-world" -> {
                            val id = i.getStringExtra("figure") ?: return
                            if (id !in FIGURE_IDS) return
                            WorldVerification.reset()
                            go(Room.WORLD) { figure = id; caught = emptySet(); round.reset(); WorldVerification.active = true }
                        }
                        "inspect-pickup" -> {
                            if (scene != Scene.WORLD) return
                            val index = i.getIntExtra("index", -1)
                            WorldVerification.focus = terrain?.pickupPositions?.getOrNull(index)
                            Log.i("WorldVerification", "inspect $figure index=$index position=${WorldVerification.focus}")
                        }
                        "verify-route" -> {
                            if (scene != Scene.WORLD) return
                            WorldVerification.focus = null
                            WorldVerification.route = true
                            restart()
                        }
                        "verify-exit" -> backToRoom()

                        "machine" -> travel(true)
                        "plinth" -> travel(false)
                        "coin" -> insertCoin()
                        "crank" -> stopCrank()
                        "start" -> stopCrank()
                        "lift" -> if (refs.box != null) { val h = holdPoint(); moveBox(h.x, h.y, h.z); holdingUi = true }
                        "shake" -> repeat(i.getIntExtra("n", 6)) { onShakeReversal(2.5f) }
                        "open" -> openBox()
                        "coins" -> setCoins(i.getIntExtra("n", START_COINS))
                    }
                    Log.i("BlindboxGacha", "debug ${i.getStringExtra("cmd")} room=$roomNow step=$stepNow coins=$coinsNow")
                }
            }
            context.registerReceiver(receiver, android.content.IntentFilter("com.jenn.blindboxworld.DEBUG"), Context.RECEIVER_EXPORTED)
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }

        fun placementSurface(entity: Entity) = listOf("placementWalls", "loftFloor", "stationFloor:", "placementPreview",
            "decor:", "placed:", "machine").any { entity.tagged(it) != null }

        val mouseHover = if (movingItem) Modifier.pointerInput(scene, placing, furnishing.carrying) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type != PointerEventType.Move && event.type != PointerEventType.Enter) continue
                    val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse && !it.pressed }
                    if (mouse == null) {
                        if (event.changes.any { it.type != PointerType.Mouse }) hoverPoint = null
                        continue
                    }
                    val p = mouse.position3d()
                    hoverPoint = Vector3(p.x, p.y, p.z)
                    aimPlacement(hoverPoint)
                }
            }
        } else Modifier

        // Keep the native pointer's 3D intersection throughout a mouse press or pinch.
        val surfacePointer = if (movingItem) Modifier.pointerInput(scene, placing, furnishing.carrying) {
            detectSpatialPointerEvent(context, targetedToEntity = TargetEntity.any(::placementSurface)) { pointers ->
                pointers.firstOrNull { it.pressed }?.position3D?.let { hit ->
                    dragPoint = Vector3(hit.x, hit.y, hit.z)
                    aimPlacement(dragPoint)
                }
                false // Leave the tap/drag recognizers responsible for confirmation.
            }
        } else Modifier

        // Look + pinch taps on 3D entities.
        val tap = Modifier.pointerInput(scene, movingItem) {
            detectSpatialTapGesture(context, targetedToEntity = TargetEntity.any { e ->
                val placementSurface = listOf("placementWalls", "loftFloor", "stationFloor:").any { e.tagged(it) != null }
                if (placementSurface) placing != null || furnishing.carrying != null
                else listOf("box", "decor:", "placed:", "placementPreview", "coin", "slot", "crank", "machine").any { e.tagged(it) != null }
            }) { v ->
                if (modalOpen()) return@detectSpatialTapGesture
                val hit = v.targetEntity ?: return@detectSpatialTapGesture
                val station = hit.tagged("stationFloor:")?.getName()?.substringAfter(':')
                when {
                    placing != null || furnishing.carrying != null -> {
                        val carried = furnishing.carrying
                        val pointingAtPreview = hit.tagged("placementPreview") != null ||
                            (carried != null && hit.tagged("decor:")?.getName() == "decor:$carried") ||
                            (carried == "machine" && hit.tagged("machine") != null)
                        dragPoint = null
                        if (!pointingAtPreview) aimPlacement(Vector3(v.position.x, v.position.y, v.position.z))
                        if (placing != null) figureTarget?.let(::finishPlacement) else finishDecor()
                    }
                    hit.tagged("loftFloor") != null || station != null -> Unit
                    hit.tagged("decor:") != null -> {
                        val id = hit.tagged("decor:")!!.getName().removePrefix("decor:")
                        if (id == "stand") {
                            selectDecor(id)
                        } else showDecorTarget(id)
                    }
                    hit.tagged("placed:") != null -> selectPlaced(hit.tagged("placed:")!!.getName().removePrefix("placed:"))
                    hit.tagged("coin") != null || hit.tagged("slot") != null -> if (roomNow == Room.MACHINE) insertCoin() else travel(true)
                    hit.tagged("crank") != null -> if (roomNow == Room.MACHINE) stopCrank() else travel(true)
                    hit.tagged("box") != null -> openBox()
                    hit.tagged("machine") != null -> if (roomNow != Room.OPENING) travel(true)
                }
            }
        }
        // Pinch-drag: the coin into the slot, the crank around its axle, the box up to shake it.
        val drag = if (scene == Scene.HOME) Modifier.pointerInput(scene, movingItem) {
            detectSpatialDragGesture(context, targetedToEntity = TargetEntity.any { e -> (if (movingItem) listOf("placementWalls", "loftFloor", "stationFloor:", "placementPreview", "decor:", "placed:", "machine") else listOf("box", "coin", "crank")).any { e.tagged(it) != null } },
                onDragStart = { at, v ->
                    val t = v.targetEntity
                    when {
                        modalOpen() -> Unit
                        movingItem -> { dragPoint = Vector3(at.x, at.y, at.z); aimPlacement(dragPoint) }
                        roomNow != Room.MACHINE -> Unit
                        t?.tagged("coin") != null && stepNow == Step.COIN -> { refs.coinDrag = true; refs.cox = 0f; refs.coy = 0f; refs.coz = 0f; buzz() }
                        t?.tagged("box") != null && stepNow == Step.BOX -> {
                            refs.holding = true; holdingUi = true
                            refs.strokeX = 0f; refs.strokeY = 0f; refs.dirX = 0; refs.dirY = 0; refs.ox = 0f; refs.oy = 0f; refs.oz = 0f
                            refs.lastRev = SystemClock.uptimeMillis()
                            val h = holdPoint(); moveBox(h.x, h.y, h.z)
                            buzz()
                        }
                    }
                },
                onDragEnd = {
                    if (movingItem && dragPoint != null) {
                        aimPlacement(dragPoint)
                        if (placing != null) figureTarget?.let(::finishPlacement) else finishDecor()
                        dragPoint = null
                    }
                    if (refs.holding) { refs.holding = false; holdingUi = false; dropBox() }
                    if (refs.coinDrag) {
                        refs.coinDrag = false
                        val c = refs.coin?.pos(); val s = gLocal(COIN_SLOT)
                        if (c != null && dist(c, s) < 0.11f) insertCoin()
                        else refs.coin?.let { val p = gLocal(COIN_SLOT, dz = .15f); it.place(p.x, p.y, p.z, 1.6f, yaw = GACHA_YAW, pitch = 90f) }
                    }
                },
                onDragCancel = { dragPoint = null; if (refs.holding) { refs.holding = false; holdingUi = false; dropBox() }; refs.coinDrag = false },
            ) { d ->
                if (modalOpen()) return@detectSpatialDragGesture
                val dx = meters(d.dragAmount.x)
                val dy = -meters(d.dragAmount.y)
                val dz = meters(d.dragAmount.z)
                val target = d.targetEntity
                if (movingItem) {
                    aimPlacement(dragPoint)
                    return@detectSpatialDragGesture
                }
                if (roomNow != Room.MACHINE) return@detectSpatialDragGesture
                when {
                    refs.coinDrag -> {
                        val (rx, rz) = toRoot(dx, dz)
                        refs.cox += rx; refs.coy += dy; refs.coz += rz
                        val p = gLocal(COIN_SLOT, dz = .15f)
                        refs.coin?.place(p.x + refs.cox, p.y + refs.coy, p.z + refs.coz, 1.6f, yaw = GACHA_YAW, pitch = 90f)
                        val c = refs.coin?.pos()
                        if (c != null && dist(c, gLocal(COIN_SLOT)) < 0.06f) { refs.coinDrag = false; insertCoin() }
                    }
                    target?.tagged("crank") != null && stepNow == Step.CRANK -> stopCrank()
                    refs.holding && stepNow == Step.BOX -> {
                        refs.ox = (refs.ox + dx).coerceIn(-.5f, .5f)
                        refs.oy = (refs.oy + dy).coerceIn(-.5f, .5f)
                        refs.oz = (refs.oz + dz).coerceIn(-.4f, .4f)
                        val (rx, rz) = toRoot(refs.ox, refs.oz)
                        val h = holdPoint()
                        moveBox(h.x + rx, h.y + refs.oy, h.z + rz, yaw = refs.strokeX * 160f, roll = -refs.strokeX * 120f)
                        val now = SystemClock.uptimeMillis()
                        fun axis(dd: Float, stroke: Float, dir: Int, set: (Float, Int) -> Unit) {
                            val s = stroke + dd
                            if (abs(s) > 0.03f) {
                                val sign = if (s > 0) 1 else -1
                                if (dir != 0 && sign != dir) {
                                    val dt = ((now - refs.lastRev).coerceAtLeast(30)) / 1000f
                                    refs.lastRev = now
                                    onShakeReversal(abs(s) / dt)
                                }
                                set(0f, sign)
                            } else set(s, dir)
                        }
                        axis(dx, refs.strokeX, refs.dirX) { s, dd -> refs.strokeX = s; refs.dirX = dd }
                        axis(dy, refs.strokeY, refs.dirY) { s, dd -> refs.strokeY = s; refs.dirY = dd }
                    }
                }
            }
        } else Modifier

        val pinchZoom = Modifier.pointerInput(scene) {
            detectSpatialScaleGesture(context) { event ->
                if (!modalOpen() && event.scaleValue > 0f) ViewZoom.scroll(6f * kotlin.math.ln(event.scaleValue))
            }
        }
        SpatialView(modifier = mouseHover.then(surfacePointer).then(tap).then(drag).then(pinchZoom), attachments = {
            // Every attachment has its own OS window and can own physical keyboard focus.
            fun keyPanel(id: String, content: @Composable () -> Unit) = AttachmentPanel(id) {
                NavigationKeyTarget(currentKeys) {
                    Box(Modifier.pointerInput(Unit) {
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    menuPointerHeld.set(event.changes.any { it.pressed })
                                }
                            }
                        } finally { menuPointerHeld.set(false) }
                    }) { content() }
                }
            }
            if (scene == Scene.WORLD) {
                keyPanel("gameMenu") {
                    ActionCard(280) {
                        GameClock(round, caught.size, WORLD_PICKUPS)
                        CardAction("Return to room") { backToRoom() }
                        CardAction("Restart", action = ::restart)

                    }
                }
                keyPanel("worldModal") {
                    when (round.phase) {
                        RoundPhase.FINISHED -> WorldResult(round, figure, figure in paintingWins || figure in worldPaintings,
                            collected = figure in worldPaintings, claim = {
                                if (!round.preview && figure in paintingWins && figure !in worldPaintings) {
                                    worldPaintings = worldPaintings + figure
                                    prefs.edit().putStringSet("worldPaintings", worldPaintings).apply()
                                    buzz()
                                }
                            }, restart = ::restart, back = { backToRoom() })
                        else -> Unit
                    }
                }
            } else keyPanel("roomInterface") {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    if (card != RoomCard.CLOCK && card != RoomCard.HIDDEN) RoomMenuClose(::closeRoomMenu)
                when (card) {
                    RoomCard.CLOCK -> {
                        GlassPill("${latestSky.clock}   ·   ${latestSky.city}", music = recordMusic,
                            onHome = ::returnToEntrance) { zoomMenu = true }

                    }
                    RoomCard.ZOOM -> ActionCard(320) {
                        ZoomActions()
                        CardAction(if (reducedMotion) "Comfort motion: on" else "Comfort motion: off") {
                            reducedMotion = !reducedMotion
                            prefs.edit().putBoolean("reducedMotion", reducedMotion).apply()
                        }
                        CardAction("Done", action = ::closeRoomMenu)
                    }
                    RoomCard.MACHINE -> MachinePanel(step, coins, freeCoinReady, pendingPrize, lastAccessoryMessage,
                        onCoin = ::insertCoin, onFreeCoin = ::grantFreeCoin, onCrank = ::stopCrank,
                        onOpen = ::openBox, onBack = ::closeRoomMenu,
                        onMove = { furnishing.begin("machine"); dismissedMachine=true; room=Room.PLINTH; refs.rootAtMachine=false })
                    RoomCard.SHOP -> {
                        val fittingFigure = displayed ?: ownedIds.firstOrNull()
                        RoomLibrary(furnishing, coins, unlockedAccessories, onClose = ::closeRoomMenu, onTryAccessory = fittingFigure?.let { id ->
                            { item ->
                                val previous = equippedAccessories[id].orEmpty()
                                draftOutfit = ACCESSORIES.filter { it.id in previous && it.slot != item.slot }
                                    .mapTo(mutableSetOf()) { it.id } + item.id
                                furnishing.closeShop()
                                wardrobeId = id
                                card = RoomCard.ACCESSORIES
                                buzz()
                            }
                        }) { id ->
                            furnishing.buyOrCarry(id, coins) { coins = it }
                            if (furnishing.carrying != null) { inspecting = null; zoomMenu = false; buzz() }
                        }
                    }
                    RoomCard.CARRY -> furnishing.carrying?.let { id ->
                        val item=furnishing.pieces.getValue(id)
                        ActionCard {
                            Text(item.title,fontSize=21.sp,fontWeight=FontWeight.Bold,color=Cream)
                            Text(if(item.wall) "Aim at an open wall" else "Point at an open spot",
                                fontSize=17.sp,color=Cream)
                            if(furnishing.target?.allowed==true) CardAction("Put down",primary=true,action=::finishDecor)
                            else Text("Choose a clear surface",color=Gold,fontSize=16.sp)
                            if (placementPointer.canEnableSensors) CardAction("Enable hand and eye tracking", action = placementPointer::enableSensors)
                        CardAction("Cancel", action = ::closeRoomMenu)
                        }
                    }
                    RoomCard.DECOR -> (furnishing.selected ?: proximityItem)?.let { id ->
                        ActionCard {
                            Text(furnishing.pieces.getValue(id).title,fontSize=21.sp,fontWeight=FontWeight.Bold,color=Cream)
                            if (furnishing.hasRecordControls(id)) CardAction(if(recordMusic.isPlaying) "Pause music" else "Play music",primary=true) {
                                recordMusic.toggle()
                                furnishing.selected=null
                                ignoredItem=id
                                card=RoomCard.CLOCK
                            }
                            if (furnishing.pieces.getValue(id).swappableArt)
                                CardAction("Swap artwork") { furnishing.openArtSwap(id) }
                            CardAction("Move item") { furnishing.begin(id) }
                            CardAction("Done", action = ::closeRoomMenu)
                        }
                    }
                    RoomCard.PLACING -> ActionCard {
                        Text("Point at the floor or a shelf", color = Color.White, fontSize = 19.sp)
                        if (figureTarget != null) CardAction("Put down", primary = true) { figureTarget?.let(::finishPlacement) }
                        val shelfSpot = furnishing.shelfSpot(placements.filterKeys { it != placing }.values)
                        if (shelfSpot != null) CardAction("Place on shelf") { finishPlacement(shelfSpot) }
                        if (placementPointer.canEnableSensors) CardAction("Enable hand and eye tracking", action = placementPointer::enableSensors)
                        CardAction("Cancel", action = ::closeRoomMenu)
                    }
                    RoomCard.STAND, RoomCard.PLACED -> {
                        val id = if (card == RoomCard.PLACED) inspecting else displayed
                        ActionCard {
                            if (id != null) {
                                CardAction("Enter world", primary = true) { enterWorld(id) }
                                CardAction("Add accessories") {
                                    draftOutfit = equippedAccessories[id].orEmpty()
                                    wardrobeId = id
                                    card = RoomCard.ACCESSORIES
                                }
                            }
                            if (ownedIds.size > 1) CardAction("Change collectible") {
                                pickerPage = ownedIds.indexOf(displayed).coerceAtLeast(0) / 4
                                card = RoomCard.PICKER
                            }
                            if (id != null) {
                                CardAction(if (card == RoomCard.STAND) "Remove from shelf" else "Move item") { beginPlacement(id) }
                            }
                            if (card==RoomCard.STAND) CardAction("Move item") { furnishing.begin("stand") }
                            if (card==RoomCard.PLACED || furnishing.selected=="stand")
                                CardAction("Done", action = ::closeRoomMenu)
                        }
                    }
                    RoomCard.ACCESSORIES -> wardrobeId?.let { id ->
                        AccessoryLibrary(id, unlockedAccessories, draftOutfit,
                            onTry = { item ->
                                if (item.id in unlockedAccessories) {
                                    draftOutfit = if (item.id in draftOutfit) draftOutfit - item.id else
                                        ACCESSORIES.filter { it.id in draftOutfit && it.slot != item.slot }
                                            .map { it.id }.toSet() + item.id
                                    buzz()
                                }
                            },
                            onCancel = ::closeRoomMenu,
                            onSave = { saveAccessories(id, draftOutfit); closeRoomMenu() })
                    }
                    RoomCard.PICKER -> if (pickerPage >= 0) {
                        CollectiblePicker(ownedIds, displayed, pickerPage,
                            onPage = { pickerPage = it.coerceIn(0, ownedIds.lastIndex / 4) },
                            onSelect = ::selectCollectible, onClose = ::closeRoomMenu)
                    }
                    RoomCard.HIDDEN -> Unit
                }
                }
            }
            if (scene != Scene.WORLD) {
                keyPanel("exitDoor") {
                    Box(Modifier.width(160.dp)) {
                        CardAction("Exit door") {
                            roomMotion.cancelWalk()
                            onExitDoor()
                        }
                    }
                }
                keyPanel("crankCue") {
                    if (card == RoomCard.MACHINE && room == Room.MACHINE && step == Step.CRANK)
                        Box(Modifier.clickable(onClick = ::stopCrank)) { Chip("Press to stop", Gold, 22) }
                }
                keyPanel("trayCue") {
                    if (card == RoomCard.MACHINE && room == Room.MACHINE && step == Step.BOX)
                        Box(Modifier.clickable(onClick = ::openBox)) { Chip("Tap to open", Gold, 22) }
                }
                keyPanel("entryDither") { EntranceDither(entryThreshold) }
                keyPanel("travelCurtain") { TravelCurtain() }
                keyPanel("objectTarget") {
                    val id = focusedDecor ?: proximityItem
                    if (id != null) ObjectTarget(furnishing.pieces.getValue(id).title) { selectDecor(id) }
                }
                keyPanel("museumLabel") {
                    Column(Modifier.width(320.dp)) {
                        Text(exhibit?.let(::nameOf) ?: "Blindbox World", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF171A1C)) // design-style: fixed-figma-color dark title ink on the display stand plaque
                        val stamp = unboxedAt[exhibit]
                        val subtitle = if (exhibit == null) "Collection display" else if (stamp == null) "Unboxed on —" else
                            "Unboxed on " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(stamp))
                        Text(subtitle, fontSize = 15.sp, color = Color(0xFF26282B)) // design-style: fixed-figma-color secondary ink on the display stand plaque
                    }
                }
            }
            keyPanel("wallet") {
                RoomWallet(coins, walletPayout, scene == Scene.HOME, libraryArrival, onLibraryCenter = { offset ->
                    refs.libraryCenter = Vector3(converter.dpToLength(offset.x.dp,LengthUnit.Meters),
                        converter.dpToLength(offset.y.dp,LengthUnit.Meters),.001f)
                }) {
                    if (scene == Scene.HOME && furnishing.carrying == null && placing == null && room != Room.OPENING && !modalOpen()) {
                        roomMotion.cancelWalk()
                        furnishing.openShop()
                    }
                }
            }
            keyPanel("plus") { Chip(plusText, Gold, 30) }
        }, initial = { content, panels ->
            if (sceneDisposed || ownedScene != null) return@SpatialView
            sfx.preload()
            val root = Entity().apply { components.set(TransformComponent()); setName("sceneRoot") }
            if (scene != Scene.WORLD) root.setOpacity(0f)
            content.addEntity(root)
            refs.root = root
            ownedScene = root
            refs.plus = panels.entity("plus")?.also { it.enabled = false }
            when (scene) {
                Scene.WORLD -> {
                    val overlay = Entity().apply { components.set(TransformComponent()); place(0f, 1.6f, 0f) }
                    val horizon = Entity.loadSuspend("asset://worlds/$figure/horizon.glb")
                    content.addEntity(horizon)
                    worldSkyEntity = horizon
                    content.addEntity(overlay)
                    hud = overlay
                    val info = infoOf(figure)
                    val floor = WorldTerrain.load(context, figure)
                    terrain = floor
                    val scenery = Entity().apply { components.set(TransformComponent()) }
                    root.addChild(scenery)
                    courtyard = scenery
                    val world = Entity().apply { components.set(TransformComponent()) }
                    val origin = floor.origin
                    world.place(origin.x, origin.y, origin.z, WORLD_SCALE)
                    scenery.addChild(world)
                    val folder = "worlds/$figure"
                    val isTung = figure == "tung-tung-tung"
                    val scans = if (isTung) "tung-courtyard" else folder
                    for (section in 0 until info.sections) {
                        val scan = Entity.loadSuspend("asset://$scans/section-$section.spz")
                        scan.useFullQualitySplats()
                        world.addChild(scan)
                    }
                    if (hasRippleWater(figure)) rippleWorld = world
                    else if (info.hasWater) world.addChild(Entity.loadSuspend("asset://$folder/water.glb"))
                    if (figure == "secret-capybara-toilet") pondKoi = PondKoi.attach(context, world)
                    val toy = loadProp(if (isTung) "asset://tung-walking.glb" else "asset://$folder/actor.glb")
                    worldWearables = WorldWearables.attach(context, toy, figure, equippedAccessories[figure].orEmpty())
                    val start = floor.actorStart
                    toy.place(start.x, start.y, start.z, ACTOR_HEIGHT / info.actorRigHeight)
                    scenery.addChild(toy)
                    worldActor = toy
                    val pickupAsset = if (isTung) "asset://tung-wood.glb" else "asset://$folder/pickup.glb"
                    val pickup = loadProp(pickupAsset)
                    floor.pickupPositions.forEachIndexed { i, p ->
                        val item = if (i == 0) pickup else pickup.clone(Entity.CloneOptions(true, true))
                            ?: loadProp(pickupAsset)
                        item.setName("pickup$i")
                        item.place(p.x, p.y, p.z)
                        scenery.addChild(item)
                        item.enabled = i !in caught
                        refs.sparks[i] = item

                    }
                    check(refs.sparks.count { it?.valid == true } == WORLD_PICKUPS) { "World must load all 30 pickups before play" }
                    refs.sparks.filterNotNull().forEach { item ->
                        val hint = Entity().apply { components.set(TransformComponent()); enabled = false }
                        hint.addChild(glowSphere(.025f, Color4(1f, .83f, .28f, 1f)).apply { place(0f, .34f, 0f) })
                        hint.addChild(Entity().apply {
                            components.set(TransformComponent())
                            components.set(com.pico.spatial.core.ecs.ModelComponent(
                                com.pico.spatial.core.ecs.resource.MeshResource.createBox(Vector3(.009f, .22f, .009f), 0f),
                                glowMaterial(Color4(1f, .83f, .28f, 1f))))
                            place(0f, .21f, 0f)
                        })
                        item.addChild(hint)
                        pickupHints.add(hint)
                    }
                    hmd?.latestData?.hmdPose?.let { pose ->
                        root.place(pose.position.x, pose.position.y - 1.55f, pose.position.z, yaw = pose.headingDegrees())
                    }
                    fun mount(id: String, x: Float, y: Float, scale: Float): Entity? =
                        panels.entity(id)?.also { it.place(x, y, -1.65f, scale); overlay.addChild(it); hudPanels[id] = it; it.enabled = false }
                    val panelWidth = converter.dpToLength(WORLD_PANEL_DP.dp, LengthUnit.Meters)
                    mount("worldModal", 0f, .025f, 1.45f / panelWidth)
                    val gameMenu = mount("gameMenu", .46f, .12f, 1.35f)
                    worldHud.mount(content, overlay, figure, gameMenu)
                }
                else -> {
                    val initialLook = latestSky.look
                    refs.rootYaw = root.alignView(returnView, hmd?.latestData?.hmdPose)
                    shell = loadLoft(context,initialLook).also { root.addChild(it) }
                    shellLook = initialLook
                    val overlay = Entity().apply { components.set(TransformComponent()); place(0f,1.6f,0f) }
                    val foreground = RoomHudForeground(overlay,root)
                    roomForeground = foreground
                    roomContent = content
                    floorTarget = roomFloorTarget(content).also { root.addChild(it) }
                    panels.entity("exitDoor")?.let {
                        // collection_room.py: walnut leaf center (-1.88, 1.11, 4.28),
                        // 6 cm thick. Mount 1.5 cm in front of its room-facing surface.
                        root.addChild(it)
                        it.place(-1.88f, 1.35f, 4.235f,
                            .42f / converter.dpToLength(160.dp, LengthUnit.Meters), yaw = 180f)
                        it.enabled = false
                        exitDoorPanel = it
                    }
                    panels.entity("roomInterface")?.let {
                        it.components.set(AccessibilityComponent("Room controls", "Time and place").apply {
                            isAccessibilityElement = true
                        })
                        content.setAccessibilityActions(it, "room.controls") { }
                        it.place(0f,.38f,-1.65f,2.18f)
                        foreground.actionAnchor.place(0f,.38f,-1.65f,2.18f)
                        foreground.layer.addChild(it)
                        roomPanel = it
                    }
                    content.addEntity(overlay)
                    hud = overlay
                    val ambient = Entity().apply { components.set(TransformComponent()) }
                    root.addChild(ambient)
                    sfx.startAmbient(ambient)
                    refs.presentationHead = overlay
                    panels.entity("entryDither")?.let {
                        it.place(0f,0f,-.4f,3f/converter.dpToLength(1024.dp,LengthUnit.Meters))
                        overlay.addChild(it);it.enabled=entryThreshold>0f;entryMask=it
                    }
                    panels.entity("travelCurtain")?.let {
                        val width=converter.dpToLength(1024.dp,LengthUnit.Meters)
                        it.place(0f,0f,-.5f,3f/width)
                        overlay.addChild(it); it.enabled=false; refs.curtain=it
                    }
                    panels.entity("objectTarget")?.let {
                        overlay.addChild(it); it.enabled=false; targetPanel=it
                    }
                    val mount = Entity().apply { components.set(TransformComponent()) }
                    val station = furnishing.visualSpot("machine")
                    mount.place(station.x, station.y, station.z)
                    root.addChild(mount)
                    mount.addChild(stationFloorTarget(true).apply { place(0f,0f,0f,yaw=GACHA_YAW) })
                    refs.machineMount = mount
                    refs.crankCue = panels.entity("crankCue")?.also { it.enabled = false; mount.addChild(it) }
                    refs.trayCue = panels.entity("trayCue")?.also { it.enabled = false; mount.addChild(it) }
                    val machine = loadProp("asset://props/gachapon.glb")
                    machine.place(0f, 0f, 0f, yaw = GACHA_YAW)
                    mount.addChild(machine); refs.machine = machine
                    // Local highlights keep enamel, candy and glass readable through the night cycle.
                    machine.addChild(Entity().apply {
                        components.set(TransformComponent())
                        components.set(com.pico.spatial.core.ecs.PointLightComponent(Color4(1f,.94f,.92f,1f), 165f, 2.4f))
                        place(-.68f,1.93f,.94f)
                    })
                    refs.stock = GlobeStock.mount(machine)
                    val crank = mountMachineCrank(mount)
                    refs.crank = crank
                    placeCrank()
                    val standMount=Entity().apply { components.set(TransformComponent()) }
                    refs.standMount=standMount
                    val stand=furnishing.spot("stand")
                    standMount.place(stand.x,stand.y,stand.z-ISLAND_Z)
                    root.addChild(standMount)
                    standMount.addChild(stationFloorTarget(false).apply { place(0f,0f,ISLAND_Z) })
                    val grass = loadProp("asset://props/display-grass.glb")
                    grass.place(0f, ISLAND_Y + .012f, ISLAND_Z, .47f)
                    standMount.addChild(grass)
                    panels.entity("museumLabel")?.let {
                        val widthMeters = converter.dpToLength(320.dp, LengthUnit.Meters)
                        it.place(0f, .539f, -1.392f, .325f / widthMeters, pitch = -10f)
                        standMount.addChild(it)
                    }
                    val vit = loadProp("asset://props/vitrine.glb")
                    vit.place(0f, ISLAND_Y, ISLAND_Z)
                    sfx.makeObjectSource(vit)
                    standMount.addChild(vit); refs.vitrine = vit
                    if (scene == Scene.HOME) {
                        machine.setName("machine")
                        machine.components.set(CollisionComponent(collisionShape = listOf(
                            ShapeResource.createBox(Vector3(.67f, .90f, .44f)).offsetByTranslation(Vector3(0f, .45f, 0f)),
                            ShapeResource.createSphere(.55f).offsetByTranslation(Vector3(0f, GlobeMix.CENTER_Y, 0f))
                        ), physicsMaterial = PhysicsMaterialResource()))
                        machine.components.set(InteractableComponent())
                        sfx.makeObjectSource(crank)
                        val glowAt = gLocal(CRANK_AXLE, dz = .083f)
                        refs.crankGlow = machineHighlight(.119f).also {
                            it.place(glowAt.x, glowAt.y, glowAt.z,yaw=GACHA_YAW,pitch=90f)
                            it.enabled = false
                            mount.addChild(it)
                        }
                        val prizeAt = gLocal(TRAY_REST, dy = .095f, dz = .105f)
                        refs.prizeGlow = machineHighlight(.128f).also {
                            it.place(prizeAt.x, prizeAt.y, prizeAt.z,yaw=GACHA_YAW,pitch=90f)
                            it.enabled = false
                            mount.addChild(it)
                        }
                        refs.slot = Entity().apply {
                            components.set(TransformComponent()); setName("slot")
                            components.set(CollisionComponent(collisionShape = listOf(ShapeResource.createBox(Vector3(.12f, .15f, .06f))),
                                physicsMaterial = PhysicsMaterialResource()))
                            components.set(InteractableComponent())
                            val p = gLocal(COIN_SLOT, dz = .02f); place(p.x, p.y, p.z, yaw = GACHA_YAW)
                        }.also { sfx.makeObjectSource(it); mount.addChild(it) }
                        refs.chute = Entity().apply { components.set(TransformComponent()); val p = gLocal(CHUTE_MOUTH); place(p.x, p.y, p.z) }
                            .also { sfx.makeObjectSource(it); mount.addChild(it) }
                        refs.coin = loadProp("asset://props/coin.glb").also { c ->
                            c.setName("coin")
                            c.components.set(CollisionComponent(collisionShape = listOf(ShapeResource.createSphere(0.045f)), physicsMaterial = PhysicsMaterialResource()))
                            c.components.set(InteractableComponent()); c.components.set(HoverEffectComponent())
                            sfx.makeObjectSource(c); c.enabled = false; mount.addChild(c)
                        }
                        refs.light = warmLight().also { mount.addChild(it) }
                        if (step == Step.BOX) {
                            registerDispensedPrize(content, mountPrize())
                            val t = gLocal(TRAY_REST)
                            moveBox(t.x,t.y,t.z)
                        }
                        refs.plus?.let { root.addChild(it) }
                        if (room == Room.MACHINE) setRootView(true)
                    }
                }
            }
            hud?.let { overlay ->
                panels.entity("wallet")?.let { panel ->
                    if (scene == Scene.HOME) panel.place(.43f, -.32f, -1.65f, 1.60f)
                    else panel.place(.43f, -.32f, -1.65f, 1.60f)
                    (roomForeground?.layer ?: overlay).addChild(panel)
                    panel.enabled = scene == Scene.HOME
                    walletPanel = panel
                    if (scene == Scene.HOME) refs.libraryPanel = panel
                }
            }
            ready.value = true
            if (scene == Scene.HOME) onRoomReady()
            if (scene == Scene.WORLD) Log.i("WorldVerification", "ready $figure pickups=${refs.sparks.count { it != null }}")
        })

        LaunchedEffect(walletPanel, scene, room, round.phase, round.preview, furnishing.shopOpen, card, accessoryFlying) {
            walletPanel?.enabled = if (scene == Scene.WORLD) round.phase == RoundPhase.FINISHED && !round.preview
                else (room != Room.OPENING || accessoryFlying) && !furnishing.shopOpen && card !in listOf(RoomCard.ACCESSORIES, RoomCard.PICKER)
        }

        LaunchedEffect(ready.value) {
            if (!ready.value) return@LaunchedEffect
            val root = refs.root ?: return@LaunchedEffect
            if (scene != Scene.WORLD) for (f in 0..12) { root.setOpacity(f / 12f); delay(25) }
            if (scene == Scene.HOME) spawnCoin()
        }
        if (scene != Scene.WORLD) {
            DisposableEffect(furnishing) { onDispose { RoomFloor.setFurnishings(null) } }
            LaunchedEffect(furnishing.saved,furnishing.carrying) { RoomFloor.setFurnishings(furnishing.obstacles()) }
            LaunchedEffect(shell,roomContent) {
                val current=shell ?: return@LaunchedEffect
                val root=refs.root ?: return@LaunchedEffect
                val content=roomContent ?: return@LaunchedEffect
                decorateRoom(current,root,content,furnishing,recordMusic,::showDecorTarget)
            }
            LaunchedEffect(ready.value, placing, furnishing.carrying) {
                if (!ready.value || !movingItem) return@LaunchedEffect
                val root = refs.root ?: return@LaunchedEffect
                val walls = placementWallTarget()
                root.addChild(walls)
                try {
                    while (true) {
                        if (dragPoint == null) aimPlacement(hoverPoint)
                        delay(24)
                    }
                } finally {
                    dragPoint = null
                    walls.releaseScene()
                }
            }
            LaunchedEffect(ready.value,furnishing.carrying,placements) {
                if(!ready.value) return@LaunchedEffect
                val held=furnishing.carrying
                val surfaces=furnishing.surfaces().filter { it.id==held }
                val cargo=placements.filterValues { p -> surfaces.any { p.x in it.x && p.z in it.z && abs(p.y-it.y)<.03f } }
                androidx.compose.runtime.snapshotFlow { furnishing.target to furnishing.spot("stand") }.collect {
                    val stand=furnishing.visualSpot("stand")
                    if (refs.settled && refs.islandId != placing) refs.island?.enabled = furnishing.visibleAtAim("stand")
                    refs.standMount?.enabled = furnishing.visibleAtAim("stand")
                    refs.standMount?.place(stand.x,stand.y,stand.z-ISLAND_Z)
                    if(held!=null && cargo.isNotEmpty()) {
                        val before=furnishing.spot(held);val after=furnishing.visualSpot(held)
                        for((id,p) in cargo) placedModels[id]?.apply {
                            enabled = furnishing.visibleAtAim(held)
                            place(p.x+after.x-before.x,p.y+after.y-before.y,
                                p.z+after.z-before.z,scaleForHeight(SHELF_FIGURE_HEIGHT))
                        }
                    }
                }
            }
            LaunchedEffect(ready.value) {
                if (ready.value) refs.root?.let { root ->
                    navigateInterior(root, Interior.LOFT, { refs.rootYaw },
                        { if (canNavigate()) hmd?.latestData?.hmdPose else null },
                        roomMotion, { refs.rootYaw = it })
                }
            }
            LaunchedEffect(ready.value, placing, furnishing.carrying, floorTarget) {
                floorTarget?.enabled = ready.value && (placing != null || furnishing.carrying != null)
            }
            LaunchedEffect(ready.value, canNavigate()) {
                val active = ready.value && canNavigate()
                roomPads?.enabled = active
                if (!active) roomMotion.cancelWalk()
            }
            LaunchedEffect(ready.value, roomMotion.status) {
                val marker = destinationMarker ?: return@LaunchedEffect
                val destination = roomMotion.goal
                if (destination != null) marker.place(destination.x, .037f, destination.z)
                marker.enabled = ready.value && destination != null
                floorTarget?.components?.get(AccessibilityComponent::class.java)?.value = roomMotion.status
            }
            LaunchedEffect(ready.value,sky.look) {
                val root = refs.root
                if (ready.value && root != null && shellLook != sky.look) {
                    val replacement = loadLoft(context,sky.look)
                    root.addChild(replacement)
                    shell?.let { it.removeFromParent(); it.destroy() }
                    shell = replacement
                    shellLook = sky.look
                }
            }
            LaunchedEffect(ready.value) {
                if (ready.value) refs.root?.let { animateWindows(context, it) { latestSky } }
            }
        }
        // The machine controls are only visible while interacting with the machine.
        if (scene == Scene.HOME) LaunchedEffect(ready.value, room, step, holdingUi) {
            if (!ready.value) return@LaunchedEffect
            val atMachine = room == Room.MACHINE || room == Room.OPENING
            refs.plus?.let { p ->
                val at = if (atMachine) machinePoint(Local(.42f, 1.42f, .30f)) else Vector3(0.45f, 1.55f, -1.3f)
                p.place(at.x, at.y, at.z, 1.0f, yaw = if (atMachine) GACHA_YAW - 25f else -15f)
            }
            if (room == Room.MACHINE) spawnCoin()
        }
        if (scene == Scene.HOME) {
            // Keep the room world-anchored during unboxing, preserving free head tracking.
            LaunchedEffect(ready.value, room, card, step) {
                val crankCue = refs.crankCue ?: return@LaunchedEffect
                val trayCue = refs.trayCue ?: return@LaunchedEffect
                val atMachine = card == RoomCard.MACHINE && room == Room.MACHINE
                crankCue.enabled = atMachine && step == Step.CRANK
                trayCue.enabled = atMachine && step == Step.BOX
                val crank = refs.crank
                val content = roomContent
                if (crankCue.enabled && crank != null && content != null) {
                    val at = gLocal(CRANK_AXLE, dy=.20f, dz=.11f)
                    crankCue.place(at.x,at.y,at.z,2.25f,yaw=GACHA_YAW)
                    publishMachineCrank(content, crank, crankCue) {
                        val active = room == Room.MACHINE && step == Step.CRANK
                        if (active) stopCrank()
                        active
                    }
                } else {
                    crank?.components?.get(AccessibilityComponent::class.java)?.isAccessibilityElement = false
                    crankCue.components[AccessibilityComponent::class.java]?.isAccessibilityElement = false
                }
                if (trayCue.enabled && content != null) {
                    val at = gLocal(TRAY_REST, dy=.25f, dz=.12f)
                    trayCue.place(at.x,at.y,at.z,2.25f,yaw=GACHA_YAW)
                    refs.box?.let { registerDispensedPrize(content, it) }
                    publishTrayCue(content, trayCue) {
                        val active = room == Room.MACHINE && step == Step.BOX && refs.box != null
                        if (active) openBox()
                        active
                    }
                } else trayCue.components[AccessibilityComponent::class.java]?.isAccessibilityElement = false
            }
            val machineSpot = furnishing.visualSpot("machine")
            LaunchedEffect(ready.value, machineSpot, furnishing.visibleAtAim("machine")) {
                if (ready.value) refs.machineMount?.apply {
                    enabled = furnishing.visibleAtAim("machine")
                    place(machineSpot.x, machineSpot.y, machineSpot.z)
                }
            }
        }
        val blowerOn = step == Step.CRANK
        if (scene == Scene.HOME) LaunchedEffect(ready.value, blowerOn) {
            if (!ready.value) return@LaunchedEffect
            if (blowerOn) refs.machine?.let { sfx.play(it, "whoosh", .9f) }
            refs.stock?.animate(blowerOn)
        }
        if (scene == Scene.HOME) LaunchedEffect(ready.value, step) {
            if (!ready.value || step != Step.DROP) return@LaunchedEffect
            val a = gLocal(CHUTE_MOUTH)
            val b = gLocal(TRAY_REST)
            val parcel = mountPrize()
            refs.chute?.let { sfx.play(it,"capsule_drop") }
            tween(.38f) { t ->
                moveBox(a.x+(b.x-a.x)*t, a.y+(b.y-a.y)*t*t, a.z+(b.z-a.z)*t,
                    scale=TRAY_BOX_SCALE*(.12f+.88f*easeOut(t)), roll=85f*(1f-t))
            }
            tween(.20f) { t -> moveBox(b.x,b.y + abs(sin(t*6.28f))*.016f*(1f-t),b.z) }
            moveBox(b.x,b.y,b.z)
            buzz()
            step = Step.BOX
            roomContent?.let { registerDispensedPrize(it, parcel) }
        }
        LaunchedEffect(coins) { if (ready.value && scene == Scene.HOME) spawnCoin() }
        if (scene == Scene.HOME) {
            WardrobePreview(roomForeground, wardrobeId, draftOutfit)
            if (pickerPage >= 0) {
                CollectiblePickerPreview(roomForeground, ownedIds.drop(pickerPage * 4).take(4), equippedAccessories)
            }
            LaunchedEffect(ready.value, placements, equippedAccessories, furnishing.carrying) {
                val root = refs.root ?: return@LaunchedEffect
                if (!ready.value) return@LaunchedEffect
                val content: SpatialViewContent = roomContent ?: return@LaunchedEffect
                for (id in placedModels.keys - placements.keys) placedModels.remove(id)?.releaseScene()
                for (entry in placements.entries) {
                    val id = entry.key
                    val spot = entry.value
                    val outfit = equippedAccessories[id].orEmpty()
                    val model = placedModels[id]?.also { it.updateWornAccessories(id, outfit) }
                        ?: loadDressedFigure(id, outfit).also {
                        if (id !in placements) {
                            it.releaseScene()
                            return@LaunchedEffect
                        }
                        it.setName("placed:$id")
                        registerPlacedCollectible(content, it, id) { selectPlaced(id) }
                        it.makeTappable(Vector3(1.2f, 1.2f, 1.2f), .54f)
                        root.addChild(it)
                        placedModels[id] = it
                    }
                    model.place(spot.x, spot.y, spot.z, scaleForHeight(SHELF_FIGURE_HEIGHT))
                    model.enabled = id in placements && id != placing
                }
            }
            LaunchedEffect(ready.value, displayed, equippedAccessories[displayed]) {
                val root = refs.root ?: return@LaunchedEffect
                if (!ready.value) return@LaunchedEffect
                val id = displayed
                val outfit = equippedAccessories[id].orEmpty()
                if (id == refs.islandId) {
                    if (id != null) refs.island?.let { model ->
                        model.updateWornAccessories(id, outfit)
                        if (id == displayed) model.enabled = id != placing
                    }
                    return@LaunchedEffect
                }
                refs.island?.let { it.removeFromParent(); it.destroy() }
                refs.island = null
                refs.islandId = null
                if (id == null) return@LaunchedEffect
                val model = loadDressedFigure(id, outfit)
                if (id != displayed) {
                    model.releaseScene()
                    return@LaunchedEffect
                }
                val stand=furnishing.visualSpot("stand")
                model.place(stand.x,stand.y+EXHIBIT_FOOT_Y,stand.z,BOX_SCALE)
                root.addChild(model)
                refs.island = model
                refs.islandId = id
            }
            LaunchedEffect(ready.value, placing) {
                val root = refs.root ?: return@LaunchedEffect
                val id = placing ?: return@LaunchedEffect
                if (!ready.value) return@LaunchedEffect
                val content: SpatialViewContent = roomContent ?: return@LaunchedEffect
                val model = loadDressedFigure(id, equippedAccessories[id].orEmpty())
                model.setName("placementPreview")
                registerPlacementPreview(content, model, id) {
                    if (placing != id || !model.enabled) false else {
                        figureTarget?.let(::finishPlacement)
                        true
                    }
                }
                model.makeTappable(Vector3(1.25f,1.3f,1.2f), .55f)
                model.enabled = false
                root.addChild(model)
                val original = placedModels[id] ?: refs.island?.takeIf { refs.islandId == id }
                original?.enabled = false
                try {
                    androidx.compose.runtime.snapshotFlow { figureTarget }.collect { spot ->
                        if (spot != null) model.place(spot.x,spot.y,spot.z,scaleForHeight(SHELF_FIGURE_HEIGHT))
                        model.enabled = spot != null
                    }
                } finally {
                    figureTarget = null
                    if (refs.root === root) {
                        model.releaseScene()
                        if (original != null && (original === refs.island || original === placedModels[id])) {
                            original.enabled = true
                        }
                    }
                }
            }
            LaunchedEffect(ready.value) {
                val root = refs.root ?: return@LaunchedEffect
                if (!ready.value) return@LaunchedEffect
                while (true) {
                    val pose=hmd?.latestData?.hmdPose
                    val head=pose?.position?.let { root.convertPositionFrom(it, null) }
                    exitDoorPanel?.enabled = head != null &&
                        hypot(head.x + 1.88f, head.z - 4.28f) < 1.65f && !modalOpen()
                    val fwd=pose?.rotation?.rotateVector(Vector3(0f,0f,-1f))
                    val direction=fwd?.let { toRoot(it.x,it.z) }
                    val stand=furnishing.spot("stand")
                    val sx=stand.x-(head?.x ?: 0f);val sz=stand.z-(head?.z ?: 0f)
                    val range=hypot(sx,sz)
                    val nearStand=head!=null && range<=2.72f && direction!=null && (sx*direction.first+sz*direction.second)>.62f*range
                    proximityItem=if (head!=null && direction!=null) furnishing.facingItem(RoomSpot(head.x,head.y,head.z),direction.first,fwd.y,direction.second) else null
                    dismissedAt?.let { at ->
                        if (SystemClock.uptimeMillis()>hudRestoreUntil && head != null && hypot(head.x-at.x,head.z-at.z)>.75f) dismissedAt=null
                    }
                    focusedDecor?.let { id ->
                        val p=furnishing.spot(id)
                        if (head == null || hypot(head.x-p.x,head.z-p.z)>2.5f) focusedDecor=null
                    }
                    val record=furnishing.spot("record-player")
                    val listening=furnishing.hasRecordControls(ignoredItem) && recordMusic.hasStarted && head!=null &&
                        (hypot(head.x-record.x,head.z-record.z)<1.95f || ignoredItem==proximityItem)
                    if (!listening && ignoredItem!=null && ignoredItem!=proximityItem && !(ignoredItem=="stand" && nearStand)) ignoredItem=null
                    card = when {
                        furnishing.shopOpen -> RoomCard.SHOP
                        furnishing.carrying!=null -> RoomCard.CARRY
                        wardrobeId != null -> RoomCard.ACCESSORIES
                        pickerPage >= 0 -> RoomCard.PICKER
                        placing != null -> RoomCard.PLACING
                        room == Room.OPENING -> RoomCard.HIDDEN
                        zoomMenu -> RoomCard.ZOOM
                        inspecting != null && inspecting in placements -> RoomCard.PLACED
                        furnishing.selected=="stand" -> RoomCard.STAND
                        furnishing.selected!=null -> RoomCard.DECOR
                        dismissedAt != null -> RoomCard.CLOCK
                        listening -> RoomCard.CLOCK
                        room == Room.MACHINE && nearMachine -> RoomCard.MACHINE
                        proximityItem=="record-player" && ignoredItem!=proximityItem -> RoomCard.DECOR
                        nearStand && ignoredItem!="stand" && room != Room.MACHINE -> RoomCard.STAND
                        else -> RoomCard.CLOCK
                    }
                    val targetId=focusedDecor ?: proximityItem
                    targetPanel?.let { target ->
                        val overlay=hud
                        val eligible=targetId != null && targetId !in setOf("stand","machine","record-player") &&
                            dismissedAt == null && targetId != ignoredItem &&
                            card==RoomCard.CLOCK && !movingItem && overlay != null
                        target.enabled=eligible
                        if (eligible) {
                            val point=roomObjectTarget(furnishing,targetId,root,overlay)
                            target.enabled=point != null
                            point?.let { target.place(it.x,it.y,it.z,.10f/converter.dpToLength(56.dp,LengthUnit.Meters)) }
                        }
                    }
                    delay(40)
                }
            }
            LaunchedEffect(ready.value, card, step, inspecting, placements) {
                val visiblePanel = roomPanel ?: return@LaunchedEffect
                val foreground = roomForeground ?: return@LaunchedEffect
                val panel = foreground.actionAnchor
                val isLibrary = card == RoomCard.ACCESSORIES
                val isLarge = isLibrary || card == RoomCard.SHOP || card == RoomCard.PICKER
                val surface = if (isLarge) foreground.modalLayer else foreground.layer
                if (visiblePanel.getParent() != surface) {
                    visiblePanel.removeFromParent()
                    surface.addChild(visiblePanel)
                }
                if (isLarge) foreground.fitModal(if (isLibrary) LIBRARY_DISTANCE else PICKER_DISTANCE)
                val root = refs.root ?: return@LaunchedEffect
                val overlay = hud ?: return@LaunchedEffect
                val headLocked = card == RoomCard.CLOCK || card == RoomCard.ZOOM || card == RoomCard.PLACING ||
                    card == RoomCard.PLACED || card == RoomCard.ACCESSORIES || card == RoomCard.PICKER ||
                    card == RoomCard.CARRY || card == RoomCard.DECOR || card == RoomCard.SHOP
                val parent = if (headLocked) overlay else root
                if (panel.getParent() != parent) { panel.removeFromParent(); parent.addChild(panel) }
                visiblePanel.enabled = card != RoomCard.HIDDEN
                visiblePanel.components[AccessibilityComponent::class.java]?.value = when (card) {
                    RoomCard.CLOCK -> "Time and place"
                    RoomCard.CARRY -> "Place room item"
                    RoomCard.DECOR -> "Room item actions"
                    RoomCard.SHOP -> "Shop"
                    RoomCard.ZOOM -> "Room zoom"
                    RoomCard.MACHINE -> "Gumball machine"
                    RoomCard.STAND -> "Display stand"
                    RoomCard.PLACED -> "Collectible actions"
                    RoomCard.PLACING -> "Choose a position"
                    RoomCard.ACCESSORIES -> "Accessory library"
                    RoomCard.PICKER -> "Choose a collectible"
                    RoomCard.HIDDEN -> ""
                }
                when (card) {
                    RoomCard.CLOCK -> panel.place(0f,.38f,-1.65f,2.18f)
                    RoomCard.ZOOM -> panel.place(0f,.18f,-1.65f,1.7f)
                    RoomCard.PLACING, RoomCard.PLACED, RoomCard.CARRY, RoomCard.DECOR -> panel.place(-.50f,.12f,-1.65f,1.45f)
                    RoomCard.STAND -> furnishing.spot("stand").let { panel.place(it.x+.62f,it.y+1.16f,it.z+.08f,1.59f,yaw=-15f) }
                    RoomCard.ACCESSORIES -> {
                        val width = converter.dpToLength(LIBRARY_WIDTH_DP.dp, LengthUnit.Meters)
                        panel.place(0f, 0f, -LIBRARY_DISTANCE, LIBRARY_WIDTH_METERS / width)
                    }
                    RoomCard.PICKER, RoomCard.SHOP -> {
                        val width = converter.dpToLength(PICKER_WIDTH_DP.dp, LengthUnit.Meters)
                        panel.place(0f, 0f, -PICKER_DISTANCE, PICKER_WIDTH_METERS / width)
                    }
                    RoomCard.MACHINE -> {
                        val p = machinePoint(when (step) {
                            Step.CRANK -> Local(.66f, .90f, .50f)
                            Step.BOX -> Local(.65f, .48f, .52f)
                            else -> GACHA_SIGN
                        })
                        val width = converter.dpToLength(320.dp,LengthUnit.Meters)
                        panel.place(p.x,p.y,p.z,.50f/width,yaw=GACHA_YAW)
                    }
                    RoomCard.HIDDEN -> Unit
                }
            }
        }
        // Meter decay, coin bob, crank pulse, museum turntable and proximity gating.
        if (scene == Scene.HOME) LaunchedEffect(ready.value) {
            if (!ready.value) return@LaunchedEffect
            var t = 0f
            while (true) {
                t += 0.04f
                val pulse = 0.5f + 0.5f * sin(t * 4f)
                if (roomNow == Room.MACHINE && stepNow == Step.BOX) {
                    if (!refs.holding) meter.floatValue = (meter.floatValue - 0.015f).coerceAtLeast(0f)
                    val m = meter.floatValue
                    refs.light?.setLight(LIGHT_MAX * m * m, RarityWarm)
                }
                refs.coin?.takeIf { step == Step.COIN && it.enabled && !refs.coinDrag }?.let { c ->
                    val p = gLocal(COIN_SLOT, dz = .15f)
                    c.place(p.x, p.y + sin(t * 3f) * .012f, p.z, 1.6f, yaw = GACHA_YAW + t * 90f, pitch = 90f)
                }
                // HMD tracking is in stage space; the teleport turns and translates the entire room.
                val head = hmd?.latestData?.hmdPose?.position
                val spot = furnishing.spot("machine")
                val localHead = head?.let { h -> refs.root?.let { it.convertPositionFrom(h, null) } }
                val range = if (nearMachine) 2.88f else 2.67f
                nearMachine = localHead != null && hypot(localHead.x-spot.x, localHead.z-spot.z) <= range
                if (!nearMachine && room != Room.MACHINE) dismissedMachine = false
                if (dismissedAt == null && !dismissedMachine && nearMachine && !modalOpen() && furnishing.carrying == null && placing == null && inspecting == null && (room == Room.PLINTH || room == Room.REVEAL)) room = Room.MACHINE
                if (step == Step.CRANK) {
                    refs.crankAngle = (refs.crankAngle + .18f) % (2f * Math.PI.toFloat())
                    placeCrank()
                }
                refs.prizeGlow?.let {
                    it.enabled = nearMachine && room == Room.MACHINE && step == Step.BOX
                    if (it.enabled) it.setOpacity(.66f + .34f * pulse)
                }
                refs.crankGlow?.let { glow ->
                    glow.enabled = nearMachine && room == Room.MACHINE && step == Step.CRANK
                    if (glow.enabled) glow.setOpacity(.66f + .34f*pulse)
                }
                if (refs.settled && (roomNow == Room.PLINTH || roomNow == Room.REVEAL || roomNow == Room.MACHINE)) refs.island?.let { f ->
                    refs.figYaw = (refs.figYaw + 0.6f) % 360f
                    val yaw = if (refs.islandId == "ballerina-capuccina") 4.6f * sin(t * .4f) else refs.figYaw
                    val stand=furnishing.visualSpot("stand")
                    f.place(stand.x,stand.y+EXHIBIT_FOOT_Y,stand.z,BOX_SCALE,yaw=yaw)
                }
                if (!nearMachine && !refs.rootAtMachine && room == Room.MACHINE) room = Room.PLINTH
                delay(40)
            }
        }
        LaunchedEffect(ready.value, caught.size, round.phase) {
            val show = round.phase == RoundPhase.PLAYING && caught.size in 25 until WORLD_PICKUPS
            pickupHints.forEachIndexed { index, hint -> hint.enabled = show && index !in caught }
        }
        if (scene == Scene.WORLD) LaunchedEffect(ready.value, round.phase) {
            if (!ready.value) return@LaunchedEffect
            val intro = round.phase == RoundPhase.INTRO
            val overlay = hud ?: return@LaunchedEffect
            for ((id, entity) in hudPanels) {
                val visible = if (id == "worldModal") round.phase == RoundPhase.FINISHED
                    else round.phase == RoundPhase.PLAYING
                entity.enabled = visible
                if (visible && entity.getParent() != overlay) overlay.addChild(entity)
                if (!visible && entity.getParent() != null) entity.removeFromParent()
            }
            worldHud.show(round.phase, 3)
            if (intro) {
                for (n in 3 downTo 1) { worldHud.show(RoundPhase.INTRO, n); delay(1000) }
                round.play()
            }
        }
        LaunchedEffect(pondKoi) {
            val school = pondKoi ?: return@LaunchedEffect
            val start = android.os.SystemClock.elapsedRealtime()
            while (true) {
                school.update((android.os.SystemClock.elapsedRealtime() - start) / 1000f)
                delay(33)
            }
        }

        LaunchedEffect(rippleWorld) {
            rippleWorld?.let { animateWorldWater(it, context, figure, swimWake) }
        }
        LaunchedEffect(hud) {
            val transform = hud?.components?.get(TransformComponent::class.java) ?: return@LaunchedEffect
            while (true) {
                val frame = androidx.compose.runtime.withFrameNanos { it }
                hmd?.latestData?.hmdPose?.let {
                    transform.setPosition(it.position)
                    transform.setQuaternion(it.rotation)
                    if (!menuPointerHeld.get()) roomForeground?.update(frame, hudOccluders, walletPanel, roomPads, roomPanel, modalOpen())
                    roomForeground?.quietHud(frame, walletPanel, roomPads, roomPanel,
                        padsAllowed = ready.value && canNavigate(),
                        walletAllowed = (room != Room.OPENING || accessoryFlying) && !furnishing.shopOpen &&
                            card !in listOf(RoomCard.ACCESSORIES, RoomCard.PICKER),
                        clock = card == RoomCard.CLOCK, panelAllowed = card != RoomCard.HIDDEN,
                        navigating = roomMotion.isNavigating,
                        interacting = SystemClock.uptimeMillis()<hudRestoreUntil || roomMotion.input.padHeld || modalOpen() || card !in listOf(RoomCard.CLOCK, RoomCard.HIDDEN))
                    worldSkyEntity?.components?.get(TransformComponent::class.java)?.apply {
                        setPosition(it.position)
                        courtyard?.let { world -> setQuaternion(world.convertRotationTo(com.pico.spatial.core.math.Quat.identity(), null)) }
                    }
                }
            }
        }
        LaunchedEffect(ready.value, worldActor, roundId) {
            val actor = worldActor ?: return@LaunchedEffect
            val garden = courtyard ?: return@LaunchedEffect
            val floor = terrain ?: return@LaunchedEffect
            if (ready.value) driveWorldActor(sticks, actor, floor, infoOf(figure), garden, worldWearables, swimWake,
                { hmd?.latestData?.hmdPose }, { round.phase == RoundPhase.PLAYING && (!WorldVerification.active || WorldVerification.route) }, ::catchSpark, { backToRoom() })
        }
        LaunchedEffect(ready.value, worldActor, roundId) {
            val root = refs.root ?: return@LaunchedEffect
            val garden = courtyard ?: return@LaunchedEffect
            val actor = worldActor ?: return@LaunchedEffect
            if (ready.value) followWorldActor(root, garden, actor, { hmd?.latestData?.hmdPose },
                sticks)
        }
        if (scene == Scene.WORLD) LaunchedEffect(ready.value) {
            if (!ready.value) return@LaunchedEffect
            val floor = terrain ?: return@LaunchedEffect
            var t = 0f
            while (true) {
                if (round.phase != RoundPhase.PLAYING) { delay(50); continue }
                for (i in 0 until WORLD_PICKUPS) if (i !in caught) refs.sparks[i]?.let { s ->
                    val h = floor.pickupPositions[i]
                    s.place(h.x, h.y + sin(t * 1.6f + i * 2f) * 0.025f, h.z, 1f + sin(t * 3f + i) * 0.06f)
                }
                t += 0.04f
                delay(40)
            }
        }
    }
}

private fun Entity.useFullQualitySplats() {
    components[com.pico.spatial.core.ecs.GaussianSplattingComponent::class.java]?.enablePerformanceMode = false
    getChildren().forEach { it.useFullQualitySplats() }
}

/** Prop GLBs keep their exported node offset/rotation; wrap them so placing the holder doesn't overwrite it. */
private suspend fun loadProp(path: String): Entity {
    val model = Entity.loadSuspend(path)
    return Entity().apply { components.set(TransformComponent()); addChild(model) }
}

private fun Entity.scaleF(): Float = components[TransformComponent::class.java]?.scaleVector?.x ?: 1f
private fun Entity.yawDeg(): Float = components[TransformComponent::class.java]?.eulerAngles?.yaw ?: 0f

@Composable
private fun Chip(text: String, color: Color, size: Int) {
    Row(Modifier.background(PanelBack, RoundedCornerShape(40.dp)).padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun MachinePanel(
    step: Step, coins: Int, freeCoinReady: Boolean,
    prize: DrawPrize?, lastAccessoryMessage: String?,
    onCoin: () -> Unit, onFreeCoin: () -> Unit, onCrank: () -> Unit,
    onOpen: () -> Unit, onBack: () -> Unit, onMove: () -> Unit,
) {
    ActionCard(width = 320) {
        Text("Daily draw  ·  1 coin", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (step == Step.COIN && lastAccessoryMessage != null) {
            Text(lastAccessoryMessage, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Gold)
            Text("Find it in Add accessories on any collectible.", fontSize = 17.sp, color = Cream)
        }
        val packageName = if (prize is DrawPrize.Figure) "blind box" else "capsule"
        val hint = when (step) {
            Step.COIN -> if (coins > 0) "Draw a Slop blind box or an accessory capsule" else "Earn coins by completing a world round"
            Step.CRANK -> "Press the highlighted crank to stop the draw"
            Step.DROP -> "Dispensing your $packageName…"
            Step.BOX -> "Your $packageName is in the tray"
        }
        Text(hint, fontSize = 19.sp, color = Color.White)
        when (step) {
            Step.COIN -> if (coins > 0) CardAction("Insert coin  ·  $coins left", primary = true, action = onCoin)
                         else if (freeCoinReady) CardAction("Daily free coin", primary = true, action = onFreeCoin)
            Step.CRANK -> CardAction("Press to stop", primary = true, action = onCrank)
            Step.BOX -> CardAction(if (prize is DrawPrize.Figure) "Open box" else "Open capsule", primary = true, action = onOpen)
            Step.DROP -> Unit
        }
        if (step==Step.COIN) CardAction("Move item",action=onMove)
        CardAction("Nevermind", action = onBack)
    }
}
