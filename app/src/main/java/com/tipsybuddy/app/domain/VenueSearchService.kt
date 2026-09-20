package com.tipsybuddy.app.domain

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class VenueLocationResult(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

class VenueSearchService {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val userAgent = "TipsyBuddy/1.0 (Android; Contact: support@tipsybuddy.app)"

    suspend fun search(query: String, context: Context): List<VenueLocationResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val results = mutableListOf<VenueLocationResult>()

        // 1. Try Android Native Geocoder
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(trimmed, 5)
            if (!addresses.isNullOrEmpty()) {
                for (addr in addresses) {
                    val feature = addr.featureName
                    val street = addr.thoroughfare
                    val houseNum = addr.subThoroughfare
                    val locality = addr.locality ?: addr.subAdminArea ?: ""
                    val adminArea = addr.adminArea ?: ""
                    val postal = addr.postalCode ?: ""

                    val isFeatureJustDigits = feature != null && feature.matches(Regex("^\\d+$"))

                    val title = if (!feature.isNullOrBlank() && !isFeatureJustDigits) {
                        feature
                    } else if (!street.isNullOrBlank()) {
                        listOfNotNull(houseNum, street).filter { it.isNotBlank() }.joinToString(" ")
                    } else {
                        trimmed
                    }

                    val fullStreet = listOfNotNull(houseNum ?: (if (isFeatureJustDigits) feature else null), street)
                        .filter { it.isNotBlank() }.joinToString(" ")
                    val addressLine = addr.getAddressLine(0) ?: listOfNotNull(
                        fullStreet.ifBlank { null },
                        locality.ifBlank { null },
                        adminArea.ifBlank { null },
                        postal.ifBlank { null }
                    ).joinToString(", ")

                    results.add(
                        VenueLocationResult(
                            title = title,
                            address = addressLine,
                            latitude = addr.latitude,
                            longitude = addr.longitude
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("VenueSearchService", "Android Geocoder search error: ${e.message}")
        }

        // 2. OpenStreetMap Nominatim Search (100% free open-source POI & address search)
        // If Android Geocoder didn't return results or for better POI coverage (bars/venues)
        if (results.size < 3) {
            try {
                val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=5"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val array = JSONArray(jsonStr)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val name = obj.optString("name").ifBlank { null }
                            val displayName = obj.optString("display_name")
                            val lat = obj.optDouble("lat", 0.0)
                            val lon = obj.optDouble("lon", 0.0)

                            if (lat == 0.0 && lon == 0.0) continue

                            // Check for duplicates
                            val isDuplicate = results.any {
                                abs(it.latitude - lat) < 0.0005 && abs(it.longitude - lon) < 0.0005
                            }
                            if (isDuplicate) continue

                            val addrObj = obj.optJSONObject("address")
                            val road = addrObj?.optString("road")?.ifBlank { null }
                            val houseNumber = addrObj?.optString("house_number")?.ifBlank { null }
                            val city = addrObj?.optString("city")?.ifBlank { null }
                                ?: addrObj?.optString("town")?.ifBlank { null }
                                ?: addrObj?.optString("village")?.ifBlank { null }
                                ?: addrObj?.optString("suburb")?.ifBlank { null }
                            val state = addrObj?.optString("state")?.ifBlank { null }

                            val title = name ?: if (road != null) {
                                listOfNotNull(houseNumber, road).joinToString(" ")
                            } else {
                                displayName.split(",").firstOrNull()?.trim() ?: trimmed
                            }

                            val cleanAddress = if (road != null && city != null) {
                                listOfNotNull(
                                    listOfNotNull(houseNumber, road).joinToString(" "),
                                    city,
                                    state
                                ).joinToString(", ")
                            } else {
                                displayName
                            }

                            results.add(
                                VenueLocationResult(
                                    title = title,
                                    address = cleanAddress,
                                    latitude = lat,
                                    longitude = lon
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("VenueSearchService", "Nominatim search error: ${e.message}")
            }
        }

        results
    }

    suspend fun reverseGeocode(lat: Double, lon: Double, context: Context): VenueLocationResult = withContext(Dispatchers.IO) {
        // 1. Try Android Native Geocoder
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val feature = addr.featureName
                val street = addr.thoroughfare
                val houseNum = addr.subThoroughfare
                val locality = addr.locality ?: addr.subAdminArea ?: ""
                val adminArea = addr.adminArea ?: ""
                val postal = addr.postalCode ?: ""

                val isFeatureDigits = feature != null && feature.matches(Regex("^\\d+$"))

                val fullStreet = listOfNotNull(houseNum ?: (if (isFeatureDigits) feature else null), street)
                    .filter { it.isNotBlank() }.joinToString(" ")

                val title = if (!feature.isNullOrBlank() && !isFeatureDigits) {
                    feature
                } else if (fullStreet.isNotBlank()) {
                    fullStreet
                } else {
                    locality.ifBlank { "Current Location" }
                }

                val addressLine = addr.getAddressLine(0) ?: listOfNotNull(
                    fullStreet.ifBlank { null },
                    locality.ifBlank { null },
                    adminArea.ifBlank { null },
                    postal.ifBlank { null }
                ).joinToString(", ")

                return@withContext VenueLocationResult(
                    title = title,
                    address = addressLine,
                    latitude = lat,
                    longitude = lon
                )
            }
        } catch (e: Exception) {
            Log.w("VenueSearchService", "Android Geocoder reverse error: ${e.message}")
        }

        // 2. Nominatim Free Reverse Geocode Fallback
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrBlank()) {
                    val obj = JSONObject(jsonStr)
                    val displayName = obj.optString("display_name")
                    val name = obj.optString("name").ifBlank { null }

                    val addrObj = obj.optJSONObject("address")
                    val road = addrObj?.optString("road")?.ifBlank { null }
                    val houseNumber = addrObj?.optString("house_number")?.ifBlank { null }
                    val city = addrObj?.optString("city")?.ifBlank { null }
                        ?: addrObj?.optString("town")?.ifBlank { null }
                        ?: addrObj?.optString("village")?.ifBlank { null }
                        ?: addrObj?.optString("suburb")?.ifBlank { null }
                    val state = addrObj?.optString("state")?.ifBlank { null }

                    val title = name ?: if (road != null) {
                        listOfNotNull(houseNumber, road).joinToString(" ")
                    } else {
                        displayName.split(",").firstOrNull()?.trim() ?: "Current Location"
                    }

                    val cleanAddress = if (road != null && city != null) {
                        listOfNotNull(
                            listOfNotNull(houseNumber, road).joinToString(" "),
                            city,
                            state
                        ).joinToString(", ")
                    } else {
                        displayName
                    }

                    return@withContext VenueLocationResult(
                        title = title,
                        address = cleanAddress,
                        latitude = lat,
                        longitude = lon
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("VenueSearchService", "Nominatim reverse error: ${e.message}")
        }

        VenueLocationResult(
            title = "Current Location",
            address = "Coordinates: ${String.format(Locale.US, "%.4f, %.4f", lat, lon)}",
            latitude = lat,
            longitude = lon
        )
    }
}
