package solve.scene.controller

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import solve.scene.model.Scene
import solve.scene.view.DelayedFrameUpdatesManager
import solve.scene.view.SceneView
import solve.utils.ServiceLocator
import solve.utils.ceilToInt
import solve.utils.structures.DoublePoint
import tornadofx.Controller
import tornadofx.onChange
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SceneController : Controller() {
    val sceneWidthProperty = SimpleDoubleProperty()
    val sceneProperty = SimpleObjectProperty(Scene(emptyList(), emptyList()))
    val scene: Scene
        get() = sceneProperty.value

    val columnsNumberProperty = SimpleObjectProperty(0)
    var columnsNumber: Int
        get() = columnsNumberProperty.value
        set(value) {
            if (value <= 0 || value > MaxColumnsNumber) {
                println("Number of the grid columns is out of range!")
                return
            }

            columnsNumberProperty.value = value
        }

    val installedMinScaleProperty = SimpleDoubleProperty(DefaultMinScale)
    var installedMinScale: Double
        get() = installedMinScaleProperty.value
        set(value) {
            if (value <= 0 || value >= installedMaxScale) {
                println("Min scene scale value should be a positive number that is less than max scene scale!")
                return
            }

            installedMinScaleProperty.value = value
        }

    val installedMaxScaleProperty = SimpleDoubleProperty(DefaultMaxScale)
    var installedMaxScale: Double
        get() = installedMaxScaleProperty.value
        set(value) {
            if (value <= 0 || value <= installedMinScale) {
                println("Max scene scale value should be a positive number that is greater than min scene scale!")
                return
            }

            installedMaxScaleProperty.value = value
        }

    val scaleProperty = SimpleDoubleProperty(calculateMinScaleDependingOnColumns())
    var scale: Double
        get() = scaleProperty.value
        set(value) {
            scaleProperty.value = value
        }

    private val minScale: Double
        get() = calculateMinScaleDependingOnColumns()
    private val maxScale: Double
        get() = installedMaxScale

    val xProperty = SimpleDoubleProperty(DefaultX)
    val yProperty = SimpleDoubleProperty(DefaultY)

    // Scroll grid methods, controller's x and y properties can't be explicitly bound to grid's x and y properties
    // because grid can't scroll in certain cases
    var scrollX: ((Double) -> Double)? = null
    var scrollY: ((Double) -> Double)? = null

    var x: Double
        get() = xProperty.value
        private set(value) {
            val scrollX = scrollX ?: throw RuntimeException("scrollX method is not set")
            scrollX(value)
        }

    var y: Double
        get() = yProperty.value
        private set(value) {
            val scrollY = scrollY ?: throw RuntimeException("scrollY method is not set")
            scrollY(value)
        }

    init {
        ServiceLocator.registerService(this)

        addGridSettingsBindings()
    }

    fun setScene(newScene: Scene, keepSettings: Boolean) {
        sceneProperty.value = newScene

        if (!keepSettings) {
            reinitializeSettings(newScene)
        } else {
            recalculateScale()
        }
    }

    fun zoomIn(mousePosition: DoublePoint) = zoom(min(scale * ScaleFactor, maxScale), mousePosition)

    fun zoomOut(mousePosition: DoublePoint) = zoom(max(scale / ScaleFactor, min(minScale, scale)), mousePosition)

    fun recalculateScale() {
        scale = max(minScale, calculateMinScaleDependingOnColumns())
    }

    private fun zoom(newScale: Double, mousePosition: DoublePoint) {
        val initialMouseX = (xProperty.value + mousePosition.x) / scale
        val initialMouseY = (yProperty.value + mousePosition.y) / scale

        DelayedFrameUpdatesManager.doLockedAction {
            scaleProperty.value = newScale

            x = initialMouseX * scaleProperty.value - mousePosition.x
            y = initialMouseY * scaleProperty.value - mousePosition.y
        }
    }

    private fun reinitializeSettings(newScene: Scene) {
        columnsNumber = calculateColumnsCount(newScene)
        setDefaultScaleRange()
        scale = calculateMinScaleDependingOnColumns()
    }

    private fun addGridSettingsBindings() {
        columnsNumberProperty.onChange { newColumnsNumber ->
            newColumnsNumber ?: return@onChange

            scale = calculateMinScaleDependingOnColumns()
        }

        installedMinScaleProperty.onChange { installedMinScale ->
            if (installedMinScale > scale) {
                scale = installedMinScale
            }
        }

        installedMaxScaleProperty.onChange { installedMaxScale ->
            if (installedMaxScale < scale) {
                scale = installedMaxScale
            }
        }
    }

    private fun calculateMinScaleDependingOnColumns(): Double {
        val displayingColumnsNumber = min(scene.frames.count(), columnsNumber)

        return min(
            max(
                installedMinScale,
                sceneWidthProperty.value /
                        ((scene.frameSize.width + SceneView.framesMargin) * displayingColumnsNumber)
            ),
            DefaultMaxScale
        )
    }

    private fun setDefaultScaleRange() {
        installedMaxScale = DefaultMaxScale
        installedMinScale = DefaultMinScale
    }

    companion object {
        const val DefaultMinScale = 0.2
        const val DefaultMaxScale = 20.0

        const val MaxColumnsNumber = 5

        private const val DefaultX = 0.0
        private const val DefaultY = 0.0

        private const val ScaleFactor = 1.15

        fun calculateColumnsCount(scene: Scene): Int {
            return min(sqrt(scene.frames.size.toDouble()).ceilToInt(), MaxColumnsNumber)
        }
    }
}
