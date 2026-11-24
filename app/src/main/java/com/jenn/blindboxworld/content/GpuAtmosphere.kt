package com.jenn.blindboxworld.content

import android.app.Activity
import android.app.Application
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.VideoComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PrimitiveSphereTopology
import com.pico.spatial.core.ecs.resource.SphereOption
import com.pico.spatial.core.ecs.resource.SurfaceRenderTexture
import com.pico.spatial.core.ecs.resource.VideoMaterial
import com.pico.spatial.core.ecs.video.VideoDimensionMode
import com.pico.spatial.core.math.Color4
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Native GPU producer for the same GLSL used by the WebGL browser preview.
 * World-space surfaces preserve stereo head tracking. No WebView, CPU pixel readback,
 * bitmap upload, or virtual display is needed. Static assets cover initialization/failure. */
internal class GpuAtmosphere(private val context: Context, private val root: Entity) : AutoCloseable {
    private val layers = mutableListOf<Layer>()
    private var closed = false
    private val app = context.applicationContext as Application
    private val observer = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) { layers.forEach { it.active = true } }
        override fun onActivityStopped(activity: Activity) { layers.forEach { it.active = false } }
        override fun onActivityCreated(activity: Activity, state: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
    fun start(sky: LoftSky) {
        app.registerActivityLifecycleCallbacks(observer)
        for (water in listOf(false, true)) {
            val layer = Layer(water)
            layers += layer
            try { layer.start(sky) }
            catch (e: Exception) { Log.w(TAG, "Static fallback", e); layer.close() }
        }
    }
    fun update(sky: LoftSky) { if (!closed) layers.forEach { it.update(sky) } }
    override fun close() {
        if (closed) return
        closed = true
        app.unregisterActivityLifecycleCallbacks(observer)
        layers.forEach { it.close() }; layers.clear()
    }
    private inner class Layer(private val water: Boolean) : AutoCloseable {
        private val width = if (water) 1024 else 1536
        private val height = if (water) 1024 else 768
        private val label = if (water) "bay" else "sky"
        private var texture: SurfaceRenderTexture? = null
        private var surface: Surface? = null
        private var entity: Entity? = null
        private var thread: HandlerThread? = null
        private var render: Handler? = null
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var released = false
        @Volatile var active = true
        @Volatile private var phase = 1f
        @Volatile private var cloud = 0f
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var program = 0
        private var frames = 0
        private var startMs = 0L
        private val uniforms = mutableMapOf<String, Int>()
        private val vertices = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,3f,-1f,-1f,3f)); position(0) }

        fun start(sky: LoftSky) {
            update(sky)
            val output = SurfaceRenderTexture(width, height).apply { toGlobal() }
            texture = output
            surface = requireNotNull(output.acquireSurface()) { "Atmosphere surface unavailable" }
            val material = VideoMaterial(BlendingMode.OPAQUE, VideoDimensionMode.MONO,
                MaterialCullingMode.NONE, Color4.BLACK).apply {
                bindSurfaceRenderTexture(output); setDepthTest(true); setDepthWrite(true)
            }
            val mesh = if (water) MeshResource.createPlane(210f,210f) else
                MeshResource.createSphere(SphereOption(140f).setTopology(PrimitiveSphereTopology.LATLON))
            entity = Entity().apply {
                components.set(TransformComponent())
                components.set(VideoComponent(mesh, material))
                setName("Live GPU $label")
                if (water) place(0f,-11.98f,0f,pitch=-90f)
                enabled = false
                root.addChild(this)
            }
            val worker = HandlerThread("LoftGpu-$label").apply { start() }
            thread = worker
            render = Handler(worker.looper).also { handler -> handler.post {
                try { initialize(); handler.post(frame) }
                catch (e: Exception) { fail(e); releaseEgl() }
            } }
        }
        fun update(sky: LoftSky) {
            phase = when (sky.phase) { SkyPhase.SUNRISE -> 0f; SkyPhase.DAY -> 1f; SkyPhase.SUNSET -> 2f; SkyPhase.NIGHT -> 3f }
            cloud = if (sky.weather.overcast) 1f else 0f
        }
        private fun initialize() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY)
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
            val configs = arrayOfNulls<EGLConfig>(1); val count = IntArray(1)
            val attributes = intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_NONE)
            check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0)
            val config = requireNotNull(configs[0])
            eglContext = EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            check(eglContext != EGL14.EGL_NO_CONTEXT)
            eglSurface = EGL14.eglCreateWindowSurface(display,config,requireNotNull(surface),intArrayOf(EGL14.EGL_NONE),0)
            check(eglSurface != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext))
            EGL14.eglSwapInterval(display,0)
            fun shader(type: Int, path: String): Int {
                val code = context.assets.open(path).bufferedReader().use { it.readText() }
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader,code); GLES20.glCompileShader(shader)
                val status=IntArray(1); GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
                check(status[0]!=0) { GLES20.glGetShaderInfoLog(shader) }
                return shader
            }
            val vertex=shader(GLES20.GL_VERTEX_SHADER,"atmosphere/webgl/atmosphere.vert")
            val fragment=shader(GLES20.GL_FRAGMENT_SHADER,"atmosphere/webgl/atmosphere.frag")
            program=GLES20.glCreateProgram()
            GLES20.glAttachShader(program,vertex); GLES20.glAttachShader(program,fragment); GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
            val status=IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
            check(status[0]!=0) { GLES20.glGetProgramInfoLog(program) }
            GLES20.glUseProgram(program)
            val p=GLES20.glGetAttribLocation(program,"p")
            GLES20.glEnableVertexAttribArray(p); GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices)
            for (name in listOf("time","phase","overcast","mode")) uniforms[name]=GLES20.glGetUniformLocation(program,name)
            GLES20.glViewport(0,0,width,height)
            startMs=SystemClock.elapsedRealtime()
        }
        private val frame = object : Runnable {
            override fun run() {
                if (released) return
                if (!active) { render?.postDelayed(this,200); return }
                try {
                    GLES20.glUniform1f(uniforms.getValue("time"),(SystemClock.elapsedRealtime()-startMs)/1000f)
                    GLES20.glUniform1f(uniforms.getValue("phase"),phase)
                    GLES20.glUniform1f(uniforms.getValue("overcast"),cloud)
                    GLES20.glUniform1f(uniforms.getValue("mode"),if (water) 1f else 0f)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,3)
                    check(EGL14.eglSwapBuffers(display,eglSurface)) { "eglSwapBuffers failed" }
                    frames++
                    if (frames==3) main.post {
                        if (!released && !closed && entity?.valid==true) {
                            entity?.enabled=true
                            Log.i(TAG,"PRESENTED $label phase=$phase")
                        }
                    }
                    if (frames%240==0) Log.i(TAG,"FRAMES $label count=$frames phase=$phase")
                    render?.postDelayed(this,if (water) 40 else 100)
                } catch (e: Exception) { fail(e) }
            }
        }
        private fun fail(e: Exception) {
            Log.w(TAG,"$label renderer fallback",e)
            main.post { if (!released && entity?.valid==true) entity?.enabled=false }
        }
        private fun releaseEgl() {
            if (display==EGL14.EGL_NO_DISPLAY) return
            if (program!=0) { GLES20.glDeleteProgram(program); program=0 }
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if (eglSurface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,eglSurface)
            if (eglContext!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,eglContext)
            EGL14.eglReleaseThread(); EGL14.eglTerminate(display)
            display=EGL14.EGL_NO_DISPLAY; eglSurface=EGL14.EGL_NO_SURFACE; eglContext=EGL14.EGL_NO_CONTEXT
        }
        override fun close() {
            if (released) return
            released=true
            val handler=render
            if (handler!=null) {
                handler.removeCallbacksAndMessages(null)
                handler.post {
                    try { releaseEgl() }
                    finally {
                        main.post { releaseResources() }
                        thread?.quitSafely()
                    }
                }
            } else releaseResources()
        }
        private fun releaseResources() {
            surface?.release(); surface=null
            entity?.let { if (it.valid) { it.removeFromParent(); it.destroy() } }; entity=null
            texture?.close(); texture=null
            Log.i(TAG,"CLOSED $label")
        }
    }
    private companion object { const val TAG="LoftGpu" }
}
