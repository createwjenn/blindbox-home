package com.jenn.blindboxworld.content

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.AccessibilityComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.setAccessibilityActions
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun BlindboxExperience() {
    var entered by remember { mutableStateOf(false) }
    var threshold by remember { mutableFloatStateOf(0f) }
    var roomReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tracking = remember { runCatching { HMDTrackingProvider() }.getOrNull() }
    DisposableEffect(tracking) {
        runCatching { tracking?.start() }
        onDispose { runCatching { tracking?.stop() } }
    }
    if (entered) HomeStage(tracking, threshold, onExitDoor = {
        roomReady=false
        threshold=0f
        entered=false
    }) { roomReady = true } else DoorLanding(tracking, threshold) {
        scope.launch {
            for (step in 1..40) { threshold = step / 40f; delay(16) }
            delay(80) // Present a fully covered frame before replacing the landing.
            entered = true
        }
    }
    LaunchedEffect(roomReady) {
        if (roomReady) {
            delay(160)
            for (step in 48 downTo 0) { threshold = step / 48f; delay(16) }
        }
    }
}

@Composable
private fun DoorLanding(tracking: HMDTrackingProvider?, threshold: Float, enter: () -> Unit) {
    val scope = rememberCoroutineScope()
    var transitionAnchor by remember { mutableStateOf<Entity?>(null) }
    var transitionPanel by remember { mutableStateOf<Entity?>(null) }
    val maskScale=3f/LocalPhysicalLengthConverter.current.dpToLength(1024.dp,LengthUnit.Meters)
    LaunchedEffect(transitionAnchor) {
        while(true) {
            withFrameNanos { }
            tracking?.latestData?.hmdPose?.let { pose ->
                transitionAnchor?.components?.get(TransformComponent::class.java)?.apply {
                    setPosition(pose.position);setQuaternion(pose.rotation)
                }
            }
        }
    }
    LaunchedEffect(threshold,transitionPanel) { transitionPanel?.enabled=threshold>0f }
    var doorLeaf by remember { mutableStateOf<Entity?>(null) }
    var doorButton by remember { mutableStateOf<Entity?>(null) }
    var landingRoot by remember { mutableStateOf<Entity?>(null) }
    var ready by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            ready = false
            transitionAnchor?.releaseScene()
            landingRoot?.releaseScene()
            landingRoot = null
        }
    }
    LaunchedEffect(landingRoot, ready) {
        if (ready) landingRoot?.let { root ->
            navigateInterior(root, Interior.LANDING, { root.heading() }, { tracking?.latestData?.hmdPose })
        }
    }
    val context = LocalContext.current
    val buttonWidth = 180.dp
    val buttonScale = .58f / LocalPhysicalLengthConverter.current.dpToLength(buttonWidth, LengthUnit.Meters)
    LaunchedEffect(landingRoot) {
        val root = landingRoot ?: return@LaunchedEffect
        animateEntryCirrus(root, context)
    }
    fun enterDoor(): Boolean {
        if (!ready || disposed) return false
        ready = false
        doorButton?.enabled = false
        scope.launch {
            val transform = doorLeaf?.components?.get(TransformComponent::class.java)
            for (step in 0..60) {
                val t=step/60f
                val eased=t*t*(3f-2f*t)
                transform?.setEulerAngles(com.pico.spatial.core.math.EulerAngles(0f,96f*eased,0f))
                delay(16)
            }
            delay(120)
            enter()
        }
        return true
    }
    val tapDoor = Modifier.pointerInput(Unit) {
        detectSpatialTapGesture(context, targetedToEntity = TargetEntity.any { it.tagged("entryDoor") != null }) {
            enterDoor()
        }
    }
    SpatialView(modifier = tapDoor, attachments = {
        AttachmentPanel("entryDither") { EntranceDither(threshold) }
        AttachmentPanel("doorButton") {
            Box(Modifier.width(buttonWidth)) {
                CardAction("Enter room", primary = true) { enterDoor() }
            }
        }
    }, initial = { content, panels ->
        if (disposed || landingRoot != null) return@SpatialView
        val initContext = currentCoroutineContext()
        val landing = Entity().apply { components.set(TransformComponent()) }
        landingRoot = landing
        try {
            content.addEntity(landing)
            val mask=Entity().apply { components.set(TransformComponent()) }
            content.addEntity(mask);transitionAnchor=mask
            panels.entity("entryDither")?.let {
                mask.addChild(it);it.place(0f,0f,-.4f,maskScale);it.enabled=false;transitionPanel=it
            }
            fun ownsLanding() = initContext.isActive && !disposed && landingRoot === landing && landing.valid
            if (!ownsLanding()) return@SpatialView
            // Receive ownership even if a native load completes after the landing is gone.
            withContext(NonCancellable) {
                val scene = Entity.loadSuspend("asset://atmosphere/landing.glb")
                if (!ownsLanding()) {
                    scene.releaseScene()
                    return@withContext
                }
                val frame = Entity().apply { components.set(TransformComponent()) }
                landing.addChild(frame)
                frame.addChild(scene)
                // Some SDK backends flatten imported GLB nodes. In that case bind the hit
                // volume at precisely the authored slab instead of making the sky interactive.
                val door = scene.namedDescendant("Entry door") ?: Entity().apply {
                    components.set(TransformComponent())
                    place(0f, .86f, -4.12f)
                    frame.addChild(this)
                }
                doorLeaf = scene.namedDescendant("Door leaf")
                door.setName("entryDoor")
                door.makeTappable(Vector3(1.42f, 2.52f, .30f), 1.25f)
                door.components.set(AccessibilityComponent("Door 404", "Entrance").apply {
                    isAccessibilityElement = true
                })
                content.setAccessibilityActions(door, "landing.front-door") {
                    onClick("Enter room") { enterDoor() }
                }
                panels.entity("doorButton")?.let { button ->
                    doorButton = button
                    // Door-local metres. The brass plaque is centered at y=1.87 and faces +Z.
                    button.place(0f, 1.62f, .175f, buttonScale)
                    door.addChild(button)
                    button.components.set(AccessibilityComponent("Entry controls", "").apply {
                        isAccessibilityElement = true
                    })
                    content.setAccessibilityActions(button, "landing.entry-controls") { }
                }
                val lamp = Entity().apply {
                    components.set(TransformComponent())
                    components.set(PointLightComponent(Color4(1f,.94f,.85f,1f),225f,4.5f))
                    place(-.85f,3.61f,-2.54f)
                }
                if (!frame.addChild(lamp)) lamp.releaseScene()
                // PICO can initially report its previous tracking origin until recenter completes.
                // Show the sky immediately, then anchor the door in this SpatialView's space.
                door.enabled = false
                val settleUntil = android.os.SystemClock.uptimeMillis() + 1100
                while (ownsLanding() && android.os.SystemClock.uptimeMillis() < settleUntil) delay(20)
                if (!ownsLanding()) return@withContext
                val head = tracking?.latestData?.hmdPose
                landing.alignView(LoftView(0f, 1.55f, 0f, 0f), head)
                // Frame the full door below the sightline (reference center at 51%, 62.5%).
                // Moving the entrance group keeps the starting eyes inside the landing's travel bounds.
                val offset = Vector3(.092f, -.74f, -3.66f)
                val desired = if (head == null) Vector3(offset.x, 1.55f + offset.y, offset.z) else {
                    // Use the launch pitch as well as yaw so a tilted headset sees the door face-on.
                    frame.components[TransformComponent::class.java]?.setQuaternion(
                        landing.convertRotationFrom(head.rotation, null))
                    val direction = head.rotation.rotateVector(offset)
                    landing.convertPositionFrom(Vector3(head.position.x + direction.x,
                        head.position.y + direction.y, head.position.z + direction.z), null)
                }
                val center = door.convertPositionTo(Vector3(0f, 1.25f, 0f), landing)
                frame.components[TransformComponent::class.java]?.setPosition(
                    Vector3(desired.x - center.x, desired.y - center.y, desired.z - center.z))
                door.enabled = true
                ready = true
            }
        } finally {
            if ((!ready || !initContext.isActive) && landingRoot === landing) {
                ready = false
                landingRoot = null
                landing.releaseScene()
            }
        }
    })
}

