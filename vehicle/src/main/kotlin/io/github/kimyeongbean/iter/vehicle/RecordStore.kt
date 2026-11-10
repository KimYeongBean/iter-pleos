package io.github.kimyeongbean.iter.vehicle

import android.content.Context
import androidx.room.Room
import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
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
 * A day record is every drive session whose start falls on that date in the time zone [zone] gives
 * when the session is saved, so a time zone change after opening applies to new sessions (#65).
 */
public class RecordStore private constructor(
    private val database: RecordDatabase,
    private val zone: () -> ZoneId,
) : Closeable {
    private val dao = database.recordDao()

    /** Saves [session]. Saving a session with the same start time and source again replaces it. */
    public suspend fun save(session: DriveSession) {
        dao.replaceSession(
            sessionEntity(session.startEpochMs, session.endEpochMs, session.source),
            session.samples.map { it.toEntity() },
        )
    }

    /**
     * Adds [newSamples] to the unfinished session that starts at [startEpochMs] from [source],
     * creating it if needed. Writes only the new samples, so saving often while driving stays cheap.
     */
    internal suspend fun appendInProgress(
        startEpochMs: Long,
        source: DriveSessionSource,
        newSamples: List<Sample>,
    ) = dao.appendSamples(sessionEntity(startEpochMs, endEpochMs = null, source), newSamples.map { it.toEntity() })

    /** Deletes every drive session, real records and fake drive data alike (debug menu, #85). */
    public suspend fun deleteAll(): Unit = dao.deleteAllSessions()

    /** Ends sessions left unfinished (app killed mid-drive) at their last saved sample. */
    internal suspend fun closeUnfinishedSessions() = dao.closeUnfinishedSessions()

    private fun sessionEntity(
        startEpochMs: Long,
        endEpochMs: Long?,
        source: DriveSessionSource,
    ) = DriveSessionEntity(
        startEpochMs = startEpochMs,
        endEpochMs = endEpochMs,
        source = source,
        dayEpochDay =
        Instant
            .ofEpochMilli(startEpochMs)
            .atZone(zone())
            .toLocalDate()
            .toEpochDay(),
    )

    /** Dates that have at least one drive session, oldest first. Emits again after every change. */
    public fun recordedDates(): Flow<List<LocalDate>> = dao.recordedDays().map { days -> days.map(LocalDate::ofEpochDay) }

    /** The day record of [date]: its drive sessions in start order. Empty if nothing was recorded. */
    public suspend fun dayRecord(date: LocalDate): List<DriveSession> = dao.sessionsOn(date.toEpochDay()).map { it.toDriveSession() }

    override fun close(): Unit = database.close()

    public companion object {
        private const val DATABASE_NAME = "records.db"

        /** Opens the store on the device. [zone], read at each save, decides which date a session belongs to. */
        public fun open(
            context: Context,
            zone: () -> ZoneId = ZoneId::systemDefault,
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
