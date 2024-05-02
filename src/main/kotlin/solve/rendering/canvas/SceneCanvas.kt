package solve.rendering.canvas

import io.github.palexdev.materialfx.controls.MFXContextMenu
import javafx.scene.Node
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import org.joml.Vector2f
import org.joml.Vector2i
import solve.rendering.engine.Window
import solve.rendering.engine.core.input.MouseButton
import solve.rendering.engine.core.input.MouseInputHandler
import solve.rendering.engine.core.renderers.FramesRenderer
import solve.rendering.engine.core.renderers.LinesLayerRenderer
import solve.rendering.engine.core.renderers.PlanesLayerRenderer
import solve.rendering.engine.core.renderers.PointsLayerRenderer
import solve.rendering.engine.core.renderers.Renderer
import solve.rendering.engine.utils.minus
import solve.rendering.engine.utils.plus
import solve.rendering.engine.utils.times
import solve.rendering.engine.utils.toFloatVector
import solve.rendering.engine.utils.toIntVector
import solve.scene.controller.SceneController
import solve.scene.model.Landmark
import solve.scene.model.Layer
import solve.scene.model.Scene
import solve.scene.model.VisualizationFrame
import solve.utils.ServiceLocator
import solve.utils.action
import solve.utils.ceilToInt
import solve.utils.getScreenPosition
import solve.utils.item
import solve.utils.lineSeparator
import solve.rendering.engine.scene.Scene as EngineScene

class SceneCanvas : OpenGLCanvas() {
    private var sceneController: SceneController? = null
    private var engineScene: EngineScene? = null

    private var isDraggingScene = false
    private var dragStartCameraPoint = Vector2f()
    private var dragStartPoint = Vector2i()

    private var leftUpperCornerCameraPosition = Vector2f()
    private var rightLowerCornerCameraPosition = Vector2f()

    private var lastLayersWithCommonSettings = listOf<Layer>()
    private var lastFramesSelection = listOf<VisualizationFrame>()
    private var framesSelectionSize = 0
    private var framesSize = Vector2i()
    private val framesRatio: Float
        get() = framesSize.x.toFloat() / framesSize.y
    private var columnsNumber = 0
    private val rowsNumber: Int
        get() = (framesSelectionSize.toFloat() / columnsNumber.toFloat()).ceilToInt()

    private var needToReinitializeRenderers = false
    private var scene: Scene? = null

    private var isFirstFramesSelection = false

    private var contextMenuInvokeFrame: VisualizationFrame? = null

    private val contextMenu = buildContextMenu(canvas)

    init {
        initializeCanvasEvents()
    }

    fun setNewScene(scene: Scene) {
        this.scene = scene
        this.framesSize = Vector2i(scene.frameSize.width.toInt(), scene.frameSize.height.toInt())
        engineScene?.framesRenderer?.setNewSceneFrames(scene.frames, framesSize.toFloatVector())
        engineScene?.landmarkRenderers?.forEach { it.setNewSceneFrames(scene.frames, framesSize.toFloatVector()) }
        isFirstFramesSelection = true

        needToReinitializeRenderers = true
    }

    fun setFramesSelection(framesSelection: List<VisualizationFrame>) {
        if (framesSelection.isNotEmpty() && isFirstFramesSelection) {
            engineScene?.initializeFramesRenderer()
            isFirstFramesSelection = false
        }

        recalculateCameraCornersPositions()
        window.camera.position = leftUpperCornerCameraPosition

        engineScene?.framesRenderer?.setFramesSelection(framesSelection)
        engineScene?.landmarkRenderers?.forEach { it.setFramesSelection(framesSelection) }
        engineScene?.landmarkRenderers?.forEach { renderer ->
            val rendererLayerSettings =
                engineScene?.landmarkLayerRendererLayers?.get(renderer)?.settings ?: return@forEach
            lastLayersWithCommonSettings =
                scene?.getLayersWithCommonSettings(rendererLayerSettings, framesSelection) ?: return@forEach
            renderer.setFramesSelectionLayers(lastLayersWithCommonSettings)
        }
        framesSelectionSize = framesSelection.count()
        lastFramesSelection = framesSelection
    }

    fun setColumnsNumber(columnsNumber: Int) {
        engineScene?.framesRenderer?.setNewGridWidth(columnsNumber)
        engineScene?.landmarkRenderers?.forEach { it.setNewGridWidth(columnsNumber) }
        this.columnsNumber = columnsNumber
    }

    fun dragTo(toScreenPoint: Vector2i) {
        val mousePoint = fromScreenToCameraPoint(toScreenPoint)
        if (isDraggingScene) {
            val dragVector = mousePoint - dragStartPoint
            window.camera.position = dragStartCameraPoint - dragVector.toFloatVector() / window.camera.scaledZoom
            constraintCameraPosition()
        }
    }

    fun startDragging(fromScreenPoint: Vector2i) {
        dragStartCameraPoint = window.camera.position
        dragStartPoint = fromScreenToCameraPoint(fromScreenPoint)
        isDraggingScene = true
    }

    fun stopDragging() {
        isDraggingScene = false
    }

