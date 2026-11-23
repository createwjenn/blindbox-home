package com.jenn.blindboxworld.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.SceneCaptureCameraPoseMode
import com.pico.spatial.core.ecs.SceneCaptureComponent
import com.pico.spatial.core.ecs.SceneCaptureFilterMode
import com.pico.spatial.core.ecs.SceneCaptureMode
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MaterialTextureParameter
import com.pico.spatial.core.ecs.resource.MaterialTextureSampler
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.RenderTextureDescriptor
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.SamplerMagFilter
import com.pico.spatial.core.ecs.resource.SamplerMinFilter
import com.pico.spatial.core.ecs.resource.SamplerWrapMode
import com.pico.spatial.core.ecs.resource.TexturePixelFormat
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import kotlin.math.exp
import kotlin.math.tan

/** Capture only this app's scenery at the entry pose. Modal attachments and prizes stay sharp. */
@Composable
internal fun GaussianSceneBackdrop(background: Entity?, head: Entity?, visible: Boolean, sky: Entity? = null, world: String? = null) {
    LaunchedEffect(background, head, sky, world, visible) {
        if (!visible || background == null || head == null || !background.valid || !head.valid) return@LaunchedEffect
        val layer = Entity().apply { components.set(TransformComponent()) }
        val resources = mutableListOf<Resource>()
        var hidScenery = false
        var fallback: Entity? = null
        try {
            head.addChild(layer)
            val target = TextureResource.createRenderTexture(RenderTextureDescriptor(224, 224, TexturePixelFormat.RGBA8_SRGB))
            resources.add(target)
            layer.components.set(SceneCaptureComponent().apply { captureMode = SceneCaptureMode.MANUAL })
            withFrameNanos { }
            val capture = layer.components.get(SceneCaptureComponent::class.java)
            val bound = capture != null && capture.setColorRenderTarget(target)
            if (!bound) {
                val photo = Entity.loadSuspend("asset://atmosphere/modal/${world ?: "room"}.glb")
                fallback = photo
                if (world != null && sky != null && sky.valid) {
                    sky.addChild(photo)
                    photo.place(0f, 0f, 0f, .84f)
                } else {
                    layer.addChild(photo)
                    photo.place(0f, 0f, -2.4f)
                }
                background.enabled = false
                hidScenery = true
                android.util.Log.i("BlindboxBackdrop", "Using Gaussian-filtered scenery for ${world ?: "room"}")
                kotlinx.coroutines.awaitCancellation()
            }
            capture!!.apply {
                cameraPoseMode = SceneCaptureCameraPoseMode.EYE_CENTER
                filterMode = SceneCaptureFilterMode.INCLUDE_RENDER_LIST_ONLY
                addEntityToRenderList(background, true)
                if (sky != null && sky.valid) addEntityToRenderList(sky, true)
                backgroundColor = Color4(.37f, .55f, .68f, 1f)
                useGlobalIBL = true
                setPerspectiveCameraParameters(118f, 1f, .04f, 150f)
                captureMode = SceneCaptureMode.ONCE
            }
            // Delay presentation until the one-shot native camera has completed a rendered frame.
            repeat(5) { withFrameNanos { } }
            val distance = 2.35f
            val width = 2f * distance * tan(Math.toRadians(59.0)).toFloat()
            val quad = MeshResource.createPlane(width, width, 0f)
            resources.add(quad)
            val filtered = MaterialTextureParameter(target, MaterialTextureSampler().apply {
                magFilter = SamplerMagFilter.LINEAR
                minFilter = SamplerMinFilter.LINEAR
                sWrapMode = SamplerWrapMode.CLAMP_TO_EDGE
                tWrapMode = SamplerWrapMode.CLAMP_TO_EDGE
            }, 0)
            val weights = (-2..2).map { exp(-.5 * it * it).toFloat() }
            // Back-to-front white layers preserve the captured RGB. A new tap contributes
            // weight / cumulativeWeight, leaving precisely the Gaussian-weighted prior taps.
            val taps = (-2..2).flatMap { y -> (-2..2).map { x -> x to y } }
                .sortedByDescending { (x,y) -> x*x + y*y }
            var accumulated = 0f
            for ((index, tap) in taps.withIndex()) {
                val (x,y) = tap
                val weight = weights[x+2] * weights[y+2]
                accumulated += weight
                val opaque = index == 0
                val material = UnlitMaterial.create(if (opaque) BlendingMode.OPAQUE else BlendingMode.TRANSPARENT).apply {
                    setBaseColorTextureParameter(filtered)
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setOpacity(weight / accumulated)
                    setApplyToneMapping(false)
                    setCullingMode(MaterialCullingMode.NONE)
                    setDepthWrite(opaque)
                }
                resources.add(material)
                layer.addChild(Entity().apply {
                    components.set(TransformComponent())
                    components.set(ModelComponent(quad, material))
                    val depth = distance - index * .003f
                    val scale = depth / distance
                    place(x * width * .0045f * scale, y * width * .0045f * scale, -depth, scale)
                })
            }
            background.enabled = false
            hidScenery = true
            android.util.Log.i("BlindboxBackdrop", "Using live Gaussian scenery for ${world ?: "room"}")
            kotlinx.coroutines.awaitCancellation()
        } finally {
            if (hidScenery && background.valid) background.enabled = true
            fallback?.releaseScene()
            layer.releaseScene()
            resources.asReversed().forEach { it.close() }
        }
    }
}
