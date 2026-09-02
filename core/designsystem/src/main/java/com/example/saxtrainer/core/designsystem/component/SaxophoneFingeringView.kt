package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.theme.SaxTrainerTheme
import com.example.saxtrainer.core.fingering.Fingering
import com.example.saxtrainer.core.fingering.SaxKey
import com.example.saxtrainer.core.fingering.SaxophoneFingerings
import com.example.saxtrainer.core.music.Note
import com.example.saxtrainer.core.music.NoteName

/**
 * Draws a saxophone fingering: pressed keys filled, open keys outlined.
 *
 * Everything is generated from [SaxophoneKeyLayout] and the [Fingering], so any note a chart knows
 * can be shown without an image per note. The view holds no fingering knowledge of its own - it is
 * given which keys are down - and no feature knowledge, so a trainer, a tuner or a reference chart
 * can all use it.
 */
@Composable
fun SaxophoneFingeringView(
    fingering: Fingering,
    modifier: Modifier = Modifier,
    width: Dp = DefaultWidth,
    pressedColor: Color = MaterialTheme.colorScheme.onSurface,
    openColor: Color = MaterialTheme.colorScheme.outline,
    bodyColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = modifier
            .size(width = width, height = width * HEIGHT_RATIO)
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
    ) {
        drawBody(bodyColor)

        val keyDiameter = size.width * KEY_DIAMETER_RATIO
        val strokeWidth = keyDiameter * STROKE_RATIO

        SaxophoneKeyLayout.keys.forEach { placement ->
            drawKey(
                placement = placement,
                pressed = fingering.isPressed(placement.key),
                keyDiameter = keyDiameter,
                strokeWidth = strokeWidth,
                pressedColor = pressedColor,
                openColor = openColor,
            )
        }
    }
}

/** The line the main keys sit on, hinting at the body of the instrument. */
private fun DrawScope.drawBody(color: Color) {
    val x = size.width * SaxophoneKeyLayout.MAIN_COLUMN_X
    drawLine(
        color = color,
        start = Offset(x, size.height * 0.04f),
        end = Offset(x, size.height * 0.98f),
        strokeWidth = size.width * BODY_STROKE_RATIO,
    )
}

private fun DrawScope.drawKey(
    placement: KeyPlacement,
    pressed: Boolean,
    keyDiameter: Float,
    strokeWidth: Float,
    pressedColor: Color,
    openColor: Color,
) {
    val centre = Offset(size.width * placement.x, size.height * placement.y)
    val diameter = keyDiameter * (placement.size / REFERENCE_KEY_SIZE)
    val color = if (pressed) pressedColor else openColor

    when (val shape = placement.shape) {
        is KeyShape.Circle -> if (pressed) {
            drawCircle(color = color, radius = diameter / 2f, center = centre)
        } else {
            drawCircle(
                color = color,
                radius = diameter / 2f,
                center = centre,
                style = Stroke(width = strokeWidth),
            )
        }

        is KeyShape.RoundedRect -> {
            val rectSize = Size(diameter * shape.widthRatio, diameter * shape.heightRatio)
            val topLeft = Offset(centre.x - rectSize.width / 2f, centre.y - rectSize.height / 2f)
            val corner = CornerRadius(rectSize.height / 2f)
            if (pressed) {
                drawRoundRect(color = color, topLeft = topLeft, size = rectSize, cornerRadius = corner)
            } else {
                drawRoundRect(
                    color = color,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}

private val DefaultWidth = 96.dp

/** The diagram is a tall front view, so height follows width. */
private const val HEIGHT_RATIO = 2.2f
private const val KEY_DIAMETER_RATIO = 0.30f
private const val REFERENCE_KEY_SIZE = 0.19f
private const val STROKE_RATIO = 0.12f
private const val BODY_STROKE_RATIO = 0.012f

@Preview(showBackground = true)
@Composable
private fun SaxophoneFingeringViewPreview() {
    SaxTrainerTheme {
        SaxophoneFingeringView(
            fingering = SaxophoneFingerings.fingeringFor(Note(NoteName.G, 4))!!,
            contentDescription = "Fingering for G",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SaxophoneFingeringViewSideKeyPreview() {
    SaxTrainerTheme {
        SaxophoneFingeringView(
            fingering = Fingering(setOf(SaxKey.LH1, SaxKey.SIDE_BB)),
        )
    }
}