private fun Entity.namedDescendant(name: String): Entity? {
    if (getName() == name) return this
    return getChildren().firstNotNullOfOrNull { it.namedDescendant(name) }
}

private val roomClockGlass = Color(0x33FFFFFF) // design-style: fixed-figma-color User-specified 20% white glass.
private val roomClockShape = RoundedCornerShape(1000.dp)

@Composable
internal fun GlassPill(label: String, music: RoomRecordPlayer? = null, onHome: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val shadow = with(LocalDensity.current) {
        Shadow(Color.Black.copy(alpha = .68f), Offset(0f, 1.dp.toPx()), 2.dp.toPx())
    }
    Row(Modifier.clip(roomClockShape).background(roomClockGlass).drawWithCache {
        val sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = .58f),
            .06f to Color.Transparent, .93f to Color.Transparent, 1f to Color.White.copy(alpha = .24f))
        onDrawBehind { drawRect(sheen) }
    }, verticalAlignment = Alignment.CenterVertically) {
        if (onHome != null) androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.jenn.blindboxworld.R.drawable.room_home),
            contentDescription = "Home — return to entrance",
            modifier = Modifier.clickable(role=Role.Button,onClick=onHome)
                .padding(start=18.dp,end=4.dp,top=10.dp,bottom=10.dp)
                .then(Modifier.width(28.dp)),
        )
        Text(label, modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 10.dp),
            style = PicoTheme.typography.titleMedium.copy(shadow = shadow), color = Color.White,
            fontWeight = FontWeight.SemiBold, fontSize = 23.sp, maxLines = 1)
        if (music?.hasStarted == true) {
            val action = if (music.isPlaying) "Pause music" else "Play music"
            Text(if (music.isPlaying) "Ⅱ" else "▶", modifier = Modifier
                .semantics { contentDescription = action }
                .clickable(role = Role.Button, onClickLabel = action) { music.toggle() }
                .padding(start = 8.dp, end = 22.dp, top = 10.dp, bottom = 10.dp),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        }
    }
}
