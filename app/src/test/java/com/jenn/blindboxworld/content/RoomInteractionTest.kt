package com.jenn.blindboxworld.content

import org.junit.Assert.*
import org.junit.Test

class RoomInteractionTest {
    @Test fun couchAndTurntableMenuEdgesClearTheObjectSilhouette() {
        val eye = RoomSpot(0f,1.55f,0f)
        for ((center, half) in listOf(
            RoomSpot(0f,.42f,-3.4f) to RoomSpot(1.53f,.42f,.55f),
            RoomSpot(0f,.99f,-1.8f) to RoomSpot(.23f,.20f,.19f)
        )) for (rotation in listOf(0f, 35f, 90f)) {
            val menu = decorMenuView(eye,center,half,rotation)
            val radius = kotlin.math.hypot(menu.x,menu.z)
            assertTrue(radius in .54f..1.351f)
            assertTrue(menu.y >= center.y)
            val side = kotlin.math.sign(menu.x)
            val innerEdge = kotlin.math.atan2(kotlin.math.abs(menu.x),-menu.z) -
                kotlin.math.atan2(DECOR_MENU_WIDTH*.5f,radius)
            for (x in listOf(-half.x,half.x)) for (z in listOf(-half.z,half.z)) {
                val (cx,cz) = rotY(x,z,rotation)
                val depth = -center.z-cz
                val clearsSide = innerEdge > kotlin.math.atan2(side*cx,depth)
                val projectedTop = eye.y + (center.y+half.y-eye.y)*radius/depth
                assertTrue(clearsSide || menu.y-.30f > projectedTop)
            }
        }
    }

    @Test fun reachIsMeasuredFromTheRotatedSurfaceRatherThanTheCenter() {
        val bounds = DecorBounds(RoomSpot(0f,.5f,0f), RoomSpot(1.5f,.5f,.2f))
        val pose = DecorPose(RoomSpot(0f,0f,0f),90f)
        assertTrue(bounds.nearby(RoomSpot(0f,1f,3.5f),pose,90f))
        assertFalse(bounds.nearby(RoomSpot(3.5f,1f,0f),pose,90f))
    }

    @Test fun wallArtGetsAReachableFrontViewEvenWhenTheFirstViewIsBlocked() {
        val bounds = DecorBounds(RoomSpot(0f,0f,0f), RoomSpot(.4f,.5f,.04f))
        val pose = DecorPose(RoomSpot(-.62f,1.47f,-4.36f))
        val eye = RoomSpot(-1.5f,1.55f,-2.35f)
        val view = requireNotNull(bounds.viewingSpot(eye,pose,0f,true) { at, _ -> at.x < -.8f })
        val point = FloorPoint(view.x,view.z)
        assertTrue(view.z > pose.at.z && view.x < -.8f)
        assertTrue(RoomFloor.isWalkable(point))
        assertTrue(RoomFloor.route(FloorPoint(eye.x,eye.z),point).isNotEmpty())
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
