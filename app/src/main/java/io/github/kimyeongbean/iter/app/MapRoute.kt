package io.github.kimyeongbean.iter.app

import io.github.kimyeongbean.iter.art.SpeedColor
import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.inDriveOrder
import io.github.kimyeongbean.iter.model.isOn
import kotlin.math.cos

/**
 * Map graph route (GLOSSARY: 맵 그래프) of a day record, ready to draw: no map, only the line.
 * Coordinates fit the unit square (0 to 1) with north up, keeping the route's shape, centred.
 * No strokes means no location was recorded ("경로 정보 없음").
 */
internal data class MapRoute(
    /** Continuous stretches of located samples, in drive time order. */
    val strokes: List<List<RoutePoint>>,
)

internal data class RoutePoint(
    /** 0 at the west edge, 1 at the east edge. */
    val x: Float,
    /** 0 at the north edge, 1 at the south edge. */
    val y: Float,
    /** Speed colour of the sample, ARGB. */
    val colorArgb: Int,
    /** Where the point is among the day's located samples, 0 (first) to 1 (last). Drives the draw-in. */
    val timePosition: Float,
    /** Turn signal left, right or hazard at this sample; false when off or missing. */
    val turnSignalOn: Boolean,
)

/** The [dayRecord]'s route from sample locations, coloured with [speedColorBaseArgb]. */
internal fun mapRoute(
    dayRecord: List<DriveSession>,
    speedColorBaseArgb: Int,
): MapRoute {
    // Each session, and each gap in location, starts a new stroke.
    val located: List<List<Sample>> =
        dayRecord
            .inDriveOrder()
            .flatMap { session ->
                session.samples
                    .fold(mutableListOf(mutableListOf<Sample>())) { strokes, sample ->
                        if (sample.location != null) {
                            strokes.last().add(sample)
                        } else if (strokes.last().isNotEmpty()) {
                            strokes.add(mutableListOf())
                        }
                        strokes
                    }.filter { it.isNotEmpty() }
            }
    val all = located.flatten()
    if (all.isEmpty()) return MapRoute(emptyList())

    val lats = all.map { it.location!!.latitudeDeg }
    val lons = all.map { it.location!!.longitudeDeg }
    val north = lats.max()
    val south = lats.min()
    val west = lons.min()
    // Longitude degrees get shorter away from the equator.
    val lonScale = cos(Math.toRadians((north + south) / 2))
    val width = (lons.max() - west) * lonScale
    val height = north - south
    val extent = maxOf(width, height)
    // A route with no extent (one place) sits in the middle.
    val scale = if (extent > 0) 1 / extent else 0.0
    val left = (1 - width * scale) / 2
    val top = (1 - height * scale) / 2
    val lastIndex = (all.size - 1).coerceAtLeast(1).toFloat()

    var index = 0
    val strokes =
        located.map { stroke ->
            stroke.map { sample ->
                val location = sample.location!!
                RoutePoint(
                    x = (left + (location.longitudeDeg - west) * lonScale * scale).toFloat(),
                    y = (top + (north - location.latitudeDeg) * scale).toFloat(),
                    colorArgb = SpeedColor.argb(speedColorBaseArgb, sample.speedKmh),
                    timePosition = index++ / lastIndex,
                    turnSignalOn = sample.turnSignal.isOn,
                )
            }
        }
    return MapRoute(strokes)
}

/** How much of the segment [from] → [to] the draw-in at [progress] (0 to 1, drive time) has drawn, 0 to 1. */
internal fun segmentDrawnFraction(
    from: RoutePoint,
    to: RoutePoint,
    progress: Float,
): Float {
    val span = to.timePosition - from.timePosition
    if (span <= 0f) return if (progress >= to.timePosition) 1f else 0f
    return ((progress - from.timePosition) / span).coerceIn(0f, 1f)
}
