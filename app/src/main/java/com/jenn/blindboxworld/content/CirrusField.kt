package com.jenn.blindboxworld.content

import android.content.Context
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Vector3
import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.*

/** Live vertex shading for spatial renderers without an Android drawing surface. */
internal class CirrusField(context: Context) {
    private val columns = 384
    private val rows = 192
    private val count = (columns+1)*(rows+1)
    private val positions = FloatArray(count*3)
    private val u = FloatArray(count)
    private val v = FloatArray(count)
    private val height = FloatArray(count)
    private val colors = ByteArray(count*4)
    private val indices = IntArray(rows*columns*6)
    private val linear = ByteArray(256) { i ->
        val x=i/255f
        val c=if (x<=.04045f) x/12.92f else ((x+.055f)/1.055f).pow(2.4f)
        (255*c).roundToInt().toByte()
    }
    private val brightness = FloatArray(1024) { 1f-exp(-3.05f*it/1023f) }
    private val warp = NoiseSlice(-6f,6f)
    private val sheet = NoiseSlice(-12f,14f)
    private val threads = NoiseSlice(-40f,44f)
    init {
        DataInputStream(GZIPInputStream(context.assets.open("atmosphere/cirrus/noise.bin.gz"))).use {
            it.readFully(warp.bytes);it.readFully(sheet.bytes);it.readFully(threads.bytes)
        }
        val pitch = atan2(35.2f,42f)
        val cosPitch=cos(pitch); val sinPitch=sin(pitch)
        var k=0
        for (j in 0..rows) {
            val lat=(.5f-j.toFloat()/rows)*PI.toFloat()
            for (i in 0..columns) {
                val id=j*(columns+1)+i
                val lon=(i.toFloat()/columns-.5f)*2*PI.toFloat()
                val x=cos(lat)*sin(lon);val y=sin(lat);val z=-cos(lat)*cos(lon)
                positions[3*id]=x;positions[3*id+1]=y;positions[3*id+2]=z
                val h=y*cosPitch-z*sinPitch
                val den=max(h,.04f)+.45f
                u[id]=x/den;v[id]=(y*sinPitch+z*cosPitch)/den;height[id]=max(h,0f).pow(.7f)
                if (i<columns && j<rows) {
                    val below=id+columns+1
                    indices[k++]=id;indices[k++]=below;indices[k++]=id+1
                    indices[k++]=id+1;indices[k++]=below;indices[k++]=below+1
                }
            }
        }
        shade(0f)
    }

    fun createMesh(): MeshResource {
        val descriptor=RawMeshDescriptorBuilder().use {
            it.streamLayout(RawMeshStreamLayout(0,0,12))
                .streamLayout(RawMeshStreamLayout(1,0,4))
                .streamAttribute(RawMeshStreamAttribute(MeshVertexAttribute.POSITION, MeshVertexAttributeType.FLOAT3))
                .streamAttribute(RawMeshStreamAttribute(MeshVertexAttribute.COLOR, MeshVertexAttributeType.UBYTE4,true,0,1))
                .vertexCount(count).indexCount(indices.size).indexType(MeshIndexSizeType.UNSIGNED_INT)
                .isDynamic(true).boundingBox(BoundingBox(Vector3.ZERO,Vector3(1f,1f,1f)))
                .addSubMesh(0,indices.size,0,"Cirrus shell").build()
        }
        return descriptor.use { desc -> RawMesh.create(desc).use { raw ->
            check(raw.populateVertexBuffer(0) { b -> b.asFloatBuffer().put(positions); true })
            check(raw.populateVertexBuffer(1) { b -> b.put(colors); true })
            check(raw.populateIndicesBuffer { b -> b.asIntBuffer().put(indices); true })
            MeshResource.createWithRawMesh(raw,"Procedural cirrus field")
        } }
    }

    fun submit(mesh: MeshResource) {
        check(mesh.updateRawMeshVertexBuffer(1) { it.put(colors); true })
    }

    fun shade(seconds: Float) {
        val t=seconds*.077f
        val wind=.2f*sin(seconds*.022f)
        val wx=.055f*sin(t);val wy=.070f*sin(t*.83f)
        val drift=.10f*sin(t*.81f);val tx=.39f*sin(t*1.6f);val ty=.31f*cos(t*1.13f)
        for (i in 0 until count) {
            val x=u[i]+wind;val y=v[i]
            val w=warp.sample(2*x+wx,2*y+wy)
            val sx=x+w*.8f;val sy=y+sin(x*2.2f)*.3f+w*.4f
            val a=((sheet.sample(sx*3,sy*3.8f+drift)-.43f)/.27f).coerceIn(0f,1f)
            val veil=a*a*(3-2*a)*(.65f+threads.sample(sx*11+tx,sy*13+ty)*.45f)*.8f
            val light=brightness[(veil*1023).toInt().coerceIn(0,1023)]
            val h=height[i]
            val red=101f-36*h;val green=159f-51*h;val blue=214f-39*h
            val k=4*i
            colors[k]=linear[(red+(244-red)*light).toInt()]
            colors[k+1]=linear[(green+(247-green)*light).toInt()]
            colors[k+2]=linear[(blue+(251-blue)*light).toInt()]
            colors[k+3]=(-1).toByte()
        }
    }

    private class NoiseSlice(val low: Float, high: Float) {
        val bytes=ByteArray(512*512)
        val scale=511f/(high-low)
        fun sample(x: Float, y: Float): Float {
            val px=((x-low)*scale).coerceIn(0f,510.9999f);val py=((y-low)*scale).coerceIn(0f,510.9999f)
            val ix=px.toInt();val iy=py.toInt()
            val fx=px-ix;val fy=py-iy;val k=iy*512+ix
            val left=(bytes[k].toInt() and 255);val right=(bytes[k+1].toInt() and 255)
            val down=(bytes[k+512].toInt() and 255);val diagonal=(bytes[k+513].toInt() and 255)
            return ((left+(right-left)*fx)*(1-fy)+(down+(diagonal-down)*fx)*fy)/255f
        }
    }
}
