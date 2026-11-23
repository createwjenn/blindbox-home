package com.jenn.blindboxworld.content

import com.pico.spatial.core.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomHudForegroundTest {
    private val eye = Vector3(0f, 1.6f, 0f)
    private val center = Vector3(0f, 1.6f, -1.65f)

    @Test fun clearAndBehindEyeLeaveHudAtAuthoredDistance() {
        val offToSide = HudOccluder(1f,1.6f,-.8f,.1f,.1f,.1f)
        val behindEye = HudOccluder(0f,1.6f,.8f,.1f,.1f,.1f)
        assertEquals(1f,offToSide.entry(eye,center,.25f),.0001f)
        assertEquals(1f,behindEye.entry(eye,center,.25f),.0001f)
    }

    @Test fun shelfAtEdgeOfShopInterceptsPanelBeforeItInterceptsTheCenterRay() {
        val shelf = HudOccluder(.30f,1.6f,-.95f,.08f,.45f,.15f)
        assertEquals(1f,shelf.entry(eye,center,0f),.0001f)
        assertTrue(shelf.entry(eye,center,.63f) in .30f.. .65f)
    }

    @Test fun tableNearWalletAndPadsKeepsBothNativeSurfacesInFront() {
        val table = HudOccluder(0f,.59f,-1.04f,1.1f,.59f,.43f)
        val wallet = Vector3(.66f,1.16f,-1.65f)
        val sticks = Vector3(-.70f,1.14f,-1.65f)
        val pinch = minOf(table.entry(eye,wallet,.23f),table.entry(eye,sticks,.24f)) * .92f
        assertTrue(pinch in .15f.. .68f)
        for (button in listOf(wallet,sticks)) {
            val projected = Vector3(button.x*pinch,eye.y+(button.y-eye.y)*pinch,button.z*pinch)
            assertEquals(1f,table.entry(eye,projected,.24f*pinch),.0001f)
            assertEquals(button.x/button.z,projected.x/projected.z,.0001f)
        }
    }
}
