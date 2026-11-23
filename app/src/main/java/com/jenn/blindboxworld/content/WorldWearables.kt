package com.jenn.blindboxworld.content

import android.content.Context
import com.pico.spatial.core.ecs.Entity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream

/** Bind-space head and neck transforms sampled from the same clips driven in CourtyardController. */
internal class WorldWearables private constructor(
    private val head: Entity, private val neck: Entity, private val clips: Map<String, Track>,
) {
    private class Track(val duration: Float, val frames: Int, val poses: FloatArray)
    private var elapsed = 0f
    private var previousRate = 1f

    fun pose(motion: String, changed: Boolean, frameSeconds: Float, rate: Float) {
        val idle = motion == "Idle" || motion == "Float"
        val track = when (motion) {
            "Idle", "Float", "Swim" -> clips["Walk"]
            "Jump" -> clips["Jump"] ?: clips["Run"]
            else -> clips[motion]
        } ?: return
        elapsed = if (changed || idle) 0f else (elapsed + frameSeconds * previousRate) % track.duration
        previousRate = rate
        val frame = (elapsed / track.duration) * (track.frames - 1)
        val left = frame.toInt()
        val right = (left + 1).coerceAtMost(track.frames - 1)
        val weight = frame - left
        fun apply(target: Entity, bone: Int) {
            fun component(n: Int): Float {
                val index = bone * 6 + n
                val a = track.poses[left * 12 + index]
                val b = track.poses[right * 12 + index]
                val delta = if (n < 3) b - a else ((b - a + 540f) % 360f) - 180f
                return a + delta * weight
            }
            target.place(component(0), component(1), component(2),
                yaw = component(3), pitch = component(4), roll = component(5))
        }
        apply(head, 0)
        apply(neck, 1)
    }

    companion object {
        suspend fun attach(context: Context, actor: Entity, id: String, equipped: Set<String>): WorldWearables? {
            if (equipped.isEmpty()) return null
            val clips = withContext(Dispatchers.IO) {
                DataInputStream(context.assets.open("accessory-motion/$id.bin").buffered()).use { data ->
                    buildMap {
                        repeat(data.readUnsignedByte()) {
                            val name = data.readUTF()
                            val duration = data.readFloat()
                            val frames = data.readUnsignedShort()
                            put(name, Track(duration, frames, FloatArray(frames * 12) { data.readFloat() }))
                        }
                    }
                }
            }
            val head = wearableHolder()
            val neck = wearableHolder()
            try {
                for (item in ACCESSORIES.filter { it.id in equipped }) {
                    val anchor = if (item.slot == AccessorySlot.NECK) neck else head
                    anchor.addChild(loadActorWearable(id, item))
                }
                actor.addChild(head)
                actor.addChild(neck)
                return WorldWearables(head, neck, clips)
            } catch (e: Throwable) {
                head.releaseScene()
                neck.releaseScene()
                throw e
            }
        }
    }
}
