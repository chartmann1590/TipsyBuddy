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
import com.tipsybuddy.app.ads.AdMobBanner
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthInsightsScreen(
    drinks: List<DrinkEntity>,
    userPrefs: UserPreferences
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Health & Sobriety Insights",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Biometric alcohol tracking & recovery metrics",
                fontSize = 12.sp,
                color = TextSecondary
            )
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
                        text = "${String.format(Locale.US, \"%.1f\", bacResult.standardDrinks)} units",
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

        AdMobBanner(modifier = Modifier.fillMaxWidth())
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
