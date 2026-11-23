package com.jenn.blindboxworld.content

import com.jenn.blindboxworld.R

internal fun worldPaintingArt(figure: String): Int = when (figure) {
    "ballerina-capuccina" -> R.drawable.painting_ballerina_capuccina
    "bananito" -> R.drawable.painting_bananito
    "chill-guy" -> R.drawable.painting_chill_guy
    "niu-lai" -> R.drawable.painting_niu_lai
    "secret-capybara-toilet" -> R.drawable.painting_secret_capybara_toilet
    "strawberlina" -> R.drawable.painting_strawberlina
    "tralalero-tralala" -> R.drawable.painting_tralalero_tralala
    "tung-tung-tung" -> R.drawable.painting_tung_tung_tung
    else -> error("Unknown collectible $figure")
}

internal fun worldPortraitArt(figure: String): Int = when (figure) {
    "ballerina-capuccina" -> R.drawable.world_portrait_ballerina_capuccina
    "bananito" -> R.drawable.world_portrait_bananito
    "chill-guy" -> R.drawable.world_portrait_chill_guy
    "niu-lai" -> R.drawable.world_portrait_niu_lai
    "secret-capybara-toilet" -> R.drawable.world_portrait_secret_capybara_toilet
    "strawberlina" -> R.drawable.world_portrait_strawberlina
    "tralalero-tralala" -> R.drawable.world_portrait_tralalero_tralala
    "tung-tung-tung" -> R.drawable.world_portrait_tung_tung_tung
    else -> error("Unknown collectible $figure")
}
