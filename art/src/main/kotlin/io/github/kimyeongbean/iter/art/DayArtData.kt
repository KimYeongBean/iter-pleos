package io.github.kimyeongbean.iter.art

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.inDriveOrder
import io.github.kimyeongbean.iter.model.isOn

/**
 * Drawing data of a day art (GLOSSARY: 그림 데이터): shapes only, no drawing.
 * Radial: the day's drive time goes clockwise round the circle from 12 o'clock, speed sets each
 * shape's outer radius and speed colour. Radii are fractions of the drawing's radius (0 to 1).
 * Speed bars stay between [SPEED_BAR_INNER_RADIUS] and [SPEED_BAR_MAX_RADIUS]; the space inside
 * and outside them is free for patterns.
 * Deterministic: the same day record and base colour always give the same data.
 */
public data class DayArtData(
    /** In drive time order. */
    val shapes: List<DayArtShape>,
) {
    public companion object {
        /** Radius where every speed bar starts. */
        public const val SPEED_BAR_INNER_RADIUS: Double = 0.25

        /** Radius a speed bar reaches at [SPEED_COLOR_MAX_KMH] and above. */
        public const val SPEED_BAR_MAX_RADIUS: Double = 0.85

        /** Share of the bar length kept at standstill, so stops stay visible. */
        private const val MIN_BAR_INTENSITY = 0.05

        /** A long day is grouped into this many shapes at most, so a grass cell stays cheap to draw. */
        private const val MAX_SHAPES = 120

        /**
         * Drawing data of [dayRecord] (its drive sessions; order does not matter) in the user's
         * [speedColorBaseArgb]. Only time, speed and vehicle signals are used, never location.
         */
        public fun of(
            dayRecord: List<DriveSession>,
            speedColorBaseArgb: Int,
        ): DayArtData {
            val samples = dayRecord.inDriveOrder().flatMap { it.samples }
            val total = samples.size
            val shapeCount = minOf(total, MAX_SHAPES)
            // Shape k covers samples [k * total / shapeCount, (k + 1) * total / shapeCount).
            val shapes =
                List(shapeCount) { k ->
                    val from = (k.toLong() * total / shapeCount).toInt()
                    val to = ((k + 1L) * total / shapeCount).toInt()
                    shape(samples.subList(from, to), startIndex = from, total = total, speedColorBaseArgb = speedColorBaseArgb)
                }
            return DayArtData(shapes)
        }

        private fun shape(
            samples: List<Sample>,
            startIndex: Int,
            total: Int,
            speedColorBaseArgb: Int,
        ): DayArtShape {
            val speedKmh = samples.sumOf { it.speedKmh } / samples.size
            val intensity = SpeedColor.intensity(speedKmh).coerceAtLeast(MIN_BAR_INTENSITY)
            val timePosition = startIndex.toDouble() / total
            return DayArtShape(
                timePosition = timePosition,
                startAngleDeg = timePosition * FULL_CIRCLE_DEG,
                sweepAngleDeg = samples.size.toDouble() / total * FULL_CIRCLE_DEG,
                innerRadius = SPEED_BAR_INNER_RADIUS,
                outerRadius = SPEED_BAR_INNER_RADIUS + (SPEED_BAR_MAX_RADIUS - SPEED_BAR_INNER_RADIUS) * intensity,
                colorArgb = SpeedColor.argb(speedColorBaseArgb, speedKmh),
                patterns = patternsOf(samples),
            )
        }

        /** A pattern shows when any sample has the signal on. A missing (null) signal never shows one. */
        private fun patternsOf(samples: List<Sample>): Set<DayArtPattern> = buildSet {
            if (samples.any { it.turnSignal.isOn }) add(DayArtPattern.TURN_SIGNAL)
            if (samples.any { it.wiperOn == true }) add(DayArtPattern.WIPER)
            if (samples.any { it.lowBeamOn == true }) add(DayArtPattern.LOW_BEAM)
        }

        private const val FULL_CIRCLE_DEG = 360.0
    }
}

/** One ring segment of a day art: a stretch of the day's drive time. */
public data class DayArtShape(
    /** Where the stretch starts in the day's drive time, 0 to 1. Drives the draw-in animation. */
    val timePosition: Double,
    /** Degrees clockwise from 12 o'clock. */
    val startAngleDeg: Double,
    val sweepAngleDeg: Double,
    val innerRadius: Double,
    val outerRadius: Double,
    /** Speed colour of the stretch's mean speed, ARGB. */
    val colorArgb: Int,
    /** Vehicle signals that were on during the stretch. */
    val patterns: Set<DayArtPattern>,
)

/** Pattern laid over a shape for a vehicle signal that was on (GLOSSARY: 무늬). */
public enum class DayArtPattern {
    /** Turn signal left, right or hazard (GLOSSARY: 깜빡이). */
    TURN_SIGNAL,
    WIPER,

    /** Low beam headlights. */
    LOW_BEAM,
}
