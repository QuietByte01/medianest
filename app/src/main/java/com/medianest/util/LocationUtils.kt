package com.medianest.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.medianest.data.db.LocationCache
import com.medianest.data.db.LocationCacheDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class VideoLocationResult(
    val placeName: String,
    val isLocationFallback: Boolean
)

object LocationUtils {

    /**
     * Extracts embedded GPS location from a video and resolves it to a human-readable place name.
     * Returns VideoLocationResult indicating whether it resolved a real GPS place or fell back to category/folder.
     */
    suspend fun getVideoLocationResult(
        context: Context,
        uri: Uri,
        fallbackCategory: String,
        locationCacheDao: LocationCacheDao? = null
    ): VideoLocationResult = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()

        // 1. Check Cache First
        locationCacheDao?.getLocation(uriStr)?.let { cached ->
            val isFallback = cached.latitude == null || cached.longitude == null
            return@withContext VideoLocationResult(cached.placeName, isLocationFallback = isFallback)
        }

        // 2. Extract metadata
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
            if (locationCacheDao != null) {
                locationCacheDao.saveLocation(LocationCache(uriStr, fallbackCategory))
            }
            return@withContext VideoLocationResult(fallbackCategory, isLocationFallback = true)
        }

        // 4. Parse and Geocode
        val coords = parseIso6709Location(locationString)
        val resolvedName = if (coords != null) {
            reverseGeocode(context, coords.first, coords.second)
        } else null

        val isFallback = resolvedName == null
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

        return@withContext VideoLocationResult(finalPlaceName, isLocationFallback = isFallback)
    }



    /**
     * Parses ISO-6709 format: "+37.7510-122.4200/" or "+37.7510-122.4200+10.0/" into Pair(Latitude, Longitude)
     */
    fun parseIso6709Location(location: String): Pair<Double, Double>? {
        return runCatching {
            val clean = location.trimEnd('/')
            // Match ISO-6709 coordinates: +lat-lon with optional +alt
            val regex = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)")
            val match = regex.find(clean) ?: return null

            val lat = match.groupValues[1].toDoubleOrNull() ?: return null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return null

            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                Pair(lat, lon)
            } else null
        }.getOrNull()
    }

    suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        try {
                            geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (continuation.isActive) {
                                        continuation.resume(addresses)
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                }
                            })
                        } catch (e: Exception) {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)
                }

                val address = addresses?.firstOrNull() ?: return@runCatching null
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
}
