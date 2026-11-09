package io.github.kimyeongbean.iter.app

import android.app.Application
import io.github.kimyeongbean.iter.vehicle.RecordStore

class IterApplication : Application() {
    /** One record store per process, so every screen reads the same database. */
    val recordStore: RecordStore by lazy { RecordStore.open(this) }
}
