package io.github.kimyeongbean.iter.app

import io.github.kimyeongbean.iter.model.DriveSession
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The one text line about a day record: how many drive sessions, from the first start to the last
 * end, to the minute. Counts and times only; no distance or speed figures (spec #68).
 */
internal data class DriveSummary(
    val sessionCount: Int,
    val firstStart: LocalTime,
    val lastEnd: LocalTime,
)

/** Clock time to the minute, as the summary and the route's start and end show it. */
internal val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The first start to the last end, as shown: "08:12 – 18:47". */
internal val DriveSummary.timeSpanText: String
    get() = "${firstStart.format(TimeFormat)} – ${lastEnd.format(TimeFormat)}"

/**
 * Summary of [dayRecord] with times in [zone], or null for a day with no drive session.
 * A session still in progress ends at its last sample, or at its start with no sample yet.
 */
internal fun driveSummary(
    dayRecord: List<DriveSession>,
    zone: ZoneId,
): DriveSummary? {
    if (dayRecord.isEmpty()) return null

    fun localTime(epochMs: Long) = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
    val firstStartEpochMs = dayRecord.minOf { it.startEpochMs }
    val lastEndEpochMs = dayRecord.maxOf { it.endEpochMs ?: it.samples.lastOrNull()?.timeEpochMs ?: it.startEpochMs }
    return DriveSummary(dayRecord.size, localTime(firstStartEpochMs), localTime(lastEndEpochMs))
}
