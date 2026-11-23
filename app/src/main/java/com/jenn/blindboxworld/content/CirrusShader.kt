package com.jenn.blindboxworld.content

import android.content.Context
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.view.Surface
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.VideoComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.SurfaceRenderTexture
import com.pico.spatial.core.ecs.video.VideoDimensionMode
import com.pico.spatial.core.ecs.resource.VideoMaterial
import java.io.Closeable

/** Feed live GPU shader output directly to the spatial scene's Android Surface. */
internal class CirrusShader(context: Context) : Closeable {
    private val width = 1280
    private val height = 640
    private val shader = RuntimeShader(context.assets.open("atmosphere/cirrus/sky.agsl").bufferedReader().use { it.readText() })
    private val paint = Paint().apply { shader = this@CirrusShader.shader }
    private val node = RenderNode("Procedural cirrus")
    private var texture: SurfaceRenderTexture? = null
    private var material: VideoMaterial? = null
    private var surface: Surface? = null
    private var renderer: HardwareRenderer? = null
    private var sky: Entity? = null

    fun attach(field: Entity, dome: MeshResource) {
        val target = SurfaceRenderTexture(width, height, 3).also { texture = it; it.toGlobal() }
        check(target.valid) { "Live sky render target is unavailable" }
        val screen = requireNotNull(target.acquireSurface()).also { surface = it }
        check(screen.isValid) { "The sky render surface is invalid" }
        val mat = VideoMaterial(BlendingMode.OPAQUE, VideoDimensionMode.MONO, MaterialCullingMode.NONE)
        material = mat
        mat.toGlobal()
        mat.bindSurfaceRenderTexture(target)
        node.setPosition(0, 0, width, height)
        val gpu = HardwareRenderer().also { renderer = it }
        gpu.setSurface(screen)
        gpu.setContentRoot(node)
        gpu.isOpaque = true
        shader.setFloatUniform("resolution",width.toFloat(),height.toFloat())
        draw(0f)
        sky = Entity().apply {
            components.set(TransformComponent())
            components.set(VideoComponent(dome,mat))
            place(0f,1.55f,0f,38f)
            field.addChild(this)
        }
    }

    fun draw(t: Float) {
        val gpu = renderer ?: return
        shader.setFloatUniform("seconds",t)
        val canvas = node.beginRecording(width,height)
        try { canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint) } finally { node.endRecording() }
        val result = gpu.createRenderRequest().syncAndDraw()
        check(result and HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND == 0)
    }

    override fun close() {
        sky?.releaseScene()
        renderer?.destroy()
        node.discardDisplayList()
        surface?.release()
        material?.close()
        texture?.close()
    }
}
