package io.github.kimyeongbean.iter.app

import androidx.compose.runtime.mutableStateMapOf
import io.github.kimyeongbean.iter.art.DayArtData
import java.time.LocalDate

/**
 * Drawing data per date, computed once and shared by the grass thumbnail and the big day art,
 * so the screen move and the draw-in only draw. Make a new cache when records or colours change.
 */
internal class DayArtCache(
    private val compute: suspend (LocalDate) -> DayArtData,
) {
    private val loaded = mutableStateMapOf<LocalDate, DayArtData>()

    /** Drawing data of [date], or null until [load] finished. Reading it is observed by Compose. */
    operator fun get(date: LocalDate): DayArtData? = loaded[date]

    suspend fun load(date: LocalDate) {
        if (date !in loaded) loaded[date] = compute(date)
    }
}
