package io.github.kimyeongbean.iter.app

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** [date] as month, day and weekday in the locale's order, without the year ("11월 10일 화요일"). */
internal fun monthDayWeekdayText(date: LocalDate): String {
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMdEEEE")
    return date.format(DateTimeFormatter.ofPattern(pattern))
}
