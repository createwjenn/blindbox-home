package com.jenn.blindboxworld.content

import kotlin.math.*
import kotlin.random.Random

/** Machine-local metres, relative to the glass centre. The solver only runs during a draw. */
internal class GlobeMix(private val random: Random = Random.Default) {
    enum class Kind(val model: String, val scale: Float, val radius: Float, val mass: Float, val contactRadius: Float = radius) {
        RED("gumball-red", .095f, .095f, 1f),
        PINK("gumball-pink", .095f, .095f, 1f),
        // The corners need more clearance from the glass than the flat sides need when stacking.
        BOX("mini-slop", .18f, .117f, .78f, .093f)
    }

    class Piece(val kind: Kind, var x: Float, var y: Float, var z: Float, var yaw: Float, var pitch: Float) {
        var vx = 0f
        var vy = 0f
        var vz = 0f
        var spin = 0f
        var tumble = 0f
    }

    companion object {
        const val CENTER_Y = 1.2582f
        const val DT = 1f / 100f
        const val SETTLE_SECONDS = 3.2f
        private const val RADIUS = .550f
        private const val FLOOR = -.450f
        private const val LID = .40f
    }

    // Stratify pairs throughout the volume before settling, so neither type starts in a separate layer.
    val pieces = buildList {
        val balls = List(20) { if (it % 2 == 0) Kind.RED else Kind.PINK }.shuffled(random)
        for (pair in balls.indices) {
            val types = listOf(balls[pair], Kind.BOX).shuffled(random)
            for ((offset, kind) in types.withIndex()) {
                val i = pair * 2 + offset
                val layer = i / 8
                val angle = (i % 8 + .5f * layer) * PI.toFloat() / 4f + random.nextFloat() * .12f
                val ring = .24f + .035f * random.nextFloat()
                add(Piece(kind, ring*cos(angle), -.30f + .12f*layer, ring*sin(angle),
                    random.nextFloat()*360f, random.nextFloat()*100f - 50f))
            }
        }
    }
    private var jetPhase = 0f

    init { repeat(320) { advance(0f, 0f) } }

    fun start() {
        jetPhase = random.nextFloat() * 2f * PI.toFloat()
        for (p in pieces) {
            p.vx = -p.z * 2f
            p.vz = p.x * 2f
            p.vy = .85f + random.nextFloat() * .65f
            p.spin = random.nextFloat()*480f - 240f
            p.tumble = random.nextFloat()*540f - 270f
        }
    }

    fun step(time: Float, powered: Boolean) {
        advance(time, if (powered) 1f else 0f)
    }

    private fun advance(time: Float, power: Float) {
        // A wandering upward air jet entrains pieces at the bottom. The lower-speed air at
        // the wall lets them fall back under gravity, generating circulation rather than a rigid orbit.
        val phase = jetPhase + time * 5.4f
        val jetX = .21f * cos(phase)
        val jetZ = .21f * sin(phase * 1.19f)
        val initialGust = 28f * ((.52f - time) / .18f).coerceIn(0f, 1f)
        for ((i,p) in pieces.withIndex()) {
            val dx = p.x - jetX
            val dz = p.z - jetZ
            val core = exp(-11f * (dx*dx + dz*dz))
            val supply = power / p.kind.mass
            val eddy = phase + i * 2.4f
            p.vx += DT * (supply * (-30f*p.z + 7f*sin(eddy+time*4f)) - 1.6f*p.vx)
            p.vz += DT * (supply * (30f*p.x + 7f*cos(eddy-time*2f)) - 1.6f*p.vz)
            p.vy += DT * (supply * (initialGust + 30f*core + 4f*sin(eddy)) - 9.81f - 1.9f*p.vy)
            p.x += p.vx * DT
            p.y += p.vy * DT
            p.z += p.vz * DT
            p.yaw += p.spin * DT
            p.pitch += p.tumble * DT
        }
        repeat(3) {
            for (i in pieces.indices) {
                for (j in i+1 until pieces.size) collide(pieces[i],pieces[j], power)
                confine(pieces[i],power)
            }
        }
    }

    private fun collide(a: Piece, b: Piece, power: Float) {
        val dx = b.x-a.x
        val dy = b.y-a.y
        val dz = b.z-a.z
        val d2 = dx*dx + dy*dy + dz*dz
        val reach = a.kind.contactRadius + b.kind.contactRadius
        if (d2 >= reach*reach) return
        val d = sqrt(d2).coerceAtLeast(.00001f)
        val nx = dx/d
        val ny = dy/d
        val nz = dz/d
        val share = b.kind.mass / (a.kind.mass + b.kind.mass)
        val depth = reach-d
        a.x -= nx*depth*share; a.y -= ny*depth*share; a.z -= nz*depth*share
        b.x += nx*depth*(1-share); b.y += ny*depth*(1-share); b.z += nz*depth*(1-share)
        val approaching = (b.vx-a.vx)*nx + (b.vy-a.vy)*ny + (b.vz-a.vz)*nz
        if (approaching >= 0f) return
        val impulse = -approaching * (1.06f + .42f*power)
        a.vx -= impulse*nx*share; a.vy -= impulse*ny*share; a.vz -= impulse*nz*share
        b.vx += impulse*nx*(1-share); b.vy += impulse*ny*(1-share); b.vz += impulse*nz*(1-share)
        a.tumble += impulse * nz * 60f
        b.spin -= impulse * nx * 60f
    }

    private fun confine(p: Piece, power: Float) {
        val radius = p.kind.radius
        val d2 = p.x*p.x + p.y*p.y + p.z*p.z
        val limit = RADIUS - radius
        if (d2 > limit*limit) {
            val inv = 1f/sqrt(d2)
            val nx = p.x*inv
            val ny = p.y*inv
            val nz = p.z*inv
            p.x = nx*limit; p.y = ny*limit; p.z = nz*limit
            val outward = (p.vx*nx + p.vy*ny + p.vz*nz).coerceAtLeast(0f)
            val impulse = (1.26f + .42f*power)*outward
            p.vx -= nx*impulse; p.vy -= ny*impulse; p.vz -= nz*impulse
        }
        if (p.y > LID-radius) {
            p.y = LID-radius
            if (p.vy > 0f) p.vy *= -.55f
        }
        val pitch = p.pitch * (PI.toFloat()/180f)
        val support = if (p.kind == Kind.BOX) p.kind.scale*(.488f*abs(cos(pitch)) + .257f*abs(sin(pitch))) else radius
        if (p.y < FLOOR + support) {
            p.y = FLOOR + support
            if (p.vy < 0f) p.vy *= -(.09f + .45f*power)
            p.vx *= .94f; p.vz *= .94f
            p.spin *= .96f; p.tumble *= .96f
        }
        if (power == 0f) { p.spin *= .984f; p.tumble *= .984f }
    }
}
