package io.github.kimyeongbean.iter.app

import androidx.compose.runtime.mutableStateMapOf
import io.github.kimyeongbean.iter.art.DayArtData
import java.time.LocalDate

/**
 * Drawing data per date, computed once and shared by the grass thumbnail and the big day art,
 * so the screen move and the draw-in only draw.
 */
internal class DayArtCache {
    /** Per date: the inputs the data was computed from, and the data. */
    private val loaded = mutableStateMapOf<LocalDate, Pair<Any, DayArtData>>()

    /**
     * Drawing data of [date], or null until the first [load] finished. While a newer [load] runs it
     * keeps the previous data, so cells don't go blank when records or colours change.
     * Reading it is observed by Compose.
     */
    operator fun get(date: LocalDate): DayArtData? = loaded[date]?.second

    /** Computes [date]'s data with [compute] unless it was already computed for equal [inputs]. */
    suspend fun load(
        date: LocalDate,
        inputs: Any,
        compute: suspend () -> DayArtData,
    ) {
        if (loaded[date]?.first != inputs) loaded[date] = inputs to compute()
    }
}
