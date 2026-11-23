package com.jenn.blindboxworld.content

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.jenn.blindboxworld.R

internal class RoomRecordPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    var isPlaying by mutableStateOf(false)
        private set
    var hasStarted by mutableStateOf(false)
        private set

    fun toggle() {
        if (isPlaying) { pause(); return }
        val turntable = player ?: (MediaPlayer.create(context, R.raw.room_record) ?: return).also {
            it.isLooping = true
            it.setVolume(.48f, .48f)
            // Roll off the high end for the warm sound of the room's small record player.
            equalizer = runCatching { Equalizer(0, it.audioSessionId) }.getOrNull()
            equalizer?.let { eq ->
                runCatching {
                    val levels = eq.bandLevelRange
                    for (i in 0 until eq.numberOfBands.toInt()) {
                        val band = i.toShort()
                        val hz = eq.getCenterFreq(band) / 1000
                        val gain = when { hz >= 9000 -> -750; hz >= 3000 -> -340; hz < 100 -> -180; else -> 0 }
                        eq.setBandLevel(band, gain.coerceIn(levels[0].toInt(), levels[1].toInt()).toShort())
                    }
                    eq.enabled = true
                }
            }
            it.setOnErrorListener { _, _, _ -> close(); true }
            player = it
        }
        turntable.start()
        isPlaying = true
        hasStarted = true
    }

    fun pause() {
        if (isPlaying) player?.pause()
        isPlaying = false
    }

    fun close() {
        equalizer?.release()
        equalizer = null
        player?.release()
        player = null
        isPlaying = false
        hasStarted = false
    }
}

@Composable
internal fun rememberRoomRecordPlayer(playingInRoom: Boolean): RoomRecordPlayer {
    val context = LocalContext.current.applicationContext
    val record = remember(context) { RoomRecordPlayer(context) }
    val view = LocalView.current
    DisposableEffect(view, record) {
        val life = view.findViewTreeLifecycleOwner()?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) record.pause() }
        life?.addObserver(observer)
        onDispose { life?.removeObserver(observer); record.close() }
    }
    LaunchedEffect(playingInRoom) { if (!playingInRoom) record.close() }
    return record
}
