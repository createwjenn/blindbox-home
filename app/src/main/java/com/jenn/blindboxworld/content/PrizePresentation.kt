package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.sin

internal class PrizePackage(val body: Entity, val pieces: Map<String, Entity>)

/** Stream the printed panels while the closed prize is still visible in the machine. */
internal suspend fun preparePrizePackage(holder: Entity, prize: DrawPrize): PrizePackage {
    val body = Entity().apply { components.set(TransformComponent()); enabled = false }
    holder.addChild(body)
    val shell = (prize as? DrawPrize.Wearable)?.shell?.removePrefix("gumball-")
    val labels = if (shell == null) listOf("lid", "front", "right", "back", "left", "base") else listOf("upper", "lower")
    val pieces = mutableMapOf<String, Entity>()
    try {
        for (label in labels) {
            val part = Entity.loadSuspend("asset://props/opening/${shell ?: "box"}-$label.glb")
            if (!holder.valid || !body.valid || !body.addChild(part)) {
                part.releaseScene()
                throw CancellationException("Prize closed while loading")
            }
            pieces[label] = part
        }
        return PrizePackage(body, pieces)
    } catch (failure: Exception) {
        body.releaseScene()
        throw failure
    }
}

/** A head-aligned little stage, independent of the room and its blurred background. */
internal suspend fun presentPrize(head: Entity, prize: DrawPrize, prepared: PrizePackage, reward: Entity,
                                  sfx: Sfx, pulse: () -> Unit, afterReveal: suspend (Entity,Entity) -> Unit) {
    val stage = Entity().apply { components.set(TransformComponent()); place(0f, 0f, -1.30f) }
    val packaging = prepared.body
    val pieces = prepared.pieces
    head.addChild(stage)
    stage.addChild(packaging)
    stage.addChild(Entity().apply {
        components.set(TransformComponent())
        components.set(PointLightComponent(Color4(1f, .96f, .92f, 1f), 88f, 2.8f))
        place(-.52f, .62f, .64f)
    })
    val box = prize is DrawPrize.Figure
    try {
        val scale = if (box) .58f else .245f
        val centreY = if (box) -.49f * scale else 0f
        packaging.place(0f, centreY, 0f, scale)
        packaging.enabled = true
        reward.enabled = false
        stage.addChild(reward)
        sfx.play(packaging, "charge_hum", .85f)
        tween(.52f) { t ->
            packaging.place(0f, centreY, 0f, scale, roll = sin(t * 48f) * 3f * t)
        }
        sfx.play(packaging, "lid_pop")
        if (box) sfx.play(packaging, "seal_tear", .85f)
        pulse()
        tween(1.05f) { t ->
            val lift = easeOut((t / .58f).coerceAtMost(1f))
            val tear = easeOut(((t - .16f) / .84f).coerceIn(0f, 1f))
            if (box) {
                pieces.getValue("lid").place(.09f * lift, .82f * lift, -.18f * lift, roll = -14f * lift)
                pieces.getValue("front").place(0f, -.22f * tear, .64f * tear, pitch = 74f * tear)
                pieces.getValue("back").place(0f, -.22f * tear, -.64f * tear, pitch = -74f * tear)
                pieces.getValue("right").place(.62f * tear, -.23f * tear, 0f, roll = -74f * tear)
                pieces.getValue("left").place(-.62f * tear, -.23f * tear, 0f, roll = 74f * tear)
                pieces.getValue("base").place(0f, -.46f * tear, 0f)
            } else {
                pieces.getValue("upper").place(-.74f * lift, 1.38f * lift, -.16f * lift, roll = 40f * lift)
                pieces.getValue("lower").place(.64f * lift, -1.22f * lift, .04f * lift, roll = -32f * lift)
            }
            if (t > .22f) {
                val reveal = easeOut(((t - .22f) / .78f).coerceIn(0f, 1f))
                reward.enabled = true
                val foot = if (box) -.26f else 0f
                reward.place(0f, foot - .04f * (1f-reveal), .10f, .53f * reveal.coerceAtLeast(.001f), yaw = 25f * (1f-reveal))
            }
            packaging.setOpacity(((1f-t)/.40f).coerceIn(0f, 1f))
        }
        packaging.enabled = false
        sfx.play(reward, (prize as? DrawPrize.Figure)?.let { infoOf(it.id).rarity.sfx } ?: "reveal_rare")
        pulse()
        delay(1800)
        afterReveal(stage,reward)
    } finally {
        if (reward.valid && reward.getParent() == stage) reward.removeFromParent()
        stage.releaseScene()
    }
}

/** The destination is reprojected every frame as the user turns or the HUD clears nearby furniture. */
internal suspend fun flyAccessoryToLibrary(reward: Entity, icon: () -> Vector3, sfx: Sfx) {
    val origin = reward.pos()
    val size = reward.components.get(TransformComponent::class.java)?.scaleVector?.x ?: .53f
    val tilt = if (icon().x >= origin.x) -15f else 15f
    sfx.play(reward,"whoosh",.65f)
    tween(.86f) { progress ->
        val t = easeInOut(progress)
        val target = icon()
        val arc = 4f*t*(1f-t)
        reward.place(origin.x+(target.x-origin.x)*t,
            origin.y+(target.y-origin.y)*t+.12f*arc,
            origin.z+(target.z-origin.z)*t+.085f*arc,
            size*(1f-t)+.007f*t, yaw=tilt*arc)
    }
    reward.enabled=false
}
