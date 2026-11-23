package com.jenn.blindboxworld.content

import android.util.Log
import com.pico.spatial.core.ecs.AmbientAudioComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.audio.Directivity
import com.pico.spatial.core.ecs.audio.DistanceAttenuationMode
import com.pico.spatial.core.ecs.resource.AudioResource

/**
 * Spatial audio: every one-shot plays as OBJECT audio on the entity it belongs to
 * (ObjectAudioComponent + Entity.playAudio), the room tone as AMBIENT audio.
 */
class Sfx {
    private val resources = HashMap<String, AudioResource>()
    private val live = ArrayList<AudioPlayerController>()
    private var ambient: AudioPlayerController? = null

    private fun res(name: String): AudioResource? = resources[name] ?: try {
        AudioResource.load(name, "asset://sfx/$name.wav", LoadType.FROM_ASSETS).also { resources[name] = it }
    } catch (e: Exception) {
        Log.w(TAG, "audio load failed: $name", e); null
    }

    /** Preload so the first rattle has no hitch. Call on the main thread after the scene exists. */
    fun preload() = listOf("rattle_1", "rattle_2", "rattle_3", "seal_tear", "charge_hum", "lid_pop",
        "reveal_common", "reveal_rare", "reveal_secret", "spark_catch", "whoosh",
        "coin_insert", "coin_spawn", "crank_click", "crank_turn", "capsule_drop", "vitrine_slide", "no_coin").forEach { res(it) }

    fun makeObjectSource(entity: Entity) {
        if (entity.components[ObjectAudioComponent::class.java] == null &&
            entity.components[AmbientAudioComponent::class.java] == null) {
            entity.components.set(ObjectAudioComponent(volume = 1f, directivity = Directivity(0f, 1f),
                distanceAttenuationMode = DistanceAttenuationMode.INVERSE_SQUARED, reverbVolume = 0.4f))
        }
    }

    fun play(entity: Entity, name: String, volume: Float = 1f): AudioPlayerController? {
        val r = res(name) ?: return null
        prune()
        return try {
            makeObjectSource(entity)
            entity.playAudio(r).also { it.setVolume(volume); live += it }
        } catch (e: Exception) {
            Log.w(TAG, "play failed: $name", e); null
        }
    }

    fun startAmbient(entity: Entity, volume: Float = 0.22f) {
        if (ambient != null) return
        val r = res("room_tone") ?: return
        try {
            entity.components.set(AmbientAudioComponent(volume, com.pico.spatial.core.ecs.audio.AmbientOrientationMode.ORIENTATION_ONLY))
            ambient = entity.playAudio(r).also { it.setLoop(true); it.setVolume(volume) }
        } catch (e: Exception) {
            Log.w(TAG, "ambient failed", e)
        }
    }

    fun stopAmbient() { ambient?.let { runCatching { it.stop(); it.close() } }; ambient = null }

    private fun prune() {
        val done = live.filter { runCatching { it.isCompleted() }.getOrDefault(true) }
        done.forEach { runCatching { it.close() } }
        live.removeAll(done.toSet())
        while (live.size > 10) live.removeAt(0).let { runCatching { it.stop(); it.close() } }
    }

    fun release() {
        stopAmbient()
        live.forEach { runCatching { it.stop(); it.close() } }; live.clear()
        resources.values.forEach { runCatching { it.close() } }; resources.clear()
    }

    companion object { private const val TAG = "BlindboxSfx" }
}
