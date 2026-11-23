package com.jenn.blindboxworld.content

import android.content.Context
import android.util.Log
import androidx.compose.runtime.withFrameNanos
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Sky at full ascent in landscape.js, with the separate orbiting mist from unboxing.js. */
internal suspend fun animateEntryCirrus(root: Entity, context: Context) {
    val field = Entity().apply { components.set(TransformComponent()) }
    if (!root.valid || !root.addChild(field)) { field.releaseScene(); return }
    val textures = mutableSetOf<TextureResource>()
    val materials = mutableListOf<UnlitMaterial>()
    val meshes = mutableListOf<MeshResource>()
    suspend fun texture(file: String): TextureResource {
        // Native loads can finish after the user enters. Keep ownership until finally runs.
        val value = withContext(NonCancellable) {
            val loaded = withContext(Dispatchers.IO) {
                TextureResource.load("atmosphere/cirrus/$file", LoadType.FROM_ASSETS)
            }
            loaded.toGlobal()
            textures.add(loaded)
            loaded
        }
        currentCoroutineContext().ensureActive()
        return value
    }
    fun material(blend: BlendingMode, alpha: Float = 1f) = UnlitMaterial.create(blend).apply {
        toGlobal()
        setCullingMode(MaterialCullingMode.NONE)
        setApplyToneMapping(false)
        setBaseColor(Color4(1f, 1f, 1f, 1f))
        setDepthWrite(blend == BlendingMode.OPAQUE)
        if (blend == BlendingMode.TRANSPARENT) setOpacity(alpha)
        materials.add(this)
    }
    fun model(mesh: MeshResource, mat: UnlitMaterial) = Entity().apply {
        components.set(TransformComponent())
        components.set(ModelComponent(mesh, mat))
        field.addChild(this)
    }
    var shader: CirrusShader? = null
    var vertexField: CirrusField? = null
    var vertexMesh: MeshResource? = null
    suspend fun useMeshField() {
        shader?.close()
        shader = null
        val values = withContext(Dispatchers.Default) { CirrusField(context) }
        if (!field.valid) return
        val mesh = values.createMesh().also { it.toGlobal(); meshes.add(it) }
        model(mesh, material(BlendingMode.OPAQUE)).place(0f, 1.55f, 0f, 38f)
        vertexField = values
        vertexMesh = mesh
    }
    try {
        try {
            val dome = cirrusDome().also { it.toGlobal(); meshes.add(it) }
            shader = CirrusShader(context)
            shader!!.attach(field, dome)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Log.w("EntryCirrus", "Using live vertex-shaded cirrus", failure)
            useMeshField()
        }
        val puffMesh = MeshResource.createPlane(1f, 1f, 0f).also { it.toGlobal(); meshes.add(it) }
        val bed = mutableListOf<UnlitMaterial>()
        val mist = mutableListOf<UnlitMaterial>()
        for (i in 0..8) {
            val image = texture("puff-$i.png")
            bed += material(BlendingMode.TRANSPARENT, .65f).apply { setBaseColorTexture(image) }
            mist += material(BlendingMode.TRANSPARENT, .30f).apply { setBaseColorTexture(image) }
        }
        val random = Random(404)
        val puffs = mutableListOf<CloudPuff>()
        // The original frontal arrangement surrounds the door base. Continue the
        // two banks around the viewer so turning reveals the same atmosphere.
        for (sector in 0..2) {
            val count = if (sector == 0) 18 else 12
            for (i in 0 until count) {
                val orbit = if (i < 5) random.nextFloat()*.13f else .17f+random.nextFloat()*.45f
                val puff = CloudPuff(
                    body = model(puffMesh, bed[i%9]),
                    frontMist = if (orbit < .22f) model(puffMesh, mist[i%9]) else null,
                    bearing = sector * 2f * PI.toFloat() / 3,
                    angle = i * 2.39996f + random.nextFloat()*.6f,
                    orbit = orbit, size = .30f+random.nextFloat()*.26f, lift=(random.nextFloat()-.5f)*.17f,
                    phase = random.nextFloat()*2f*PI.toFloat(), speed=.025f+random.nextFloat()*.025f,
                    radius = 13.8f + random.nextFloat()*1.4f
                )
                puff.pose(0f)
                puffs += puff
            }
        }
        val start = withFrameNanos { it }
        var lastPuffFrame = start
        var lastSkyFrame = start
        Log.i("EntryCirrus", "Live ${if (shader == null) "mesh" else "GPU"} cirrus and ${puffs.size} independent clouds")
        while (currentCoroutineContext().isActive && root.valid && field.valid) {
            val now = withFrameNanos { it }
            val seconds = (now - start)*1e-9f
            if (now-lastSkyFrame >= if (shader != null) 50_000_000L else 100_000_000L) {
                val active = shader
                if (active != null) {
                    try {
                        active.draw(seconds)
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        Log.w("EntryCirrus", "Surface ended, continuing with the live cloud mesh", failure)
                        useMeshField()
                    }
                } else {
                    val values = vertexField
                    val mesh = vertexMesh
                    if (values != null && mesh != null) {
                        withContext(Dispatchers.Default) { values.shade(seconds) }
                        if (mesh.valid && field.valid) values.submit(mesh)
                    }
                }
                lastSkyFrame = now
            }
            if (now-lastPuffFrame >= 33_000_000L) {
                puffs.forEach { it.pose(seconds) }
                lastPuffFrame = now
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Log.e("EntryCirrus", "Could not render the unboxing sky", error)
    } finally {
        shader?.close()
        field.releaseScene()
        materials.asReversed().forEach { it.close() }
        meshes.asReversed().forEach { it.close() }
        textures.forEach { it.close() }
    }
}

private data class CloudPuff(
    val body: Entity, val frontMist: Entity?, val bearing: Float, val angle: Float, val orbit: Float,
    val size: Float, val lift: Float, val phase: Float, val speed: Float, val radius: Float,
) {
    fun pose(seconds: Float) {
        val a = angle + seconds*speed
        val azimuth = bearing + atan(1.73f*orbit*cos(a))
        val y = -.44f + lift - sin(a)*orbit*.76f
        val scale = size * 1.73f * radius * (1f + .07f*sin(seconds*.23f+phase))
        val degrees = (180f/PI.toFloat())
        val x = sin(azimuth)
        val z = -cos(azimuth)
        body.place(radius*x, 1.55f+radius*y, radius*z, scale, yaw=-degrees*azimuth, roll=degrees*.22f*sin(a))
        frontMist?.place((radius-.3f)*x, 1.55f+radius*(y-.15f), (radius-.3f)*z, scale*.82f,
            yaw=-degrees*azimuth, roll=degrees*.12f*sin(a+.9f))
    }
}

private fun cirrusDome(): MeshResource {
    val positions = mutableListOf<Vector3>()
    val uvs = mutableListOf<Vector2>()
    val triangles = mutableListOf<Int>()
    val longitude = 64
    val latitude = 32
    for (j in 0..latitude) {
        val v = j.toFloat()/latitude
        val elevation = (.5f-v)*PI.toFloat()
        for (i in 0..longitude) {
            val u = i.toFloat()/longitude
            val a = (u-.5f)*2f*PI.toFloat()
            positions += Vector3(cos(elevation)*sin(a), sin(elevation), -cos(elevation)*cos(a))
            uvs += Vector2(u,v)
            if (j<latitude && i<longitude) {
                val left=j*(longitude+1)+i
                val below=left+longitude+1
                triangles.addAll(listOf(left,below,left+1,left+1,below,below+1))
            }
        }
    }
    return MeshResource.createWithMeshModel(MeshModel(positions,triangles,uv0=uvs), null, "Unboxing sky")
}
