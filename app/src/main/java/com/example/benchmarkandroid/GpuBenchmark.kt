package com.example.benchmarkandroid

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.View
import android.view.ViewGroup
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GpuBenchmark(private val activity: Activity) {

    data class GpuResults(
        val averageFps: Double,
        val minFps: Double,
        val maxFps: Double,
        val p95Fps: Double,
        val p99Fps: Double,
        val frameCount: Int
    )

    private var renderer: BenchmarkRenderer? = null

    fun runBenchmark(durationSeconds: Int): GpuResults {
        val latch = CountDownLatch(1)
        var glView: GLSurfaceView? = null
        renderer = BenchmarkRenderer()

        activity.runOnUiThread {
            glView = GLSurfaceView(activity).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

                //  Fill entire screen
                val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                visibility = View.VISIBLE
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                rootView.addView(this)
            }
            latch.countDown()
        }

        latch.await(2, TimeUnit.SECONDS)

        val surfaceWaitStart = System.currentTimeMillis()
        while (!renderer!!.isSurfaceReady() && System.currentTimeMillis() - surfaceWaitStart < 3000) {
            Thread.sleep(100)
        }

        if (!renderer!!.isSurfaceReady()) {
            activity.runOnUiThread {
                glView?.let {
                    val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                    rootView.removeView(it)
                }
            }
            return GpuResults(0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }

        renderer!!.startBenchmark(durationSeconds)

        val maxWaitTime = (durationSeconds + 5) * 1000L
        val startWait = System.currentTimeMillis()
        while (!renderer!!.isComplete()) {
            Thread.sleep(100)
            if (System.currentTimeMillis() - startWait > maxWaitTime) {
                break
            }
        }

        val results = renderer!!.getResults()

        activity.runOnUiThread {
            glView?.let {
                val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                rootView.removeView(it)
            }
        }

        return results
    }

    fun getPerSecondFps(): List<Double> {
        return renderer?.getPerSecondFps() ?: emptyList()
    }

    private class BenchmarkRenderer : GLSurfaceView.Renderer {
        private var program = 0
        private var cubeBuffer: FloatBuffer? = null
        private var colorBuffer: FloatBuffer? = null

        private val frameTimes = mutableListOf<Long>()
        private val perSecondFps = mutableListOf<Double>()
        private var lastFrameTime = 0L
        private var benchmarkStartTime = 0L
        private var benchmarkDuration = 0
        private var lastSecondTime = 0L
        private var framesInCurrentSecond = 0
        @Volatile private var isRunning = false
        @Volatile private var completed = false
        @Volatile private var surfaceReady = false

        private var rotation = 0f

        private val mvpMatrix = FloatArray(16)
        private val projectionMatrix = FloatArray(16)
        private val viewMatrix = FloatArray(16)
        private val modelMatrix = FloatArray(16)

        private val cubeVertices = floatArrayOf(
            -1f, -1f,  1f,  1f, -1f,  1f,  1f,  1f,  1f, -1f,  1f,  1f,
            -1f, -1f, -1f, -1f,  1f, -1f,  1f,  1f, -1f,  1f, -1f, -1f,
            -1f,  1f, -1f, -1f,  1f,  1f,  1f,  1f,  1f,  1f,  1f, -1f,
            -1f, -1f, -1f,  1f, -1f, -1f,  1f, -1f,  1f, -1f, -1f,  1f,
            1f, -1f, -1f,  1f,  1f, -1f,  1f,  1f,  1f,  1f, -1f,  1f,
            -1f, -1f, -1f, -1f, -1f,  1f, -1f,  1f,  1f, -1f,  1f, -1f
        )

        private val cubeColors = FloatArray(24 * 4) { i ->
            when ((i / 4) % 6) {
                0 -> if (i % 4 == 0) 1f else if (i % 4 == 3) 1f else 0f // Red
                1 -> if (i % 4 == 1) 1f else if (i % 4 == 3) 1f else 0f // Green
                2 -> if (i % 4 == 2) 1f else if (i % 4 == 3) 1f else 0f // Blue
                3 -> if (i % 4 < 2) 1f else if (i % 4 == 3) 1f else 0f // Yellow
                4 -> if (i % 4 == 1 || i % 4 == 2) 1f else if (i % 4 == 3) 1f else 0f // Cyan
                else -> if (i % 4 == 0 || i % 4 == 2) 1f else if (i % 4 == 3) 1f else 0f // Magenta
            }
        }

        private val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            attribute vec4 vColor;
            varying vec4 fColor;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                fColor = vColor;
            }
        """.trimIndent()

        private val fragmentShaderCode = """
            precision mediump float;
            varying vec4 fColor;
            void main() {
                gl_FragColor = fColor;
            }
        """.trimIndent()

        fun startBenchmark(durationSeconds: Int) {
            benchmarkDuration = durationSeconds
            benchmarkStartTime = System.currentTimeMillis()
            lastFrameTime = benchmarkStartTime
            lastSecondTime = benchmarkStartTime
            framesInCurrentSecond = 0
            isRunning = true
            completed = false
            frameTimes.clear()
            perSecondFps.clear()
        }

        fun isSurfaceReady(): Boolean = surfaceReady

        fun isComplete(): Boolean = completed

        fun getPerSecondFps(): List<Double> = perSecondFps.toList()

        fun getResults(): GpuResults {
            if (frameTimes.isEmpty()) {
                return GpuResults(0.0, 0.0, 0.0, 0.0, 0.0, 0)
            }

            val fps = frameTimes.map { if (it > 0) 1000.0 / it else 0.0 }.filter { it > 0 }.sorted()

            if (fps.isEmpty()) {
                return GpuResults(0.0, 0.0, 0.0, 0.0, 0.0, 0)
            }

            val avgFps = fps.average()
            val minFps = fps.minOrNull() ?: 0.0
            val maxFps = fps.maxOrNull() ?: 0.0

            val p95Index = (fps.size * 0.05).toInt().coerceAtMost(fps.size - 1).coerceAtLeast(0)
            val p99Index = (fps.size * 0.01).toInt().coerceAtMost(fps.size - 1).coerceAtLeast(0)

            val p95Fps = fps[p95Index]
            val p99Fps = fps[p99Index]

            return GpuResults(avgFps, minFps, maxFps, p95Fps, p99Fps, fps.size)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            android.util.Log.d("GPU_BENCHMARK", "onSurfaceCreated called")
            GLES20.glClearColor(0.1f, 0.1f, 0.15f, 1.0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)

            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            cubeBuffer = ByteBuffer.allocateDirect(cubeVertices.size * 4).run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(cubeVertices)
                    position(0)
                }
            }

            colorBuffer = ByteBuffer.allocateDirect(cubeColors.size * 4).run {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(cubeColors)
                    position(0)
                }
            }

            surfaceReady = true
            android.util.Log.d("GPU_BENCHMARK", "Surface ready set to true")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            val ratio = if (height > 0) width.toFloat() / height.toFloat() else 1f

            Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 10f, 1000f)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (!surfaceReady) return

            if (isRunning) {
                val currentTime = System.currentTimeMillis()
                val frameTime = currentTime - lastFrameTime

                if (frameTime > 0 && frameTime < 1000) {
                    frameTimes.add(frameTime)
                    framesInCurrentSecond++
                }

                // Calculate FPS per-second
                if (currentTime - lastSecondTime >= 1000) {
                    val fps = framesInCurrentSecond * 1000.0 / (currentTime - lastSecondTime)
                    perSecondFps.add(fps)
                    framesInCurrentSecond = 0
                    lastSecondTime = currentTime
                }

                lastFrameTime = currentTime

                if (currentTime - benchmarkStartTime >= benchmarkDuration * 1000) {
                    isRunning = false
                    completed = true
                    android.util.Log.d("GPU_BENCHMARK", "Benchmark complete. Frames: ${frameTimes.size}, Per-second FPS: $perSecondFps")
                }
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)

            Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 400f, 0f, 0f, 0f, 0f, 1f, 0f)

            val gridSize = 32
            val offset = (gridSize - 1) / 2.0f * 2.5f

            for (i in 0 until gridSize) {
                for (j in 0 until gridSize) {
                    Matrix.setIdentityM(modelMatrix, 0)
                    // Translation places cubes in a centered 32x32 grid
                    Matrix.translateM(modelMatrix, 0, (i * 2.5f) - offset, (j * 2.5f) - offset, 0f)
                    Matrix.rotateM(modelMatrix, 0, rotation + i * 10f + j * 5f, 1f, 1f, 1f)

                    val tempMatrix = FloatArray(16)
                    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
                    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

                    drawCube()
                }
            }

            rotation += 2f
        }

        private fun drawCube() {
            val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
            val colorHandle = GLES20.glGetAttribLocation(program, "vColor")
            val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, cubeBuffer)

            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 16, colorBuffer)

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            for (i in 0 until 6) {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, i * 4, 4)
            }

            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(colorHandle)
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}