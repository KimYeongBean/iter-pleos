package io.github.kimyeongbean.iter.vehicle

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.TurnSignal
import kotlinx.coroutines.flow.Flow

// Storage layout of the record store. Internal: other modules only see RecordStore.

@Entity(
    tableName = "drive_session",
    indices = [Index(value = ["startEpochMs", "source"], unique = true), Index("dayEpochDay")],
)
internal data class DriveSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochMs: Long,
    val endEpochMs: Long?,
    val source: DriveSessionSource,
    /** Device-local date of startEpochMs as LocalDate.toEpochDay(). */
    val dayEpochDay: Long,
)

@Entity(
    tableName = "sample",
    foreignKeys = [
        ForeignKey(
            entity = DriveSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
internal data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timeEpochMs: Long,
    val speedKmh: Double,
    val turnSignal: TurnSignal?,
    val wiperOn: Boolean?,
    val lowBeamOn: Boolean?,
    val steeringAngleDeg: Double?,
    val brakePedalPressed: Boolean?,
    val latitudeDeg: Double?,
    val longitudeDeg: Double?,
)

internal data class DriveSessionWithSamples(
    @Embedded val session: DriveSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val samples: List<SampleEntity>,
)

@Dao
internal abstract class RecordDao {
    @Query("DELETE FROM drive_session WHERE startEpochMs = :startEpochMs AND source = :source")
    abstract suspend fun deleteSession(
        startEpochMs: Long,
        source: DriveSessionSource,
    )

    @Insert
    abstract suspend fun insertSession(session: DriveSessionEntity): Long

    @Insert
    abstract suspend fun insertSamples(samples: List<SampleEntity>)

    /** Replaces a session with the same start time and source, so saving again updates it. */
    @Transaction
    open suspend fun replaceSession(
        session: DriveSessionEntity,
        samples: List<SampleEntity>,
    ) {
        deleteSession(session.startEpochMs, session.source)
        val id = insertSession(session)
        insertSamples(samples.map { it.copy(sessionId = id) })
    }

    @Query("SELECT id FROM drive_session WHERE startEpochMs = :startEpochMs AND source = :source")
    abstract suspend fun sessionId(
        startEpochMs: Long,
        source: DriveSessionSource,
    ): Long?

    /** Adds [samples] to the session with the same start time and source, creating it if needed. */
    @Transaction
    open suspend fun appendSamples(
        session: DriveSessionEntity,
        samples: List<SampleEntity>,
    ) {
        val id = sessionId(session.startEpochMs, session.source) ?: insertSession(session)
        insertSamples(samples.map { it.copy(sessionId = id) })
    }

    /** Ends every unfinished session at its last sample (its start if it has none). */
    @Query(
        """
        UPDATE drive_session
        SET endEpochMs = COALESCE(
            (SELECT MAX(timeEpochMs) FROM sample WHERE sample.sessionId = drive_session.id),
            startEpochMs
        )
        WHERE endEpochMs IS NULL
        """,
    )
    abstract suspend fun closeUnfinishedSessions()

    @Query("SELECT DISTINCT dayEpochDay FROM drive_session ORDER BY dayEpochDay")
    abstract fun recordedDays(): Flow<List<Long>>

    @Transaction
    @Query("SELECT * FROM drive_session WHERE dayEpochDay = :dayEpochDay ORDER BY startEpochMs")
    abstract suspend fun sessionsOn(dayEpochDay: Long): List<DriveSessionWithSamples>
}

@Database(entities = [DriveSessionEntity::class, SampleEntity::class], version = 1, exportSchema = false)
internal abstract class RecordDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
}
