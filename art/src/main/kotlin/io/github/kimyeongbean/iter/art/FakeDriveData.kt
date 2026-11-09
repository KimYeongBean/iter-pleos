package io.github.kimyeongbean.iter.art

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.TurnSignal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates fake drive data (GLOSSARY: 가짜 주행 데이터): complete drive sessions over several dates.
 * Deterministic: the same arguments always give the same sessions.
 */
object FakeDriveData {
    private const val RECORDED_DAY_PERCENT = 70
    private const val RAINY_DAY_PERCENT = 20
    private const val LOW_BEAM_FROM_HOUR = 18
    private const val MIN_DURATION_S = 8 * 60
    private const val EXTRA_DURATION_S = 12 * 60
    private const val SLOW_DOWN_BEFORE_END_S = 45
    private const val METRES_PER_DEGREE = 111_320.0

    /** Generic city-centre start point; not anyone's real location. */
    private val START_POINT = GeoPoint(latitudeDeg = 37.5665, longitudeDeg = 126.9780)

    /**
     * Normal drives (no harsh events, every signal present) on about 70% of the [dayCount] days
     * ending at [lastDate]. Each recorded day has a morning drive and sometimes an evening drive.
     * Session start times are local times in [zone].
     */
    fun normal(
        lastDate: LocalDate,
        dayCount: Int,
        zone: ZoneId,
        seed: Long = 0L,
    ): List<DriveSession> {
        require(dayCount > 0) { "dayCount must be positive: $dayCount" }
        val random = Random(seed)
        val firstDate = lastDate.minusDays(dayCount - 1L)
        return (0 until dayCount).flatMap { offset ->
            if (random.nextInt(100) >= RECORDED_DAY_PERCENT) return@flatMap emptyList()
            val date = firstDate.plusDays(offset.toLong())
            val rainy = random.nextInt(100) < RAINY_DAY_PERCENT
            val startTimes =
                buildList {
                    add(LocalTime.of(7 + random.nextInt(3), random.nextInt(60)))
                    if (random.nextBoolean()) add(LocalTime.of(17 + random.nextInt(4), random.nextInt(60)))
                }
            startTimes.map { time ->
                normalSession(
                    startEpochMs = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                    durationS = MIN_DURATION_S + random.nextInt(EXTRA_DURATION_S),
                    wiperOn = rainy,
                    lowBeamOn = time.hour >= LOW_BEAM_FROM_HOUR,
                    random = random,
                )
            }
        }
    }

    private fun normalSession(
        startEpochMs: Long,
        durationS: Int,
        wiperOn: Boolean,
        lowBeamOn: Boolean,
        random: Random,
    ): DriveSession {
        var speedKmh = 0.0
        var targetKmh = 0.0
        var headingDeg = random.nextDouble(360.0)
        var turnLeftS = 0
        var steeringAngleDeg = 0.0
        var latitudeDeg = START_POINT.latitudeDeg + random.nextDouble(-0.02, 0.02)
        var longitudeDeg = START_POINT.longitudeDeg + random.nextDouble(-0.02, 0.02)

        val samples =
            List(durationS) { second ->
                if (second >= durationS - SLOW_DOWN_BEFORE_END_S) {
                    targetKmh = 0.0
                } else if (second % 45 == 0) {
                    targetKmh = if (random.nextInt(100) < 20) 0.0 else random.nextDouble(30.0, 100.0)
                }
                // Gentle acceleration and braking only: this is the "normal" kind.
                val stepKmh = (targetKmh - speedKmh).coerceIn(-3.0, 2.0)
                speedKmh = (speedKmh + stepKmh).coerceAtLeast(0.0)

                if (turnLeftS == 0 && speedKmh in 10.0..50.0 && random.nextInt(100) < 2) {
                    turnLeftS = 5 + random.nextInt(6)
                    steeringAngleDeg = (if (random.nextBoolean()) 1 else -1) * random.nextDouble(10.0, 40.0)
                }
                val turning = turnLeftS > 0
                if (turning) {
                    turnLeftS--
                    headingDeg += steeringAngleDeg * 0.2
                }

                val metres = speedKmh / 3.6
                val headingRad = Math.toRadians(headingDeg)
                latitudeDeg += metres * cos(headingRad) / METRES_PER_DEGREE
                longitudeDeg += metres * sin(headingRad) / (METRES_PER_DEGREE * cos(Math.toRadians(latitudeDeg)))

                Sample(
                    timeEpochMs = startEpochMs + second * SAMPLE_INTERVAL_MS,
                    speedKmh = speedKmh,
                    turnSignal =
                    when {
                        !turning -> TurnSignal.OFF
                        steeringAngleDeg < 0 -> TurnSignal.LEFT
                        else -> TurnSignal.RIGHT
                    },
                    wiperOn = wiperOn,
                    lowBeamOn = lowBeamOn,
                    steeringAngleDeg = if (turning) steeringAngleDeg else 0.0,
                    brakePedalPressed = stepKmh < -0.5,
                    location = GeoPoint(latitudeDeg, longitudeDeg),
                )
            }
        return DriveSession(
            startEpochMs = startEpochMs,
            endEpochMs = samples.last().timeEpochMs,
            source = DriveSessionSource.FAKE_DRIVE_DATA,
            samples = samples,
        )
    }
}
