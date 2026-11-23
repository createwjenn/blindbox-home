package com.jenn.blindboxworld.content

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeMixTest {
    private fun surfaceHeight(piece: GlobeMix.Piece): Float {
        if (piece.kind != GlobeMix.Kind.BOX) return piece.y + piece.kind.radius
        val angle = Math.toRadians(piece.pitch.toDouble()).toFloat()
        return piece.y + piece.kind.scale * (.488f * abs(cos(angle)) + .257f * abs(sin(angle)))
    }

    @Test fun stockSettlesAroundTheEquatorAndBlowsImmediatelyUntilRelease() {
        for (seed in 0..12) {
            val mix = GlobeMix(Random(seed))
            val pieces = mix.pieces
            assertEquals(20, pieces.count { it.kind == GlobeMix.Kind.BOX })
            assertEquals(10, pieces.count { it.kind == GlobeMix.Kind.RED })
            assertEquals(10, pieces.count { it.kind == GlobeMix.Kind.PINK })

            // The upper surface of the pile is near the equator (half of a spherical bowl).
            val heights = pieces.map(::surfaceHeight).sorted()
            assertTrue("Idle fill for seed $seed: $heights", heights[36] in -.07f.. .07f)
            val initial = pieces.map { it.y }
            mix.start()
            repeat(600) { tick ->
                mix.step(tick * GlobeMix.DT, powered = true)
                for ((i, p) in pieces.withIndex()) {
                    val extent = sqrt(p.x * p.x + p.y * p.y + p.z * p.z) + p.kind.radius
                    assertTrue("Piece $i crossed the glass at step $tick", extent <= .551f)
                }
                if (tick == 9) {
                    assertTrue("First air pulse must visibly lift the pile",
                        pieces.indices.all { pieces[it].y - initial[it] > .035f })
                }
            }
            assertTrue("Mixing must continue while the crank awaits a turn",
                pieces.any { it.y > .12f && abs(it.vy) > .10f })

            repeat((GlobeMix.SETTLE_SECONDS / GlobeMix.DT).toInt()) { tick ->
                mix.step(tick * GlobeMix.DT, powered = false)
            }
            assertTrue("Stock should settle back into the lower hemisphere",
                pieces.map(::surfaceHeight).sorted()[36] < .12f)
        }
    }
}
