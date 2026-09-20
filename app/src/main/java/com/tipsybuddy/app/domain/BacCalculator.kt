package com.tipsybuddy.app.domain

import com.tipsybuddy.app.data.DrinkEntity
import kotlin.math.max

data class BacResult(
    val bac: Double,
    val zone: BacZone,
    val hoursToSober: Double,
    val standardDrinks: Double,
    val totalCost: Double,
    val waterCount: Int,
    val alcoholCount: Int,
    val hydrationScorePercent: Int,
    val hangoverRisk: HangoverRisk,
    val shouldSuggestTaxi: Boolean
)

enum class BacZone(val title: String, val subtitle: String, val colorHex: Long) {
    SOBER("Sober", "Normal reflexes and clear mind", 0xFF10B981),
    BUZZED("Buzzed", "Relaxed & elevated mood", 0xFF38BDF8),
    TIPSY("Tipsy", "Impaired judgment. Do not drive!", 0xFFF59E0B),
    INTOXICATED("Intoxicated", "Legally drunk. Call an Uber/Lyft now!", 0xFFEF4444),
    HIGH_RISK("High Risk", "Severe impairment. Stop drinking & drink water!", 0xFFDC2626)
}

enum class HangoverRisk(val label: String, val advice: String, val colorHex: Long) {
    LOW("Low", "You should feel fresh tomorrow. Keep drinking water!", 0xFF10B981),
    MODERATE("Moderate", "Drink a large glass of water before sleeping to avoid dry mouth.", 0xFF38BDF8),
    HIGH("High", "Expect a headache tomorrow. Drink electrolytes and eat carbs before bed.", 0xFFF59E0B),
    SEVERE("Severe", "High risk of painful hangover! Stop alcohol immediately, drink water & take B-vitamins.", 0xFFEF4444)
}

data class DrinkPreset(
    val name: String,
    val category: String,
    val volumeOz: Double,
    val abv: Double,
    val defaultPrice: Double,
    val emoji: String
)

object BacCalculator {

    val POPULAR_PRESETS = listOf(
        DrinkPreset("Draft Beer", "Beer", 12.0, 5.0, 7.00, "🍺"),
        DrinkPreset("Craft IPA", "Beer", 16.0, 6.8, 9.00, "🍻"),
        DrinkPreset("Red Wine", "Wine", 5.0, 13.0, 12.00, "🍷"),
        DrinkPreset("White Wine", "Wine", 5.0, 12.0, 11.00, "🥂"),
        DrinkPreset("Margarita", "Cocktail", 6.0, 15.0, 14.00, "🍸"),
        DrinkPreset("Old Fashioned", "Cocktail", 3.5, 32.0, 15.00, "🥃"),
        DrinkPreset("Long Island", "Cocktail", 8.0, 22.0, 16.00, "🍹"),
        DrinkPreset("Tequila Shot", "Shot", 1.5, 40.0, 8.00, "🍋"),
        DrinkPreset("Whiskey Shot", "Shot", 1.5, 40.0, 9.00, "🥃"),
        DrinkPreset("Hard Seltzer", "Seltzer", 12.0, 5.0, 6.50, "🥤"),
        DrinkPreset("Glass of Water", "Water", 12.0, 0.0, 0.00, "💧")
    )

