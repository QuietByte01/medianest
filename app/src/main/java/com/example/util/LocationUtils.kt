package com.example.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.example.data.db.LocationCache
import com.example.data.db.LocationCacheDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object LocationUtils {

    /**
     * Extracts embedded GPS location from a video and resolves it to a human-readable place name.
     * Leverages Room database for caching to avoid repeated Geocoder and MetadataRetriever calls.
     */
    suspend fun getPlaceNameFromVideo(
        context: Context,
        uri: Uri,
        fallbackCategory: String,
        locationCacheDao: LocationCacheDao? = null
    ): String = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()

        // 1. Check Cache First
        if (locationCacheDao != null) {
            val cached = locationCacheDao.getLocation(uriStr)
            if (cached != null) return@withContext cached.placeName
        }

        // 2. Not in cache, proceed to extract metadata
        val retriever = MediaMetadataRetriever()
        var locationString: String? = null

        try {
            retriever.setDataSource(context, uri)
            // Extract ISO-6709 location string (e.g. "+37.7510-122.4200/" or "+37.7510-122.4200+010.000/")
            locationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { retriever.release() }
        }

        // 3. Handle absence of location data
        if (locationString.isNullOrBlank()) {
            // Save fallback result to cache if it doesn't exist to avoid re-scanning metadata
            if (locationCacheDao != null) {
                locationCacheDao.saveLocation(LocationCache(uriStr, fallbackCategory))
            }
            return@withContext fallbackCategory
        }

        // 4. Parse and Geocode
        val coords = parseIso6709Location(locationString)
        val resolvedName = if (coords != null) {
            reverseGeocode(context, coords.first, coords.second)
        } else null

        val finalPlaceName = resolvedName ?: fallbackCategory

        // 5. Save to Cache
        if (locationCacheDao != null) {
            locationCacheDao.saveLocation(
                LocationCache(
                    mediaUri = uriStr,
                    placeName = finalPlaceName,
                    latitude = coords?.first,
                    longitude = coords?.second
                )
            )
        }

        return@withContext finalPlaceName
    }

    /**
     * Parses ISO-6709 format: "+37.7510-122.4200/" into Pair(Latitude, Longitude)
     */
    private fun parseIso6709Location(location: String): Pair<Double, Double>? {
        return runCatching {
            val clean = location.trimEnd('/')
            // Match ISO-6709 format like +37.7510-122.4200 or +37.7510-122.4200+10.00
            val regex = Regex("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)")
            val match = regex.find(clean) ?: return null

            val lat = match.groupValues[1].toDouble()
            val lon = match.groupValues[2].toDouble()
            Pair(lat, lon)
        }.getOrNull()
    }

    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null

        return runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var asyncAddresses: List<Address>? = null
                geocoder.getFromLocation(lat, lon, 1) { asyncAddresses = it }
                asyncAddresses
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)
            }

            val address = addresses?.firstOrNull() ?: return null
            val locality = address.locality ?: address.subAdminArea
            val country = address.countryName ?: address.adminArea

            when {
                !locality.isNullOrBlank() && !country.isNullOrBlank() -> "$locality, $country"
                !locality.isNullOrBlank() -> locality
                !country.isNullOrBlank() -> country
                else -> address.featureName
            }
        }.getOrNull()
    }
}
