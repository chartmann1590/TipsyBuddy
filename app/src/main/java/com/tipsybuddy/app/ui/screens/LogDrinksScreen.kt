package com.tipsybuddy.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.domain.DrinkPreset
import com.tipsybuddy.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun LogDrinksScreen(
    drinks: List<DrinkEntity>,
    onAddDrink: (name: String, category: String, volumeOz: Double, abv: Double, price: Double) -> Unit,
    onDeleteDrink: (DrinkEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showCustomDialog by remember { mutableStateOf(false) }
    var celebrationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(celebrationMessage) {
        if (celebrationMessage != null) {
            kotlinx.coroutines.delay(2200)
            celebrationMessage = null
        }
    }

    val categories = listOf(
        "All",
        "Domestic Beer",
        "Import Beer",
        "Craft Beer",
        "Mixed Drinks",
        "Cocktails",
        "Seltzers",
        "Shots",
        "Wine",
        "Water & Recovery"
    )

    val filteredPresets = remember(selectedCategory, searchQuery) {
        val base = when (selectedCategory) {
            "All" -> BacCalculator.BUILT_IN_CATALOG
            "Water & Recovery", "Water" -> BacCalculator.BUILT_IN_CATALOG.filter { it.category == "Water" || it.abv == 0.0 }
            else -> BacCalculator.BUILT_IN_CATALOG.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        if (searchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                (searchQuery.contains("water", ignoreCase = true) && it.category == "Water")
            }
        }
    }

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Drink Logger 🍻",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Tap popular beers, mixed drinks, or build custom",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { showCustomDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Custom", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // Celebration Animation Banner
        androidx.compose.animation.AnimatedVisibility(
            visible = celebrationMessage != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x3310B981),
                border = BorderStroke(1.dp, NeonGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = celebrationMessage ?: "",
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Bud Light, Heineken, Margarita, Vodka...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = TextMuted)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Text(text = "✕", color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = NeonGold,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) NeonGold else SurfaceDark,
                    border = BorderStroke(1.dp, if (isSelected) NeonGold else CardBorder),
                    modifier = Modifier.clickable { selectedCategory = category }
                ) {
                    Text(
                        text = category,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else TextSecondary
                    )
                }
            }
        }

        // Preset Drink Cards Grid/List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BUILT-IN DRINKS (${filteredPresets.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
            if (searchQuery.isNotBlank()) {
                Text(
                    text = "Clear search",
                    fontSize = 11.sp,
                    color = NeonCyan,
                    modifier = Modifier.clickable { searchQuery = "" }
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filteredPresets) { preset ->
                Card(
                    modifier = Modifier
                        .width(155.dp)
                        .clickable {
                            onAddDrink(preset.name, preset.category, preset.volumeOz, preset.abv, preset.defaultPrice)
                            celebrationMessage = "${preset.emoji} Added ${preset.name}! Tab +$${String.format(Locale.US, "%.2f", preset.defaultPrice)}"
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = preset.emoji, fontSize = 28.sp)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0C1220),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = preset.category.take(10),
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = preset.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${preset.volumeOz.toInt()} oz • ${preset.abv}% ABV",
                            fontSize = 11.sp,
                            color = NeonCyan
                        )
                        Text(
                            text = if (preset.defaultPrice > 0) "$${String.format(Locale.US, "%.2f", preset.defaultPrice)}" else "Free / $0",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonGreen
                        )
                    }
                }
            }
        }

        // Tonight's Logged Drinks Timeline
        Text(
            text = "TONIGHT'S TIMELINE (${drinks.size} items)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.sp
        )

        if (drinks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🍹", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No drinks logged yet tonight",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tap a preset above to log your first drink!",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(drinks, key = { it.id }) { drink ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0C1220)),
                                contentAlignment = Alignment.Center
                            ) {
                                val emoji = when {
                                    drink.isWater -> "💧"
                                    drink.category.contains("Beer", ignoreCase = true) -> "🍺"
                                    drink.category.contains("Wine", ignoreCase = true) -> "🍷"
                                    drink.category.contains("Cocktail", ignoreCase = true) -> "🍸"
                                    drink.category.contains("Shot", ignoreCase = true) -> "🍋"
                                    drink.category.contains("Seltzer", ignoreCase = true) -> "🥤"
                                    else -> "🍹"
                                }
                                Text(text = emoji, fontSize = 20.sp)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = drink.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${drink.volumeOz} oz • ${drink.abv}% ABV • $${String.format(Locale.US, "%.2f", drink.price)}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            Text(
                                text = timeFormatter.format(Date(drink.timestamp)),
                                fontSize = 12.sp,
                                color = TextMuted
                            )

                            IconButton(
                                onClick = {
                                    onDeleteDrink(drink)
                                    Toast.makeText(context, "Removed ${drink.name}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = CoralRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Drink Dialog
    if (showCustomDialog) {
        CustomDrinkDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { name, category, vol, abv, price ->
                onAddDrink(name, category, vol, abv, price)
                showCustomDialog = false
                Toast.makeText(context, "Added custom drink: $name", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun CustomDrinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, volumeOz: Double, abv: Double, price: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Cocktail") }
    var volumeOz by remember { mutableFloatStateOf(8.0f) }
    var abv by remember { mutableFloatStateOf(12.0f) }
    var priceStr by remember { mutableStateOf("12.00") }

    val categoryOptions = listOf("Cocktail", "Beer", "Wine", "Shot", "Seltzer", "Custom")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                text = "Build Custom Drink",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Drink Name") },
                    placeholder = { Text("e.g. Gin Tonic, Pitcher") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonGold,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category chips
                Text(text = "Category", fontSize = 12.sp, color = TextMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categoryOptions) { cat ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (category == cat) NeonGold else Color(0xFF0C1220),
                            border = BorderStroke(1.dp, if (category == cat) NeonGold else CardBorder),
                            modifier = Modifier.clickable { category = cat }
                        ) {
                            Text(
                                text = cat,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (category == cat) Color.Black else TextSecondary
                            )
                        }
                    }
                }

                // Volume Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Volume", fontSize = 12.sp, color = TextMuted)
                        Text(text = "${volumeOz.roundToInt()} oz", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = volumeOz,
                        onValueChange = { volumeOz = it },
                        valueRange = 1f..32f,
                        colors = SliderDefaults.colors(thumbColor = NeonGold, activeTrackColor = NeonGold)
                    )
                }

                // ABV Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Alcohol By Volume (ABV)", fontSize = 12.sp, color = TextMuted)
                        Text(text = "${abv.roundToInt()}%", fontSize = 12.sp, color = NeonGold, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = abv,
                        onValueChange = { abv = it },
                        valueRange = 0f..70f,
                        colors = SliderDefaults.colors(thumbColor = AmberGlow, activeTrackColor = AmberGlow)
                    )
                }

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Price ($)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonGold,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.ifBlank { "Custom Drink" }
                    val finalPrice = priceStr.toDoubleOrNull() ?: 0.0
                    onConfirm(finalName, category, volumeOz.toDouble(), abv.toDouble(), finalPrice)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGold)
            ) {
                Text(text = "Add Drink", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = TextSecondary)
            }
        }
    )
}
