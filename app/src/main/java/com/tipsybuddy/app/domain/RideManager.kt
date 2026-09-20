package com.tipsybuddy.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Calendar
import kotlin.math.*

data class RideFareEstimate(
    val serviceName: String,
    val typeName: String, // "UberX", "Uber XL", "Lyft Standard", "Lyft XL"
    val minPrice: Double,
    val maxPrice: Double,
    val durationMinutes: Int,
    val distanceMiles: Double,
    val iconEmoji: String
)

object RideManager {

    fun calculateDistanceMiles(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) {
            return 4.5 // Default realistic night-out ride distance (miles)
        }
        val earthRadiusMiles = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val dist = earthRadiusMiles * c
        return if (dist < 0.5) 2.5 else dist
    }

    fun getEstimatedFares(
        pickupLat: Double,
        pickupLon: Double,
        destLat: Double,
        destLon: Double
    ): List<RideFareEstimate> {
        var miles = calculateDistanceMiles(pickupLat, pickupLon, destLat, destLon)
        if (miles > 30.0) {
            miles = 4.8 // Realistic urban night-out trip if mock/uncalibrated coordinates
        }
        val avgSpeedMph = 24.0 // City night traffic
        val durationMins = max(7, (miles / avgSpeedMph * 60).toInt())

        // Check if late night (surge multiplier 1.25x between 10pm and 3am)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val surge = if (hour >= 22 || hour <= 3) 1.25 else 1.0

        // UberX: $2.55 base + $1.65/mi + $0.35/min + $3.00 booking fee
        val uberXBase = (2.55 + (miles * 1.65) + (durationMins * 0.35) + 3.00) * surge
        val uberXMin = uberXBase * 0.95
        val uberXMax = uberXBase * 1.15

        // Uber XL: $3.85 base + $2.45/mi + $0.45/min + $3.50 booking fee
        val uberXLBase = (3.85 + (miles * 2.45) + (durationMins * 0.45) + 3.50) * surge
        val uberXLMin = uberXLBase * 0.95
        val uberXLMax = uberXLBase * 1.15

        // Lyft Standard: $2.50 base + $1.60/mi + $0.32/min + $3.00 service fee
        val lyftBase = (2.50 + (miles * 1.60) + (durationMins * 0.32) + 3.00) * surge
        val lyftMin = lyftBase * 0.95
        val lyftMax = lyftBase * 1.15

        // Lyft XL: $3.75 base + $2.40/mi + $0.44/min + $3.50 service fee
        val lyftXLBase = (3.75 + (miles * 2.40) + (durationMins * 0.44) + 3.50) * surge
        val lyftXLMin = lyftXLBase * 0.95
        val lyftXLMax = lyftXLBase * 1.15

        return listOf(
            RideFareEstimate("Uber", "UberX (Affordable 4 seats)", uberXMin, uberXMax, durationMins, miles, "🚗"),
            RideFareEstimate("Uber", "Uber XL (Spacious 6 seats)", uberXLMin, uberXLMax, durationMins, miles, "🚙"),
            RideFareEstimate("Lyft", "Lyft Standard (Reliable 4 seats)", lyftMin, lyftMax, durationMins, miles, "⚡"),
            RideFareEstimate("Lyft", "Lyft XL (Party Group 6 seats)", lyftXLMin, lyftXLMax, durationMins, miles, "🚐")
        )
    }

    fun launchUber(
        context: Context,
        homeAddress: String,
        homeLat: Double,
        homeLon: Double,
        currentLat: Double = 0.0,
        currentLon: Double = 0.0
    ) {
        val encodedAddress = try {
            URLEncoder.encode(homeAddress, "UTF-8")
        } catch (e: Exception) {
            homeAddress
        }

        val nativeUriBuilder = StringBuilder("uber://?action=setPickup")
        if (currentLat != 0.0 && currentLon != 0.0) {
            nativeUriBuilder.append("&pickup[latitude]=$currentLat&pickup[longitude]=$currentLon")
        } else {
            nativeUriBuilder.append("&pickup=my_location")
        }
        if (homeLat != 0.0 && homeLon != 0.0) {
            nativeUriBuilder.append("&dropoff[latitude]=$homeLat&dropoff[longitude]=$homeLon")
        }
        if (encodedAddress.isNotBlank()) {
            nativeUriBuilder.append("&dropoff[formatted_address]=$encodedAddress")
        }

        val nativeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(nativeUriBuilder.toString())).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(nativeIntent)
        } catch (e: Exception) {
            // Web fallback
            val webUriBuilder = StringBuilder("https://m.uber.com/ul/?action=setPickup&client_id=tipsybuddy")
            if (currentLat != 0.0 && currentLon != 0.0) {
                webUriBuilder.append("&pickup[latitude]=$currentLat&pickup[longitude]=$currentLon")
            } else {
                webUriBuilder.append("&pickup=my_location")
            }
            if (homeLat != 0.0 && homeLon != 0.0) {
                webUriBuilder.append("&dropoff[latitude]=$homeLat&dropoff[longitude]=$homeLon")
            }
            if (encodedAddress.isNotBlank()) {
                webUriBuilder.append("&dropoff[formatted_address]=$encodedAddress")
            }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUriBuilder.toString())).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }

    fun launchLyft(
        context: Context,
        homeLat: Double,
        homeLon: Double,
        homeAddress: String
    ) {
        val encodedDest = try {
            URLEncoder.encode(homeAddress, "UTF-8")
        } catch (e: Exception) {
            homeAddress
        }

        val nativeUri = if (homeLat != 0.0 && homeLon != 0.0) {
            "lyft://ridetype?id=lyft&destination[latitude]=$homeLat&destination[longitude]=$homeLon"
        } else if (encodedDest.isNotBlank()) {
            "lyft://ridetype?id=lyft&destination[address]=$encodedDest"
        } else {
            "lyft://ridetype?id=lyft"
        }

        val nativeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(nativeUri)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(nativeIntent)
        } catch (e: Exception) {
            val webUri = if (homeLat != 0.0 && homeLon != 0.0) {
                "https://lyft.com/ride?id=lyft&destination[latitude]=$homeLat&destination[longitude]=$homeLon"
            } else {
                "https://lyft.com/ride?id=lyft"
            }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }

    fun callEmergencyContact(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
