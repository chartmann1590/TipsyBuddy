package com.tipsybuddy.app.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.translation.AppTranslationManager
import com.tipsybuddy.app.translation.LanguageItem
import com.tipsybuddy.app.translation.LocalAppTranslationManager
import com.tipsybuddy.app.translation.ModelDownloadStatus
import com.tipsybuddy.app.translation.tr
import com.tipsybuddy.app.ui.theme.*

@Composable
fun OnboardingScreen(
    userPrefs: UserPreferences,
    onFinish: () -> Unit
) {
    val translationManager = LocalAppTranslationManager.current
    val transVersion = translationManager.versionState.value
    val currentLang = translationManager.currentLanguageState.value
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar: Step Indicator & Skip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalSteps) { index ->
                    val isActive = index == currentStep
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (isActive) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isActive) NeonGold else SurfaceDark)
                    )
                }
            }

            if (currentStep < totalSteps - 1) {
                TextButton(
                    onClick = {
                        userPrefs.isOnboardingCompleted = true
                        onFinish()
                    }
                ) {
                    Text(
                        text = "Skip".tr(),
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        // Main Slide Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                label = "onboarding_slide"
            ) { step ->
                when (step) {
                    0 -> LanguageSelectionSlide(
                        translationManager = translationManager,
                        userPrefs = userPrefs,
                        onLanguageSelectedAndReady = {
                            // ready
                        }
                    )
                    1 -> FeatureWalkthroughSlide(
                        icon = Icons.Filled.LocalBar,
                        iconColor = NeonGold,
                        title = "Smart BAC & Metabolism".tr(),
                        subtitle = "Real-time science to keep your night in check".tr(),
                        bullets = listOf(
                            FeatureBullet(
                                "🍺 One-Tap Drink Logging".tr(),
                                "Easily log beers, wine, cocktails, shots, and water with accurate ABV and volume.".tr()
                            ),
                            FeatureBullet(
                                "📊 Scientific Widmark Formula".tr(),
                                "Accurate blood alcohol concentration tailored to your personal weight and gender.".tr()
                            ),
                            FeatureBullet(
                                "⏱️ Sobriety Countdown".tr(),
                                "Know precisely when you'll return to 0.00% BAC and when it's legally safe to drive.".tr()
                            ),
                            FeatureBullet(
                                "💧 Hydration & Tab Tracking".tr(),
                                "Monitor your water-to-alcohol ratio and keep a live tally of your night-out tab.".tr()
                            )
                        )
                    )
                    2 -> FeatureWalkthroughSlide(
                        icon = Icons.Filled.Watch,
                        iconColor = NeonCyan,
                        title = "Wear OS Watch Companion".tr(),
                        subtitle = "Discreet safety and live tracking on your wrist".tr(),
                        bullets = listOf(
                            FeatureBullet(
                                "⌚ Live BAC at a Glance".tr(),
                                "Check your blood alcohol level right from your watch face without taking your phone out.".tr()
                            ),
                            FeatureBullet(
                                "📳 Stealth Vibration Alerts".tr(),
                                "Discreet haptic pulses alert you before you surpass your chosen drinking limits.".tr()
                            ),
                            FeatureBullet(
                                "⚡ Wrist Quick-Logging".tr(),
                                "Tap to log a beer, wine, or water straight from your watch tile in seconds.".tr()
                            ),
                            FeatureBullet(
                                "💓 Heart Rate & Vitals Sync".tr(),
                                "Continuous vitals monitoring warns you of alcohol-induced elevated heart rates.".tr()
                            )
                        )
                    )
                    3 -> FeatureWalkthroughSlide(
                        icon = Icons.Filled.Widgets,
                        iconColor = NeonGreen,
                        title = "Home Widget & Safe Rides".tr(),
                        subtitle = "Instant access and a reliable ride home".tr(),
                        bullets = listOf(
                            FeatureBullet(
                                "📱 Home Screen Widget".tr(),
                                "Add the TipsyBuddy widget for 1-tap logging and live BAC gauges without opening the app.".tr()
                            ),
                            FeatureBullet(
                                "🚖 1-Tap Uber & Lyft Rides".tr(),
                                "Dispatch safe transportation home with your saved destination address automatically pre-filled.".tr()
                            ),
                            FeatureBullet(
                                "📍 Live Venue Radar & Link".tr(),
                                "Check in at venues and share an encrypted live link with friends so you stay connected.".tr()
                            ),
                            FeatureBullet(
                                "🚨 Quick Emergency Dial".tr(),
                                "Instant 1-touch dial for your designated emergency contact or safety buddy.".tr()
                            )
                        )
                    )
                }
            }
        }

        // Bottom Navigation Bar
        val downloadStatus by translationManager.downloadStatus
        val isDownloading = downloadStatus is ModelDownloadStatus.Downloading
        val canProceed = !(currentStep == 0 && isDownloading)

        Surface(
            color = SurfaceDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back".tr(), tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Back".tr(), color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep < totalSteps - 1) {
                            currentStep++
                        } else {
                            userPrefs.isOnboardingCompleted = true
                            onFinish()
                        }
                    },
                    enabled = canProceed,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGold,
                        disabledContainerColor = SurfaceDark
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    if (currentStep == totalSteps - 1) {
                        Text(
                            text = "Get Started 🚀".tr(),
                            color = BgDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    } else {
                        Text(
                            text = if (isDownloading) "Downloading...".tr() else "Next".tr(),
                            color = if (canProceed) BgDark else TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = "Next".tr(),
                            tint = if (canProceed) BgDark else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSelectionSlide(
    translationManager: AppTranslationManager,
    userPrefs: UserPreferences,
    onLanguageSelectedAndReady: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentLangCode by translationManager.currentLanguageState
    val downloadStatus by translationManager.downloadStatus

    val popularList = translationManager.popularLanguages
    val allList = translationManager.allSupportedLanguages

    val filteredList = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allList
        } else {
            allList.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.nativeName.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "🌐 Choose Your Language".tr(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "TipsyBuddy uses Google's free ML Kit on-device translation. Select a language to download its offline model (~30MB).".tr(),
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Model download status banner
        when (val status = downloadStatus) {
            is ModelDownloadStatus.Downloading -> {
                Surface(
                    color = Color(0x22F59E0B),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonGold),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NeonGold,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = status.progressMessage.tr(),
                                color = NeonGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = NeonGold,
                            trackColor = SurfaceDark
                        )
                    }
                }
            }
            is ModelDownloadStatus.Ready -> {
                if (currentLangCode != "en") {
                    Surface(
                        color = Color(0x2210B981),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeonGreen),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Model downloaded & active for offline use!".tr(),
                                color = NeonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            is ModelDownloadStatus.Error -> {
                Surface(
                    color = Color(0x22EF4444),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoralRed),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = CoralRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.message,
                            color = CoralRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            else -> {}
        }

        // Quick popular language chips
        Text(
            text = "Popular Languages".tr(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(popularList.take(8)) { item ->
                val isSelected = currentLangCode.equals(item.code, ignoreCase = true)
                Surface(
                    color = if (isSelected) NeonGold else SurfaceDark,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isSelected) NeonGold else CardBorder),
                    modifier = Modifier.clickable {
                        translationManager.selectLanguage(item) { success ->
                            if (success) onLanguageSelectedAndReady()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = item.flagEmoji, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.displayName,
                            color = if (isSelected) BgDark else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search 50+ languages...".tr(), color = TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search".tr(), tint = TextMuted, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = NeonGold,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Full Languages List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredList, key = { it.code }) { item ->
                val isSelected = currentLangCode.equals(item.code, ignoreCase = true)
                Surface(
                    color = if (isSelected) Color(0x33F59E0B) else SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isSelected) NeonGold else CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            translationManager.selectLanguage(item) { success ->
                                if (success) onLanguageSelectedAndReady()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = item.flagEmoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = item.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) NeonGold else TextPrimary
                                )
                                if (item.nativeName != item.displayName) {
                                    Text(
                                        text = item.nativeName,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Selected",
                                tint = NeonGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class FeatureBullet(val title: String, val desc: String)

@Composable
fun FeatureWalkthroughSlide(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    bullets: List<FeatureBullet>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Feature Header Banner
        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(iconColor.copy(alpha = 0.3f), iconColor.copy(alpha = 0.05f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Key Features & Capabilities".tr(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bullet cards
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(bullets) { bullet ->
                Surface(
                    color = SurfaceDark.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = bullet.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bullet.desc,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}
