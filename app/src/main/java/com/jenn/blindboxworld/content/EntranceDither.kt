package com.jenn.blindboxworld.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val entranceBayer = intArrayOf(0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5)
internal fun entranceDitherRank(x: Int, y: Int): Int = entranceBayer[(y and 3)*4+(x and 3)]

@Composable
internal fun EntranceDither(threshold: Float) {
    Canvas(Modifier.size(1024.dp)) {
        if (threshold <= 0f) return@Canvas
        if (threshold >= 1f) { drawRect(Color.Black); return@Canvas }
        val cells=128
        val w=size.width/cells;val h=size.height/cells
        for(y in 0 until cells) for(x in 0 until cells) {
            val rank=(entranceDitherRank(x,y)+(y.toFloat()/cells))/16f
            if(threshold > rank || threshold>=1f) drawRect(Color.Black,
                Offset(x*w,y*h),Size(w+.5f,h+.5f))
        }
    }
}
