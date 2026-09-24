package com.tipsybuddy.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hartmann.crosspromo.HartmannCrossPromo
import com.hartmann.crosspromo.ui.HartmannCrossPromoRow
import com.tipsybuddy.app.ads.AdMobBanner
import com.tipsybuddy.app.ads.OtherApp
import com.tipsybuddy.app.ads.OtherAppsCatalog
import com.tipsybuddy.app.ads.findActivity
import com.tipsybuddy.app.billing.SubscriptionManager
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.translation.AppTranslationManager
import com.tipsybuddy.app.translation.LanguageItem
import com.tipsybuddy.app.translation.LocalAppTranslationManager
import com.tipsybuddy.app.translation.ModelDownloadStatus
import com.tipsybuddy.app.translation.tr
import com.tipsybuddy.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ProfileSettingsScreen(
    userPrefs: UserPreferences,
    onClearAllData: () -> Unit,
    onOpenTour: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val translationManager = LocalAppTranslationManager.current
    val subscriptionManager = remember { SubscriptionManager.getInstance(context) }
    val isAdFree by subscriptionManager.isAdFree.collectAsState()

    var userName by remember { mutableStateOf(userPrefs.userName) }
    var weightLbs by remember { mutableFloatStateOf(userPrefs.weightLbs) }
    var gender by remember { mutableStateOf(userPrefs.gender) }
    var homeAddress by remember { mutableStateOf(userPrefs.homeAddress) }
    var emergencyName by remember { mutableStateOf(userPrefs.emergencyContactName) }
    var emergencyPhone by remember { mutableStateOf(userPrefs.emergencyContactPhone) }
    var currentSessionId by remember { mutableStateOf(userPrefs.liveSessionId) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val currentLangCode by translationManager.currentLanguageState
    val currentLangItem = remember(currentLangCode) {
        translationManager.getLanguageItem(currentLangCode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(text = "Profile & Settings".tr(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = "Personalize your BAC calculation and safety preferences".tr(), fontSize = 12.sp, color = TextSecondary)
        }

        // APP LANGUAGE & TRANSLATION CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "APP LANGUAGE & TRANSLATION".tr(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Surface(
                    color = Color(0xFF0C1220),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = currentLangItem.flagEmoji, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentLangItem.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (currentLangCode == "en") "Default English".tr() else "${currentLangItem.nativeName} • Offline ML Kit".tr(),
                                    fontSize = 12.sp,
                                    color = NeonGold
                                )
                            }
                        }

                        Button(
                            onClick = { showLanguageDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F59E0B))
                        ) {
                            Text(text = "Change".tr(), color = NeonGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Button to replay app onboarding tour
                OutlinedButton(
                    onClick = onOpenTour,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Revisit App Tour & Guides (Watch & Widget)".tr(), color = NeonCyan, fontSize = 13.sp)
                }
            }
        }

        // BIOMETRICS CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = "BIOMETRICS (FOR ACCURATE BAC)".tr(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it; userPrefs.userName = it },
                    label = { Text("Your Name".tr()) },
                    placeholder = { Text("e.g. Alex".tr()) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = NeonGold, unfocusedBorderColor = CardBorder),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "Biological Sex (affects body water ratio)".tr(), fontSize = 12.sp, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isMale = gender.equals("MALE", ignoreCase = true)
                    Surface(shape = RoundedCornerShape(10.dp), color = if (isMale) NeonGold else Color(0xFF0C1220), border = BorderStroke(1.dp, if (isMale) NeonGold else CardBorder), modifier = Modifier.weight(1f).clickable { gender = "MALE"; userPrefs.gender = "MALE" }) {
                        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Male (r = 0.68)".tr(), fontWeight = FontWeight.Bold, color = if (isMale) Color.Black else TextSecondary)
                        }
                    }
                    val isFemale = gender.equals("FEMALE", ignoreCase = true)
                    Surface(shape = RoundedCornerShape(10.dp), color = if (isFemale) NeonGold else Color(0xFF0C1220), border = BorderStroke(1.dp, if (isFemale) NeonGold else CardBorder), modifier = Modifier.weight(1f).clickable { gender = "FEMALE"; userPrefs.gender = "FEMALE" }) {
                        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Female (r = 0.55)".tr(), fontWeight = FontWeight.Bold, color = if (isFemale) Color.Black else TextSecondary)
                        }
                    }
                }
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Body Weight".tr(), fontSize = 12.sp, color = TextSecondary)
                        Text(text = "${weightLbs.roundToInt()} lbs (~${(weightLbs * 0.453592).roundToInt()} kg)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonGold)
                    }
                    Slider(value = weightLbs, onValueChange = { weightLbs = it; userPrefs.weightLbs = it }, valueRange = 90f..320f, colors = SliderDefaults.colors(thumbColor = NeonGold, activeTrackColor = NeonGold))
                }
            }
        }

        // SAFE RIDE DEFAULTS CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = "SAFE RIDE DEFAULTS".tr(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
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
                    label = { Text("Home Address (Auto-Ride Destination)".tr()) },
                    placeholder = { Text("e.g. 742 Evergreen Terrace, Springfield".tr()) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = NeonGold, unfocusedBorderColor = CardBorder),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = emergencyName, onValueChange = { emergencyName = it; userPrefs.emergencyContactName = it }, label = { Text("Emergency Contact / Friend Name".tr()) }, placeholder = { Text("e.g. Best Friend, Mom".tr()) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = NeonGold, unfocusedBorderColor = CardBorder), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emergencyPhone, onValueChange = { emergencyPhone = it; userPrefs.emergencyContactPhone = it }, label = { Text("Emergency Phone Number".tr()) }, placeholder = { Text("e.g. 555-123-4567".tr()) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = NeonGold, unfocusedBorderColor = CardBorder), modifier = Modifier.fillMaxWidth())
            }
        }

        // LIVE SHARING SESSION CODE CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "LIVE SHARING SESSION CODE".tr(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = currentSessionId, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        Text(text = "Permanent link: tipsybuddy.web.app/?session=$currentSessionId", fontSize = 11.sp, color = TextSecondary)
                    }
                    IconButton(onClick = {
                        val newId = userPrefs.resetNewSessionId()
                        currentSessionId = newId
                        Toast.makeText(context, translationManager.translate("New session generated: $newId"), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Generate New Session".tr(), tint = NeonGold)
                    }
                }
            }
        }

        // AD-FREE SUBSCRIPTION CARD
        SubscriptionSection(subscriptionManager = subscriptionManager)

        // Dynamic cross-promotion ("More from Hartmann Studios") - only shown for non-subscribers
        if (!isAdFree) {
            if (HartmannCrossPromo.isInitialized) {
                HartmannCrossPromoRow(placement = "settings")
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "OUR OTHER APPS".tr(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                        Text(text = "More apps from Charles — tap to open on Play Store".tr(), fontSize = 12.sp, color = TextSecondary)
                        OtherAppsCatalog.apps.forEach { app -> OtherAppRow(app = app) }
                    }
                }
            }

            AdMobBanner(modifier = Modifier.fillMaxWidth())
        }

        SupportFeedbackSection()

        // DATA MANAGEMENT CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "DATA MANAGEMENT".tr(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                OutlinedButton(onClick = { showClearDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CoralRed)) {
                    Text(text = "Reset All History & Logged Drinks".tr(), color = CoralRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(text = "TipsyBuddy v1.0.0 • Built with Kotlin, Jetpack Compose, Material 3 & OpenStreetMap. 100% Free & Open Source.".tr(), fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Reset All Drinking Data?".tr(), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("This will delete all logged drinks, check-ins, and calendar records. This action cannot be undone.".tr(), color = TextSecondary) },
            confirmButton = {
                Button(onClick = { onClearAllData(); showClearDialog = false; Toast.makeText(context, translationManager.translate("All drinking data cleared"), Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColors(containerColor = CoralRed)) {
                    Text("Clear All".tr(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel".tr(), color = TextSecondary) } }
        )
    }

    // Change Language Dialog
    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌐 Select Language".tr(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        IconButton(onClick = { showLanguageDialog = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close".tr(), tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LanguageSelectionSlide(
                        translationManager = translationManager,
                        userPrefs = userPrefs,
                        onLanguageSelectedAndReady = {
                            Toast.makeText(context, translationManager.translate("Language updated"), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
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
            Text(text = "Open →".tr(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeonCyan)
        }
    }
}

@Composable
private fun SubscriptionSection(
    subscriptionManager: SubscriptionManager
) {
    val context = LocalContext.current
    val isAdFree by subscriptionManager.isAdFree.collectAsState()
    val formattedPrice by subscriptionManager.formattedPrice.collectAsState()
    var isRestoring by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAdFree) Color(0xFF0D251D) else SurfaceDark
        ),
        border = BorderStroke(1.dp, if (isAdFree) NeonGreen else CardBorder)
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
                    text = if (isAdFree) "✨ PRO MEMBERSHIP".tr() else "⭐ UPGRADE TO AD-FREE".tr(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAdFree) NeonGreen else NeonGold,
                    letterSpacing = 1.sp
                )

                Surface(
                    color = if (isAdFree) NeonGreen.copy(alpha = 0.2f) else NeonGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isAdFree) "ACTIVE".tr() else "GOOGLE PLAY".tr(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAdFree) NeonGreen else NeonGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (isAdFree) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(30.dp)
                    )
                    Column {
                        Text(
                            text = "Ad-Free Experience Unlocked".tr(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "All banner and interstitial ads are completely removed. Thank you for supporting TipsyBuddy!".tr(),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            subscriptionManager.openPlayStoreSubscriptions(activity)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Manage Subscription on Google Play".tr(), color = TextSecondary, fontSize = 12.sp)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = NeonGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "TipsyBuddy Ad-Free".tr(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Enjoy an uninterrupted, 100% ad-free experience with zero banner or interstitial ads.".tr(),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    color = Color(0xFF0C1220),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Subscription".tr(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Cancel anytime via Google Play".tr(),
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = formattedPrice,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGold
                        )
                    }
                }

                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            val started = subscriptionManager.launchPurchaseFlow(activity)
                            if (!started) {
                                Toast.makeText(
                                    context,
                                    "Connecting to Google Play, please try again in a moment".tr(),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGold)
                ) {
                    Icon(Icons.Filled.Stars, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Subscribe to Remove Ads ($formattedPrice)".tr(),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                TextButton(
                    onClick = {
                        isRestoring = true
                        subscriptionManager.restorePurchases { found ->
                            isRestoring = false
                            val msg = if (found) {
                                "Ad-Free subscription restored successfully!".tr()
                            } else {
                                "No active subscription found on this Google Play account".tr()
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRestoring
                ) {
                    Text(
                        text = if (isRestoring) "Checking Google Play...".tr() else "Restore Purchases".tr(),
                        color = NeonCyan,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

