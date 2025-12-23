package me.chabad360.zmanim.wearos.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.util.Log
import android.text.format.DateFormat
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.Zman
import io.github.kdroidfilter.kosherkotlin.ZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.metadata.UsesElevation
import io.github.kdroidfilter.kosherkotlin.metadata.ZmanAuthority
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
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.collections.distinct
import kotlin.collections.plus
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


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
        val zmanim = computeZmanim(geo)
        val is24h = DateFormat.is24HourFormat(appContext)
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

    @OptIn(ExperimentalTime::class)
    private fun computeZmanim(geo: GeoLocation): List<ZmanDisplay> {
        val zmanimCal = ComplexZmanimCalendar(geo)
        val is24h = DateFormat.is24HourFormat(appContext)
        val zmanim = listOf<ZmanDisplay>(
            ZmanDisplay("tzais", "Tzais", zmanimCal.tzais, formatTime(zmanimCal.tzais.value, geo.timeZone, is24h)),
            ZmanDisplay("alosHashachar", "Alos Hashachar", zmanimCal.alosHashachar, formatTime(zmanimCal.alosHashachar.value, geo.timeZone, is24h)),
            ZmanDisplay("alos72", "Alos (72 min)", zmanimCal.alos72, formatTime(zmanimCal.alos72.value, geo.timeZone, is24h)),
            ZmanDisplay("chatzos", "Chatzos", zmanimCal.chatzos, formatTime(zmanimCal.chatzos.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaGRA", "Sof Zman Krias Shema (GRA)", zmanimCal.sofZmanShmaGRA, formatTime(zmanimCal.sofZmanShmaGRA.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA", "Sof Zman Krias Shema (MGA)", zmanimCal.sofZmanShmaMGA, formatTime(zmanimCal.sofZmanShmaMGA.value, geo.timeZone, is24h)),
            ZmanDisplay("tzais72", "Tzais (72 min)", zmanimCal.tzais72, formatTime(zmanimCal.tzais72.value, geo.timeZone, is24h)),
            ZmanDisplay("candleLighting", "Candle Lighting", zmanimCal.candleLighting, formatTime(zmanimCal.candleLighting.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaGRA", "Sof Zman Tefila (GRA)", zmanimCal.sofZmanTfilaGRA, formatTime(zmanimCal.sofZmanTfilaGRA.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA", "Sof Zman Tefila (MGA)", zmanimCal.sofZmanTfilaMGA, formatTime(zmanimCal.sofZmanTfilaMGA.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaGedola", "Mincha Gedola", zmanimCal.minchaGedola, formatTime(zmanimCal.minchaGedola.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaKetana", "Mincha Ketana", zmanimCal.minchaKetana, formatTime(zmanimCal.minchaKetana.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha", "Plag Hamincha", zmanimCal.plagHamincha, formatTime(zmanimCal.plagHamincha.value, geo.timeZone, is24h)),
                    ZmanDisplay("plagHamincha120MinutesZmanis", "Plag Hamincha (120 min Zmanis)", zmanimCal.plagHamincha120MinutesZmanis, formatTime(zmanimCal.plagHamincha120MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha120Minutes", "Plag Hamincha (120 min)", zmanimCal.plagHamincha120Minutes, formatTime(zmanimCal.plagHamincha120Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("alos60", "Alos (60 min)", zmanimCal.alos60, formatTime(zmanimCal.alos60.value, geo.timeZone, is24h)),
            ZmanDisplay("alos72Zmanis", "Alos (72 min Zmanis)", zmanimCal.alos72Zmanis, formatTime(zmanimCal.alos72Zmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("alos96", "Alos (96 min)", zmanimCal.alos96, formatTime(zmanimCal.alos96.value, geo.timeZone, is24h)),
            ZmanDisplay("alos90Zmanis", "Alos (90 min Zmanis)", zmanimCal.alos90Zmanis, formatTime(zmanimCal.alos90Zmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("alos96Zmanis", "Alos (96 min Zmanis)", zmanimCal.alos96Zmanis, formatTime(zmanimCal.alos96Zmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("alos90", "Alos (90 min)", zmanimCal.alos90, formatTime(zmanimCal.alos90.value, geo.timeZone, is24h)),
            ZmanDisplay("alos120", "Alos (120 min)", zmanimCal.alos120, formatTime(zmanimCal.alos120.value, geo.timeZone, is24h)),
            ZmanDisplay("alos120Zmanis", "Alos (120 min Zmanis)", zmanimCal.alos120Zmanis, formatTime(zmanimCal.alos120Zmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("alos26Degrees", "Alos (26°)", zmanimCal.alos26Degrees, formatTime(zmanimCal.alos26Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("alos18Degrees", "Alos (18°)", zmanimCal.alos18Degrees, formatTime(zmanimCal.alos18Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("alos19Degrees", "Alos (19°)", zmanimCal.alos19Degrees, formatTime(zmanimCal.alos19Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("alos19Point8Degrees", "Alos (19.8°)", zmanimCal.alos19Point8Degrees, formatTime(zmanimCal.alos19Point8Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("alos16Point1Degrees", "Alos (16.1°)", zmanimCal.alos16Point1Degrees, formatTime(zmanimCal.alos16Point1Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("misheyakir11Point5Degrees", "Misheyakir (11.5°)", zmanimCal.misheyakir11Point5Degrees, formatTime(zmanimCal.misheyakir11Point5Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("misheyakir11Degrees", "Misheyakir (11°)", zmanimCal.misheyakir11Degrees, formatTime(zmanimCal.misheyakir11Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("misheyakir10Point2Degrees", "Misheyakir (10.2°)", zmanimCal.misheyakir10Point2Degrees, formatTime(zmanimCal.misheyakir10Point2Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("misheyakir7Point65Degrees", "Misheyakir (7.65°)", zmanimCal.misheyakir7Point65Degrees, formatTime(zmanimCal.misheyakir7Point65Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("misheyakir9Point5Degrees", "Misheyakir (9.5°)", zmanimCal.misheyakir9Point5Degrees, formatTime(zmanimCal.misheyakir9Point5Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA19Point8Degrees", "Sof Zman Shema (MGA, 19.8°)", zmanimCal.sofZmanShmaMGA19Point8Degrees, formatTime(zmanimCal.sofZmanShmaMGA19Point8Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA16Point1Degrees", "Sof Zman Shema (MGA, 16.1°)", zmanimCal.sofZmanShmaMGA16Point1Degrees, formatTime(zmanimCal.sofZmanShmaMGA16Point1Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA18Degrees", "Sof Zman Shema (MGA, 18°)", zmanimCal.sofZmanShmaMGA18Degrees, formatTime(zmanimCal.sofZmanShmaMGA18Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA72Minutes", "Sof Zman Shema (MGA, 72Minutes)", zmanimCal.sofZmanShmaMGA72Minutes, formatTime(zmanimCal.sofZmanShmaMGA72Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA72MinutesZmanis", "sofZmanShmaMGA72MinutesZmanis", zmanimCal.sofZmanShmaMGA72MinutesZmanis, formatTime(zmanimCal.sofZmanShmaMGA72MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA90Minutes", "sofZmanShmaMGA90Minutes", zmanimCal.sofZmanShmaMGA90Minutes, formatTime(zmanimCal.sofZmanShmaMGA90Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA90MinutesZmanis", "sofZmanShmaMGA90MinutesZmanis", zmanimCal.sofZmanShmaMGA90MinutesZmanis, formatTime(zmanimCal.sofZmanShmaMGA90MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA96Minutes", "sofZmanShmaMGA96Minutes", zmanimCal.sofZmanShmaMGA96Minutes, formatTime(zmanimCal.sofZmanShmaMGA96Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA96MinutesZmanis", "sofZmanShmaMGA96MinutesZmanis", zmanimCal.sofZmanShmaMGA96MinutesZmanis, formatTime(zmanimCal.sofZmanShmaMGA96MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShma3HoursBeforeChatzos", "sofZmanShma3HoursBeforeChatzos", zmanimCal.sofZmanShma3HoursBeforeChatzos, formatTime(zmanimCal.sofZmanShma3HoursBeforeChatzos.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaMGA120Minutes", "sofZmanShmaMGA120Minutes", zmanimCal.sofZmanShmaMGA120Minutes, formatTime(zmanimCal.sofZmanShmaMGA120Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaAlos16Point1ToSunset", "sofZmanShmaAlos16Point1ToSunset", zmanimCal.sofZmanShmaAlos16Point1ToSunset, formatTime(zmanimCal.sofZmanShmaAlos16Point1ToSunset.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees", "sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees", zmanimCal.sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees, formatTime(zmanimCal.sofZmanShmaAlos16Point1ToTzaisGeonim7Point083Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA19Point8Degrees", "sofZmanTfilaMGA19Point8Degrees", zmanimCal.sofZmanTfilaMGA19Point8Degrees, formatTime(zmanimCal.sofZmanTfilaMGA19Point8Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA16Point1Degrees", "sofZmanTfilaMGA16Point1Degrees", zmanimCal.sofZmanTfilaMGA16Point1Degrees, formatTime(zmanimCal.sofZmanTfilaMGA16Point1Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA18Degrees", "sofZmanTfilaMGA18Degrees", zmanimCal.sofZmanTfilaMGA18Degrees, formatTime(zmanimCal.sofZmanTfilaMGA18Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA72Minutes", "sofZmanTfilaMGA72Minutes", zmanimCal.sofZmanTfilaMGA72Minutes, formatTime(zmanimCal.sofZmanTfilaMGA72Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA72MinutesZmanis", "sofZmanTfilaMGA72MinutesZmanis", zmanimCal.sofZmanTfilaMGA72MinutesZmanis, formatTime(zmanimCal.sofZmanTfilaMGA72MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA90Minutes", "sofZmanTfilaMGA90Minutes", zmanimCal.sofZmanTfilaMGA90Minutes, formatTime(zmanimCal.sofZmanTfilaMGA90Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA90MinutesZmanis", "sofZmanTfilaMGA90MinutesZmanis", zmanimCal.sofZmanTfilaMGA90MinutesZmanis, formatTime(zmanimCal.sofZmanTfilaMGA90MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA96Minutes", "sofZmanTfilaMGA96Minutes", zmanimCal.sofZmanTfilaMGA96Minutes, formatTime(zmanimCal.sofZmanTfilaMGA96Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA96MinutesZmanis", "sofZmanTfilaMGA96MinutesZmanis", zmanimCal.sofZmanTfilaMGA96MinutesZmanis, formatTime(zmanimCal.sofZmanTfilaMGA96MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfilaMGA120Minutes", "sofZmanTfilaMGA120Minutes", zmanimCal.sofZmanTfilaMGA120Minutes, formatTime(zmanimCal.sofZmanTfilaMGA120Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("sofZmanTfila2HoursBeforeChatzos", "sofZmanTfila2HoursBeforeChatzos", zmanimCal.sofZmanTfila2HoursBeforeChatzos, formatTime(zmanimCal.sofZmanTfila2HoursBeforeChatzos.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaGedola30Minutes", "minchaGedola30Minutes", zmanimCal.minchaGedola30Minutes, formatTime(zmanimCal.minchaGedola30Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaGedola72Minutes", "minchaGedola72Minutes", zmanimCal.minchaGedola72Minutes, formatTime(zmanimCal.minchaGedola72Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaGedola16Point1Degrees", "minchaGedola16Point1Degrees", zmanimCal.minchaGedola16Point1Degrees, formatTime(zmanimCal.minchaGedola16Point1Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaGedolaAhavatShalom", "minchaGedolaAhavatShalom", zmanimCal.minchaGedolaAhavatShalom, formatTime(zmanimCal.minchaGedolaAhavatShalom.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaGedolaGreaterThan30", "minchaGedolaGreaterThan30", zmanimCal.minchaGedolaGreaterThan30, formatTime(zmanimCal.minchaGedolaGreaterThan30.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaKetana16Point1Degrees", "minchaKetana16Point1Degrees", zmanimCal.minchaKetana16Point1Degrees, formatTime(zmanimCal.minchaKetana16Point1Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaKetanaAhavatShalom", "minchaKetanaAhavatShalom", zmanimCal.minchaKetanaAhavatShalom, formatTime(zmanimCal.minchaKetanaAhavatShalom.value, geo.timeZone, is24h)),
            ZmanDisplay("minchaKetana72Minutes", "minchaKetana72Minutes", zmanimCal.minchaKetana72Minutes, formatTime(zmanimCal.minchaKetana72Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha60Minutes", "plagHamincha60Minutes", zmanimCal.plagHamincha60Minutes, formatTime(zmanimCal.plagHamincha60Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha72Minutes", "plagHamincha72Minutes", zmanimCal.plagHamincha72Minutes, formatTime(zmanimCal.plagHamincha72Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha90Minutes", "plagHamincha90Minutes", zmanimCal.plagHamincha90Minutes, formatTime(zmanimCal.plagHamincha90Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha96Minutes", "plagHamincha96Minutes", zmanimCal.plagHamincha96Minutes, formatTime(zmanimCal.plagHamincha96Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha96MinutesZmanis", "plagHamincha96MinutesZmanis", zmanimCal.plagHamincha96MinutesZmanis, formatTime(zmanimCal.plagHamincha96MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha90MinutesZmanis", "plagHamincha90MinutesZmanis", zmanimCal.plagHamincha90MinutesZmanis, formatTime(zmanimCal.plagHamincha90MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha72MinutesZmanis", "plagHamincha72MinutesZmanis", zmanimCal.plagHamincha72MinutesZmanis, formatTime(zmanimCal.plagHamincha72MinutesZmanis.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha16Point1Degrees", "plagHamincha16Point1Degrees", zmanimCal.plagHamincha16Point1Degrees, formatTime(zmanimCal.plagHamincha16Point1Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha19Point8Degrees", "plagHamincha19Point8Degrees", zmanimCal.plagHamincha19Point8Degrees, formatTime(zmanimCal.plagHamincha19Point8Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha26Degrees", "plagHamincha26Degrees", zmanimCal.plagHamincha26Degrees, formatTime(zmanimCal.plagHamincha26Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("plagHamincha18Degrees", "plagHamincha18Degrees", zmanimCal.plagHamincha18Degrees, formatTime(zmanimCal.plagHamincha18Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("plagAlosToSunset", "plagAlosToSunset", zmanimCal.plagAlosToSunset, formatTime(zmanimCal.plagAlosToSunset.value, geo.timeZone, is24h)),
            ZmanDisplay("plagAlos16Point1ToTzaisGeonim7Point083Degrees", "plagAlos16Point1ToTzaisGeonim7Point083Degrees", zmanimCal.plagAlos16Point1ToTzaisGeonim7Point083Degrees, formatTime(zmanimCal.plagAlos16Point1ToTzaisGeonim7Point083Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("plagAhavatShalom", "plagAhavatShalom", zmanimCal.plagAhavatShalom, formatTime(zmanimCal.plagAhavatShalom.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosRT13Point24Degrees", "bainHashmashosRT13Point24Degrees", zmanimCal.bainHashmashosRT13Point24Degrees, formatTime(zmanimCal.bainHashmashosRT13Point24Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosRT58Point5Minutes", "bainHashmashosRT58Point5Minutes", zmanimCal.bainHashmashosRT58Point5Minutes, formatTime(zmanimCal.bainHashmashosRT58Point5Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosRT13Point5MinutesBefore7Point083Degrees", "bainHashmashosRT13Point5MinutesBefore7Point083Degrees", zmanimCal.bainHashmashosRT13Point5MinutesBefore7Point083Degrees, formatTime(zmanimCal.bainHashmashosRT13Point5MinutesBefore7Point083Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosRT2Stars", "bainHashmashosRT2Stars", zmanimCal.bainHashmashosRT2Stars, formatTime(zmanimCal.bainHashmashosRT2Stars.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosYereim18Minutes", "bainHashmashosYereim18Minutes", zmanimCal.bainHashmashosYereim18Minutes, formatTime(zmanimCal.bainHashmashosYereim18Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosYereim3Point05Degrees", "bainHashmashosYereim3Point05Degrees", zmanimCal.bainHashmashosYereim3Point05Degrees, formatTime(zmanimCal.bainHashmashosYereim3Point05Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosYereim16Point875Minutes", "bainHashmashosYereim16Point875Minutes", zmanimCal.bainHashmashosYereim16Point875Minutes, formatTime(zmanimCal.bainHashmashosYereim16Point875Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosYereim2Point8Degrees", "bainHashmashosYereim2Point8Degrees", zmanimCal.bainHashmashosYereim2Point8Degrees, formatTime(zmanimCal.bainHashmashosYereim2Point8Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosYereim13Point5Minutes", "bainHashmashosYereim13Point5Minutes", zmanimCal.bainHashmashosYereim13Point5Minutes, formatTime(zmanimCal.bainHashmashosYereim13Point5Minutes.value, geo.timeZone, is24h)),
            ZmanDisplay("bainHashmashosYereim2Point1Degrees", "bainHashmashosYereim2Point1Degrees", zmanimCal.bainHashmashosYereim2Point1Degrees, formatTime(zmanimCal.bainHashmashosYereim2Point1Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("sunset", "sunset", Zman.DateBased(
                    ZmanDefinition(
                        ZmanType.SHKIAH,
                        ZmanAuthority.Unanimous,
                        UsesElevation.IF_SET
                    )
                ) {zmanimCal.sunset }, formatTime(zmanimCal.sunset, geo.timeZone, is24h)),
    ZmanDisplay("seaLevelSunset", "seaLevelSunset", Zman.DateBased(
                    ZmanDefinition(
                        ZmanType.SHKIAH, ZmanAuthority.Unanimous, UsesElevation.NEVER
                    )
                ) { zmanimCal.seaLevelSunset }, formatTime(zmanimCal.seaLevelSunset, geo.timeZone, is24h)),
            ZmanDisplay("sunrise", "sunrise", Zman.DateBased(
                    ZmanDefinition(
                        ZmanType.HANAITZ,
                        ZmanAuthority.Unanimous,
                        UsesElevation.IF_SET
                    )
                ) { zmanimCal.sunrise }, formatTime(zmanimCal.sunrise, geo.timeZone, is24h)),
            ZmanDisplay("seaLevelSunrise", "seaLevelSunrise", Zman.DateBased(
                    ZmanDefinition(
                        ZmanType.HANAITZ, ZmanAuthority.Unanimous, UsesElevation.NEVER
                    )
                ) { zmanimCal.seaLevelSunrise }, formatTime(zmanimCal.seaLevelSunrise, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim3Point7Degrees", "tzaisGeonim3Point7Degrees", zmanimCal.tzaisGeonim3Point7Degrees, formatTime(zmanimCal.tzaisGeonim3Point7Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim3Point8Degrees", "tzaisGeonim3Point8Degrees", zmanimCal.tzaisGeonim3Point8Degrees, formatTime(zmanimCal.tzaisGeonim3Point8Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim5Point95Degrees", "tzaisGeonim5Point95Degrees", zmanimCal.tzaisGeonim5Point95Degrees, formatTime(zmanimCal.tzaisGeonim5Point95Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim3Point65Degrees", "tzaisGeonim3Point65Degrees", zmanimCal.tzaisGeonim3Point65Degrees, formatTime(zmanimCal.tzaisGeonim3Point65Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim3Point676Degrees", "tzaisGeonim3Point676Degrees", zmanimCal.tzaisGeonim3Point676Degrees, formatTime(zmanimCal.tzaisGeonim3Point676Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim4Point61Degrees", "tzaisGeonim4Point61Degrees", zmanimCal.tzaisGeonim4Point61Degrees, formatTime(zmanimCal.tzaisGeonim4Point61Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim4Point37Degrees", "tzaisGeonim4Point37Degrees", zmanimCal.tzaisGeonim4Point37Degrees, formatTime(zmanimCal.tzaisGeonim4Point37Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim5Point88Degrees", "tzaisGeonim5Point88Degrees", zmanimCal.tzaisGeonim5Point88Degrees, formatTime(zmanimCal.tzaisGeonim5Point88Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim4Point8Degrees", "tzaisGeonim4Point8Degrees", zmanimCal.tzaisGeonim4Point8Degrees, formatTime(zmanimCal.tzaisGeonim4Point8Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim6Point45Degrees", "tzaisGeonim6Point45Degrees", zmanimCal.tzaisGeonim6Point45Degrees, formatTime(zmanimCal.tzaisGeonim6Point45Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim7Point083Degrees", "tzaisGeonim7Point083Degrees", zmanimCal.tzaisGeonim7Point083Degrees, formatTime(zmanimCal.tzaisGeonim7Point083Degrees.value, geo.timeZone, is24h)),
            ZmanDisplay("tzaisGeonim7Point67Degrees", "tzaisGeonim7Point67Degrees", zmanimCal.tzaisGeonim7Point67Degrees, formatTime(zmanimCal.tzaisGeonim7Point67Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("tzaisGeonim8Point5Degrees", "tzaisGeonim8Point5Degrees", zmanimCal.tzaisGeonim8Point5Degrees, formatTime(zmanimCal.tzaisGeonim8Point5Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("tzaisGeonim9Point3Degrees", "tzaisGeonim9Point3Degrees", zmanimCal.tzaisGeonim9Point3Degrees, formatTime(zmanimCal.tzaisGeonim9Point3Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("tzaisGeonim9Point75Degrees", "tzaisGeonim9Point75Degrees", zmanimCal.tzaisGeonim9Point75Degrees, formatTime(zmanimCal.tzaisGeonim9Point75Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais60", "tzais60", zmanimCal.tzais60, formatTime(zmanimCal.tzais60.value, geo.timeZone, is24h)),
                ZmanDisplay("tzaisAteretTorah", "tzaisAteretTorah", zmanimCal.tzaisAteretTorah, formatTime(zmanimCal.tzaisAteretTorah.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanShmaAteretTorah", "sofZmanShmaAteretTorah", zmanimCal.sofZmanShmaAteretTorah, formatTime(zmanimCal.sofZmanShmaAteretTorah.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanTfilahAteretTorah", "sofZmanTfilahAteretTorah", zmanimCal.sofZmanTfilahAteretTorah, formatTime(zmanimCal.sofZmanTfilahAteretTorah.value, geo.timeZone, is24h)),
                ZmanDisplay("minchaGedolaAteretTorah", "minchaGedolaAteretTorah", zmanimCal.minchaGedolaAteretTorah, formatTime(zmanimCal.minchaGedolaAteretTorah.value, geo.timeZone, is24h)),
                ZmanDisplay("minchaKetanaAteretTorah", "minchaKetanaAteretTorah", zmanimCal.minchaKetanaAteretTorah, formatTime(zmanimCal.minchaKetanaAteretTorah.value, geo.timeZone, is24h)),
                ZmanDisplay("plagHaminchaAteretTorah", "plagHaminchaAteretTorah", zmanimCal.plagHaminchaAteretTorah, formatTime(zmanimCal.plagHaminchaAteretTorah.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais72Zmanis", "tzais72Zmanis", zmanimCal.tzais72Zmanis, formatTime(zmanimCal.tzais72Zmanis.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais90Zmanis", "tzais90Zmanis", zmanimCal.tzais90Zmanis, formatTime(zmanimCal.tzais90Zmanis.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais96Zmanis", "tzais96Zmanis", zmanimCal.tzais96Zmanis, formatTime(zmanimCal.tzais96Zmanis.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais90", "tzais90", zmanimCal.tzais90, formatTime(zmanimCal.tzais90.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais120", "tzais120", zmanimCal.tzais120, formatTime(zmanimCal.tzais120.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais120Zmanis", "tzais120Zmanis", zmanimCal.tzais120Zmanis, formatTime(zmanimCal.tzais120Zmanis.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais16Point1Degrees", "tzais16Point1Degrees", zmanimCal.tzais16Point1Degrees, formatTime(zmanimCal.tzais16Point1Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais26Degrees", "tzais26Degrees", zmanimCal.tzais26Degrees, formatTime(zmanimCal.tzais26Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais18Degrees", "tzais18Degrees", zmanimCal.tzais18Degrees, formatTime(zmanimCal.tzais18Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais19Point8Degrees", "tzais19Point8Degrees", zmanimCal.tzais19Point8Degrees, formatTime(zmanimCal.tzais19Point8Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais96", "tzais96", zmanimCal.tzais96, formatTime(zmanimCal.tzais96.value, geo.timeZone, is24h)),
                ZmanDisplay("fixedLocalChatzos", "fixedLocalChatzos", zmanimCal.fixedLocalChatzos, formatTime(zmanimCal.fixedLocalChatzos.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanKidushLevanaBetweenMoldos", "sofZmanKidushLevanaBetweenMoldos", zmanimCal.sofZmanKidushLevanaBetweenMoldos, formatTime(zmanimCal.sofZmanKidushLevanaBetweenMoldos.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanKidushLevana15Days", "sofZmanKidushLevana15Days", zmanimCal.sofZmanKidushLevana15Days, formatTime(zmanimCal.sofZmanKidushLevana15Days.value, geo.timeZone, is24h)),
                ZmanDisplay("tchilasZmanKidushLevana3Days", "tchilasZmanKidushLevana3Days", zmanimCal.tchilasZmanKidushLevana3Days, formatTime(zmanimCal.tchilasZmanKidushLevana3Days.value, geo.timeZone, is24h)),
                ZmanDisplay("zmanMolad", "zmanMolad", zmanimCal.zmanMolad, formatTime(zmanimCal.zmanMolad.value, geo.timeZone, is24h)),
                ZmanDisplay("tchilasZmanKidushLevana7Days", "tchilasZmanKidushLevana7Days", zmanimCal.tchilasZmanKidushLevana7Days, formatTime(zmanimCal.tchilasZmanKidushLevana7Days.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanAchilasChametzGRA", "sofZmanAchilasChametzGRA", zmanimCal.sofZmanAchilasChametzGRA, formatTime(zmanimCal.sofZmanAchilasChametzGRA.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanAchilasChametzMGA72Minutes", "sofZmanAchilasChametzMGA72Minutes", zmanimCal.sofZmanAchilasChametzMGA72Minutes, formatTime(zmanimCal.sofZmanAchilasChametzMGA72Minutes.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanAchilasChametzMGA16Point1Degrees", "sofZmanAchilasChametzMGA16Point1Degrees", zmanimCal.sofZmanAchilasChametzMGA16Point1Degrees, formatTime(zmanimCal.sofZmanAchilasChametzMGA16Point1Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanBiurChametzGRA", "sofZmanBiurChametzGRA", zmanimCal.sofZmanBiurChametzGRA, formatTime(zmanimCal.sofZmanBiurChametzGRA.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanBiurChametzMGA72Minutes", "sofZmanBiurChametzMGA72Minutes", zmanimCal.sofZmanBiurChametzMGA72Minutes, formatTime(zmanimCal.sofZmanBiurChametzMGA72Minutes.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanBiurChametzMGA16Point1Degrees", "sofZmanBiurChametzMGA16Point1Degrees", zmanimCal.sofZmanBiurChametzMGA16Point1Degrees, formatTime(zmanimCal.sofZmanBiurChametzMGA16Point1Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("solarMidnight", "solarMidnight", zmanimCal.solarMidnight, formatTime(zmanimCal.solarMidnight.value, geo.timeZone, is24h)),
                ZmanDisplay("alosBaalHatanya", "alosBaalHatanya", zmanimCal.alosBaalHatanya, formatTime(zmanimCal.alosBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanShmaBaalHatanya", "sofZmanShmaBaalHatanya", zmanimCal.sofZmanShmaBaalHatanya, formatTime(zmanimCal.sofZmanShmaBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanTfilaBaalHatanya", "sofZmanTfilaBaalHatanya", zmanimCal.sofZmanTfilaBaalHatanya, formatTime(zmanimCal.sofZmanTfilaBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanAchilasChametzBaalHatanya", "sofZmanAchilasChametzBaalHatanya", zmanimCal.sofZmanAchilasChametzBaalHatanya, formatTime(zmanimCal.sofZmanAchilasChametzBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanBiurChametzBaalHatanya", "sofZmanBiurChametzBaalHatanya", zmanimCal.sofZmanBiurChametzBaalHatanya, formatTime(zmanimCal.sofZmanBiurChametzBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("minchaGedolaBaalHatanya", "minchaGedolaBaalHatanya", zmanimCal.minchaGedolaBaalHatanya, formatTime(zmanimCal.minchaGedolaBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("minchaGedolaBaalHatanyaGreaterThan30", "minchaGedolaBaalHatanyaGreaterThan30", zmanimCal.minchaGedolaBaalHatanyaGreaterThan30, formatTime(zmanimCal.minchaGedolaBaalHatanyaGreaterThan30.value, geo.timeZone, is24h)),
                ZmanDisplay("minchaKetanaBaalHatanya", "minchaKetanaBaalHatanya", zmanimCal.minchaKetanaBaalHatanya, formatTime(zmanimCal.minchaKetanaBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("plagHaminchaBaalHatanya", "plagHaminchaBaalHatanya", zmanimCal.plagHaminchaBaalHatanya, formatTime(zmanimCal.plagHaminchaBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("tzaisBaalHatanya", "tzaisBaalHatanya", zmanimCal.tzaisBaalHatanya, formatTime(zmanimCal.tzaisBaalHatanya.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanShmaMGA18DegreesToFixedLocalChatzos", "sofZmanShmaMGA18DegreesToFixedLocalChatzos", zmanimCal.sofZmanShmaMGA18DegreesToFixedLocalChatzos, formatTime(zmanimCal.sofZmanShmaMGA18DegreesToFixedLocalChatzos.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos", "sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos", zmanimCal.sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos, formatTime(zmanimCal.sofZmanShmaMGA16Point1DegreesToFixedLocalChatzos.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanShmaMGA90MinutesToFixedLocalChatzos", "sofZmanShmaMGA90MinutesToFixedLocalChatzos", zmanimCal.sofZmanShmaMGA90MinutesToFixedLocalChatzos, formatTime(zmanimCal.sofZmanShmaMGA90MinutesToFixedLocalChatzos.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanShmaMGA72MinutesToFixedLocalChatzos", "sofZmanShmaMGA72MinutesToFixedLocalChatzos", zmanimCal.sofZmanShmaMGA72MinutesToFixedLocalChatzos, formatTime(zmanimCal.sofZmanShmaMGA72MinutesToFixedLocalChatzos.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanShmaGRASunriseToFixedLocalChatzos", "sofZmanShmaGRASunriseToFixedLocalChatzos", zmanimCal.sofZmanShmaGRASunriseToFixedLocalChatzos, formatTime(zmanimCal.sofZmanShmaGRASunriseToFixedLocalChatzos.value, geo.timeZone, is24h)),
                ZmanDisplay("sofZmanTfilaGRASunriseToFixedLocalChatzos", "sofZmanTfilaGRASunriseToFixedLocalChatzos", zmanimCal.sofZmanTfilaGRASunriseToFixedLocalChatzos, formatTime(zmanimCal.sofZmanTfilaGRASunriseToFixedLocalChatzos.value, geo.timeZone, is24h)),
                ZmanDisplay("minchaGedolaGRAFixedLocalChatzos30Minutes", "minchaGedolaGRAFixedLocalChatzos30Minutes", zmanimCal.minchaGedolaGRAFixedLocalChatzos30Minutes, formatTime(zmanimCal.minchaGedolaGRAFixedLocalChatzos30Minutes.value, geo.timeZone, is24h)),
                ZmanDisplay("minchaKetanaGRAFixedLocalChatzosToSunset", "minchaKetanaGRAFixedLocalChatzosToSunset", zmanimCal.minchaKetanaGRAFixedLocalChatzosToSunset, formatTime(zmanimCal.minchaKetanaGRAFixedLocalChatzosToSunset.value, geo.timeZone, is24h)),
                ZmanDisplay("plagHaminchaGRAFixedLocalChatzosToSunset", "plagHaminchaGRAFixedLocalChatzosToSunset", zmanimCal.plagHaminchaGRAFixedLocalChatzosToSunset, formatTime(zmanimCal.plagHaminchaGRAFixedLocalChatzosToSunset.value, geo.timeZone, is24h)),
                ZmanDisplay("tzais50", "tzais50", zmanimCal.tzais50, formatTime(zmanimCal.tzais50.value, geo.timeZone, is24h)),
                ZmanDisplay("samuchLeMinchaKetanaGRA", "samuchLeMinchaKetanaGRA", zmanimCal.samuchLeMinchaKetanaGRA, formatTime(zmanimCal.samuchLeMinchaKetanaGRA.value, geo.timeZone, is24h)),
                ZmanDisplay("samuchLeMinchaKetana16Point1Degrees", "samuchLeMinchaKetana16Point1Degrees", zmanimCal.samuchLeMinchaKetana16Point1Degrees, formatTime(zmanimCal.samuchLeMinchaKetana16Point1Degrees.value, geo.timeZone, is24h)),
                ZmanDisplay("samuchLeMinchaKetana72Minutes", "samuchLeMinchaKetana72Minutes", zmanimCal.samuchLeMinchaKetana72Minutes, formatTime(zmanimCal.samuchLeMinchaKetana72Minutes.value, geo.timeZone, is24h)),
            ).sortedBy { it.zman.value }
        return zmanim
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
 }

 data class UiState(
     val location: String? = "Jerusalem",
     val isRefreshing: Boolean = false,
     val isStale: Boolean = false,
     val error: String? = null,
     val zmanim: List<ZmanDisplay> = emptyList(),
     val disabledIds: Set<ZmanType> = emptySet(),
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

 data class ZmanDisplay(
     val key: String,
     val name: String,
     val zman: Zman.DateBased,
     val timeText: String
 )

