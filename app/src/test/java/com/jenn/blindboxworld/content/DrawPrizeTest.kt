package com.jenn.blindboxworld.content

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class DrawPrizeTest {
    @Test fun drawSplitsAccessoryAndFigureOddsFromTheFirstCoin() {
        val starters = libraryUnlocks(emptySet())
        assertEquals(ACCESSORIES.take(3).map { it.id }.toSet(), starters)
        val random = Random(281)
        val accessories = (1..20000).count { DrawPrize.pick(emptySet(), starters, random) is DrawPrize.Wearable }
        assertTrue("Expected roughly 55% accessory draws, got $accessories/20000", accessories in 10700..11300)
    }

    @Test fun bothContainersDropAndPrioritizeNewAccessoriesUntilCatalogueIsComplete() {
        var unlocked = libraryUnlocks(emptySet())
        val ownedFigure = FIGURE_IDS.first()
        val random = Random(281)
        var figures = 0
        repeat(250) {
            when (val prize = DrawPrize.pick(setOf(ownedFigure), unlocked, random)) {
                is DrawPrize.Figure -> {
                    assertNotEquals(ownedFigure, prize.id)
                    figures++
                }
                is DrawPrize.Wearable -> {
                    if (unlocked.size < ACCESSORIES.size) assertFalse(prize.item.id in unlocked)
                    unlocked = unlocked + prize.item.id
                }
            }
        }
        assertTrue(figures > 0)
        assertEquals(ACCESSORIES.map { it.id }.toSet(), unlocked)
        val duplicates = (1..20000).count { DrawPrize.pick(FIGURE_IDS.toSet(), unlocked, random) is DrawPrize.Wearable }
        assertTrue("Expected 55% accessory draws with a full catalogue, got $duplicates/20000", duplicates in 10700..11300)
    }

    @Test fun paidDrawsSurviveRestartsIncludingPreviousFigureOnlySaves() {
        FIGURE_IDS.forEach { assertEquals(DrawPrize.Figure(it), DrawPrize.restore(it)) }
        ACCESSORIES.forEach {
            val prize = DrawPrize.Wearable(it)
            assertEquals(prize, DrawPrize.restore(prize.saveKey))
        }
        assertNull(DrawPrize.restore(null))
        assertNull(DrawPrize.restore("accessory/deleted"))
        assertNull(DrawPrize.restore("unknown"))
    }

    @Test fun savedOutfitsOnlyContainUnlockedItemsAndOneItemPerSlot() {
        val all = ACCESSORIES.map { it.id }.toSet()
        val starters = libraryUnlocks(setOf("removed"))
        assertEquals(starters, wearableOutfit(all + "unknown", starters))
        val outfit = wearableOutfit(all, all)
        assertEquals(ACCESSORIES.map { it.slot }.toSet().size, outfit.size)
        assertEquals(emptySet<String>(), wearableOutfit(emptySet(), all))
    }
}
