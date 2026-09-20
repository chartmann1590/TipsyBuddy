package com.tipsybuddy.wear.data

data class WearQuickDrink(
    val id: String,
    val name: String,
    val category: String,
    val volumeOz: Double,
    val abv: Double,
    val price: Double,
    val emoji: String,
    val colorHex: Long
)

object WearDrinkPresets {
    val PRESETS = listOf(
        WearQuickDrink("beer", "Draft Beer", "Domestic Beer", 12.0, 5.0, 6.0, "🍺", 0xFFF59E0B),
        WearQuickDrink("shot", "Liquor Shot", "Shots", 1.5, 40.0, 8.0, "🍋", 0xFFEF4444),
        WearQuickDrink("cocktail", "Cocktail", "Cocktails", 6.0, 14.0, 12.0, "🍸", 0xFFA855F7),
        WearQuickDrink("wine", "Glass of Wine", "Wine", 5.0, 12.5, 10.0, "🍷", 0xFFE11D48),
        WearQuickDrink("seltzer", "Hard Seltzer", "Seltzers", 12.0, 5.0, 6.5, "🥤", 0xFF06B6D4),
        WearQuickDrink("water", "Pint of Water", "Water", 16.0, 0.0, 0.0, "💧", 0xFF38BDF8)
    )
}
