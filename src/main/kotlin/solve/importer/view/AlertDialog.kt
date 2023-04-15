package solve.importer.view

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.text.Font
import kotlinx.coroutines.cancel
import solve.constants.IconsAlertError
import solve.utils.loadResourcesImage
import solve.utils.mfxButton
import tornadofx.*

class AlertDialog(contentText: String) : View() {

    val errorIcon = loadResourcesImage(IconsAlertError)

    override val root =
        borderpane {
            top {
                label("Error") {
                    graphicTextGap = 15.0
                    graphic = ImageView(errorIcon)
                    BorderPane.setMargin(this, Insets(0.0,0.0,0.0,14.0))
                    prefHeight=0.0
                    font = Font.font("Roboto Condensed", 20.0)
                }
            }
            center {
                label(contentText){
                    BorderPane.setMargin(this, Insets(0.0, 14.0, 0.0, 14.0))
                    style = "-fx-font-family: Roboto Condensed; -fx-font-size: 14px;  -fx-text-fill: #000000;"
                    BorderPane.setAlignment(this, Pos.CENTER_LEFT)
                    isWrapText = true
                    prefWidth = 300.0
                }
            }

            bottom {
                mfxButton("OK") {
                    BorderPane.setMargin(this, Insets(0.0, 14.0, 14.0, 0.0))
                    maxWidth = 40.0
                    prefHeight = 23.0
                    style = "-fx-font-family: Roboto Condensed; -fx-font-size: 14px; -fx-font-weight: Bold; -fx-text-fill: #78909C;"
                    BorderPane.setAlignment(this, Pos.TOP_RIGHT)
                    prefWidth = 180.0
                    action {
                        find<ControlPanel>().coroutineScope.cancel()
                        close()
                    }
                }
            }
        }
}