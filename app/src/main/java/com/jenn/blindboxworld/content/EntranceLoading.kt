package com.jenn.blindboxworld.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text

internal class EntryLoad {
    var percent by mutableIntStateOf(-1)
        private set
    var figure by mutableStateOf<String?>(null)
        private set
    val active get() = percent >= 0
    fun begin(world: String? = null) { figure = world; percent = 0 }
    fun reached(value: Int) { if (active && value > percent) percent = value }
    fun clear() { percent = -1; figure = null }
}

@Composable
internal fun EntranceLoading(entry: EntryLoad) {
    Box(Modifier.size(1024.dp), contentAlignment = Alignment.Center) {
        if (!entry.active) return@Box
        val figure = entry.figure
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            figure?.let {
                Image(painterResource(worldPortraitArt(it)), nameOf(it),
                    Modifier.offset(y = (-50).dp).size(72.dp))
            }
            Text("${entry.percent}%", color = Color.White, fontSize = 16.sp)
            Text(figure?.let { "entering ${nameOf(it)}'s world" } ?: "building your blindbox home",
                modifier = Modifier.offset(y = 23.dp), color = Color.White, fontSize = 11.sp)
        }
    }
}
