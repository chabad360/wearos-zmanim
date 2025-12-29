package me.chabad360.zmanim.wearos.presentation

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.util.Log
import android.text.format.DateFormat
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.Zman
import io.github.kdroidfilter.kosherkotlin.metadata.UsesElevation
import io.github.kdroidfilter.kosherkotlin.metadata.ZmanAuthority
import io.github.kdroidfilter.kosherkotlin.metadata.ZmanDefinition
import io.github.kdroidfilter.kosherkotlin.metadata.ZmanType
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import me.chabad360.zmanim.wearos.presentation.data.DataStoreManager
import me.chabad360.zmanim.wearos.presentation.data.PersistentState
import me.chabad360.zmanim.wearos.presentation.data.SerializableLocation
import me.chabad360.zmanim.wearos.presentation.theme.ZmanimTheme
import me.chabad360.zmanim.wearos.tile.MainTileService
import me.chabad360.zmanim.wearos.util.LocationHelper
import me.chabad360.zmanim.wearos.util.ZmanimCalculator
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class MainViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(appContext) as T
    }
}

class MainViewModel(private val appContext: Context) : ViewModel() {

    private val fused: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val dataStore = DataStoreManager(appContext)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Track if cached data has been loaded
    private var cachedDataLoaded = false

    init {
        // Load cached state on startup
        viewModelScope.launch {
            val cachedState = dataStore.stateFlow.first()
            if (cachedState != null) {
                val isStale = dataStore.isStateStale(cachedState)
                restoreFromCache(cachedState, isStale)
            }
            cachedDataLoaded = true
        }
    }

    /**
     * Restore UI state from cached persistent state
     */
    private fun restoreFromCache(cached: PersistentState, isStale: Boolean) {
        val geo = GeoLocation(
            cached.location.name,
            cached.location.latitude,
            cached.location.longitude,
            cached.location.altitude,
            TimeZone.of(cached.location.timezoneId)
        )
        val cal = ComplexZmanimCalendar(geo)
        val zmanim = zmanimMap(cal)

        if (isStale) {
            // Data is from a different day, recalculate zmanim for today
            val zmanDisplays = ZmanimCalculator.computeZmanim(cal, appContext)
            _uiState.value = _uiState.value.copy(
                location = cached.location.name,
                zmanim = zmanim,
                zmanDisplays = zmanDisplays,  // Fresh zmanim for today
                timeZone = TimeZone.of(cached.location.timezoneId),
                disabledIds = cached.disabledIds,
                isStale = false,  // No longer stale after recalculation
                isRefreshing = false
            )
            // Persist the updated state with today's zmanim
            persistState(geo, zmanDisplays)
        } else {
            // Data is current, use cached zmanim
            _uiState.value = _uiState.value.copy(
                location = cached.location.name,
                zmanim = zmanim,
                zmanDisplays = cached.zmanim,  // Use cached zmanim from today
                timeZone = TimeZone.of(cached.location.timezoneId),
                disabledIds = cached.disabledIds,
                isStale = false,
                isRefreshing = false
            )
        }
    }

    /**
     * Save current state to persistent storage
     */
    @OptIn(ExperimentalTime::class)
    private fun persistState(geo: GeoLocation, zmanDisplays: List<ZmanDisplay>) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = PersistentState(
                lastComputedMillis = Clock.System.now().toEpochMilliseconds(),
                location = SerializableLocation(
                    name = geo.locationName ?: "Unknown",
                    latitude = geo.latitude,
                    longitude = geo.longitude,
                    altitude = geo.elevation,
                    timezoneId = geo.timeZone.id
                ),
                zmanim = zmanDisplays,  // ZmanDisplay is already serializable, use directly
                disabledIds = _uiState.value.disabledIds
            )
            dataStore.saveState(state)