    fun handleClick(screenPoint: Vector2i, mouseButton: MouseButton) {
        val frameCoordinates = determineFrameCoordinates(screenPoint)
        val frameIndexCoordinates = frameCoordinates.toIntVector()
        val frameIndex = frameIndexCoordinates.y * columnsNumber + frameIndexCoordinates.x
        // Frame local coordinate including spacing area.
        var frameLocalCoordinate = Vector2f(frameCoordinates.x % 1, frameCoordinates.y % 1)
        // Frame local coordinate excluding spacing area.
        frameLocalCoordinate = frameToShaderVector(frameLocalCoordinate, framesSize)
        frameLocalCoordinate.x /= framesRatio
        val framePixelCoordinate =
            Vector2f(frameLocalCoordinate.x * framesSize.x, frameLocalCoordinate.y * framesSize.y).toIntVector()

        if (mouseButton == landmarkInteractionMouseButton) {
            tryInteractWithLandmark(frameIndex, framePixelCoordinate)
        } else if (mouseButton == contextMenuMouseButton) {
            contextMenuInvokeFrame = getContextMenuInvokeFrame(frameIndex) ?: return
            val canvasScreenPosition = canvas.getScreenPosition()
            contextMenu.show(
                canvas,
                canvasScreenPosition.x + screenPoint.x.toDouble(),
                canvasScreenPosition.y + screenPoint.y.toDouble()
            )
        }
    }

    fun zoomToPoint(screenPoint: Vector2i, newZoom: Float) {
        val cameraPoint = fromScreenToCameraPoint(screenPoint)
        window.camera.zoomToPoint(cameraPoint, newZoom)

        recalculateCameraCornersPositions()
        constraintCameraPosition()
    }

    override fun onInit() {
        super.onInit()
        val controller = ServiceLocator.getService<SceneController>() ?: return
        sceneController = controller

        engineScene = EngineScene(FramesRenderer(window))
    }

    override fun onDraw(deltaTime: Float) {
        checkRenderersInitialization()

        if (needToReinitializeRenderers) {
            return
        }

        engineScene?.update()
    }

    private fun determineFrameCoordinates(screenPoint: Vector2i): Vector2f {
        return shaderToFrameVector(window.calculateTopLeftCornerShaderPosition(), framesSize) +
            screenToFrameVector(screenPoint, framesSize, window)
    }

    private fun tryInteractWithLandmark(frameIndex: Int, frameInteractionPixel: Vector2i) {
        if (frameIndex >= lastFramesSelection.count()) {
            return
        }

        val interactingVisualizationFrame = lastFramesSelection[frameIndex]
        var clickedLandmark: Landmark? = null

        interactingVisualizationFrame.layers.forEach { layer ->
            when (layer) {
                is Layer.PointLayer -> {
                    val pointLandmarks = layer.getLandmarks()
                    val landmarkIndex =
                        MouseInputHandler.indexOfClickedPointLandmark(
                            pointLandmarks,
                            frameInteractionPixel,
                            layer.settings.selectedRadius.toFloat() / window.camera.zoom
                        )

                    if (landmarkIndex == -1) {
                        return@forEach
                    }

                    clickedLandmark = pointLandmarks[landmarkIndex]
                }
                is Layer.LineLayer -> {
                    val lineLandmarks = layer.getLandmarks()
                    val landmarkIndex =
                        MouseInputHandler.indexOfClickedLineLandmark(
                            lineLandmarks,
                            frameInteractionPixel,
                            layer.settings.selectedWidth.toFloat() / window.camera.zoom
                        )

                    if (landmarkIndex == -1) {
                        return@forEach
                    }

                    clickedLandmark = lineLandmarks[landmarkIndex]
                }
                is Layer.PlaneLayer -> {}
            }
        }

        val selectedLandmark = clickedLandmark ?: return
        if (selectedLandmark.layerState.selectedLandmarksUIDs.contains(selectedLandmark.uid)) {
            selectedLandmark.layerState.deselectLandmark(selectedLandmark.uid)
        } else {
            selectedLandmark.layerState.selectLandmark(selectedLandmark.uid)
        }
    }

    private fun checkRenderersInitialization() {
        if (needToReinitializeRenderers) {
            reinitializeRenderers()
        }
    }

    private fun reinitializeRenderers() {
        engineScene?.clearLandmarkRenderers()
        val scene = this.scene ?: return

        scene.layers.forEach { layer ->
            addLandmarkRenderer(layer, scene)
        }
        engineScene?.landmarkRenderers?.forEach {
            it.setNewGridWidth(sceneController?.installedColumnsNumber ?: SceneController.MaxColumnsNumber)
        }
        needToReinitializeRenderers = false
    }

    private fun addLandmarkRenderer(layer: Layer, scene: Scene) {
        val addingRenderer = when (layer) {
            is Layer.PointLayer -> PointsLayerRenderer(window) { sceneController?.scene }
            is Layer.LineLayer -> LinesLayerRenderer(window) { sceneController?.scene }
            is Layer.PlaneLayer -> PlanesLayerRenderer(window) { sceneController?.scene }
        }
        addingRenderer.setNewSceneFrames(scene.frames, framesSize.toFloatVector())
        engineScene?.addLandmarkRenderer(addingRenderer, layer)
    }

