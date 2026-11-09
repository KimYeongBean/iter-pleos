package io.github.kimyeongbean.iter.app

import android.app.Application
import io.github.kimyeongbean.iter.vehicle.RecordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class IterApplication : Application() {
    /** One record store per process, so every screen reads the same database. */
    val recordStore: RecordStore by lazy { RecordStore.open(this) }

    /** Speed colour base and map graph background the user picked. */
    internal val speedColorSettingsStore by lazy { SpeedColorSettingsStore(this) }

    /** Work that must finish even if the activity is recreated (e.g. inserting fake drive data). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
