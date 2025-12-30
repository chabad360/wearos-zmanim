package me.chabad360.zmanim.wearos.util

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationHelper {
    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * Resolve place name from coordinates using Geocoder
     */
    suspend fun resolvePlaceName(context: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
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
}