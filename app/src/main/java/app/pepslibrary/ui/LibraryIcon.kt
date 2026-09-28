package app.pepslibrary.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material's "menu book" icon, a book lying open (path data from Google's Material Icons, Apache 2.0), for the
 * footer's Library button. Defined here because it's only in material-icons-extended; see [PinIcons].
 */
internal val LibraryIcon: ImageVector by lazy {
    val paths = listOf(
        // The open covers, with the left page solid and the right page hollow.
        "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5" +
            "S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20" +
            "c1.95,0 4.05,0.4 5.5,1.5c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05c0.1,0.05 0.15,0.05 0.25,0.05" +
            "c0.25,0 0.5,-0.25 0.5,-0.5V6C22.4,5.55 21.75,5.25 21,5z" +
            "M21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5" +
            "c1.2,0 2.4,0.15 3.5,0.5V18.5z",
        // Three lines of text on the right page.
        "M17.5,10.5c0.88,0 1.73,0.09 2.5,0.26V9.24C19.21,9.09 18.36,9 17.5,9c-1.7,0 -3.24,0.29 -4.5,0.83v1.66" +
            "C14.13,10.85 15.7,10.5 17.5,10.5z",
        "M13,12.49v1.66c1.13,-0.64 2.7,-0.99 4.5,-0.99c0.88,0 1.73,0.09 2.5,0.26V11.9c-0.79,-0.15 -1.64,-0.24 -2.5,-0.24" +
            "C15.8,11.66 14.26,11.96 13,12.49z",
        "M17.5,14.33c-1.7,0 -3.24,0.29 -4.5,0.83v1.66c1.13,-0.64 2.7,-0.99 4.5,-0.99c0.88,0 1.73,0.09 2.5,0.26v-1.52" +
            "C19.21,14.41 18.36,14.33 17.5,14.33z",
    )
    ImageVector.Builder("MenuBook", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply { paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) } }
        .build()
}
