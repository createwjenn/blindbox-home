package com.jenn.blindboxworld.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.cos
import kotlin.math.sin

/** Attachment origin and finished width in the unscaled figure's glTF coordinate space. */
internal data class WearFit(
    val x: Float, val y: Float, val z: Float, val width: Float, val yaw: Float = 0f,
)

private data class FigureFit(val hat: WearFit, val glasses: WearFit, val mustache: WearFit, val collar: WearFit) {
    operator fun get(slot: AccessorySlot): WearFit = when (slot) {
        AccessorySlot.HEAD -> hat
        AccessorySlot.EYES -> glasses
        AccessorySlot.MOUTH -> mustache
        AccessorySlot.NECK -> collar
    }
}

// Landmarks in the textured meshes. The seated capybara includes a toilet and duck,
// the shark's eyes sit on either side of its snout, and Chill Guy's eyes face at an angle.
private val statuettes = mapOf(
    "niu-lai" to FigureFit(WearFit(0f,.888f,-.028f,.61f), WearFit(0f,.680f,.215f,.50f),
        WearFit(0f,.555f,.333f,.22f), WearFit(0f,.455f,.082f,.48f)),
    "strawberlina" to FigureFit(WearFit(0f,.842f,-.025f,.61f), WearFit(0f,.552f,.190f,.435f),
        WearFit(0f,.469f,.191f,.16f), WearFit(0f,.399f,.053f,.34f)),
    "bananito" to FigureFit(WearFit(-.012f,.917f,.052f,.34f), WearFit(0f,.666f,.090f,.246f),
        WearFit(0f,.610f,.081f,.145f), WearFit(0f,.524f,-.021f,.28f)),
    "tung-tung-tung" to FigureFit(WearFit(-.073f,.962f,-.024f,.56f), WearFit(-.073f,.629f,.219f,.463f),
        WearFit(-.073f,.495f,.261f,.20f), WearFit(-.073f,.403f,.017f,.49f)),
    "tralalero-tralala" to FigureFit(WearFit(0f,.732f,.118f,.49f), WearFit(0f,.500f,.473f,.55f),
        WearFit(0f,.394f,.446f,.27f), WearFit(0f,.290f,.184f,.48f)),
    "ballerina-capuccina" to FigureFit(WearFit(.016f,.967f,0f,.79f), WearFit(-.035f,.777f,.303f,.565f,-11f),
        WearFit(-.043f,.696f,.296f,.215f,-11f), WearFit(-.030f,.552f,.045f,.25f)),
    "chill-guy" to FigureFit(WearFit(.087f,.858f,-.066f,.48f), WearFit(.102f,.733f,.159f,.315f,19f),
        WearFit(.096f,.564f,.146f,.18f,19f), WearFit(.065f,.472f,-.015f,.385f)),
    "secret-capybara-toilet" to FigureFit(WearFit(-.06f,.832f,.089f,.47f), WearFit(-.062f,.683f,.359f,.50f),
        WearFit(-.062f,.577f,.394f,.23f), WearFit(-.060f,.443f,.158f,.46f)),
)

