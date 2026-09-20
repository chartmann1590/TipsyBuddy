package com.tipsybuddy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarHistoryScreen(
    allDrinks: List<DrinkEntity>,
    userWeightLbs: Float,
    userGender: String
) {
    var calendarMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateStr by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Group drinks by sessionDate
    val drinksByDate = remember(allDrinks) {
        allDrinks.groupBy { it.sessionDate }
    }

    // Days in current calendar month
    val daysInMonth = remember(calendarMonth) {
        val cal = calendarMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val daysList = mutableListOf<String?>()
        // Leading blanks
        for (i in 0 until firstDayOfWeek) {
            daysList.add(null)
        }
        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            daysList.add(dayFormatter.format(cal.time))
        }
        daysList
    }

    // Monthly aggregates
    val currentMonthPrefix = remember(calendarMonth) {
        SimpleDateFormat("yyyy-MM", Locale.US).format(calendarMonth.time)
    }

    val monthlyDrinks = remember(allDrinks, currentMonthPrefix) {
        allDrinks.filter { it.sessionDate.startsWith(currentMonthPrefix) }
    }

    val monthlyDrinkingDays = remember(monthlyDrinks) {
        monthlyDrinks.filter { it.category != "Water" }.map { it.sessionDate }.distinct().size
    }

    val monthlyTotalSpent = remember(monthlyDrinks) {
        monthlyDrinks.sumOf { it.price }
    }

    val selectedDateDrinks = remember(allDrinks, selectedDateStr) {
        allDrinks.filter { it.sessionDate == selectedDateStr }
    }

    val selectedDayBac = remember(selectedDateDrinks, userWeightLbs, userGender) {
        BacCalculator.calculate(
            drinks = selectedDateDrinks,
            weightLbs = userWeightLbs,
            gender = userGender
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title & Month Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Drinking Calendar",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val prev = calendarMonth.clone() as Calendar
                    prev.add(Calendar.MONTH, -1)
                    calendarMonth = prev
                }) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = NeonGold)
                }

                Text(
                    text = monthYearFormat.format(calendarMonth.time),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonGold
                )

                IconButton(onClick = {
                    val next = calendarMonth.clone() as Calendar
                    next.add(Calendar.MONTH, 1)
                    calendarMonth = next
                }) {
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next Month", tint = NeonGold)
                }
            }
        }

        // Monthly Stats Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = "DRINKING DAYS", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text(text = "$monthlyDrinkingDays days", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonGold)
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                val totalDays = calendarMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
                val soberDays = (totalDays - monthlyDrinkingDays).coerceAtLeast(0)
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = "SOBER DAYS", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text(text = "$soberDays days", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = "MONTHLY TAB", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text(text = "$${monthlyTotalSpent.toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                }
            }
        }

        // Calendar Days Grid Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Day Names
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayName ->
                        Text(
                            text = dayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Weeks & Days
                val chunkedDays = daysInMonth.chunked(7)
                chunkedDays.forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (i in 0 until 7) {
                            val dateStr = if (i < week.size) week[i] else null
                            if (dateStr == null) {
                                Box(modifier = Modifier.size(36.dp))
                            } else {
                                val dayNum = dateStr.split("-").lastOrNull()?.toIntOrNull() ?: 1
                                val drinksOnDate = drinksByDate[dateStr] ?: emptyList()
                                val alcoholCount = drinksOnDate.filter { it.category != "Water" }.size
                                val isSelected = dateStr == selectedDateStr

                                val dotColor = when {
                                    alcoholCount >= 5 -> CoralRed
                                    alcoholCount in 3..4 -> NeonGold
                                    alcoholCount in 1..2 -> NeonGreen
                                    else -> Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) NeonGold else Color.Transparent)
                                        .clickable { selectedDateStr = dateStr },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) Color.Black else TextPrimary
                                        )

                                        if (alcoholCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.Black else dotColor)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Date Breakdown Section
        Text(
            text = "BREAKDOWN FOR $selectedDateStr",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.sp
        )

        if (selectedDateDrinks.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Box(
                    modifier = Modifier.padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🌱 Sober day! No drinks logged on this date.",
                        fontSize = 13.sp,
                        color = NeonGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Selected Day Summary Card
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
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Total Drinks", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "${selectedDateDrinks.filter { it.category != "Water" }.size}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Est. Peak BAC", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format(Locale.US, "%.3f%%", selectedDayBac.bac),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(selectedDayBac.zone.colorHex)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Tab Cost", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", selectedDayBac.totalCost)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )
                    }
                }
            }

            // Drinks List for this date
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(selectedDateDrinks) { drink ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C1220),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val emoji = when (drink.category) {
                                    "Beer" -> "🍺"
                                    "Wine" -> "🍷"
                                    "Cocktail" -> "🍸"
                                    "Shot" -> "🍋"
                                    "Water" -> "💧"
                                    else -> "🍹"
                                }
                                Text(text = emoji, fontSize = 18.sp)
                                Column {
                                    Text(text = drink.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = "${drink.volumeOz} oz • ${drink.abv}% ABV", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = timeFormatter.format(Date(drink.timestamp)), fontSize = 11.sp, color = TextMuted)
                                if (drink.price > 0) {
                                    Text(text = "$${drink.price}", fontSize = 11.sp, color = NeonGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
