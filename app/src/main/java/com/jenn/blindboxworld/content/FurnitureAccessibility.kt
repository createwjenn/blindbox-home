package com.jenn.blindboxworld.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.AccessibilityComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.ui.foundation.content.setAccessibilityActions

/** The contract index requires a literal selector for every reachable room object. */
internal fun registerFurnitureAction(content: SpatialViewContent, target: Entity, piece: Furnishing, inspect: () -> Boolean) {
    target.components.set(AccessibilityComponent("Room item", "Inspect room item").apply {
        label = piece.title
        isAccessibilityElement = true
    })
    when (piece.id) {
        "stand" -> content.setAccessibilityActions(target, "room.display-stand") { onClick("Inspect item", inspect) }
        "record-player" -> content.setAccessibilityActions(target, "room.record-player") { onClick("Inspect item", inspect) }
        "console" -> content.setAccessibilityActions(target, "room.listening-shelf") { onClick("Inspect item", inspect) }
        "vinyl-rack" -> content.setAccessibilityActions(target, "room.decor.vinyl-rack") { onClick("Inspect item", inspect) }
        "mushroom" -> content.setAccessibilityActions(target, "room.decor.mushroom") { onClick("Inspect item", inspect) }
        "speaker-left" -> content.setAccessibilityActions(target, "room.decor.speaker-left") { onClick("Inspect item", inspect) }
        "speaker-right" -> content.setAccessibilityActions(target, "room.decor.speaker-right") { onClick("Inspect item", inspect) }
        "arc-lamp" -> content.setAccessibilityActions(target, "room.decor.arc-lamp") { onClick("Inspect item", inspect) }
        "sofa" -> content.setAccessibilityActions(target, "room.decor.sofa") { onClick("Inspect item", inspect) }
        "wool-rug" -> content.setAccessibilityActions(target, "room.decor.wool-rug") { onClick("Inspect item", inspect) }
        "coffee-table" -> content.setAccessibilityActions(target, "room.decor.coffee-table") { onClick("Inspect item", inspect) }
        "chair" -> content.setAccessibilityActions(target, "room.decor.chair") { onClick("Inspect item", inspect) }
        "ottoman" -> content.setAccessibilityActions(target, "room.decor.ottoman") { onClick("Inspect item", inspect) }
        "sideboard" -> content.setAccessibilityActions(target, "room.decor.sideboard") { onClick("Inspect item", inspect) }
        "table-globe" -> content.setAccessibilityActions(target, "room.decor.table-globe") { onClick("Inspect item", inspect) }
        "sofa-lamp" -> content.setAccessibilityActions(target, "room.decor.sofa-lamp") { onClick("Inspect item", inspect) }
        "orb" -> content.setAccessibilityActions(target, "room.decor.orb") { onClick("Inspect item", inspect) }
        "laurel-left" -> content.setAccessibilityActions(target, "room.decor.laurel-left") { onClick("Inspect item", inspect) }
        "laurel-right" -> content.setAccessibilityActions(target, "room.decor.laurel-right") { onClick("Inspect item", inspect) }
        "dining-table" -> content.setAccessibilityActions(target, "room.decor.dining-table") { onClick("Inspect item", inspect) }
        "chair-a" -> content.setAccessibilityActions(target, "room.decor.chair-a") { onClick("Inspect item", inspect) }
        "chair-b" -> content.setAccessibilityActions(target, "room.decor.chair-b") { onClick("Inspect item", inspect) }
        "chair-c" -> content.setAccessibilityActions(target, "room.decor.chair-c") { onClick("Inspect item", inspect) }
        "chair-d" -> content.setAccessibilityActions(target, "room.decor.chair-d") { onClick("Inspect item", inspect) }
        "ochre-canvas" -> content.setAccessibilityActions(target, "room.decor.ochre-canvas") { onClick("Inspect item", inspect) }
        "unboxing-print" -> content.setAccessibilityActions(target, "room.decor.unboxing-print") { onClick("Inspect item", inspect) }
        "slop-print" -> content.setAccessibilityActions(target, "room.decor.slop-print") { onClick("Inspect item", inspect) }
        "capybara-print" -> content.setAccessibilityActions(target, "room.decor.capybara-print") { onClick("Inspect item", inspect) }
        "calendar" -> content.setAccessibilityActions(target, "room.decor.calendar") { onClick("Inspect item", inspect) }
        "convex-mirror" -> content.setAccessibilityActions(target, "room.decor.convex-mirror") { onClick("Inspect item", inspect) }
        "lotus_lounge" -> content.setAccessibilityActions(target, "room.decor.lotus_lounge") { onClick("Inspect item", inspect) }
        "sage_reader" -> content.setAccessibilityActions(target, "room.decor.sage_reader") { onClick("Inspect item", inspect) }
        "tangerine_club" -> content.setAccessibilityActions(target, "room.decor.tangerine_club") { onClick("Inspect item", inspect) }
        "matcha_table" -> content.setAccessibilityActions(target, "room.decor.matcha_table") { onClick("Inspect item", inspect) }
        "onyx_nightstand" -> content.setAccessibilityActions(target, "room.decor.onyx_nightstand") { onClick("Inspect item", inspect) }
        "coral_sidecar" -> content.setAccessibilityActions(target, "room.decor.coral_sidecar") { onClick("Inspect item", inspect) }
        "opal_saucer" -> content.setAccessibilityActions(target, "room.decor.opal_saucer") { onClick("Inspect item", inspect) }
        "mint_beacon" -> content.setAccessibilityActions(target, "room.decor.mint_beacon") { onClick("Inspect item", inspect) }
        "blush_lantern" -> content.setAccessibilityActions(target, "room.decor.blush_lantern") { onClick("Inspect item", inspect) }
        "marigold_pouf" -> content.setAccessibilityActions(target, "room.decor.marigold_pouf") { onClick("Inspect item", inspect) }
        "plum_pouf" -> content.setAccessibilityActions(target, "room.decor.plum_pouf") { onClick("Inspect item", inspect) }
        "cloud_pouf" -> content.setAccessibilityActions(target, "room.decor.cloud_pouf") { onClick("Inspect item", inspect) }
        "fern_bowl" -> content.setAccessibilityActions(target, "room.decor.fern_bowl") { onClick("Inspect item", inspect) }
        "olive_tree" -> content.setAccessibilityActions(target, "room.decor.olive_tree") { onClick("Inspect item", inspect) }
        "rose_agave" -> content.setAccessibilityActions(target, "room.decor.rose_agave") { onClick("Inspect item", inspect) }
        "kiln_vase" -> content.setAccessibilityActions(target, "room.decor.kiln_vase") { onClick("Inspect item", inspect) }
        "celadon_vase" -> content.setAccessibilityActions(target, "room.decor.celadon_vase") { onClick("Inspect item", inspect) }
        "cobalt_pitcher" -> content.setAccessibilityActions(target, "room.decor.cobalt_pitcher") { onClick("Inspect item", inspect) }
        "linen_rack" -> content.setAccessibilityActions(target, "room.decor.linen_rack") { onClick("Inspect item", inspect) }
        "moss_cabinet" -> content.setAccessibilityActions(target, "room.decor.moss_cabinet") { onClick("Inspect item", inspect) }
        "berry_credenza" -> content.setAccessibilityActions(target, "room.decor.berry_credenza") { onClick("Inspect item", inspect) }
        "oculus" -> content.setAccessibilityActions(target, "room.decor.oculus") { onClick("Inspect item", inspect) }
        "pebble_stack" -> content.setAccessibilityActions(target, "room.decor.pebble_stack") { onClick("Inspect item", inspect) }
        "arch_pair" -> content.setAccessibilityActions(target, "room.decor.arch_pair") { onClick("Inspect item", inspect) }
        "petite_maple_frame" -> content.setAccessibilityActions(target, "room.decor.petite_maple_frame") { onClick("Inspect item", inspect) }
        "gallery_oak_frame" -> content.setAccessibilityActions(target, "room.decor.gallery_oak_frame") { onClick("Inspect item", inspect) }
        "salon_gilt_frame" -> content.setAccessibilityActions(target, "room.decor.salon_gilt_frame") { onClick("Inspect item", inspect) }
        "art:ballerina-capuccina" -> content.setAccessibilityActions(target, "room.decor.art-ballerina-capuccina") { onClick("Inspect item", inspect) }
        "art:bananito" -> content.setAccessibilityActions(target, "room.decor.art-bananito") { onClick("Inspect item", inspect) }
        "art:chill-guy" -> content.setAccessibilityActions(target, "room.decor.art-chill-guy") { onClick("Inspect item", inspect) }
        "art:niu-lai" -> content.setAccessibilityActions(target, "room.decor.art-niu-lai") { onClick("Inspect item", inspect) }
        "art:secret-capybara-toilet" -> content.setAccessibilityActions(target, "room.decor.art-secret-capybara-toilet") { onClick("Inspect item", inspect) }
        "art:strawberlina" -> content.setAccessibilityActions(target, "room.decor.art-strawberlina") { onClick("Inspect item", inspect) }
        "art:tralalero-tralala" -> content.setAccessibilityActions(target, "room.decor.art-tralalero-tralala") { onClick("Inspect item", inspect) }
        "art:tung-tung-tung" -> content.setAccessibilityActions(target, "room.decor.art-tung-tung-tung") { onClick("Inspect item", inspect) }
    }
}
