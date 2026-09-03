package com.example.saxtrainer.core.designsystem.component

import com.example.saxtrainer.core.fingering.SaxKey

/** One key resolved to concrete coordinates, ready to be drawn by whatever is drawing. */
sealed interface KeyGeometry {
    data class Ellipse(val centreX: Float, val centreY: Float, val radius: Float) : KeyGeometry

    data class Bar(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val cornerRadius: Float,
    ) : KeyGeometry
}

data class ResolvedKey(val key: SaxKey, val geometry: KeyGeometry)

/** A guide line of the instrument body: the key column and the break between the hands. */
data class BodyGuide(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val dashed: Boolean,
)

/**
 * The diagram's measurements and how a placement turns into coordinates.
 *
 * Kept here rather than in the view so that everything drawing this diagram - the Compose renderer
 * and the chart exporter - works from one set of numbers and cannot drift apart.
 */
object SaxophoneDiagramMetrics {

    /** The panel is a tall front view, so height follows width. */
    const val HEIGHT_RATIO = 2.7f

    private const val KEY_DIAMETER_RATIO = 0.26f
    private const val REFERENCE_KEY_SIZE = 0.19f
    private const val STROKE_RATIO = 0.12f
    private const val BODY_STROKE_RATIO = 0.012f

    fun keyDiameter(width: Float): Float = width * KEY_DIAMETER_RATIO

    fun strokeWidth(width: Float): Float = keyDiameter(width) * STROKE_RATIO

    fun bodyStrokeWidth(width: Float): Float = width * BODY_STROKE_RATIO

    /** Every key placed on a panel of [width] by [height]. */
    fun resolve(width: Float, height: Float): List<ResolvedKey> {
        val baseDiameter = keyDiameter(width)
        return SaxophoneKeyLayout.keys.map { placement ->
            val centreX = width * placement.x
            val centreY = height * placement.y
            val diameter = baseDiameter * (placement.size / REFERENCE_KEY_SIZE)

            val geometry = when (val shape = placement.shape) {
                is KeyShape.Circle -> KeyGeometry.Ellipse(centreX, centreY, diameter / 2f)

                is KeyShape.RoundedRect -> {
                    val barWidth = diameter * shape.widthRatio
                    val barHeight = diameter * shape.heightRatio
                    KeyGeometry.Bar(
                        left = centreX - barWidth / 2f,
                        top = centreY - barHeight / 2f,
                        width = barWidth,
                        height = barHeight,
                        cornerRadius = minOf(barWidth, barHeight) / 2f,
                    )
                }
            }
            ResolvedKey(placement.key, geometry)
        }
    }

    /** The column the main keys sit on, and the dashed break between the two hands. */
    fun bodyGuides(width: Float, height: Float): List<BodyGuide> = listOf(
        BodyGuide(
            startX = width * SaxophoneKeyLayout.MAIN_COLUMN_X,
            startY = height * 0.03f,
            endX = width * SaxophoneKeyLayout.MAIN_COLUMN_X,
            endY = height * 0.97f,
            dashed = false,
        ),
        BodyGuide(
            startX = width * 0.08f,
            startY = height * SaxophoneKeyLayout.HAND_DIVIDER_Y,
            endX = width * 0.92f,
            endY = height * SaxophoneKeyLayout.HAND_DIVIDER_Y,
            dashed = true,
        ),
    )
}
