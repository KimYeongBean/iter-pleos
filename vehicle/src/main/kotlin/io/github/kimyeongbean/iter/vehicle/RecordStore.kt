package io.github.kimyeongbean.iter.vehicle

import android.content.Context
import androidx.room.Room
import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.Sample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.Closeable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * On-device store of drive sessions (GLOSSARY: 기록 저장소).
 * Real records and fake drive data are saved and read the same way (ADR 0001).
 * A day record is every drive session whose start falls on that date in [zone].
 */
class RecordStore private constructor(
    private val database: RecordDatabase,
    private val zone: ZoneId,
) : Closeable {
    private val dao = database.recordDao()

    /** Saves [session]. Saving a session with the same start time and source again replaces it. */
    suspend fun save(session: DriveSession) {
        val day = Instant.ofEpochMilli(session.startEpochMs).atZone(zone).toLocalDate()
        dao.replaceSession(
            DriveSessionEntity(
                startEpochMs = session.startEpochMs,
                endEpochMs = session.endEpochMs,
                source = session.source,
                dayEpochDay = day.toEpochDay(),
            ),
            session.samples.map { it.toEntity() },
        )
    }

    /** Dates that have at least one drive session, oldest first. Emits again after every change. */
    fun recordedDates(): Flow<List<LocalDate>> = dao.recordedDays().map { days -> days.map(LocalDate::ofEpochDay) }

    /** The day record of [date]: its drive sessions in start order. Empty if nothing was recorded. */
    suspend fun dayRecord(date: LocalDate): List<DriveSession> = dao.sessionsOn(date.toEpochDay()).map { it.toDriveSession() }

    override fun close() = database.close()

    companion object {
        private const val DATABASE_NAME = "records.db"

        /** Opens the store on the device. [zone] decides which date a session belongs to. */
        fun open(
            context: Context,
            zone: ZoneId = ZoneId.systemDefault(),
        ): RecordStore {
            val database =
                Room.databaseBuilder(context.applicationContext, RecordDatabase::class.java, DATABASE_NAME).build()
            return RecordStore(database, zone)
        }
    }
}

private fun Sample.toEntity() = SampleEntity(
    sessionId = 0,
    timeEpochMs = timeEpochMs,
    speedKmh = speedKmh,
    turnSignal = turnSignal,
    wiperOn = wiperOn,
    lowBeamOn = lowBeamOn,
    steeringAngleDeg = steeringAngleDeg,
    brakePedalPressed = brakePedalPressed,
    latitudeDeg = location?.latitudeDeg,
    longitudeDeg = location?.longitudeDeg,
)

private fun DriveSessionWithSamples.toDriveSession() = DriveSession(
    startEpochMs = session.startEpochMs,
    endEpochMs = session.endEpochMs,
    source = session.source,
    samples =
    samples.sortedBy { it.timeEpochMs }.map {
        Sample(
            timeEpochMs = it.timeEpochMs,
            speedKmh = it.speedKmh,
            turnSignal = it.turnSignal,
            wiperOn = it.wiperOn,
            lowBeamOn = it.lowBeamOn,
            steeringAngleDeg = it.steeringAngleDeg,
            brakePedalPressed = it.brakePedalPressed,
            location =
            if (it.latitudeDeg != null && it.longitudeDeg != null) {
                GeoPoint(it.latitudeDeg, it.longitudeDeg)
            } else {
                null
            },
        )
    },
)
