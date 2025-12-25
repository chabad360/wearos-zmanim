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
import java.util.Locale
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

    init {
        // Load cached state on startup
        viewModelScope.launch {
            val cachedState = dataStore.stateFlow.first()
            if (cachedState != null) {
                val isStale = dataStore.isStateStale(cachedState)
                restoreFromCache(cachedState, isStale)
            }
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

        _uiState.value = _uiState.value.copy(
            location = cached.location.name,
            zmanim = zmanim,
            zmanDisplays = cached.zmanim,  // ZmanDisplay is already serializable, use directly
            timeZone = TimeZone.of(cached.location.timezoneId),
            disabledIds = cached.disabledIds,
            isStale = isStale,
            isRefreshing = false
        )
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
        }
    }

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
                        updateForGeo(buildGeo(loc.latitude, loc.longitude, loc.altitude, tz))
                    }
                } else {
                    Log.w("GPS", "fresh fix")
                    // Try a fresh fix
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                        .addOnSuccessListener { fresh ->
                            viewModelScope.launch(Dispatchers.Default) {
                                val geo = fresh?.let { buildGeo(it.latitude, it.longitude, it.altitude, tz) }
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
        val cal = ComplexZmanimCalendar(geo)
        val zmanim = zmanimMap(cal)
        val zmanDisplays = computeZmanim(cal)
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
         if (error == null) {
             persistState(geo, zmanDisplays)
         }
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

    @OptIn(ExperimentalTime::class)
    private fun computeZmanim(zmanimCal: ComplexZmanimCalendar): List<ZmanDisplay> {
        val geo = zmanimCal.geoLocation
        val is24h = DateFormat.is24HourFormat(appContext)

        val zmanDisplays = listOf(
            ZmanDisplay("tzais", "Tzais", zmanimCal.tzais.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("chatzos", "Chatzos", zmanimCal.chatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaGRA", "Sof Zman Krias Shema (GRA)", zmanimCal.sofZmanShmaGRA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA", "Sof Zman Krias Shema (MGA)", zmanimCal.sofZmanShmaMGA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais72", "Tzais (72 min)", zmanimCal.tzais72.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("candleLighting", "Candle Lighting", zmanimCal.candleLighting.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaGRA", "Sof Zman Tefila (GRA)", zmanimCal.sofZmanTfilaGRA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA", "Sof Zman Tefila (MGA)", zmanimCal.sofZmanTfilaMGA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedola", "Mincha Gedola", zmanimCal.minchaGedola.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alosHashachar", "Alos Hashachar", zmanimCal.alosHashachar.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos72", "Alos (72 min)", zmanimCal.alos72.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos60", "Alos (60 min)", zmanimCal.alos60.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos72Zmanis", "Alos (72 min Zmanis)", zmanimCal.alos72Zmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos96", "Alos (96 min)", zmanimCal.alos96.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos90Zmanis", "Alos (90 min Zmanis)", zmanimCal.alos90Zmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos96Zmanis", "Alos (96 min Zmanis)", zmanimCal.alos96Zmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos90", "Alos (90 min)", zmanimCal.alos90.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos120", "Alos (120 min)", zmanimCal.alos120.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos120Zmanis", "Alos (120 min Zmanis)", zmanimCal.alos120Zmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos26Degrees", "Alos (26°)", zmanimCal.alos26Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos18Degrees", "Alos (18°)", zmanimCal.alos18Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos19Degrees", "Alos (19°)", zmanimCal.alos19Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos19Point8Degrees", "Alos (19.8°)", zmanimCal.alos19Point8Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alos16Point1Degrees", "Alos (16.1°)", zmanimCal.alos16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("misheyakir11Point5Degrees", "Misheyakir (11.5°)", zmanimCal.misheyakir11Point5Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("misheyakir11Degrees", "Misheyakir (11°)", zmanimCal.misheyakir11Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("misheyakir10Point2Degrees", "Misheyakir (10.2°)", zmanimCal.misheyakir10Point2Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("misheyakir7Point65Degrees", "Misheyakir (7.65°)", zmanimCal.misheyakir7Point65Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("misheyakir9Point5Degrees", "Misheyakir (9.5°)", zmanimCal.misheyakir9Point5Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA19Point8Degrees", "Sof Zman Shema (MGA, 19.8°)", zmanimCal.sofZmanShmaMGA19Point8Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA16Point1Degrees", "Sof Zman Shema (MGA, 16.1°)", zmanimCal.sofZmanShmaMGA16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA18Degrees", "Sof Zman Shema (MGA, 18°)", zmanimCal.sofZmanShmaMGA18Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA72Minutes", "Sof Zman Shema (MGA, 72 min)", zmanimCal.sofZmanShmaMGA72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA72MinutesZmanis", "Sof Zman Shema (MGA, 72 min Zmanis)", zmanimCal.sofZmanShmaMGA72MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA90Minutes", "Sof Zman Shema (MGA, 90 min)", zmanimCal.sofZmanShmaMGA90Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA90MinutesZmanis", "Sof Zman Shema (MGA, 90 min Zmanis)", zmanimCal.sofZmanShmaMGA90MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA96Minutes", "Sof Zman Shema (MGA, 96 min)", zmanimCal.sofZmanShmaMGA96Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA96MinutesZmanis", "Sof Zman Shema (MGA, 96 min Zmanis)", zmanimCal.sofZmanShmaMGA96MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShma3HoursBeforeChatzos", "Sof Zman Shema (3 hours before Chatzos)", zmanimCal.sofZmanShma3HoursBeforeChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA120Minutes", "Sof Zman Shema (MGA, 120 min)", zmanimCal.sofZmanShmaMGA120Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaAlos16Point1ToSunset", "Sof Zman Shema (Alos 16.1° to sunset)", zmanimCal.sofZmanShmaAlos16Point1ToSunset.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees", "Sof Zman Shema (Alos 16.1° to Tzais Geonim 7.083°)", zmanimCal.sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA19Point8Degrees", "Sof Zman Tefila (MGA, 19.8°)", zmanimCal.sofZmanTfilaMGA19Point8Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA16Point1Degrees", "Sof Zman Tefila (MGA, 16.1°)", zmanimCal.sofZmanTfilaMGA16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA18Degrees", "Sof Zman Tefila (MGA, 18°)", zmanimCal.sofZmanTfilaMGA18Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA72Minutes", "Sof Zman Tefila (MGA, 72 min)", zmanimCal.sofZmanTfilaMGA72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA72MinutesZmanis", "Sof Zman Tefila (MGA, 72 min Zmanis)", zmanimCal.sofZmanTfilaMGA72MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA90Minutes", "Sof Zman Tefila (MGA, 90 min)", zmanimCal.sofZmanTfilaMGA90Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA90MinutesZmanis", "Sof Zman Tefila (MGA, 90 min Zmanis)", zmanimCal.sofZmanTfilaMGA90MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA96Minutes", "Sof Zman Tefila (MGA, 96 min)", zmanimCal.sofZmanTfilaMGA96Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA96MinutesZmanis", "Sof Zman Tefila (MGA, 96 min Zmanis)", zmanimCal.sofZmanTfilaMGA96MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaMGA120Minutes", "Sof Zman Tefila (MGA, 120 min)", zmanimCal.sofZmanTfilaMGA120Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfila2HoursBeforeChatzos", "Sof Zman Tefila (2 hours before Chatzos)", zmanimCal.sofZmanTfila2HoursBeforeChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedola30Minutes", "Mincha Gedola (30 min)", zmanimCal.minchaGedola30Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedola72Minutes", "Mincha Gedola (72 min)", zmanimCal.minchaGedola72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedola16Point1Degrees", "Mincha Gedola (16.1°)", zmanimCal.minchaGedola16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedolaAhavatShalom", "Mincha Gedola (Ahavat Shalom)", zmanimCal.minchaGedolaAhavatShalom.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedolaGreaterThan30", "Mincha Gedola (>30 min)", zmanimCal.minchaGedolaGreaterThan30.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaKetana16Point1Degrees", "Mincha Ketana (16.1°)", zmanimCal.minchaKetana16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaKetanaAhavatShalom", "Mincha Ketana (Ahavat Shalom)", zmanimCal.minchaKetanaAhavatShalom.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaKetana72Minutes", "Mincha Ketana (72 min)", zmanimCal.minchaKetana72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaKetana", "Mincha Ketana", zmanimCal.minchaKetana.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha", "Plag Hamincha", zmanimCal.plagHamincha.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha120MinutesZmanis", "Plag Hamincha (120 min Zmanis)", zmanimCal.plagHamincha120MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha120Minutes", "Plag Hamincha (120 min)", zmanimCal.plagHamincha120Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha60Minutes", "Plag Hamincha (60 min)", zmanimCal.plagHamincha60Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha72Minutes", "Plag Hamincha (72 min)", zmanimCal.plagHamincha72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha90Minutes", "Plag Hamincha (90 min)", zmanimCal.plagHamincha90Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha96Minutes", "Plag Hamincha (96 min)", zmanimCal.plagHamincha96Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha96MinutesZmanis", "Plag Hamincha (96 min Zmanis)", zmanimCal.plagHamincha96MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha90MinutesZmanis", "Plag Hamincha (90 min Zmanis)", zmanimCal.plagHamincha90MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha72MinutesZmanis", "Plag Hamincha (72 min Zmanis)", zmanimCal.plagHamincha72MinutesZmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha16Point1Degrees", "Plag Hamincha (16.1°)", zmanimCal.plagHamincha16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha19Point8Degrees", "Plag Hamincha (19.8°)", zmanimCal.plagHamincha19Point8Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha26Degrees", "Plag Hamincha (26°)", zmanimCal.plagHamincha26Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHamincha18Degrees", "Plag Hamincha (18°)", zmanimCal.plagHamincha18Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagAlosToSunset", "Plag Hamincha (Alos to sunset)", zmanimCal.plagAlosToSunset.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagAlos16Point1ToTzaisGeonim7Point083Degrees", "Plag Hamincha (Alos 16.1° to Tzais Geonim 7.083°)", zmanimCal.plagAlos16Point1ToTzaisGeonim7Point083Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagAhavatShalom", "Plag Hamincha (Ahavat Shalom)", zmanimCal.plagAhavatShalom.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosRT13Point24Degrees", "Bain Hashmashos (RT, 13.24°)", zmanimCal.bainHashmashosRT13Point24Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosRT58Point5Minutes", "Bain Hashmashos (RT, 58.5 min)", zmanimCal.bainHashmashosRT58Point5Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosRT13Point5MinutesBefore7Point083Degrees", "Bain Hashmashos (RT, 13.5 min before 7.083°)", zmanimCal.bainHashmashosRT13Point5MinutesBefore7Point083Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosRT2Stars", "Bain Hashmashos (RT, 2 stars)", zmanimCal.bainHashmashosRT2Stars.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosYereim18Minutes", "Bain Hashmashos (Yereim, 18 min)", zmanimCal.bainHashmashosYereim18Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosYereim3Point05Degrees", "Bain Hashmashos (Yereim, 3.05°)", zmanimCal.bainHashmashosYereim3Point05Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosYereim16Point875Minutes", "Bain Hashmashos (Yereim, 16.875 min)", zmanimCal.bainHashmashosYereim16Point875Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosYereim2Point8Degrees", "Bain Hashmashos (Yereim, 2.8°)", zmanimCal.bainHashmashosYereim2Point8Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosYereim13Point5Minutes", "Bain Hashmashos (Yereim, 13.5 min)", zmanimCal.bainHashmashosYereim13Point5Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("bainHashmashosYereim2Point1Degrees", "Bain Hashmashos (Yereim, 2.1°)", zmanimCal.bainHashmashosYereim2Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sunset", "Sunset", zmanimCal.sunset?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("seaLevelSunset", "Sea Level Sunset", zmanimCal.seaLevelSunset?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sunrise", "Sunrise", zmanimCal.sunrise?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("seaLevelSunrise", "Sea Level Sunrise", zmanimCal.seaLevelSunrise?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim3Point7Degrees", "Tzais Geonim (3.7°)", zmanimCal.tzaisGeonim3Point7Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim3Point8Degrees", "Tzais Geonim (3.8°)", zmanimCal.tzaisGeonim3Point8Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim5Point95Degrees", "Tzais Geonim (5.95°)", zmanimCal.tzaisGeonim5Point95Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim3Point65Degrees", "Tzais Geonim (3.65°)", zmanimCal.tzaisGeonim3Point65Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim3Point676Degrees", "Tzais Geonim (3.676°)", zmanimCal.tzaisGeonim3Point676Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim4Point61Degrees", "Tzais Geonim (4.61°)", zmanimCal.tzaisGeonim4Point61Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim4Point37Degrees", "Tzais Geonim (4.37°)", zmanimCal.tzaisGeonim4Point37Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim5Point88Degrees", "Tzais Geonim (5.88°)", zmanimCal.tzaisGeonim5Point88Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim4Point8Degrees", "Tzais Geonim (4.8°)", zmanimCal.tzaisGeonim4Point8Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim6Point45Degrees", "Tzais Geonim (6.45°)", zmanimCal.tzaisGeonim6Point45Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim7Point083Degrees", "Tzais Geonim (7.083°)", zmanimCal.tzaisGeonim7Point083Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim7Point67Degrees", "Tzais Geonim (7.67°)", zmanimCal.tzaisGeonim7Point67Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim8Point5Degrees", "Tzais Geonim (8.5°)", zmanimCal.tzaisGeonim8Point5Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim9Point3Degrees", "Tzais Geonim (9.3°)", zmanimCal.tzaisGeonim9Point3Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisGeonim9Point75Degrees", "Tzais Geonim (9.75°)", zmanimCal.tzaisGeonim9Point75Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais60", "Tzais (60 min)", zmanimCal.tzais60.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisAteretTorah", "Tzais (Ateret Torah)", zmanimCal.tzaisAteretTorah.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaAteretTorah", "Sof Zman Shema (Ateret Torah)", zmanimCal.sofZmanShmaAteretTorah.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilahAteretTorah", "Sof Zman Tefila (Ateret Torah)", zmanimCal.sofZmanTfilahAteretTorah.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedolaAteretTorah", "Mincha Gedola (Ateret Torah)", zmanimCal.minchaGedolaAteretTorah.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaKetanaAteretTorah", "Mincha Ketana (Ateret Torah)", zmanimCal.minchaKetanaAteretTorah.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHaminchaAteretTorah", "Plag Hamincha (Ateret Torah)", zmanimCal.plagHaminchaAteretTorah.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais72Zmanis", "Tzais (72 min Zmanis)", zmanimCal.tzais72Zmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais90Zmanis", "Tzais (90 min Zmanis)", zmanimCal.tzais90Zmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais96Zmanis", "Tzais (96 min Zmanis)", zmanimCal.tzais96Zmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais90", "Tzais (90 min)", zmanimCal.tzais90.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais120", "Tzais (120 min)", zmanimCal.tzais120.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais120Zmanis", "Tzais (120 min Zmanis)", zmanimCal.tzais120Zmanis.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais16Point1Degrees", "Tzais (16.1°)", zmanimCal.tzais16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais26Degrees", "Tzais (26°)", zmanimCal.tzais26Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais18Degrees", "Tzais (18°)", zmanimCal.tzais18Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais19Point8Degrees", "Tzais (19.8°)", zmanimCal.tzais19Point8Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais96", "Tzais (96 min)", zmanimCal.tzais96.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("fixedLocalChatzos", "Fixed Local Chatzos", zmanimCal.fixedLocalChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanKidushLevanaBetweenMoldos", "Sof Zman Kiddush Levana (between moldos)", zmanimCal.sofZmanKidushLevanaBetweenMoldos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanKidushLevana15Days", "Sof Zman Kiddush Levana (15 days)", zmanimCal.sofZmanKidushLevana15Days.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tchilasZmanKidushLevana3Days", "Tchilas Zman Kiddush Levana (3 days)", zmanimCal.tchilasZmanKidushLevana3Days.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("zmanMolad", "Zman Molad", zmanimCal.zmanMolad.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tchilasZmanKidushLevana7Days", "Tchilas Zman Kiddush Levana (7 days)", zmanimCal.tchilasZmanKidushLevana7Days.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanAchilasChametzGRA", "Sof Zman Achilas Chametz (GRA)", zmanimCal.sofZmanAchilasChametzGRA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanAchilasChametzMGA72Minutes", "Sof Zman Achilas Chametz (MGA, 72 min)", zmanimCal.sofZmanAchilasChametzMGA72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanAchilasChametzMGA16Point1Degrees", "Sof Zman Achilas Chametz (MGA, 16.1°)", zmanimCal.sofZmanAchilasChametzMGA16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanBiurChametzGRA", "Sof Zman Biur Chametz (GRA)", zmanimCal.sofZmanBiurChametzGRA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanBiurChametzMGA72Minutes", "Sof Zman Biur Chametz (MGA, 72 min)", zmanimCal.sofZmanBiurChametzMGA72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanBiurChametzMGA16Point1Degrees", "Sof Zman Biur Chametz (MGA, 16.1°)", zmanimCal.sofZmanBiurChametzMGA16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("solarMidnight", "Solar Midnight", zmanimCal.solarMidnight.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("alosBaalHatanya", "Alos (Baal HaTanya)", zmanimCal.alosBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaBaalHatanya", "Sof Zman Shema (Baal HaTanya)", zmanimCal.sofZmanShmaBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaBaalHatanya", "Sof Zman Tefila (Baal HaTanya)", zmanimCal.sofZmanTfilaBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanAchilasChametzBaalHatanya", "Sof Zman Achilas Chametz (Baal HaTanya)", zmanimCal.sofZmanAchilasChametzBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanBiurChametzBaalHatanya", "Sof Zman Biur Chametz (Baal HaTanya)", zmanimCal.sofZmanBiurChametzBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedolaBaalHatanya", "Mincha Gedola (Baal HaTanya)", zmanimCal.minchaGedolaBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedolaBaalHatanyaGreaterThan30", "Mincha Gedola (Baal HaTanya, >30 min)", zmanimCal.minchaGedolaBaalHatanyaGreaterThan30.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaKetanaBaalHatanya", "Mincha Ketana (Baal HaTanya)", zmanimCal.minchaKetanaBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHaminchaBaalHatanya", "Plag Hamincha (Baal HaTanya)", zmanimCal.plagHaminchaBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzaisBaalHatanya", "Tzais (Baal HaTanya)", zmanimCal.tzaisBaalHatanya.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA18DegreesToFixedLocalChatzos", "Sof Zman Shema (MGA 18° to fixed local Chatzos)", zmanimCal.sofZmanShmaMGA18DegreesToFixedLocalChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos", "Sof Zman Shema (MGA 16.1° to fixed local Chatzos)", zmanimCal.sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA90MinutesToFixedLocalChatzos", "Sof Zman Shema (MGA 90 min to fixed local Chatzos)", zmanimCal.sofZmanShmaMGA90MinutesToFixedLocalChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaMGA72MinutesToFixedLocalChatzos", "Sof Zman Shema (MGA 72 min to fixed local Chatzos)", zmanimCal.sofZmanShmaMGA72MinutesToFixedLocalChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanShmaGRASunriseToFixedLocalChatzos", "Sof Zman Shema (GRA sunrise to fixed local Chatzos)", zmanimCal.sofZmanShmaGRASunriseToFixedLocalChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanTfilaGRASunriseToFixedLocalChatzos", "Sof Zman Tefila (GRA sunrise to fixed local Chatzos)", zmanimCal.sofZmanTfilaGRASunriseToFixedLocalChatzos.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaGedolaGRAFixedLocalChatzos30Minutes", "Mincha Gedola (GRA fixed local Chatzos +30 min)", zmanimCal.minchaGedolaGRAFixedLocalChatzos30Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("minchaKetanaGRAFixedLocalChatzosToSunset", "Mincha Ketana (GRA fixed local Chatzos to sunset)", zmanimCal.minchaKetanaGRAFixedLocalChatzosToSunset.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("plagHaminchaGRAFixedLocalChatzosToSunset", "Plag Hamincha (GRA fixed local Chatzos to sunset)", zmanimCal.plagHaminchaGRAFixedLocalChatzosToSunset.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tzais50", "Tzais (50 min)", zmanimCal.tzais50.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("samuchLeMinchaKetanaGRA", "Samuch LeMincha Ketana (GRA)", zmanimCal.samuchLeMinchaKetanaGRA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("samuchLeMinchaKetana16Point1Degrees", "Samuch LeMincha Ketana (16.1°)", zmanimCal.samuchLeMinchaKetana16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("samuchLeMinchaKetana72Minutes", "Samuch LeMincha Ketana (72 min)", zmanimCal.samuchLeMinchaKetana72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ).sortedBy { it.fromEpoch }
        return zmanDisplays
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
                val city = addr.locality ?: addr.subAdminArea
                when {
                    city != null  -> city
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            Log.w("Geocoder", "reverse geocode failed", e)
            null
        }
    }

    fun toggleZmanVisiblity(zman: ZmanDisplay): Unit {
        val newDisabledIds = if (_uiState.value.disabledIds.contains(zman.key)) {
            _uiState.value.disabledIds - zman.key
        } else {
            _uiState.value.disabledIds + zman.key
        }

        _uiState.value = _uiState.value.copy(disabledIds = newDisabledIds)

        // Persist the change
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.updateDisabledIds(newDisabledIds)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class UiState(
    val location: String? = "Jerusalem",
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: String? = null,
    val zmanim: Map<String, () -> Zman.DateBased> = emptyMap(),
    val zmanDisplays: List<ZmanDisplay> = emptyList(),
    val disabledIds: Set<String> = emptySet(),
    val timeZone: TimeZone = TimeZone.of("UTC"),
    val showingEdit: Boolean = false
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
