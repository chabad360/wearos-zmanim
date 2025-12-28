package me.chabad360.zmanim.wearos.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import me.chabad360.zmanim.wearos.presentation.data.DataStoreManager
import me.chabad360.zmanim.wearos.presentation.data.PersistentState
import me.chabad360.zmanim.wearos.presentation.data.SerializableLocation
import me.chabad360.zmanim.wearos.tile.MainTileService
import me.chabad360.zmanim.wearos.util.LocationHelper
import me.chabad360.zmanim.wearos.util.ZmanimCalculator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Background worker that periodically updates zmanim data.
 * Runs every hour to check if:
 * 1. Data is stale (from a different day) - recalculates zmanim
 * 2. Location has changed significantly (>100m) - updates location and zmanim
 */
class ZmanimUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val dataStore = DataStoreManager(applicationContext)
    private val fused: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)

    @OptIn(ExperimentalTime::class)
    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "ZmanimUpdateWorker started")

            val currentState = dataStore.stateFlow.first()

            if (currentState == null) {
                Log.d(TAG, "No cached data, skipping update")
                return@withContext Result.success()
            }

            val isStale = dataStore.isStateStale(currentState)

            // Check if we have location permissions
            val hasLocationPermission = (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) &&
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

            var shouldUpdate = false
            var newLocation: SerializableLocation? = null

            // Try to get current location if we have permission
            if (hasLocationPermission) {
                try {
                    val location = fused.getCurrentLocation(Priority.PRIORITY_LOW_POWER,
                        null
                    ).await()
                    if (location != null) {
                        val distance = LocationHelper.calculateDistance(
                            currentState.location.latitude,
                            currentState.location.longitude,
                            location.latitude,
                            location.longitude
                        )

                        Log.d(TAG, "Location distance: ${distance}m")

                        if (distance > 100) {
                            // Location changed significantly
                            newLocation = SerializableLocation(
                                name = resolvePlaceName(location.latitude, location.longitude)
                                    ?: "${location.latitude},${location.longitude}",
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitude = location.altitude,
                                timezoneId = TimeZone.currentSystemDefault().id
                            )
                            shouldUpdate = true
                            Log.d(TAG, "Location changed significantly, will update")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get location", e)
                    // Continue with stale data check even if location fails
                }
            }

            // If data is stale or location changed, update
            if (isStale || shouldUpdate) {
                val locationToUse = newLocation ?: currentState.location

                Log.d(TAG, "Updating zmanim (stale: $isStale, locationChanged: ${newLocation != null})")

                val geo = GeoLocation(
                    locationToUse.name,
                    locationToUse.latitude,
                    locationToUse.longitude,
                    locationToUse.altitude,
                    TimeZone.of(locationToUse.timezoneId)
                )

                val cal = ComplexZmanimCalendar(geo)
                val zmanDisplays = ZmanimCalculator.computeZmanim(cal, applicationContext)

                val newState = PersistentState(
                    lastComputedMillis = Clock.System.now().toEpochMilliseconds(),
                    location = locationToUse,
                    zmanim = zmanDisplays,
                    disabledIds = currentState.disabledIds
                )

                dataStore.saveState(newState)

                // Request tile update
                MainTileService.requestUpdate(applicationContext)

                Log.d(TAG, "Zmanim updated successfully")
            } else {
                Log.d(TAG, "No update needed (data is current)")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update zmanim", e)
            Result.retry()
        }
    }

    /**
     * Resolve place name from coordinates using Geocoder
     */
    private suspend fun resolvePlaceName(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(applicationContext, java.util.Locale.getDefault())
                val results = geocoder.getFromLocation(lat, lon, 1)
                val addr = results?.firstOrNull()
                addr?.locality ?: addr?.subAdminArea
            } catch (e: Exception) {
                Log.w(TAG, "Geocoding failed", e)
                null
            }
        }

    companion object {
        private const val TAG = "ZmanimUpdateWorker"
        const val WORK_NAME = "zmanim_periodic_update"
    }
}

