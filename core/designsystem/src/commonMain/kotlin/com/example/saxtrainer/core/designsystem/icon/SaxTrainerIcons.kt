package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icons, as drawing instructions rather than as files.
 *
 * They are Kotlin and not vector drawables because a drawable is an Android resource: it cannot be
 * reached from shared code, and every screen that wanted one would need a resource lookup only one
 * platform has. The path data is the same data either way - see `tools/icons.map` for where each
 * one comes from and how to replace it.
 *
 * Used through [com.example.saxtrainer.core.designsystem.component.IconSource.Vector], like any
 * Material icon.
 */
object SaxTrainerIcons

/**
 * Builds one icon from the same three things a vector drawable holds: a square viewport, an
 * optional group translation, and the path data.
 *
 * The fill is a placeholder. Icons are always drawn through `Icon(...)`, which replaces it with the
 * theme's content colour, which is why the sources carry no colour of their own either.
 */
internal fun saxIcon(
    name: String,
    viewport: Float,
    vararg pathData: String,
    translateY: Float = 0f,
): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = viewport,
        viewportHeight = viewport,
    )
    val translated = translateY != 0f
    if (translated) builder.addGroup(translationY = translateY)
    pathData.forEach { builder.addPath(pathData = addPathNodes(it), fill = Placeholder) }
    if (translated) builder.clearGroup()
    return builder.build()
}

private val IconSize = 24.dp
/** Never seen: every icon is drawn with `Icon(tint = ...)`, which replaces the fill. */
private val Placeholder = SolidColor(Color.Black)
