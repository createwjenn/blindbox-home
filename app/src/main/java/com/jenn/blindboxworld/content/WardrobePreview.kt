package com.jenn.blindboxworld.content

import androidx.compose.runtime.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Color4
import kotlinx.coroutines.awaitCancellation

/** The preview shares the panel's head anchor. Its feet project onto the painted turntable. */
@Composable
internal fun WardrobePreview(foreground: RoomHudForeground?, figureId: String?, outfit: Set<String>) {
    var figure by remember { mutableStateOf<Entity?>(null) }
    LaunchedEffect(foreground, figureId) {
        if (foreground == null || figureId == null) return@LaunchedEffect
        val model = loadDressedFigure(figureId, emptySet())
        val studio = Entity().apply { components.set(TransformComponent()); addChild(model) }
        var previewLight: Entity? = null
        try {
            val depth = LIBRARY_DISTANCE - .25f
            val unitsPerDp = LIBRARY_WIDTH_METERS / LIBRARY_WIDTH_DP * depth / LIBRARY_DISTANCE
            studio.place(-202f * unitsPerDp, -73f * unitsPerDp, -depth)
            model.place(0f, 0f, 0f, .25f, yaw = 3f)
            val lamp = Entity().apply {
                components.set(TransformComponent())
                components.set(PointLightComponent(Color4(1f, .94f, .87f, 1f), 16f, .85f))
                place(-.24f, .34f, .38f)
            }
            studio.addChild(lamp)
            foreground.addPreviewLight(lamp, 16f)
            previewLight = lamp
            foreground.modalLayer.addChild(studio)
            foreground.sortPreview(model)
            figure = model
            awaitCancellation()
        } finally {
            figure = null
            previewLight?.let(foreground::removePreviewLight)
            studio.releaseScene()
        }
    }
    LaunchedEffect(figure, figureId, outfit) {
        if (figureId != null) figure?.updateWornAccessories(figureId, outfit)
    }
}
