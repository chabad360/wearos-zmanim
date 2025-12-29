package me.chabad360.zmanim.wearos.util

import android.content.Context
import android.text.format.DateFormat
import io.github.kdroidfilter.kosherkotlin.ComplexZmanimCalendar
import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import me.chabad360.zmanim.wearos.presentation.ZmanDisplay
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/**
 * Utility class for calculating zmanim
 */
object ZmanimCalculator {

    /**
     * Compute all zmanim displays for a given location
     */
    @OptIn(ExperimentalTime::class)
    fun calculateZmanim(
        context: Context,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        locationName: String,
        timezoneId: String
    ): List<ZmanDisplay> {
        val geo = GeoLocation(
            locationName,
            latitude,
            longitude,
            altitude,
            TimeZone.of(timezoneId)
        )
        val cal = ComplexZmanimCalendar(geo)
        val today =  computeZmanim(cal, context)
        val tomorrowCal = ComplexZmanimCalendar(geo).apply {
            jewishCalendar.setDate(jewishCalendar.gregorianLocalDate.plus(1, DateTimeUnit.DAY))
        }
        val tomorrow = computeZmanim(tomorrowCal, context)
        return today + tomorrow
    }

    /**
     * Compute all zmanim for the given calendar
     */
    @OptIn(ExperimentalTime::class)
    fun computeZmanim(zmanimCal: ComplexZmanimCalendar, context: Context): List<ZmanDisplay> {
        val geo = zmanimCal.geoLocation
        val is24h = DateFormat.is24HourFormat(context)

        return listOf(
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
            ZmanDisplay("sunset", "Shekia", zmanimCal.sunset?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("seaLevelSunset", "Shekia (Sea Level)", zmanimCal.seaLevelSunset?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sunrise", "Neitz", zmanimCal.sunrise?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("seaLevelSunrise", "Neitz (Sea Level)", zmanimCal.seaLevelSunrise?.epochSeconds, geo.timeZone, is24h),
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
            ZmanDisplay("zmanMolad", "Molad", zmanimCal.zmanMolad.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("tchilasZmanKidushLevana7Days", "Tchilas Zman Kiddush Levana (7 days)", zmanimCal.tchilasZmanKidushLevana7Days.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanAchilasChametzGRA", "Sof Zman Achilas Chametz (GRA)", zmanimCal.sofZmanAchilasChametzGRA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanAchilasChametzMGA72Minutes", "Sof Zman Achilas Chametz (MGA, 72 min)", zmanimCal.sofZmanAchilasChametzMGA72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanAchilasChametzMGA16Point1Degrees", "Sof Zman Achilas Chametz (MGA, 16.1°)", zmanimCal.sofZmanAchilasChametzMGA16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanBiurChametzGRA", "Sof Zman Biur Chametz (GRA)", zmanimCal.sofZmanBiurChametzGRA.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanBiurChametzMGA72Minutes", "Sof Zman Biur Chametz (MGA, 72 min)", zmanimCal.sofZmanBiurChametzMGA72Minutes.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("sofZmanBiurChametzMGA16Point1Degrees", "Sof Zman Biur Chametz (MGA, 16.1°)", zmanimCal.sofZmanBiurChametzMGA16Point1Degrees.value?.epochSeconds, geo.timeZone, is24h),
            ZmanDisplay("solarMidnight", "Chatzos", zmanimCal.solarMidnight.value?.epochSeconds, geo.timeZone, is24h),
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
    }
}

