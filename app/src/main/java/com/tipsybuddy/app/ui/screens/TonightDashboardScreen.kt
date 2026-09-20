package com.tipsybuddy.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tipsybuddy.app.data.CheckInEntity
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.domain.BacZone
import com.tipsybuddy.app.domain.RideManager
import com.tipsybuddy.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TonightDashboardScreen(
    drinks: List<DrinkEntity>,
    latestCheckIn: CheckInEntity?,
    userPrefs: UserPreferences,
    onAddDrink: (name: String, category: String, volumeOz: Double, abv: Double, price: Double) -> Unit,
    onNavigateToVenues: () -> Unit,
    onNavigateToRides: () -> Unit,
    onNavigateToLog: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Real-time ticking every 30s to update BAC
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

    // Animated glow pulse around the BAC gauge
    val infiniteTransition = rememberInfiniteTransition(label = "bacPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (bacResult.bac > 0.0) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (bacResult.bac >= 0.08) 800 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = if (bacResult.bac > 0.0) 0.5f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (bacResult.bac >= 0.08) 800 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val todayStr = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (userPrefs.userName.isNotBlank()) "Hey ${userPrefs.userName}! 🍻" else "Tonight's Night Out 🍻",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = todayStr,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            if (userPrefs.isLiveSharing) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x3310B981),
                    border = BorderStroke(1.dp, NeonGreen),
                    modifier = Modifier.clickable { onNavigateToVenues() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Text(
                            text = "LIVE SHARING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )
                    }
                }
            }
        }

        // Circular BAC Meter Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ESTIMATED BLOOD ALCOHOL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                // Large BAC Circular Gauge Display with Pulsing Glow Aura
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(175.dp)
                ) {
                    // Pulsing Outer Glow Aura
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Color(bacResult.zone.colorHex).copy(alpha = pulseAlpha * 0.4f)
                            )
                    )

                    // Inner Circular Gauge
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0C1220))
                            .border(
                                width = 4.dp,
                                color = Color(bacResult.zone.colorHex),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(Locale.US, "%.3f", bacResult.bac) + "%",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(bacResult.zone.colorHex)
                            )
                            Text(
                                text = "BAC",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Zone Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(bacResult.zone.colorHex).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(bacResult.zone.colorHex).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = bacResult.zone.title.uppercase(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(bacResult.zone.colorHex)
                    )
                }

                Text(
                    text = bacResult.zone.subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                if (bacResult.hoursToSober > 0.0) {
                    val soberCalendar = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, (bacResult.hoursToSober * 60).toInt())
                    }
                    val soberTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(soberCalendar.time)
                    Text(
                        text = "⏱️ Estimated sober at ~$soberTimeStr (~${String.format(Locale.US, "%.1f", bacResult.hoursToSober)} hrs)",
                        fontSize = 12.sp,
                        color = NeonGold,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Proactive Smart Taxi Suggestion Banner
        if (bacResult.shouldSuggestTaxi) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (bacResult.zone == BacZone.INTOXICATED || bacResult.zone == BacZone.HIGH_RISK)
                        Color(0x33EF4444) else Color(0x33F59E0B)
                ),
                border = BorderStroke(
                    1.dp,
                    if (bacResult.zone == BacZone.INTOXICATED || bacResult.zone == BacZone.HIGH_RISK)
                        CoralRed else NeonGold
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🚕", fontSize = 28.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (bacResult.bac >= 0.08) "Legally Intoxicated! Do Not Drive." else "You're Tipsy! Time to Call a Ride.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (userPrefs.homeAddress.isNotBlank())
                                    "Leave the car keys behind. One tap summons a ride straight to your home (${userPrefs.homeAddress})."
                                else
                                    "Leave the car keys behind. One tap summons a ride safely home.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                RideManager.launchUber(
                                    context = context,
                                    homeAddress = userPrefs.homeAddress,
                                    homeLat = userPrefs.homeLat,
                                    homeLon = userPrefs.homeLon
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "🚗 Open Uber", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                RideManager.launchLyft(
                                    context = context,
                                    homeLat = userPrefs.homeLat,
                                    homeLon = userPrefs.homeLon,
                                    homeAddress = userPrefs.homeAddress
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF00BF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "⚡ Open Lyft", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { onNavigateToRides() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeonGold)
                    ) {
                        Text(text = "View Safe Rides & Fare Estimates →", color = NeonGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Night Out Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "DRINKS", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${drinks.filter { it.category != "Water" }.size}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGold
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", bacResult.standardDrinks)} standard",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "TOTAL SPENT", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "$${String.format(Locale.US, "%.0f", bacResult.totalCost)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    Text(text = "On tonight's tab", fontSize = 11.sp, color = TextSecondary)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "HYDRATION", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${bacResult.hydrationScorePercent}%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Text(text = "${bacResult.waterCount} waters logged", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        // Quick 1-Tap Drink Logger Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUICK ADD DRINK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "See all drinks →",
                        fontSize = 12.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToLog() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickDrinkButton(emoji = "🍺", name = "Beer", onClick = {
                        onAddDrink("Draft Beer", "Beer", 12.0, 5.0, 7.0)
                        Toast.makeText(context, "🍺 Added Beer (12oz, 5%)", Toast.LENGTH_SHORT).show()
                    })
                    QuickDrinkButton(emoji = "🍷", name = "Wine", onClick = {
                        onAddDrink("Wine Glass", "Wine", 5.0, 12.5, 11.0)
                        Toast.makeText(context, "🍷 Added Wine (5oz, 12.5%)", Toast.LENGTH_SHORT).show()
                    })
                    QuickDrinkButton(emoji = "🍸", name = "Cocktail", onClick = {
                        onAddDrink("Cocktail", "Cocktail", 6.0, 15.0, 14.0)
                        Toast.makeText(context, "🍸 Added Cocktail (15%)", Toast.LENGTH_SHORT).show()
                    })
                    QuickDrinkButton(emoji = "🍋", name = "Shot", onClick = {
                        onAddDrink("Spirits Shot", "Shot", 1.5, 40.0, 8.0)
                        Toast.makeText(context, "🍋 Added 1.5oz Shot (40%)", Toast.LENGTH_SHORT).show()
                    })
                    QuickDrinkButton(emoji = "💧", name = "Water", onClick = {
                        onAddDrink("Pint of Water", "Water", 16.0, 0.0, 0.0)
                        Toast.makeText(context, "💧 Water Logged! Hydration up!", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }

        // Active Venue Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToVenues() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x3338BDF8)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📍", fontSize = 22.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = latestCheckIn?.venueName ?: "Not Checked In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = latestCheckIn?.address?.ifBlank { "Tap to check in to current bar" }
                            ?: "Tap to check in & share live location",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Navigate",
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
private fun QuickDrinkButton(
    emoji: String,
    name: String,
    onClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .scale(scale.value)
            .clickable {
                coroutineScope.launch {
                    scale.animateTo(0.82f, animationSpec = tween(60))
                    scale.animateTo(
                        1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                onClick()
            }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0C1220),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = emoji, fontSize = 26.sp)
            }
        }
        Text(text = name, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
    }
}
