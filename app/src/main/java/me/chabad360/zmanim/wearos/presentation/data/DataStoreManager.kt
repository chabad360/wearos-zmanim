package me.chabad360.zmanim.wearos.presentation.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import android.util.Log
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * Serializer for PersistentState using kotlinx.serialization JSON
 */
object PersistentStateSerializer : Serializer<PersistentState?> {
    override val defaultValue: PersistentState? = null

    private val json = Json {
        ignoreUnknownKeys = true // Allow migration from future versions
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): PersistentState? {
        return try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) {
                null
            } else {
                val state = json.decodeFromString<PersistentState>(bytes.decodeToString())
                // Check version compatibility
                if (state.version > PersistentState.CURRENT_VERSION) {
                    Log.w("DataStore", "State version ${state.version} is newer than current ${PersistentState.CURRENT_VERSION}, data may be incomplete")
                }
                state
            }
        } catch (e: SerializationException) {
            Log.e("DataStore", "Error deserializing state", e)
            null
        }
    }

    override suspend fun writeTo(t: PersistentState?, output: OutputStream) {
        if (t != null) {
            output.write(json.encodeToString(PersistentState.serializer(), t).encodeToByteArray())
        }
    }
}

/**
 * Extension to create the DataStore instance
 */
private val Context.persistentStateDataStore: DataStore<PersistentState?> by dataStore(
    fileName = "zmanim_state.json",
    serializer = PersistentStateSerializer
)

/**
 * Manages persistence of zmanim app state using DataStore.
 * Handles saving/loading location, computed zmanim, and user preferences.
 */
class DataStoreManager(context: Context) {

    private val dataStore = context.persistentStateDataStore

    /**
     * Flow of the current persistent state.
     * Emits null if no state has been saved yet.
     */
    val stateFlow: Flow<PersistentState?> = dataStore.data
        .catch { exception ->
            Log.e("DataStore", "Error reading state", exception)
            emit(null)
        }

    /**
     * Save the complete app state
     */
    suspend fun saveState(state: PersistentState) {
        dataStore.updateData { state }
    }

    /**
     * Update only the disabled IDs
     */
    suspend fun updateDisabledIds(disabledIds: Set<String>) {
        dataStore.updateData { current ->
            current?.copy(disabledIds = disabledIds) ?: run {
                Log.w("DataStore", "Cannot update disabledIds when no state exists")
                current
            }
        }
    }

    /**
     * Clear all persisted state
     */
    suspend fun clearState() {
        dataStore.updateData { null }
    }

    /**
     * Check if cached zmanim data is stale (from a different day)
     */
    @OptIn(ExperimentalTime::class)
    fun isStateStale(state: PersistentState?): Boolean {
        if (state == null) return true

        val now = Clock.System.now()
        val lastComputed = Instant.fromEpochMilliseconds(state.lastComputedMillis)
        val timezone = TimeZone.of(state.location.timezoneId)

        val nowDate = now.toLocalDateTime(timezone).date
        val lastDate = lastComputed.toLocalDateTime(timezone).date

        return nowDate != lastDate
    }

    /**
     * Flow that emits true if the current state is stale
     */
    val isStaleFlow: Flow<Boolean> = stateFlow.map { isStateStale(it) }
}

