package io.github.kimyeongbean.iter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GrassCellTest {
    // 2026-11-11 is a Wednesday.
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `grid is whole Monday-first weeks with no cells after today`() {
        val cells = grassCells(today, emptyList(), minWeeks = 2)

        assertEquals(14, cells.size)
        assertEquals(GrassCell(LocalDate.of(2026, 11, 2), recorded = false), cells.first())
        assertEquals(GrassCell(today, recorded = false), cells[9])
        (10..13).forEach { assertNull(cells[it]) }
    }

    @Test
    fun `recorded days are filled and other days stay empty`() {
        val recorded = listOf(LocalDate.of(2026, 11, 3), today)

        val cells = grassCells(today, recorded, minWeeks = 2).filterNotNull()

        assertEquals(recorded, cells.filter { it.recorded }.map { it.date })
        assertEquals(8, cells.count { !it.recorded })
    }

    @Test
    fun `grid reaches back to the oldest recorded week`() {
        val cells = grassCells(today, listOf(LocalDate.of(2026, 10, 1)), minWeeks = 2)

        // 2026-10-01 is a Thursday; its week starts on Monday 2026-09-28.
        assertEquals(GrassCell(LocalDate.of(2026, 9, 28), recorded = false), cells.first())
        assertEquals(GrassCell(LocalDate.of(2026, 10, 1), recorded = true), cells[3])
    }
}
