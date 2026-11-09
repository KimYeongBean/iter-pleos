package io.github.kimyeongbean.iter.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One grass cell: a date and whether it has a day record. */
internal data class GrassCell(
    val date: LocalDate,
    val recorded: Boolean,
)

/**
 * Grass grid cells, Monday-first weeks, oldest week first.
 * Covers at least [minWeeks] weeks ending with the week of [today], and reaches back to the
 * oldest recorded date. Slots after [today] are null (no cell). Dates after [today] are ignored.
 */
internal fun grassCells(
    today: LocalDate,
    recordedDates: Collection<LocalDate>,
    minWeeks: Int = 5,
): List<GrassCell?> {
    val recorded = recordedDates.filterTo(HashSet()) { it <= today }
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val oldest = recorded.minOrNull() ?: today
    val firstWeek =
        minOf(
            oldest.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            thisWeek.minusWeeks(minWeeks - 1L),
        )
    val dayCount = (thisWeek.toEpochDay() - firstWeek.toEpochDay() + 7).toInt()
    return List(dayCount) { i ->
        val date = firstWeek.plusDays(i.toLong())
        if (date > today) null else GrassCell(date, date in recorded)
    }
}