    private fun fromScreenToCameraPoint(screenPoint: Vector2i) = screenPoint - (window.size / 2)

    private fun constraintCameraPosition() {
        window.camera.position.x =
            window.camera.position.x.coerceIn(leftUpperCornerCameraPosition.x, rightLowerCornerCameraPosition.x)
        window.camera.position.y =
            window.camera.position.y.coerceIn(leftUpperCornerCameraPosition.y, rightLowerCornerCameraPosition.y)
    }

    private fun recalculateCameraCornersPositions() {
        val halfScreenSize = (Vector2f(window.width.toFloat(), window.height.toFloat()) / 2f) / window.camera.scaledZoom
        leftUpperCornerCameraPosition = halfScreenSize

        val framesSelectionSize = Vector2f(
            columnsNumber * framesSize.x + (columnsNumber - 1) * Renderer.getSpacingWidth(framesSize),
            rowsNumber * framesSize.y + (rowsNumber - 1) * Renderer.getSpacingWidth(framesSize)
        )
        val framesSelectionScreenSize =
            framesSelectionSize * window.camera.zoom / IdentityFramesSizeScale / window.camera.scaledZoom

        rightLowerCornerCameraPosition = framesSelectionScreenSize - leftUpperCornerCameraPosition

        rightLowerCornerCameraPosition.x =
            rightLowerCornerCameraPosition.x.coerceAtLeast(leftUpperCornerCameraPosition.x)
        rightLowerCornerCameraPosition.y =
            rightLowerCornerCameraPosition.y.coerceAtLeast(leftUpperCornerCameraPosition.y)
    }

    private fun buildContextMenu(parent: Node): MFXContextMenu {
        val contextMenu = MFXContextMenu(parent)
        contextMenu.addCopyTimestampItem()
        contextMenu.lineSeparator()
        contextMenu.addAssociateKeypointsItem()
        contextMenu.addClearAssociationsItem()

        return contextMenu
    }

    private fun MFXContextMenu.addCopyTimestampItem() {
        item("Copy timestamp").action {
            val invokeFrame = contextMenuInvokeFrame ?: return@action

            val timestamp = invokeFrame.timestamp
            val clipboardContent = ClipboardContent().also { it.putString(timestamp.toString()) }
            Clipboard.getSystemClipboard().setContent(clipboardContent)
        }
    }

    private fun MFXContextMenu.addAssociateKeypointsItem() {
        item("Associate keypoints").action {
            val invokeFrame = contextMenuInvokeFrame ?: return@action

            println("assoiation")
            invokeFrame.toString()
        }
    }

    private fun MFXContextMenu.addClearAssociationsItem() {
        item("Clear associations").action {
            val invokeFrame = contextMenuInvokeFrame ?: return@action

            println("clear associations")
            invokeFrame.toString()
        }
    }

    private fun getContextMenuInvokeFrame(contextMenuInvokeFrameIndex: Int): VisualizationFrame? {
        if (contextMenuInvokeFrameIndex >= lastFramesSelection.count()) {
            println("The index of the invoked frame is incorrect!")
            return null
        }

        return lastFramesSelection[contextMenuInvokeFrameIndex]
    }

    companion object {
        const val IdentityFramesSizeScale = 1.605f

        private val landmarkInteractionMouseButton = MouseButton.Left
        private val contextMenuMouseButton = MouseButton.Right

        // Used for frame to shader and shader to frame vector conversions.
        private fun getFrameToShaderConversionCoefficients(framesSize: Vector2i): Vector2f {
            return Vector2f(
                (framesSize.x + Renderer.getSpacingWidth(framesSize)) / framesSize.y,
                (1 + Renderer.FramesSpacing)
            )
        }

        // Converts screen vector to frame coordinates.
        // One frame including spacing area corresponds to a (1, 1) frame vector.
        fun screenToFrameVector(screenVector: Vector2i, framesSize: Vector2i, window: Window): Vector2f {
            val shaderVector = window.screenToShaderVector(screenVector)
            val frameVector = shaderToFrameVector(shaderVector, framesSize)

            return frameVector
        }

        fun shaderToFrameVector(shaderVector: Vector2f, framesSize: Vector2i): Vector2f {
            val frameVector = Vector2f(shaderVector)
            val frameToShaderMultiplier = getFrameToShaderConversionCoefficients(framesSize)
            frameVector.x /= frameToShaderMultiplier.x
            frameVector.y /= frameToShaderMultiplier.y

            return frameVector
        }

        fun frameToShaderVector(frameVector: Vector2f, framesSize: Vector2i): Vector2f {
            val shaderVector = Vector2f(frameVector)
            val frameToShaderMultiplier = getFrameToShaderConversionCoefficients(framesSize)
            shaderVector.x *= frameToShaderMultiplier.x
            shaderVector.y *= frameToShaderMultiplier.y

            return shaderVector
        }
    }
}
