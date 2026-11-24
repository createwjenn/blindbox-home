package com.jenn.blindboxworld.content

import org.junit.Assert.*
import org.junit.Test

class RoomInteractionTest {
    @Test fun targetAvoidsObjectAboveWhenThereIsClearSpaceBelow() {
        val item=TargetRect(-.1f,.1f,-.1f,.1f)
        val above=TargetRect(-.3f,.3f,.11f,.4f)
        val p=chooseTargetPosition(item,listOf(item,above))
        assertTrue(p.second < item.bottom)
        assertEquals(0f,above.overlap(p.first,p.second,.065f),.0001f)
    }
    @Test fun targetRemainsInReadableViewBoundsAtScreenEdge() {
        val p=chooseTargetPosition(TargetRect(.8f,1.5f,-.2f,.9f),emptyList())
        assertTrue(p.first in -.85f.. .85f)
        assertTrue(p.second in -.48f.. .48f)
    }
    @Test fun retreatUsesReachableFloorInsteadOfFurnitureOrWall() {
        for(from in listOf(FloorPoint(0f,-.73f),FloorPoint(-2.96f,-2.89f),FloorPoint(-1.88f,3.83f))) {
            val destination=roomRetreat(from,FloorPoint(0f,1f))
            assertNotNull(destination)
            assertTrue(RoomFloor.isWalkable(destination!!))
            assertTrue(RoomFloor.route(from,destination).isNotEmpty())
            assertTrue(from.distanceTo(destination)>=.29f)
        }
    }
}
