package solve.utils

import javafx.scene.control.Alert
import javafx.scene.image.Image
import javafx.stage.Modality
import javafx.stage.Window
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

fun loadImage(path: String): Image? {
    val file = File(path)

    if (!file.canRead()) {
        println("The image file cannot be read!")
        return null
    }

    var image: Image? = null
    try {
        image = Image(file.inputStream())
    } catch (exception: IOException) {
        println("Input error while loading the image!\n${exception.message}")
    }

    return image
}

fun readFileText(filePath: String): String? {
    val file = File(filePath)

    if (!file.canRead()) {
        println("The text file cannot be read!")
        return null
    }

    var text: String? = null
    try {
        text = file.readText()
    } catch (exception: IOException) {
        println("Input error while reading the text!\n${exception.message}")
    }

    return text
}

fun loadBufferedImage(filePath: String): BufferedImage? {
    val file = File(filePath)

    if (!file.canRead()) {
        println("The image file cannot be read!")
        return null
    }

    var image: BufferedImage? = null
    try {
        image = ImageIO.read(file)
    } catch (exception: IOException) {
        println("Input error while loading the image!\n${exception.message}")
    }

    return image
}

fun createAlert(content: String, owner: Window) {
    val alert = Alert(Alert.AlertType.ERROR, content).apply {
        headerText = ""
        initModality(Modality.APPLICATION_MODAL)
        initOwner(owner)
    }
    alert.show()
}

fun MutableList<String>.toStringWithoutBrackets(): String {
    return this.toString().replace("[", "").replace("]", "")
}
