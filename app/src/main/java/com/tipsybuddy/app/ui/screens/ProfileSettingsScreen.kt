package com.tipsybuddy.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tipsybuddy.app.ads.AdMobBanner
import com.tipsybuddy.app.ads.OtherApp
import com.tipsybuddy.app.ads.OtherAppsCatalog
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ProfileSettingsScreen(
    userPrefs: UserPreferences,
    onClearAllData: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var userName by remember { mutableStateOf(userPrefs.userName) }
    var weightLbs by remember { mutableFloatStateOf(userPrefs.weightLbs) }
    var gender by remember { mutableStateOf(userPrefs.gender) }
    var homeAddress by remember { mutableStateOf(userPrefs.homeAddress) }
    var emergencyName by remember { mutableStateOf(userPrefs.emergencyContactName) }
    var emergencyPhone by remember { mutableStateOf(userPrefs.emergencyContactPhone) }
    var currentSessionId by remember { mutableStateOf(userPrefs.liveSessionId) }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(text = "Profile & Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = "Personalize your BAC calculation and safety preferences", fontSize = 12.sp, color = TextSecondary)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = "BIOMETRICS (FOR ACCURATE BAC)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it; userPrefs.userName = it },
                    label = { Text("Your Name") },
                    placeholder = { Text("e.g. Alex") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = NeonGold, unfocusedBorderColor = CardBorder),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "Biological Sex (affects body water ratio)", fontSize = 12.sp, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isMale = gender.equals("MALE", ignoreCase = true)
                    Surface(shape = RoundedCornerShape(10.dp), color = if (isMale) NeonGold else Color(0xFF0C1220), border = BorderStroke(1.dp, if (isMale) NeonGold else CardBorder), modifier = Modifier.weight(1f).clickable { gender = "MALE"; userPrefs.gender = "MALE" }) {
                        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Male (r = 0.68)", fontWeight = FontWeight.Bold, color = if (isMale) Color.Black else TextSecondary)
                        }
                    }
                    val isFemale = gender.equals("FEMALE", ignoreCase = true)
                    Surface(shape = RoundedCornerShape(10.dp), color = if (isFemale) NeonGold else Color(0xFF0C1220), border = BorderStroke(1.dp, if (isFemale) NeonGold else CardBorder), modifier = Modifier.weight(1f).clickable { gender = "FEMALE"; userPrefs.gender = "FEMALE" }) {
                        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Female (r = 0.55)", fontWeight = FontWeight.Bold, color = if (isFemale) Color.Black else TextSecondary)
                        }
                    }
                }
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Body Weight", fontSize = 12.sp, color = TextSecondary)
                        Text(text = "${weightLbs.roundToInt()} lbs (~${(weightLbs * 0.453592).roundToInt()} kg)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonGold)
                    }
                    Slider(value = weightLbs, onValueChange = { weightLbs = it; userPrefs.weightLbs = it }, valueRange = 90f..320f, colors = SliderDefaults.colors(thumbColor = NeonGold, activeTrackColor = NeonGold))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = "SAFE RIDE DEFAULTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = homeAddress,
                    onValueChange = {
                        homeAddress = it; userPrefs.homeAddress = it
                        if (it.isNotBlank()) {
                            try {
                                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                val results = geocoder.getFromLocationName(it, 1)
                                if (!results.isNullOrEmpty()) { userPrefs.homeLat = results[0].latitude; userPrefs.homeLon = results[0].longitude }
                            } catch (e: Exception) { }
                        }
                    },
                    label = { Text("Home Address (Auto-Ride Destination)") },
                    placeholder = { Text("e.g. 742 Evergreen Terrace, Springfield") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = NeonGold, unfocusedBorderColor = CardBorder),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = emergencyName, onValueChange = { emergencyName = it; userPrefs.emergencyContactName = it }, label = { Text("Emergency Contact / Friend Name") }, placeholder = { Text("e.g. Best Friend, Mom") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = NeonGold, unfocusedBorderColor = CardBorder), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emergencyPhone, onValueChange = { emergencyPhone = it; userPrefs.emergencyContactPhone = it }, label = { Text("Emergency Phone Number") }, placeholder = { Text("e.g. 555-123-4567") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = NeonGold, unfocusedBorderColor = CardBorder), modifier = Modifier.fillMaxWidth())
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "LIVE SHARING SESSION CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = currentSessionId, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        Text(text = "Permanent link: tipsybuddy.web.app/?session=$currentSessionId", fontSize = 11.sp, color = TextSecondary)
                    }
                    IconButton(onClick = {
                        val newId = userPrefs.resetNewSessionId()
                        currentSessionId = newId
                        Toast.makeText(context, "New session generated: $newId", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Generate New Session", tint = NeonGold)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "OUR OTHER APPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                Text(text = "More apps from Charles — tap to open on Play Store", fontSize = 12.sp, color = TextSecondary)
                OtherAppsCatalog.apps.forEach { app -> OtherAppRow(app = app) }
            }
        }

        AdMobBanner(modifier = Modifier.fillMaxWidth())

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "DATA MANAGEMENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                OutlinedButton(onClick = { showClearDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CoralRed)) {
                    Text(text = "Reset All History & Logged Drinks", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(text = "TipsyBuddy v1.0.0 • Built with Kotlin, Jetpack Compose, Material 3 & OpenStreetMap. 100% Free & Open Source.", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Reset All Drinking Data?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("This will delete all logged drinks, check-ins, and calendar records. This action cannot be undone.", color = TextSecondary) },
            confirmButton = {
                Button(onClick = { onClearAllData(); showClearDialog = false; Toast.makeText(context, "All drinking data cleared", Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColors(containerColor = CoralRed)) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

@Composable
private fun OtherAppRow(app: OtherApp) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0C1220),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth().clickable { OtherAppsCatalog.open(context, app) }
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = app.emoji, fontSize = 22.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = app.tagline, fontSize = 11.sp, color = TextSecondary)
            }
            Text(text = "Open →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeonCyan)
        }
    }
}
