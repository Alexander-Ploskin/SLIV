package solve.rendering.canvas

import com.huskerdev.openglfx.GLCanvasAnimator
import com.huskerdev.openglfx.events.GLDisposeEvent
import com.huskerdev.openglfx.events.GLInitializeEvent
import com.huskerdev.openglfx.events.GLRenderEvent
import com.huskerdev.openglfx.events.GLReshapeEvent
import com.huskerdev.openglfx.lwjgl.LWJGLExecutor
import org.joml.Vector2i
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.opengl.GL.createCapabilities
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_MULTISAMPLE
import solve.rendering.engine.Window
import solve.rendering.engine.utils.minus
import solve.rendering.engine.utils.plus
import solve.rendering.engine.utils.toFloatVector
import com.huskerdev.openglfx.OpenGLCanvas as OpenGLFXCanvas

abstract class OpenGLCanvas {
    val canvas: OpenGLFXCanvas = OpenGLFXCanvas.create(LWJGLExecutor.LWJGL_MODULE)

    protected lateinit var window: Window

    init {
        canvas.animator = GLCanvasAnimator(OpenGLCanvasFPS)
    }

    open fun onInit() {
        glEnable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_MULTISAMPLE)
        glDepthFunc(GL_LEQUAL)
    }

    open fun onDraw(deltaTime: Float) { }

    open fun onDispose() { }

    protected fun initializeCanvasEvents() {
        canvas.addOnInitEvent(this::canvasInit)
        canvas.addOnReshapeEvent(this::reshape)
        canvas.addOnRenderEvent(this::render)
        canvas.addOnDisposeEvent(this::dispose)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun canvasInit(event: GLInitializeEvent) {
        window = Window(canvas.width.toInt(), canvas.height.toInt())
        createCapabilities()
        onInit()
    }

    private fun render(event: GLRenderEvent) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        onDraw(event.delta.toFloat())
    }

    private fun reshape(event: GLReshapeEvent) {
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()

        val newWindowSize = Vector2i(event.width, event.height)
        normalizeResizedCameraPosition(newWindowSize, window.size)
        window.resize(event.width, event.height)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun dispose(event: GLDisposeEvent) {
        onDispose()
    }

    private fun normalizeResizedCameraPosition(newWindowSize: Vector2i, oldWindowSize: Vector2i) {
        val resizeDelta = newWindowSize - oldWindowSize
        window.camera.position += resizeDelta.toFloatVector() / (2f * window.camera.scaledZoom)
    }

    companion object {
        private const val OpenGLCanvasFPS = 60.0
    }
}
