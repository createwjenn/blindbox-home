package com.jenn.blindboxworld.content

import android.content.Context
import android.os.SystemClock
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelFormat
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** The eight light studies share the large geometry and lightmap buffer from the authored loft. */
internal suspend fun loadLoft(context: Context, look: String): Entity {
    val bytes = withContext(Dispatchers.IO) {
        val base = context.assets.open("collection-room.glb").use { it.readBytes() }
        val variant = context.assets.open("atmosphere/$look.look").use { it.readBytes() }
        val binStart = 28 + ByteBuffer.wrap(base).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        val jsonSize = ByteBuffer.wrap(variant).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        val extra = 4 + jsonSize
        val binSize = base.size - binStart + variant.size - extra
        val length = 28 + jsonSize + binSize
        ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546C67); putInt(2); putInt(length)
            putInt(jsonSize); putInt(0x4E4F534A); put(variant,4,jsonSize)
            putInt(binSize); putInt(0x004E4942); put(base,binStart,base.size-binStart)
            put(variant,extra,variant.size-extra)
        }.array()
    }
    return bytes.inputStream().use { Entity.loadSuspend(it,ModelFormat.GLTF) }
}

private fun group() = Entity().apply { components.set(TransformComponent()) }

internal suspend fun animateWindows(root: Entity, currentSky: () -> LoftSky) {
    // All coordinates are local to the loft so the weather follows the room when approaching the machine.
    val effects = group()
    root.addChild(effects)
    fun add(e: Entity): Entity { effects.addChild(e); return e }
    val sun = add(glowSphere(.35f, Color4(1f,.87f,.58f,1f)))
    val windowLight = add(group().apply {
        components.set(PointLightComponent(Color4(1f,.80f,.57f,1f),0f,8.5f))
    })
    val flock = add(group()).apply { enabled=false }
    val birds = List(4) { Entity.loadSuspend("asset://atmosphere/swallow.glb").also(flock::addChild) }
    val jet = add(Entity.loadSuspend("asset://atmosphere/airliner.glb")).apply { enabled=false }
    val shower = add(group())
    val streakMesh = MeshResource.createBox(Vector3(.026f,.026f,1.05f),0f)
    val streakMaterial = glowMaterial(Color4(.85f,.92f,1f,1f))
    val meteors = List(4) { group().apply {
        components.set(ModelComponent(streakMesh,streakMaterial))
        shower.addChild(this)
    } }
    val precipitation = mutableMapOf<String, List<Entity>>()
    val start = SystemClock.elapsedRealtime()
    var previous: LoftSky? = null
    var activeWeather: List<Entity> = emptyList()
    while (true) {
        val t = (SystemClock.elapsedRealtime()-start)/1000f
        val sky = currentSky()
        val night = sky.phase == SkyPhase.NIGHT
        if (previous != sky) {
            val p = sky.sunProgress
            val elevation = sin(PI*p).toFloat()
            sun.enabled = !night && !sky.weather.overcast
            sun.place(-25f, 3f+8.4f*elevation, 19f-38f*p)
            val warm = sky.phase == SkyPhase.SUNRISE || sky.phase == SkyPhase.SUNSET
            windowLight.place(-3.55f,1.75f+1.25f*elevation,3.6f-7.2f*p)
            windowLight.setLight(when {
                night -> 12f
                sky.weather.overcast -> 90f
                warm -> 370f
                else -> 300f+300f*elevation
            },when {
                night -> Color4(.35f,.43f,.72f,1f)
                sky.weather.overcast -> Color4(.76f,.82f,.96f,1f)
                warm -> Color4(1f,.61f,.32f,1f)
                else -> Color4(1f,.90f,.72f,1f)
            })
            val kind = when(sky.weather) {
                Weather.RAIN, Weather.THUNDER -> "rain"
                Weather.SNOW -> "snow"
                else -> null
            }
            activeWeather.forEach { it.enabled = false }
            activeWeather = if (kind == null) emptyList() else precipitation.getOrPut(kind) {
                List(2) { add(Entity.loadSuspend("asset://atmosphere/$kind.glb")) }
            }
            activeWeather.forEach { it.enabled = true }
            shower.enabled = night && !sky.weather.overcast
            previous = sky
        }
        if (activeWeather.isNotEmpty()) {
            val snow = sky.weather == Weather.SNOW
            val drop = (t * if (snow) .58f else 4.4f) % 6f
            val drift = if (snow) .25f*sin(t*.44f) else 0f
            activeWeather.forEachIndexed { i,e -> e.place(0f,6f*i-drop,drift) }
        }
        val flight = (t+4f)%43f
        val showBirds = !night && !sky.weather.overcast && flight<10f
        if (flock.enabled != showBirds) flock.enabled=showBirds
        if (showBirds) {
            flock.place(-7.5f,3.3f+.26f*sin(flight*.7f),-10f+flight*2f)
            birds.forEachIndexed { i,bird ->
                val bank = sin(t*4.3f+i)
                bird.place(-i*.38f,i*.12f+bank*.07f,-i*.45f,.48f,roll=bank*14f)
            }
        }
        val passage = (t+44f)%79f
        val showJet = !night && !sky.weather.overcast && passage<18f
        if (jet.enabled != showJet) jet.enabled=showJet
        if (showJet) jet.place(-22.5f,7.7f+passage*.043f,19f-passage*2.15f,.73f,yaw=180f,roll=-5f)
        if (shower.enabled) {
            val burst = (t+1f)%51f
            meteors.forEachIndexed { i,streak ->
                val elapsed = burst-i*.64f
                val visible = elapsed in 0f..1.05f
                if (streak.enabled != visible) streak.enabled=visible
                if (visible) streak.place(-17f-i,8.2f+i*.52f-2.9f*elapsed,-8f+i*2.4f+7.7f*elapsed,pitch=21f)
            }
        }
        delay(if (showBirds || showJet || activeWeather.isNotEmpty() || (shower.enabled && (t+1f)%51f<4f)) 45L else 180L)
    }
}
