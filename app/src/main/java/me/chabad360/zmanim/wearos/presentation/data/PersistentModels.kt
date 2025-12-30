package me.chabad360.zmanim.wearos.presentation.data

import io.github.kdroidfilter.kosherkotlin.util.GeoLocation
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import me.chabad360.zmanim.wearos.presentation.ZmanDisplay

/**
 * Serializable representation of a geographic location.
 * Stores all data needed to recreate a GeoLocation for zmanim calculation.
 */
@Serializable
data class SerializableLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timezoneId: String
)
{
    constructor(geo: GeoLocation) : this(
        geo.locationName,
        geo.latitude,
        geo.longitude,
        geo.elevation,
        geo.timeZone.id
    )

    fun toGeoLocation() = GeoLocation(name, latitude, longitude, altitude, TimeZone.of(timezoneId))
}

/**
 * The root persistent state object.
 * Version field enables migration between schema versions.
 *
 * Note: ZmanDisplay is already serializable, so we store it directly.
 */
@Serializable
data class PersistentState(
    val version: Int = CURRENT_VERSION,
    val lastComputedMillis: Long,
    val location: SerializableLocation,
    val zmanim: List<ZmanDisplay>,
    val disabledIds: Set<String> = emptySet()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

