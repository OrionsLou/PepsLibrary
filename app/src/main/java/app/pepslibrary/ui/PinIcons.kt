package app.pepslibrary.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material's "push pin" icon, outlined and filled (path data from Google's Material Icons, Apache 2.0). Defined here
 * because it's only in material-icons-extended, a large dependency to add for two icons.
 */
object PinIcons {
    val Outlined: ImageVector by lazy {
        icon(
            "PushPinOutlined",
            "M14,4v5c0,1.12 0.37,2.16 1,3H9c0.65,-0.86 1,-1.9 1,-3V4H14M17,2H7C6.45,2 6,2.45 6,3c0,0.55 0.45,1 1,1" +
                "c0,0 0,0 0,0l1,0v5c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1l1,-1v-7H19v-2c0,0 0,0 0,0c-1.66,0 -3,-1.34 -3,-3" +
                "V4l1,0c0,0 0,0 0,0c0.55,0 1,-0.45 1,-1C18,2.45 17.55,2 17,2L17,2z",
        )
    }

    val Filled: ImageVector by lazy {
        icon(
            "PushPinFilled",
            "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5" +
                "c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z",
        )
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
