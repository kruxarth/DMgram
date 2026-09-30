package app.dmgram.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * DMGram's own line icons, drawn to Instagram's conventions (24 grid, 2 px
 * rounded stroke; the selected tab is the same shape filled) so muscle memory
 * carries over. Tint comes from Icon.
 */
object Glyphs {
    private const val HOUSE = "M3 10.2 L12 3 L21 10.2 V20 A1 1 0 0 1 20 21 H15 V15.5 A3 3 0 0 0 9 15.5 V21 H4 A1 1 0 0 1 3 20 Z"
    private const val PLANE = "M21.5 2.5 L2.5 9.3 L10.3 13.7 L14.7 21.5 Z"
    private const val PLANE_FOLD = "M21.5 2.5 L10.3 13.7"
    private const val HEAD = "M12 3.5 A4 4 0 1 1 12 11.5 A4 4 0 1 1 12 3.5 Z"
    private const val SHOULDERS = "M4 21 A8 7 0 0 1 20 21 Z"
    private const val LENS = "M10.5 3 A7.5 7.5 0 1 1 10.5 18 A7.5 7.5 0 1 1 10.5 3 Z"
    private const val HANDLE = "M16 16 L21 21"
    private const val HEART = "M12 20.5 C8 17.3 3 13.6 3 8.9 A4.9 4.9 0 0 1 12 6.2 A4.9 4.9 0 0 1 21 8.9 C21 13.6 16 17.3 12 20.5 Z"

    val HomeOutline = icon("home", stroke = listOf(HOUSE))
    val HomeFilled = icon("home.fill", fill = listOf(HOUSE), stroke = listOf(HOUSE))
    val DirectOutline = icon("direct", stroke = listOf(PLANE, PLANE_FOLD))
    val DirectFilled = icon("direct.fill", fill = listOf(PLANE), stroke = listOf(PLANE))
    val PersonOutline = icon("person", stroke = listOf(HEAD, SHOULDERS))
    val PersonFilled = icon("person.fill", fill = listOf(HEAD, SHOULDERS), stroke = listOf(HEAD, SHOULDERS))
    val Search = icon("search", stroke = listOf(LENS, HANDLE))
    val Heart = icon("heart", stroke = listOf(HEART))

    private fun icon(
        name: String,
        fill: List<String> = emptyList(),
        stroke: List<String> = emptyList(),
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        fill.forEach { builder.addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        stroke.forEach {
            builder.addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
