package com.jenn.blindboxworld.content

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

internal sealed interface DrawPrize {
    // Existing saves store a plain figure ID for a paid, unopened draw.
    val saveKey: String

    data class Figure(val id: String) : DrawPrize {
        override val saveKey get() = id
    }

    data class Wearable(val item: Accessory) : DrawPrize {
        override val saveKey get() = "accessory/${item.id}"

        // Both regular shells in the globe can dispense prizes. Keep the colour stable across a restart.
        val shell get() = if (item.id.hashCode() % 2 == 0) "gumball-pink" else "gumball-red"
    }

    companion object {
        fun restore(key: String?): DrawPrize? {
            if (key == null) return null
            if (key in FIGURE_IDS) return Figure(key)
            if (!key.startsWith("accessory/")) return null
            val id = key.removePrefix("accessory/")
            return ACCESSORIES.find { it.id == id }?.let(::Wearable)
        }

        fun pick(owned: Set<String>, unlocked: Set<String>, random: Random = Random.Default): DrawPrize {
            val locked = ACCESSORIES.filterNot { it.id in unlocked }
            if (random.nextInt(100) < 55) {
                return Wearable(locked.ifEmpty { ACCESSORIES }.random(random))
            }
            val fresh = FIGURE_IDS.filterNot { it in owned }
            return Figure(fresh.ifEmpty { FIGURE_IDS }.random(random))
        }
    }
}

internal fun libraryUnlocks(saved: Set<String>): Set<String> =
    (ACCESSORIES.take(3) + ACCESSORIES.filter { it.id in saved }).mapTo(mutableSetOf()) { it.id }

internal fun wearableOutfit(requested: Set<String>, unlocked: Set<String>): Set<String> =
    ACCESSORIES.filter { it.id in requested && it.id in unlocked }
        .distinctBy { it.slot }.mapTo(mutableSetOf()) { it.id }


/** Older saves timestamped figure reveals, but had no day field for machine purchases. */
internal fun legacyFigureDrawDay(unboxed: Collection<Long>, zone: ZoneId = ZoneId.systemDefault()): String? =
    unboxed.maxOrNull()?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }

internal const val EXTRA_DRAW_COST = 20

/** Slot gestures can only pay for the daily draw. Additional draws require the exchange button. */
internal fun drawCost(lastDay: String?, today: String, exchange: Boolean, balance: Int): Int? {
    if ((lastDay == today) != exchange) return null
    val price = if (exchange) EXTRA_DRAW_COST else 1
    return price.takeIf { balance >= it }
}
