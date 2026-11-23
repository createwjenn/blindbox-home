package com.jenn.blindboxworld.content

import com.pico.spatial.core.math.Color4

/** Figure catalogue, rarity, and every per-figure tuning number in one place. */

enum class Rarity(val color: Color4, val sfx: String, val label: String) {
    COMMON(Color4(1f, 0.86f, 0.62f, 1f), "reveal_common", "Common"),
    RARE(Color4(1f, 0.35f, 0.72f, 1f), "reveal_rare", "Rare"),
    SECRET(Color4(1f, 0.78f, 0.18f, 1f), "reveal_secret", "Secret"),
}

/** Rig and neck heights come from the website mesh bounds and neck/head inverse bind matrices. */
data class FigureInfo(
    val id: String, val rarity: Rarity, val sections: Int, val actorRigHeight: Float,
    val collectible: String, val swimNeckHeight: Float? = null,
) {
    val hasWater get() = swimNeckHeight != null
}

val FIGURES = listOf(
    FigureInfo("niu-lai", Rarity.COMMON, 2, .9745f, "pink flowers", .518f),
    FigureInfo("strawberlina", Rarity.COMMON, 4, .9797f, "strawberries"),
    FigureInfo("bananito", Rarity.COMMON, 5, .9767f, "bananas", .625f),
    FigureInfo("tung-tung-tung", Rarity.RARE, 5, .8963f, "wood chunks", .481f),
    FigureInfo("tralalero-tralala", Rarity.RARE, 5, .8729f, "fish", .469f),
    FigureInfo("ballerina-capuccina", Rarity.COMMON, 3, .9792f, "coffee beans"),
    FigureInfo("chill-guy", Rarity.COMMON, 2, .9791f, "leaves"),
    FigureInfo("secret-capybara-toilet", Rarity.SECRET, 5, .9769f, "little poops", .613f),
)
val FIGURE_IDS = FIGURES.map { it.id }
fun infoOf(id: String) = FIGURES.first { it.id == id }
fun nameOf(id: String) = id.split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

/** All figure .glb files are ~0.98 m tall with the base at y = 0; scale = height / 0.98. */
const val FIGURE_GLB_HEIGHT = 0.98f
fun scaleForHeight(meters: Float) = meters / FIGURE_GLB_HEIGHT

/** Museum plinth in front of the player; its top (and the vitrine base) is at y = 0.677. */
const val ISLAND_Y = 0.677f
const val EXHIBIT_FOOT_Y = ISLAND_Y + .066f
const val ISLAND_Z = -1.7f
const val VITRINE_LIFT = 0.75f

/** Gachapon in the far-left corner, front (+Z local) turned toward the player start. Anchors are machine-local. */
const val GACHA_X = -3.62f
const val GACHA_Z = -3.70f
val GACHA_YAW = Math.toDegrees(kotlin.math.atan2(-GACHA_X.toDouble(), -GACHA_Z.toDouble())).toFloat()
data class Local(val x: Float, val y: Float, val z: Float)
val CRANK_AXLE = Local(0f, .574f, .332f)
val COIN_SLOT = Local(0f, .729f, .332f)
val CHUTE_MOUTH = Local(0f, .236f, .200f)
val TRAY_REST = Local(0f, .122f, .268f)
val GACHA_SIGN = Local(0f, 2.06f, .08f)
const val TRAY_BOX_SCALE = .18f
const val START_COINS = 1
const val BOX_SCALE = 0.38f
const val BOX_GLB_HEIGHT = 0.979f

/**
 * Console collection slots (runtime coords). Top y = 0.905 only in the free gaps
 * (x < -1.25, -0.3..0.08, x > 1.25); the top faces of the galvanized shelves are at y = 0.416 and 0.106.
 */
data class Slot(val x: Float, val y: Float, val z: Float = -4.12f)
val SHELF_SLOTS = listOf(
    Slot(-1.38f, 0.905f), Slot(1.38f, 0.905f),
    Slot(-1.05f, 0.416f), Slot(-0.55f, 0.416f), Slot(0.55f, 0.416f), Slot(1.05f, 0.416f),
    Slot(-0.80f, 0.106f), Slot(0.80f, 0.106f),
)
const val SHELF_FIGURE_HEIGHT = 0.12f
