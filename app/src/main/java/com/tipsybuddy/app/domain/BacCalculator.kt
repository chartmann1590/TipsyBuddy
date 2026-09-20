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

    val BUILT_IN_CATALOG = listOf(
        // Domestic Beers
        DrinkPreset("Bud Light", "Domestic Beer", 12.0, 4.2, 5.50, "🍺"),
        DrinkPreset("Coors Light", "Domestic Beer", 12.0, 4.2, 5.50, "🍺"),
        DrinkPreset("Miller Lite", "Domestic Beer", 12.0, 4.2, 5.50, "🍺"),
        DrinkPreset("Michelob Ultra", "Domestic Beer", 12.0, 4.2, 6.00, "🍺"),
        DrinkPreset("Pabst Blue Ribbon (PBR)", "Domestic Beer", 12.0, 4.8, 4.50, "🍺"),
        DrinkPreset("Yuengling Traditional", "Domestic Beer", 12.0, 4.5, 5.50, "🍺"),

        // Imported Beers
        DrinkPreset("Corona Extra", "Import Beer", 12.0, 4.6, 7.00, "🍺"),
        DrinkPreset("Heineken Lager", "Import Beer", 12.0, 5.0, 7.00, "🍺"),
        DrinkPreset("Stella Artois", "Import Beer", 11.2, 5.0, 7.50, "🍺"),
        DrinkPreset("Guinness Draught", "Import Beer", 16.0, 4.2, 8.50, "🍻"),
        DrinkPreset("Modelo Especial", "Import Beer", 12.0, 4.4, 7.00, "🍺"),
        DrinkPreset("Dos Equis (XX)", "Import Beer", 12.0, 4.2, 6.50, "🍺"),
        DrinkPreset("Blue Moon Belgian White", "Import Beer", 12.0, 5.4, 7.00, "🍺"),

        // Craft Beers & Cider
        DrinkPreset("Hazy IPA", "Craft Beer", 16.0, 6.8, 8.50, "🍻"),
        DrinkPreset("Double IPA (DIPA)", "Craft Beer", 12.0, 8.2, 9.50, "🍻"),
        DrinkPreset("Craft Stout / Porter", "Craft Beer", 16.0, 6.0, 8.50, "🍻"),
        DrinkPreset("Hard Apple Cider", "Craft Beer", 12.0, 5.0, 6.50, "🍏"),

        // Basic Mixed Drinks
        DrinkPreset("Vodka Soda", "Mixed Drinks", 6.0, 12.0, 10.00, "🍸"),
        DrinkPreset("Gin & Tonic", "Mixed Drinks", 6.0, 11.0, 10.00, "🍸"),
        DrinkPreset("Rum & Coke", "Mixed Drinks", 6.0, 12.0, 10.00, "🍹"),
        DrinkPreset("Whiskey Ginger / Highball", "Mixed Drinks", 6.0, 12.0, 10.00, "🥃"),
        DrinkPreset("Tequila Soda & Lime", "Mixed Drinks", 6.0, 12.0, 10.00, "🍸"),
        DrinkPreset("Vodka Cranberry", "Mixed Drinks", 6.0, 11.0, 10.00, "🍸"),

        // Classic Cocktails
        DrinkPreset("Classic Margarita", "Cocktails", 6.0, 15.0, 13.00, "🍸"),
        DrinkPreset("Moscow Mule", "Cocktails", 6.0, 12.0, 12.00, "🍹"),
        DrinkPreset("Old Fashioned", "Cocktails", 3.5, 32.0, 14.00, "🥃"),
        DrinkPreset("Espresso Martini", "Cocktails", 4.0, 20.0, 15.00, "🍸"),
        DrinkPreset("Aperol Spritz", "Cocktails", 6.0, 11.0, 13.00, "🥂"),
        DrinkPreset("Long Island Iced Tea", "Cocktails", 8.0, 22.0, 15.00, "🍹"),
        DrinkPreset("Mojito", "Cocktails", 7.0, 12.0, 12.00, "🌿"),

        // Hard Seltzers
        DrinkPreset("White Claw Hard Seltzer", "Seltzers", 12.0, 5.0, 6.50, "🥤"),
        DrinkPreset("Truly Hard Seltzer", "Seltzers", 12.0, 5.0, 6.50, "🥤"),
        DrinkPreset("High Noon Sun Sips", "Seltzers", 12.0, 4.5, 8.00, "🥤"),
        DrinkPreset("Twisted Tea", "Seltzers", 12.0, 5.0, 6.00, "🥤"),

        // Shots
        DrinkPreset("Tequila Shot (Blanco)", "Shots", 1.5, 40.0, 8.00, "🍋"),
        DrinkPreset("Vodka Shot", "Shots", 1.5, 40.0, 7.50, "🍋"),
        DrinkPreset("Whiskey / Bourbon Shot", "Shots", 1.5, 40.0, 8.00, "🥃"),
        DrinkPreset("Fireball Cinnamon Shot", "Shots", 1.5, 33.0, 6.00, "🔥"),
        DrinkPreset("Jägerbomb", "Shots", 4.0, 18.0, 9.00, "💣"),
        DrinkPreset("Green Tea Shot", "Shots", 2.0, 15.0, 8.50, "🍵"),

        // Wine & Bubbles
        DrinkPreset("Red Wine (Cabernet/Pinot)", "Wine", 5.0, 13.5, 11.00, "🍷"),
        DrinkPreset("White Wine (Chardonnay/Sauv)", "Wine", 5.0, 12.5, 11.00, "🥂"),
        DrinkPreset("Prosecco / Champagne", "Wine", 5.0, 12.0, 12.00, "🍾"),
        DrinkPreset("Rosé Wine", "Wine", 5.0, 12.5, 10.50, "🍷"),

        // Water & Recovery
        DrinkPreset("Pint of Ice Water", "Water", 16.0, 0.0, 0.00, "💧"),
        DrinkPreset("Club Soda with Lime", "Water", 12.0, 0.0, 2.00, "🫧"),
        DrinkPreset("Liquid Death Sparkling", "Water", 16.0, 0.0, 3.50, "💧"),
        DrinkPreset("Electrolyte Sports Drink", "Water", 16.0, 0.0, 3.50, "⚡")
    )

    val POPULAR_PRESETS = BUILT_IN_CATALOG

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

        val alcoholDrinks = drinks.filter { it.isAlcoholic }
        val waterDrinks = drinks.filter { it.isWater }

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
