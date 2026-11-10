package io.github.kimyeongbean.iter.app

import io.github.kimyeongbean.iter.art.SpeedColor
import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.inDriveOrder
import io.github.kimyeongbean.iter.model.isOn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Distance between rain drops along a wiper stretch, in unit square lengths. */
private const val RAIN_DROP_SPACING = 0.04f

/** Golden ratio fraction: spreads drop fall offsets evenly without a visible pattern. */
private const val FALL_OFFSET_STEP = 0.618034f

/** Faster than this between two locations means one of them is wrong (#64, proposed value). */
private const val MAX_PLAUSIBLE_SPEED_KMH = 300.0

private const val EARTH_RADIUS_KM = 6371.0

private const val MS_PER_HOUR = 3_600_000.0

/**
 * Map graph route (GLOSSARY: 맵 그래프) of a day record, ready to draw: no map, only the line.
 * Coordinates fit the unit square (0 to 1) with north up, keeping the route's shape, centred.
 * No strokes means no location was recorded ("경로 정보 없음").
 */
internal data class MapRoute(
    /** Continuous stretches of located samples, in drive time order. */
    val strokes: List<List<RoutePoint>>,
    /** Rain effect drops along stretches with the wiper on, computed once so drawing only draws. */
    val rainDrops: List<RainDrop>,
)

internal data class RoutePoint(
    /** 0 at the west edge, 1 at the east edge. */
    val x: Float,
    /** 0 at the north edge, 1 at the south edge. */
    val y: Float,
    /** Speed colour of the sample, ARGB. */
    val colorArgb: Int,
    /** How fast the sample was, 0 at standstill to 1 at the speed colour's strongest speed. */
    val speedIntensity: Float,
    /** Where the point is among the day's located samples, 0 (first) to 1 (last). Drives the draw-in. */
    val timePosition: Float,
    /** Turn signal left, right or hazard at this sample; false when off or missing. */
    val turnSignalOn: Boolean,
    /** Wiper on at this sample; false when off or missing. */
    val wiperOn: Boolean,
    /** Low beam on at this sample; false when off or missing. */
    val lowBeamOn: Boolean,
)

/** One drop of the rain effect, anchored on the route. */
internal data class RainDrop(
    /** Anchor on the route, in the same unit square as [RoutePoint]. */
    val x: Float,
    val y: Float,
    /** Drive time position of the anchor, 0 to 1: the drop shows once the draw-in passes it. */
    val timePosition: Float,
    /** Where in its fall the drop is at phase 0, 0 to 1, so drops fall out of step. */
    val fallOffset: Float,
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
                    .withoutLocationJumps()
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
    if (all.isEmpty()) return MapRoute(emptyList(), emptyList())

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
                    speedIntensity = SpeedColor.intensity(sample.speedKmh).toFloat(),
                    timePosition = index++ / lastIndex,
                    turnSignalOn = sample.turnSignal.isOn,
                    wiperOn = sample.wiperOn == true,
                    lowBeamOn = sample.lowBeamOn == true,
                )
            }
        }
    return MapRoute(strokes, rainDrops(strokes))
}

/**
 * Leaves out located samples too far from every located neighbour to have been driven to in time
 * (an old fix when GPS starts, a single wrong fix). With fewer than three locations nothing is left
 * out: there is no telling which one is wrong. Only for drawing; saved samples stay as they are.
 * Two or more wrong fixes in a row are close to each other, so they stay.
 */
private fun List<Sample>.withoutLocationJumps(): List<Sample> {
    val located = filter { it.location != null }
    if (located.size < 3) return this
    val jumps =
        located.filterIndexed { i, sample ->
            listOfNotNull(located.getOrNull(i - 1), located.getOrNull(i + 1)).all { tooFastBetween(it, sample) }
        }
    return filterNot { sample -> jumps.any { it === sample } }
}

private fun tooFastBetween(
    a: Sample,
    b: Sample,
): Boolean {
    val from = a.location!!
    val to = b.location!!
    val lat = Math.toRadians((from.latitudeDeg + to.latitudeDeg) / 2)
    val northKm = Math.toRadians(to.latitudeDeg - from.latitudeDeg) * EARTH_RADIUS_KM
    val eastKm = Math.toRadians(to.longitudeDeg - from.longitudeDeg) * EARTH_RADIUS_KM * cos(lat)
    val hours = abs(b.timeEpochMs - a.timeEpochMs) / MS_PER_HOUR
    // Two fixes at the same time give no speed to judge by.
    if (hours == 0.0) return false
    return hypot(northKm, eastKm) > MAX_PLAUSIBLE_SPEED_KMH * hours
}

/**
 * Drops every [RAIN_DROP_SPACING] along segments touching a wiper sample (the same rule as the
 * turn signal band), each stretch of such segments starting with a drop.
 */
private fun rainDrops(strokes: List<List<RoutePoint>>): List<RainDrop> {
    val drops = mutableListOf<RainDrop>()
    strokes.forEach { stroke ->
        // Distance from the segment start to the next drop; null outside a wiper stretch.
        var next: Float? = null
        stroke.zipWithNext().forEach { (a, b) ->
            if (!a.wiperOn && !b.wiperOn) {
                next = null
                return@forEach
            }
            val length = hypot(b.x - a.x, b.y - a.y)
            var at = next ?: 0f
            while (at <= length) {
                val f = if (length > 0f) at / length else 0f
                drops +=
                    RainDrop(
                        x = a.x + (b.x - a.x) * f,
                        y = a.y + (b.y - a.y) * f,
                        timePosition = a.timePosition + (b.timePosition - a.timePosition) * f,
                        fallOffset = (drops.size * FALL_OFFSET_STEP) % 1f,
                    )
                at += RAIN_DROP_SPACING
            }
            next = at - length
        }
    }
    return drops
}

/** How far through its fall window [drop] is at [phase] (0 to 1, one fall cycle): 0 top, 1 bottom. */
internal fun rainFall(
    drop: RainDrop,
    phase: Float,
): Float = (phase + drop.fallOffset) % 1f

/** Drop opacity at [fall] (see [rainFall]): fades in at the top and out at the bottom. */
internal fun rainAlpha(fall: Float): Float = sin(PI * fall).toFloat().coerceAtLeast(0f)

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