    fun calculate(
        drinks: List<DrinkEntity>,
        weightLbs: Float,
        gender: String, // "MALE" or "FEMALE"
        currentTimeMs: Long = System.currentTimeMillis()
    ): BacResult {
        if (drinks.isEmpty()) {
            return BacResult(
                bac = 0.0,
                zone = BacZone.SOBER,
                hoursToSober = 0.0,
                standardDrinks = 0.0,
                totalCost = 0.0,
                waterCount = 0,
                alcoholCount = 0,
                hydrationScorePercent = 100,
                hangoverRisk = HangoverRisk.LOW,
                shouldSuggestTaxi = false
            )
        }

        val alcoholDrinks = drinks.filter { it.category != "Water" && it.abv > 0.0 }
        val waterDrinks = drinks.filter { it.category == "Water" || it.abv == 0.0 }

        val waterCount = waterDrinks.size
        val alcoholCount = alcoholDrinks.size
        val totalCost = drinks.sumOf { it.price }

        if (alcoholDrinks.isEmpty()) {
            return BacResult(
                bac = 0.0,
                zone = BacZone.SOBER,
                hoursToSober = 0.0,
                standardDrinks = 0.0,
                totalCost = totalCost,
                waterCount = waterCount,
                alcoholCount = 0,
                hydrationScorePercent = 100,
                hangoverRisk = HangoverRisk.LOW,
                shouldSuggestTaxi = false
            )
        }

        // Standard drink calculation (1 standard drink = 14 grams of alcohol = 0.6 oz pure alcohol)
        var totalAlcoholGrams = 0.0
        var totalStandardDrinks = 0.0

        for (drink in alcoholDrinks) {
            val alcoholOz = drink.volumeOz * (drink.abv / 100.0)
            val grams = alcoholOz * 29.5735 * 0.789 // 1 oz = 29.5735 ml, alcohol density = 0.789 g/ml
            totalAlcoholGrams += grams
            totalStandardDrinks += (grams / 14.0)
        }

        // First drink time
        val firstDrinkTimeMs = alcoholDrinks.minOf { it.timestamp }
        val elapsedHours = max(0.0, (currentTimeMs - firstDrinkTimeMs) / 3600000.0)

        // Widmark formula constants
        val weightGrams = weightLbs * 453.592
        val r = if (gender.equals("FEMALE", ignoreCase = true)) 0.55 else 0.68
        val betaEliminationRate = 0.015 // ~0.015% per hour

        // BAC = (Alcohol grams / (Weight grams * r)) * 100 - (beta * hours)
        val rawBac = (totalAlcoholGrams / (weightGrams * r)) * 100.0
        val eliminatedBac = betaEliminationRate * elapsedHours
        val currentBac = max(0.0, rawBac - eliminatedBac)

        val hoursToSober = if (currentBac > 0.0) currentBac / betaEliminationRate else 0.0

        // Zone mapping
        val zone = when {
            currentBac < 0.02 -> BacZone.SOBER
            currentBac < 0.05 -> BacZone.BUZZED
            currentBac < 0.08 -> BacZone.TIPSY
            currentBac < 0.15 -> BacZone.INTOXICATED
            else -> BacZone.HIGH_RISK
        }

        // Hydration score (ratio of water to alcohol)
        val hydrationScore = if (alcoholCount == 0) {
            100
        } else {
            val ratio = waterCount.toDouble() / alcoholCount.toDouble()
            max(10, (ratio * 100).toInt().coerceAtMost(100))
        }

        // Hangover risk evaluation
        val hangoverRisk = when {
            currentBac >= 0.12 || totalStandardDrinks >= 7 -> HangoverRisk.SEVERE
            currentBac >= 0.07 || totalStandardDrinks >= 4.5 -> HangoverRisk.HIGH
            currentBac >= 0.03 || totalStandardDrinks >= 2.5 -> HangoverRisk.MODERATE
            else -> HangoverRisk.LOW
        }

        // Suggest taxi if BAC >= 0.05% or 3+ standard drinks
        val shouldSuggestTaxi = currentBac >= 0.05 || totalStandardDrinks >= 3.0

        return BacResult(
            bac = currentBac,
            zone = zone,
            hoursToSober = hoursToSober,
            standardDrinks = totalStandardDrinks,
            totalCost = totalCost,
            waterCount = waterCount,
            alcoholCount = alcoholCount,
            hydrationScorePercent = hydrationScore,
            hangoverRisk = hangoverRisk,
            shouldSuggestTaxi = shouldSuggestTaxi
        )
    }
}
