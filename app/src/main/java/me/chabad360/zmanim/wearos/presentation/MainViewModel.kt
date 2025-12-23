package me.chabad360.zmanim.wearos.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.kdroidfilter.kosherkotlin.Zman
import io.github.kdroidfilter.kosherkotlin.ZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.metadata.ZmanDefinition
import io.github.kdroidfilter.kosherkotlin.metadata.ZmanType
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.time.ExperimentalTime

class MainViewModel(private val appContext: Context) : ViewModel() {

    private val fused: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun refreshLocation() {
        fun fallbackGeo(): GeoLocation = GeoLocation("Jerusalem", 31.778, 35.235, 0.0, TimeZone.of("Asia/Jerusalem"))
        updateForGeo(fallbackGeo())

        _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
        val tz = TimeZone.currentSystemDefault()
        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    viewModelScope.launch(Dispatchers.Default) {
                        updateForGeo(GeoLocation("${loc.latitude},${loc.longitude}", loc.latitude, loc.longitude, loc.altitude, tz))
                    }
                } else {
                    Log.w("GPS", "fresh fix")
                    // Try a fresh fix
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                        .addOnSuccessListener { fresh ->
                            viewModelScope.launch(Dispatchers.Default) {
                                val geo = fresh?.let { GeoLocation("${it.latitude},${it.longitude}", it.latitude, it.longitude, it.altitude, tz) }
                                if (geo != null) {
                                    updateForGeo(geo)
                                } else {
                                    Log.w("GPS",  "unable to get location $geo", )
                                    updateForGeo(fallbackGeo(), error = "Unable to acquire location")
                                }
                            }
                        }
                        .addOnFailureListener { err ->
                            viewModelScope.launch(Dispatchers.Default) {
                                Log.w("GPS",  err)
                                updateForGeo(fallbackGeo(), error = err.message ?: "Unable to refresh location")
                            }
                        }
                }
            }
            .addOnFailureListener { err ->
                viewModelScope.launch(Dispatchers.Default) {
                    Log.w("GPS",  err)
                    updateForGeo(fallbackGeo(), error = err.message ?: "Unable to refresh location")
                }
            }
    }

    private fun updateForGeo(geo: GeoLocation, error: String? = null) {
        val zmanim = computeZmanim(geo)
        _uiState.value = _uiState.value.copy(
            isRefreshing = false,
            location = geo.locationName,
            zmanim = zmanim,
            timeZone = geo.timeZone,
            disabledIds = _uiState.value.disabledIds,
            isStale = false,
            error = error
        )
    }

    fun toggleEdit() {
        _uiState.value = _uiState.value.copy(showingEdit = !_uiState.value.showingEdit)
    }

    fun toggleZman(id: ZmanType) {
        _uiState.value = _uiState.value.copy(
            disabledIds = _uiState.value.disabledIds.toMutableSet().apply {
                if (contains(id)) remove(id) else add(id)
            }
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun computeZmanim(geo: GeoLocation): List<Zman.DateBased> {
        val zmanimCal = ZmanimCalendar(geo)
        return zmanimCal.allZmanim.sortedBy { it.value }
    }
}

data class UiState(
    val location: String? = "Jerusalem",
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: String? = null,
    val zmanim: List<Zman.DateBased> = emptyList(),
    val disabledIds: Set<ZmanType> = emptySet(),
    val timeZone: TimeZone = TimeZone.of("UTC"),
    val showingEdit: Boolean = false
)