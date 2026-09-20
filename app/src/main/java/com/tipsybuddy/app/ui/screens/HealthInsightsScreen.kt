package com.tipsybuddy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.ui.theme.*
import com.tipsybuddy.app.wear.PhoneWatchVitals
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthInsightsScreen(
    drinks: List<DrinkEntity>,
    userPrefs: UserPreferences,
    watchVitals: PhoneWatchVitals = PhoneWatchVitals()
) {
    val scrollState = rememberScrollState()

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val bacResult = remember(drinks, userPrefs.weightLbs, userPrefs.gender, currentTimeMs) {
        BacCalculator.calculate(
            drinks = drinks,
            weightLbs = userPrefs.weightLbs,
            gender = userPrefs.gender,
            currentTimeMs = currentTimeMs
        )
    }

    val isReadingFresh = watchVitals.lastSyncedTimestamp > 0L &&
        (currentTimeMs - watchVitals.lastSyncedTimestamp) < 30 * 60 * 1000L
    val hasValidLiveHr = watchVitals.heartRateBpm > 0 && isReadingFresh
    val isLiveTachycardiaRisk = hasValidLiveHr && watchVitals.isTachycardiaRisk

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Column {
            Text(
                text = "Health Insights & Recovery",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "Track the physiological impact of alcohol on your sleep, metabolism, and heart rate.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Wear OS Smartwatch Live Biometrics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(
                1.dp,
                if (isLiveTachycardiaRisk) CoralRed else CardBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "⌚", fontSize = 16.sp)
                        Text(
                            text = "WEAR OS LIVE VITALS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (watchVitals.isWatchConnected) Color(0x3310B981) else Color(0x33F59E0B),
                        border = BorderStroke(1.dp, if (watchVitals.isWatchConnected) NeonGreen else NeonGold)
                    ) {
                        Text(
                            text = if (watchVitals.isWatchConnected) "WATCH PAIRED" else "READY TO SYNC",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (watchVitals.isWatchConnected) NeonGreen else NeonGold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Heart rate display
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "❤️", fontSize = 16.sp)
                            Text(
                                text = if (hasValidLiveHr) "${watchVitals.heartRateBpm} BPM" else "-- BPM",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isLiveTachycardiaRisk) CoralRed else if (hasValidLiveHr) NeonCyan else TextMuted
                            )
                        }
                        Text(
                            text = when {
                                hasValidLiveHr && watchVitals.peakHeartRateBpm > 0 -> "Peak: ${watchVitals.peakHeartRateBpm} BPM"
                                hasValidLiveHr -> "Active Rate"
                                watchVitals.heartRateBpm > 0 && !isReadingFresh -> "Reading Stale (>30m)"
                                watchVitals.isWatchConnected -> "Awaiting Reading..."
                                else -> "Watch Not Synced"
                            },
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    // Night steps walked
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "👟", fontSize = 16.sp)
                            Text(
                                text = if (isReadingFresh || watchVitals.stepsTonight > 0) "${watchVitals.stepsTonight}" else "--",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isReadingFresh || watchVitals.stepsTonight > 0) NeonGold else TextMuted
                            )
                        }
                        Text(
                            text = if (isReadingFresh || watchVitals.stepsTonight == 0) "Steps Tonight" else "Steps (Historical)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (hasValidLiveHr && (watchVitals.isTachycardiaRisk || (bacResult.bac >= 0.05 && watchVitals.heartRateBpm > 85))) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x22EF4444),
                        border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = "⚠️", fontSize = 14.sp)
                            Text(
                                text = "Elevated Heart Rate: Alcohol causes peripheral vasodilation and activates sympathetic cardiac stress. Drink water to ease cardiovascular strain.",
                                fontSize = 11.sp,
                                color = Color(0xFFFCA5A5),
                                lineHeight = 15.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = "💡 Real-Time Sync: TipsyBuddy Wear streams heart rate, intoxication vitals, and steps from your smartwatch directly to your recovery log.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Hangover Risk Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, Color(bacResult.hangoverRisk.colorHex).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HANGOVER RISK INDEX",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(bacResult.hangoverRisk.colorHex).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(bacResult.hangoverRisk.colorHex))
                    ) {
                        Text(
                            text = bacResult.hangoverRisk.label.uppercase(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(bacResult.hangoverRisk.colorHex)
                        )
                    }
                }

                Text(
                    text = bacResult.hangoverRisk.advice,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = CardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Standard Drinks Consumed:", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        text = String.format(Locale.US, "%.1f units", bacResult.standardDrinks),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGold
                    )
                }
            }
        }

        // Hydration Score Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HYDRATION BALANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${bacResult.hydrationScorePercent}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }

                LinearProgressIndicator(
                    progress = { bacResult.hydrationScorePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = NeonCyan,
                    trackColor = Color(0xFF0C1220)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Alcohol Drinks", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "${bacResult.alcoholCount} 🍸",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Glasses of Water", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "${bacResult.waterCount} 💧",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }

                Text(
                    text = "💡 Medical Tip: Alcohol suppresses vasopressin (the antidiuretic hormone), expelling 4x more water than consumed. Drink 1 glass of water per alcoholic beverage to avoid next-day dehydration.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )
            }
        }

        // Widmark Formula Engine Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "WIDMARK BIO-ALCOHOL FORMULA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0C1220),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "BAC = [ Alcohol (g) / (Weight (g) × r) ] × 100 - (β × Time)",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ParamRow("Body Weight (W)", "${userPrefs.weightLbs.toInt()} lbs (${(userPrefs.weightLbs * 0.453592).toInt()} kg)")
                    ParamRow("Gender Factor (r)", if (userPrefs.gender.equals("FEMALE", true)) "0.55 (Female body water)" else "0.68 (Male body water)")
                    ParamRow("Metabolism Rate (β)", "0.015% BAC eliminated per hour")
                    ParamRow("Current BAC", String.format(Locale.US, "%.3f%%", bacResult.bac))
                    if (bacResult.hoursToSober > 0) {
                        ParamRow("Time to 0.00% Sober", String.format(Locale.US, "%.1f hours", bacResult.hoursToSober))
                    }
                }
            }
        }

        // Night Survival & Recovery Protocol
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "NIGHT-OUT RECOVERY GUIDE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                GuideItem(
                    emoji = "🍞",
                    title = "Before Bed Carbohydrates",
                    desc = "Eat complex carbs (toast, crackers, rice) to steady blood sugar and aid liver glycogen."
                )
                GuideItem(
                    emoji = "🧂",
                    title = "Electrolytes & Salt",
                    desc = "Replenish sodium, potassium, and magnesium lost to frequent urination before sleeping."
                )
                GuideItem(
                    emoji = "🚫",
                    title = "Avoid Tylenol (Acetaminophen)",
                    desc = "Never take acetaminophen while alcohol is in your system; it produces toxic liver metabolites."
                )
            }
        }
    }
}

@Composable
private fun ParamRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun GuideItem(emoji: String, title: String, desc: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = desc, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
    }
}