// The playable rigs are separate, T-posed assets. Their noses, shoulders and crowns
// differ from the shelf poses, especially the capybara, which stands outside its toilet.
private val actors = mapOf(
    "niu-lai" to FigureFit(WearFit(0f,.885f,-.043f,.61f), WearFit(0f,.680f,.196f,.50f),
        WearFit(0f,.556f,.325f,.22f), WearFit(0f,.457f,.095f,.48f)),
    "strawberlina" to FigureFit(WearFit(0f,.833f,.008f,.61f), WearFit(0f,.552f,.243f,.435f),
        WearFit(0f,.468f,.244f,.16f), WearFit(0f,.396f,.059f,.34f)),
    "bananito" to FigureFit(WearFit(-.012f,.917f,.055f,.34f), WearFit(0f,.666f,.109f,.246f),
        WearFit(0f,.610f,.092f,.145f), WearFit(0f,.520f,-.022f,.28f)),
    "tung-tung-tung" to FigureFit(WearFit(0f,.880f,-.025f,.49f), WearFit(0f,.574f,.194f,.416f),
        WearFit(0f,.457f,.240f,.18f), WearFit(0f,.398f,.014f,.445f)),
    "tralalero-tralala" to FigureFit(WearFit(0f,.706f,-.031f,.46f), WearFit(0f,.475f,.363f,.51f),
        WearFit(0f,.370f,.337f,.245f), WearFit(0f,.296f,.052f,.445f)),
    "ballerina-capuccina" to FigureFit(WearFit(.043f,.967f,0f,.79f), WearFit(.044f,.777f,.296f,.565f),
        WearFit(.044f,.696f,.305f,.215f), WearFit(.044f,.553f,.048f,.25f)),
    "chill-guy" to FigureFit(WearFit(.066f,.855f,-.066f,.49f), WearFit(.075f,.735f,.169f,.32f,20f),
        WearFit(.077f,.560f,.149f,.18f,20f), WearFit(.058f,.477f,-.004f,.385f)),
    "secret-capybara-toilet" to FigureFit(WearFit(0f,.828f,.037f,.47f), WearFit(0f,.678f,.282f,.50f),
        WearFit(0f,.578f,.32f,.23f), WearFit(0f,.444f,.01f,.44f)),
)

internal fun wearableHolder() = Entity().apply { components.set(TransformComponent()) }

/** Moving this holder moves the original model and every fitted piece together. */
internal suspend fun loadDressedFigure(id: String, equipped: Collection<String>): Entity {
    val figure = wearableHolder()
    try {
        figure.addChild(Entity.loadSuspend("asset://figures/$id.glb"))
        figure.updateWornAccessories(id, equipped)
        currentCoroutineContext().ensureActive()
        return figure
    } catch (e: Throwable) {
        figure.releaseScene()
        throw e
    }
}

/** Stage missing pieces before swapping them, so a cancelled preview keeps its last complete outfit. */
internal suspend fun Entity.updateWornAccessories(id: String, equipped: Collection<String>) {
    val wanted = ACCESSORIES.filter { it.id in equipped }
    val existing = getChildren().filter { it.getName().startsWith("wear:") }
        .associateBy { it.getName().removePrefix("wear:") }
    val pending = mutableListOf<Entity>()
    try {
        for (item in wanted) {
            if (item.id !in existing) pending += loadWornItem(item, statuettes.getValue(id)[item.slot])
        }
        currentCoroutineContext().ensureActive()
        if (!valid) return
        for ((key, entity) in existing) if (key !in equipped) entity.releaseScene()
        pending.forEach { addChild(it) }
        pending.clear()
    } finally {
        pending.forEach { it.releaseScene() }
    }
}

internal suspend fun loadActorWearable(figureId: String, item: Accessory): Entity =
    loadWornItem(item, actors.getValue(figureId)[item.slot])

private suspend fun loadWornItem(item: Accessory, fit: WearFit): Entity {
    val holder = wearableHolder()
    try {
        holder.addChild(Entity.loadSuspend("asset://${item.modelPath}"))
        holder.setName("wear:${item.id}")
        if (item.id == "moon_monocle") {
            // The chain is part of the authored width. Set its one lens over the right eye.
            val yaw = Math.toRadians(fit.yaw.toDouble())
            val offset = .10f * fit.width
            holder.place(fit.x + offset * cos(yaw).toFloat(), fit.y, fit.z - offset * sin(yaw).toFloat(),
                fit.width * .54f, yaw = fit.yaw)
        } else {
            holder.place(fit.x, fit.y, fit.z, fit.width, yaw = fit.yaw)
        }
        return holder
    } catch (e: Throwable) {
        holder.releaseScene()
        throw e
    }
}