            // Request tile update after persisting data
            MainTileService.requestUpdate(appContext)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun refreshLocation() {
        fun fallbackGeo(): GeoLocation = GeoLocation("Jerusalem", 31.778, 35.235, 0.0, TimeZone.of("Asia/Jerusalem"))

        // Only set refreshing to true, don't immediately overwrite cached data with fallback
        _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
        val tz = TimeZone.currentSystemDefault()
        fused.getCurrentLocation(Priority.PRIORITY_LOW_POWER, CancellationTokenSource().token)
            .addOnSuccessListener { fresh ->
                viewModelScope.launch(Dispatchers.Default) {
                    val geo = fresh?.let { buildGeo(it.latitude, it.longitude, it.altitude, tz) }
                    if (geo != null && shouldUpdateLocation(geo)) {
                        updateForGeo(geo)
                    } else if (geo != null) {
                        // Location unchanged
                        _uiState.value = _uiState.value.copy(isRefreshing = false)
                        Log.d("GPS", "Location unchanged, skipping update")
                    } else {
                        // Only fallback to Jerusalem if we have no cached location
                        if (_uiState.value.location == null) {
                            Log.w("GPS", "Unable to get location, falling back to Jerusalem")
                            updateForGeo(fallbackGeo(), error = "Unable to acquire location")
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isRefreshing = false,
                                error = "Unable to update location, using cached data"
                            )
                            Log.w("GPS", "Unable to get location, keeping cached data")
                        }
                    }
                }
            }
            .addOnFailureListener { err ->
                viewModelScope.launch(Dispatchers.Default) {
                    Log.w("GPS", err)
                    // Only fallback to Jerusalem if we have no cached location
                    if (_uiState.value.location == null) {
                        updateForGeo(fallbackGeo(), error = err.message ?: "Unable to refresh location, falling back to jerusalem")
                    } else {
                        // Keep existing cached location
                        _uiState.value = _uiState.value.copy(
                            isRefreshing = false,
                            error = "Unable to update location, using cached data"
                        )
                    }
                }
            }
    }

    /**
     * Check if location has changed significantly (>100m) to warrant recalculation
     */
    private fun shouldUpdateLocation(newGeo: GeoLocation): Boolean {
        val currentState = _uiState.value

        // Always update if we don't have a location yet
        if (currentState.location == null) {
            return true
        }

        // Get current location from state flow
        val cachedState = runCatching {
            kotlinx.coroutines.runBlocking {
                dataStore.stateFlow.first()
            }
        }.getOrNull() ?: return true

        val oldLat = cachedState.location.latitude
        val oldLon = cachedState.location.longitude

        val distance = LocationHelper.calculateDistance(oldLat, oldLon, newGeo.latitude, newGeo.longitude)

        Log.d("GPS", "Location distance: ${distance}m")

        // Only update if moved more than 100m
        return distance > 100
    }

    private fun updateForGeo(geo: GeoLocation, error: String? = null) {
        val cal = ComplexZmanimCalendar(geo)
        val zmanim = zmanimMap(cal)
        val zmanDisplays = ZmanimCalculator.computeZmanim(cal, appContext)
         _uiState.value = _uiState.value.copy(
             isRefreshing = false,
             location = geo.locationName,
             zmanim = zmanim,
             zmanDisplays = zmanDisplays,
             timeZone = geo.timeZone,
             disabledIds = _uiState.value.disabledIds,
             isStale = false,
             error = error
         )

         // Persist the new state
//         if (error == null) {
             persistState(geo, zmanDisplays)
//         }
    }

    @OptIn(ExperimentalTime::class)
    private fun zmanimMap(zmanimCal: ComplexZmanimCalendar): Map<String, () -> Zman.DateBased> {
        return mapOf(
            "tzais" to zmanimCal::tzais,
            "chatzos" to zmanimCal::chatzos,
            "sofZmanShmaGRA" to zmanimCal::sofZmanShmaGRA,
            "sofZmanShmaMGA" to zmanimCal::sofZmanShmaMGA,
            "tzais72" to zmanimCal::tzais72,
            "candleLighting" to zmanimCal::candleLighting,
            "sofZmanTfilaGRA" to zmanimCal::sofZmanTfilaGRA,
            "sofZmanTfilaMGA" to zmanimCal::sofZmanTfilaMGA,
            "minchaGedola" to zmanimCal::minchaGedola,
            "alosHashachar" to zmanimCal::alosHashachar,
            "alos72" to zmanimCal::alos72,
            "alos60" to zmanimCal::alos60,
            "alos72Zmanis" to zmanimCal::alos72Zmanis,
            "alos96" to zmanimCal::alos96,
            "alos90Zmanis" to zmanimCal::alos90Zmanis,
            "alos96Zmanis" to zmanimCal::alos96Zmanis,
            "alos90" to zmanimCal::alos90,
            "alos120" to zmanimCal::alos120,
            "alos120Zmanis" to zmanimCal::alos120Zmanis,
            "alos26Degrees" to zmanimCal::alos26Degrees,
            "alos18Degrees" to zmanimCal::alos18Degrees,
            "alos19Degrees" to zmanimCal::alos19Degrees,
            "alos19Point8Degrees" to zmanimCal::alos19Point8Degrees,
            "alos16Point1Degrees" to zmanimCal::alos16Point1Degrees,
            "misheyakir11Point5Degrees" to zmanimCal::misheyakir11Point5Degrees,
            "misheyakir11Degrees" to zmanimCal::misheyakir11Degrees,
            "misheyakir10Point2Degrees" to zmanimCal::misheyakir10Point2Degrees,
            "misheyakir7Point65Degrees" to zmanimCal::misheyakir7Point65Degrees,
            "misheyakir9Point5Degrees" to zmanimCal::misheyakir9Point5Degrees,
            "sofZmanShmaMGA19Point8Degrees" to zmanimCal::sofZmanShmaMGA19Point8Degrees,
            "sofZmanShmaMGA16Point1Degrees" to zmanimCal::sofZmanShmaMGA16Point1Degrees,
            "sofZmanShmaMGA18Degrees" to zmanimCal::sofZmanShmaMGA18Degrees,
            "sofZmanShmaMGA72Minutes" to zmanimCal::sofZmanShmaMGA72Minutes,
            "sofZmanShmaMGA72MinutesZmanis" to zmanimCal::sofZmanShmaMGA72MinutesZmanis,
            "sofZmanShmaMGA90Minutes" to zmanimCal::sofZmanShmaMGA90Minutes,
            "sofZmanShmaMGA90MinutesZmanis" to zmanimCal::sofZmanShmaMGA90MinutesZmanis,
            "sofZmanShmaMGA96Minutes" to zmanimCal::sofZmanShmaMGA96Minutes,
            "sofZmanShmaMGA96MinutesZmanis" to zmanimCal::sofZmanShmaMGA96MinutesZmanis,
            "sofZmanShma3HoursBeforeChatzos" to zmanimCal::sofZmanShma3HoursBeforeChatzos,
            "sofZmanShmaMGA120Minutes" to zmanimCal::sofZmanShmaMGA120Minutes,
            "sofZmanShmaAlos16Point1ToSunset" to zmanimCal::sofZmanShmaAlos16Point1ToSunset,
            "sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees" to zmanimCal::sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees,
            "sofZmanTfilaMGA19Point8Degrees" to zmanimCal::sofZmanTfilaMGA19Point8Degrees,
            "sofZmanTfilaMGA16Point1Degrees" to zmanimCal::sofZmanTfilaMGA16Point1Degrees,
            "sofZmanTfilaMGA18Degrees" to zmanimCal::sofZmanTfilaMGA18Degrees,
            "sofZmanTfilaMGA72Minutes" to zmanimCal::sofZmanTfilaMGA72Minutes,
            "sofZmanTfilaMGA72MinutesZmanis" to zmanimCal::sofZmanTfilaMGA72MinutesZmanis,
            "sofZmanTfilaMGA90Minutes" to zmanimCal::sofZmanTfilaMGA90Minutes,
            "sofZmanTfilaMGA90MinutesZmanis" to zmanimCal::sofZmanTfilaMGA90MinutesZmanis,
            "sofZmanTfilaMGA96Minutes" to zmanimCal::sofZmanTfilaMGA96Minutes,
            "sofZmanTfilaMGA96MinutesZmanis" to zmanimCal::sofZmanTfilaMGA96MinutesZmanis,
            "sofZmanTfilaMGA120Minutes" to zmanimCal::sofZmanTfilaMGA120Minutes,
            "sofZmanTfila2HoursBeforeChatzos" to zmanimCal::sofZmanTfila2HoursBeforeChatzos,
            "minchaGedola30Minutes" to zmanimCal::minchaGedola30Minutes,
            "minchaGedola72Minutes" to zmanimCal::minchaGedola72Minutes,
            "minchaGedola16Point1Degrees" to zmanimCal::minchaGedola16Point1Degrees,
            "minchaGedolaAhavatShalom" to zmanimCal::minchaGedolaAhavatShalom,
            "minchaGedolaGreaterThan30" to zmanimCal::minchaGedolaGreaterThan30,
            "minchaKetana16Point1Degrees" to zmanimCal::minchaKetana16Point1Degrees,
            "minchaKetanaAhavatShalom" to zmanimCal::minchaKetanaAhavatShalom,
            "minchaKetana72Minutes" to zmanimCal::minchaKetana72Minutes,
            "minchaKetana" to zmanimCal::minchaKetana,
            "plagHamincha" to zmanimCal::plagHamincha,
            "plagHamincha120MinutesZmanis" to zmanimCal::plagHamincha120MinutesZmanis,
            "plagHamincha120Minutes" to zmanimCal::plagHamincha120Minutes,
            "plagHamincha60Minutes" to zmanimCal::plagHamincha60Minutes,
            "plagHamincha72Minutes" to zmanimCal::plagHamincha72Minutes,
            "plagHamincha90Minutes" to zmanimCal::plagHamincha90Minutes,
            "plagHamincha96Minutes" to zmanimCal::plagHamincha96Minutes,
            "plagHamincha96MinutesZmanis" to zmanimCal::plagHamincha96MinutesZmanis,
            "plagHamincha90MinutesZmanis" to zmanimCal::plagHamincha90MinutesZmanis,
            "plagHamincha72MinutesZmanis" to zmanimCal::plagHamincha72MinutesZmanis,
            "plagHamincha16Point1Degrees" to zmanimCal::plagHamincha16Point1Degrees,
            "plagHamincha19Point8Degrees" to zmanimCal::plagHamincha19Point8Degrees,
            "plagHamincha26Degrees" to zmanimCal::plagHamincha26Degrees,
            "plagHamincha18Degrees" to zmanimCal::plagHamincha18Degrees,
            "plagAlosToSunset" to zmanimCal::plagAlosToSunset,
            "plagAlos16Point1ToTzaisGeonim7Point083Degrees" to zmanimCal::plagAlos16Point1ToTzaisGeonim7Point083Degrees,
            "plagAhavatShalom" to zmanimCal::plagAhavatShalom,
            "bainHashmashosRT13Point24Degrees" to zmanimCal::bainHashmashosRT13Point24Degrees,
            "bainHashmashosRT58Point5Minutes" to zmanimCal::bainHashmashosRT58Point5Minutes,
            "bainHashmashosRT13Point5MinutesBefore7Point083Degrees" to zmanimCal::bainHashmashosRT13Point5MinutesBefore7Point083Degrees,
            "bainHashmashosRT2Stars" to zmanimCal::bainHashmashosRT2Stars,
            "bainHashmashosYereim18Minutes" to zmanimCal::bainHashmashosYereim18Minutes,
            "bainHashmashosYereim3Point05Degrees" to zmanimCal::bainHashmashosYereim3Point05Degrees,
            "bainHashmashosYereim16Point875Minutes" to zmanimCal::bainHashmashosYereim16Point875Minutes,
            "bainHashmashosYereim2Point8Degrees" to zmanimCal::bainHashmashosYereim2Point8Degrees,
            "bainHashmashosYereim13Point5Minutes" to zmanimCal::bainHashmashosYereim13Point5Minutes,
            "bainHashmashosYereim2Point1Degrees" to zmanimCal::bainHashmashosYereim2Point1Degrees,
            "sunset" to { Zman.DateBased(
                ZmanDefinition(
                    ZmanType.SHKIAH,
                    ZmanAuthority.Unanimous,
                    UsesElevation.IF_SET
                )
            ) {zmanimCal.sunset } },
            "seaLevelSunset" to { Zman.DateBased(
                ZmanDefinition(
                    ZmanType.SHKIAH, ZmanAuthority.Unanimous, UsesElevation.NEVER
                )
            ) { zmanimCal.seaLevelSunset } },
            "sunrise" to { Zman.DateBased(
                ZmanDefinition(
                    ZmanType.HANAITZ,
                    ZmanAuthority.Unanimous,
                    UsesElevation.IF_SET
                )
            ) { zmanimCal.sunrise } },
            "seaLevelSunrise" to { Zman.DateBased(
                ZmanDefinition(
                    ZmanType.HANAITZ, ZmanAuthority.Unanimous, UsesElevation.NEVER
                )
            ) { zmanimCal.seaLevelSunrise } },
            "tzaisGeonim3Point7Degrees" to zmanimCal::tzaisGeonim3Point7Degrees,
            "tzaisGeonim3Point8Degrees" to zmanimCal::tzaisGeonim3Point8Degrees,
            "tzaisGeonim5Point95Degrees" to zmanimCal::tzaisGeonim5Point95Degrees,
            "tzaisGeonim3Point65Degrees" to zmanimCal::tzaisGeonim3Point65Degrees,
            "tzaisGeonim3Point676Degrees" to zmanimCal::tzaisGeonim3Point676Degrees,
            "tzaisGeonim4Point61Degrees" to zmanimCal::tzaisGeonim4Point61Degrees,
            "tzaisGeonim4Point37Degrees" to zmanimCal::tzaisGeonim4Point37Degrees,
            "tzaisGeonim5Point88Degrees" to zmanimCal::tzaisGeonim5Point88Degrees,
            "tzaisGeonim4Point8Degrees" to zmanimCal::tzaisGeonim4Point8Degrees,
            "tzaisGeonim6Point45Degrees" to zmanimCal::tzaisGeonim6Point45Degrees,
            "tzaisGeonim7Point083Degrees" to zmanimCal::tzaisGeonim7Point083Degrees,
            "tzaisGeonim7Point67Degrees" to zmanimCal::tzaisGeonim7Point67Degrees,
            "tzaisGeonim8Point5Degrees" to zmanimCal::tzaisGeonim8Point5Degrees,
            "tzaisGeonim9Point3Degrees" to zmanimCal::tzaisGeonim9Point3Degrees,
            "tzaisGeonim9Point75Degrees" to zmanimCal::tzaisGeonim9Point75Degrees,
            "tzais60" to zmanimCal::tzais60,
            "tzaisAteretTorah" to zmanimCal::tzaisAteretTorah,
            "sofZmanShmaAteretTorah" to zmanimCal::sofZmanShmaAteretTorah,
            "sofZmanTfilahAteretTorah" to zmanimCal::sofZmanTfilahAteretTorah,
            "minchaGedolaAteretTorah" to zmanimCal::minchaGedolaAteretTorah,
            "minchaKetanaAteretTorah" to zmanimCal::minchaKetanaAteretTorah,
            "plagHaminchaAteretTorah" to zmanimCal::plagHaminchaAteretTorah,
            "tzais72Zmanis" to zmanimCal::tzais72Zmanis,
            "tzais90Zmanis" to zmanimCal::tzais90Zmanis,
            "tzais96Zmanis" to zmanimCal::tzais96Zmanis,
            "tzais90" to zmanimCal::tzais90,
            "tzais120" to zmanimCal::tzais120,
            "tzais120Zmanis" to zmanimCal::tzais120Zmanis,
            "tzais16Point1Degrees" to zmanimCal::tzais16Point1Degrees,
            "tzais26Degrees" to zmanimCal::tzais26Degrees,
            "tzais18Degrees" to zmanimCal::tzais18Degrees,
            "tzais19Point8Degrees" to zmanimCal::tzais19Point8Degrees,
            "tzais96" to zmanimCal::tzais96,
            "fixedLocalChatzos" to zmanimCal::fixedLocalChatzos,
            "sofZmanKidushLevanaBetweenMoldos" to zmanimCal::sofZmanKidushLevanaBetweenMoldos,
            "sofZmanKidushLevana15Days" to zmanimCal::sofZmanKidushLevana15Days,
            "tchilasZmanKidushLevana3Days" to zmanimCal::tchilasZmanKidushLevana3Days,
            "zmanMolad" to zmanimCal::zmanMolad,
            "tchilasZmanKidushLevana7Days" to zmanimCal::tchilasZmanKidushLevana7Days,
            "sofZmanAchilasChametzGRA" to zmanimCal::sofZmanAchilasChametzGRA,
            "sofZmanAchilasChametzMGA72Minutes" to zmanimCal::sofZmanAchilasChametzMGA72Minutes,
            "sofZmanAchilasChametzMGA16Point1Degrees" to zmanimCal::sofZmanAchilasChametzMGA16Point1Degrees,
            "sofZmanBiurChametzGRA" to zmanimCal::sofZmanBiurChametzGRA,
            "sofZmanBiurChametzMGA72Minutes" to zmanimCal::sofZmanBiurChametzMGA72Minutes,
            "sofZmanBiurChametzMGA16Point1Degrees" to zmanimCal::sofZmanBiurChametzMGA16Point1Degrees,
            "solarMidnight" to zmanimCal::solarMidnight,
            "alosBaalHatanya" to zmanimCal::alosBaalHatanya,
            "sofZmanShmaBaalHatanya" to zmanimCal::sofZmanShmaBaalHatanya,
            "sofZmanTfilaBaalHatanya" to zmanimCal::sofZmanTfilaBaalHatanya,
            "sofZmanAchilasChametzBaalHatanya" to zmanimCal::sofZmanAchilasChametzBaalHatanya,
            "sofZmanBiurChametzBaalHatanya" to zmanimCal::sofZmanBiurChametzBaalHatanya,
            "minchaGedolaBaalHatanya" to zmanimCal::minchaGedolaBaalHatanya,
            "minchaGedolaBaalHatanyaGreaterThan30" to zmanimCal::minchaGedolaBaalHatanyaGreaterThan30,
            "minchaKetanaBaalHatanya" to zmanimCal::minchaKetanaBaalHatanya,
            "plagHaminchaBaalHatanya" to zmanimCal::plagHaminchaBaalHatanya,
            "tzaisBaalHatanya" to zmanimCal::tzaisBaalHatanya,
            "sofZmanShmaMGA18DegreesToFixedLocalChatzos" to zmanimCal::sofZmanShmaMGA18DegreesToFixedLocalChatzos,
            "sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos" to zmanimCal::sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos,
            "sofZmanShmaMGA90MinutesToFixedLocalChatzos" to zmanimCal::sofZmanShmaMGA90MinutesToFixedLocalChatzos,
            "sofZmanShmaMGA72MinutesToFixedLocalChatzos" to zmanimCal::sofZmanShmaMGA72MinutesToFixedLocalChatzos,
            "sofZmanShmaGRASunriseToFixedLocalChatzos" to zmanimCal::sofZmanShmaGRASunriseToFixedLocalChatzos,
            "sofZmanTfilaGRASunriseToFixedLocalChatzos" to zmanimCal::sofZmanTfilaGRASunriseToFixedLocalChatzos,
            "minchaGedolaGRAFixedLocalChatzos30Minutes" to zmanimCal::minchaGedolaGRAFixedLocalChatzos30Minutes,
            "minchaKetanaGRAFixedLocalChatzosToSunset" to zmanimCal::minchaKetanaGRAFixedLocalChatzosToSunset,
            "plagHaminchaGRAFixedLocalChatzosToSunset" to zmanimCal::plagHaminchaGRAFixedLocalChatzosToSunset,
            "tzais50" to zmanimCal::tzais50,
            "samuchLeMinchaKetanaGRA" to zmanimCal::samuchLeMinchaKetanaGRA,
            "samuchLeMinchaKetana16Point1Degrees" to zmanimCal::samuchLeMinchaKetana16Point1Degrees,
            "samuchLeMinchaKetana72Minutes" to zmanimCal::samuchLeMinchaKetana72Minutes,
        )
    }

    private suspend fun buildGeo(lat: Double, lon: Double, altitude: Double, tz: TimeZone): GeoLocation {
        val displayName = resolvePlaceName(lat, lon) ?: "$lat,$lon"
        return GeoLocation(displayName, lat, lon, altitude, tz)
    }

    private suspend fun resolvePlaceName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(appContext, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lon, 1)
            val addr = results?.firstOrNull()
            if (addr != null) {
                when {
                    addr.locality != null  -> addr.locality
                    addr.featureName != null -> addr.featureName
                    addr.subAdminArea != null -> addr.subAdminArea
                    addr.adminArea != null -> addr.adminArea
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            Log.w("Geocoder", "reverse geocode failed", e)
            null
        }
    }

    fun toggleZmanVisiblity(zman: ZmanDisplay) {
        val newDisabledIds = if (_uiState.value.disabledIds.contains(zman.key)) {
            _uiState.value.disabledIds - zman.key
        } else {
            _uiState.value.disabledIds + zman.key
        }

        _uiState.value = _uiState.value.copy(disabledIds = newDisabledIds)

        // Persist the change and request tile update
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.updateDisabledIds(newDisabledIds)
            MainTileService.requestUpdate(appContext)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Check if background location permission should be requested
     * Returns true if foreground permission is granted but background is not
     */
    fun shouldRequestBackgroundLocation(context: Context): Boolean {
        val hasForegroundPermission = (
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )

        val hasBackgroundPermission = context.checkSelfPermission(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Only show if we have foreground but not background
        return hasForegroundPermission && !hasBackgroundPermission
    }

    /**
     * Show the background location permission prompt
     */
    fun showBackgroundLocationPrompt() {
        _uiState.value = _uiState.value.copy(showBackgroundLocationPrompt = true)
    }

    /**
     * Dismiss the background location permission prompt
     */
    fun dismissBackgroundLocationPrompt() {
        _uiState.value = _uiState.value.copy(showBackgroundLocationPrompt = false)
    }
}

data class UiState(
    val location: String? = null,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: String? = null,
    val zmanim: Map<String, () -> Zman.DateBased> = emptyMap(),
    val zmanDisplays: List<ZmanDisplay> = emptyList(),
    val disabledIds: Set<String> = setOf(
        "sunset",
        "sunrise",
        "tzais",
        "sofZmanShmaGRA",
        "sofZmanShmaMGA",
        "tzais72",
        "sofZmanTfilaGRA",
        "sofZmanTfilaMGA",
        "minchaGedola",
        "alosHashachar",
        "alos72",
        "alos60",
        "alos72Zmanis",
        "alos96",
        "alos90Zmanis",
        "alos96Zmanis",
        "alos90",
        "alos120",
        "alos120Zmanis",
        "alos26Degrees",
        "alos18Degrees",
        "alos19Degrees",
        "alos19Point8Degrees",
        "alos16Point1Degrees",
        "misheyakir11Point5Degrees",
        "misheyakir11Degrees",
//        "misheyakir10Point2Degrees",
        "misheyakir7Point65Degrees",
        "misheyakir9Point5Degrees",
        "sofZmanShmaMGA19Point8Degrees",
        "sofZmanShmaMGA16Point1Degrees",
        "sofZmanShmaMGA18Degrees",
        "sofZmanShmaMGA72Minutes",
        "sofZmanShmaMGA72MinutesZmanis",
        "sofZmanShmaMGA90Minutes",
        "sofZmanShmaMGA90MinutesZmanis",
        "sofZmanShmaMGA96Minutes",
        "sofZmanShmaMGA96MinutesZmanis",
        "sofZmanShma3HoursBeforeChatzos",
        "sofZmanShmaMGA120Minutes",
        "sofZmanShmaAlos16Point1ToSunset",
        "sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees",
        "sofZmanTfilaMGA19Point8Degrees",
        "sofZmanTfilaMGA16Point1Degrees",
        "sofZmanTfilaMGA18Degrees",
        "sofZmanTfilaMGA72Minutes",
        "sofZmanTfilaMGA72MinutesZmanis",
        "sofZmanTfilaMGA90Minutes",
        "sofZmanTfilaMGA90MinutesZmanis",
        "sofZmanTfilaMGA96Minutes",
        "sofZmanTfilaMGA96MinutesZmanis",
        "sofZmanTfilaMGA120Minutes",
        "sofZmanTfila2HoursBeforeChatzos",
        "minchaGedola30Minutes",
        "minchaGedola72Minutes",
        "minchaGedola16Point1Degrees",
        "minchaGedolaAhavatShalom",
        "minchaGedolaGreaterThan30",
        "minchaKetana16Point1Degrees",
        "minchaKetanaAhavatShalom",
        "minchaKetana72Minutes",
        "minchaKetana",
        "plagHamincha",
        "plagHamincha120MinutesZmanis",
        "plagHamincha120Minutes",
        "plagHamincha60Minutes",
        "plagHamincha72Minutes",
        "plagHamincha90Minutes",
        "plagHamincha96Minutes",
        "plagHamincha96MinutesZmanis",
        "plagHamincha90MinutesZmanis",
        "plagHamincha72MinutesZmanis",
        "plagHamincha16Point1Degrees",
        "plagHamincha19Point8Degrees",
        "plagHamincha26Degrees",
        "plagHamincha18Degrees",
        "plagAlosToSunset",
        "plagAlos16Point1ToTzaisGeonim7Point083Degrees",
        "plagAhavatShalom",
        "bainHashmashosRT13Point24Degrees",
        "bainHashmashosRT58Point5Minutes",
        "bainHashmashosRT13Point5MinutesBefore7Point083Degrees",
        "bainHashmashosRT2Stars",
        "bainHashmashosYereim18Minutes",
        "bainHashmashosYereim3Point05Degrees",
        "bainHashmashosYereim16Point875Minutes",
        "bainHashmashosYereim2Point8Degrees",
        "bainHashmashosYereim13Point5Minutes",
        "bainHashmashosYereim2Point1Degrees",
        "tzaisGeonim3Point7Degrees",
        "tzaisGeonim3Point8Degrees",
        "tzaisGeonim5Point95Degrees",
        "tzaisGeonim3Point65Degrees",
        "tzaisGeonim3Point676Degrees",
        "tzaisGeonim4Point61Degrees",
        "tzaisGeonim4Point37Degrees",
        "tzaisGeonim5Point88Degrees",
        "tzaisGeonim4Point8Degrees",
        "tzaisGeonim6Point45Degrees",
        "tzaisGeonim7Point083Degrees",
        "tzaisGeonim7Point67Degrees",
        "tzaisGeonim8Point5Degrees",
        "tzaisGeonim9Point3Degrees",
        "tzaisGeonim9Point75Degrees",
        "tzais60",
        "tzaisAteretTorah",
        "sofZmanShmaAteretTorah",
        "sofZmanTfilahAteretTorah",
        "minchaGedolaAteretTorah",
        "minchaKetanaAteretTorah",
        "plagHaminchaAteretTorah",
        "tzais72Zmanis",
        "tzais90Zmanis",
        "tzais96Zmanis",
        "tzais90",
        "tzais120",
        "tzais120Zmanis",
        "tzais16Point1Degrees",
        "tzais26Degrees",
        "tzais18Degrees",
        "tzais19Point8Degrees",
        "tzais96",
        "fixedLocalChatzos",
        "sofZmanKidushLevanaBetweenMoldos",
        "sofZmanKidushLevana15Days",
        "tchilasZmanKidushLevana3Days",
        "zmanMolad",
        "tchilasZmanKidushLevana7Days",
        "sofZmanAchilasChametzGRA",
        "sofZmanAchilasChametzMGA72Minutes",
        "sofZmanAchilasChametzMGA16Point1Degrees",
        "sofZmanBiurChametzGRA",
        "sofZmanBiurChametzMGA72Minutes",
        "sofZmanBiurChametzMGA16Point1Degrees",
        "sofZmanShmaMGA18DegreesToFixedLocalChatzos",
        "sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos",
        "sofZmanShmaMGA90MinutesToFixedLocalChatzos",
        "sofZmanShmaMGA72MinutesToFixedLocalChatzos",
        "sofZmanShmaGRASunriseToFixedLocalChatzos",
        "sofZmanTfilaGRASunriseToFixedLocalChatzos",
        "minchaGedolaGRAFixedLocalChatzos30Minutes",
        "minchaKetanaGRAFixedLocalChatzosToSunset",
        "plagHaminchaGRAFixedLocalChatzosToSunset",
        "tzais50",
        "samuchLeMinchaKetanaGRA",
        "samuchLeMinchaKetana16Point1Degrees",
        "samuchLeMinchaKetana72Minutes",
    ),
    val timeZone: TimeZone = TimeZone.of("UTC"),
    val showingEdit: Boolean = false,
    val showBackgroundLocationPrompt: Boolean = false
)


@OptIn(ExperimentalTime::class)
private fun formatTime(item: Instant?, timeZone: TimeZone, is24h: Boolean): String {
    val lt = item?.toLocalDateTime(timeZone)?.time
    return if (lt == null) {
        "--:--"
    } else if (is24h) {
        String.format("%02d:%02d", lt.hour, lt.minute)
    } else {
        val hour12 = (lt.hour % 12).let { if (it == 0) 12 else it }
        val ampm = if (lt.hour < 12) "AM" else "PM"
        String.format("%d:%02d %s", hour12, lt.minute, ampm)
    }
}

@Serializable
data class ZmanDisplay(
    val key: String,
    val name: String,
    val fromEpoch: Long?,
    val timeZone: TimeZone,
    val uses24h: Boolean
) {
    @OptIn(ExperimentalTime::class)
    val timeText = formatTime(if (fromEpoch != null) Instant.fromEpochSeconds(fromEpoch) else null, timeZone, uses24h)

}
