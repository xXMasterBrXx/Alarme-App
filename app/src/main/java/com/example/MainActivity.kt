package com.example

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.alarm.AlarmScreen
import com.example.ui.alarm.AlarmViewModel
import com.example.ui.clock.ClockScreen
import com.example.ui.components.AmbientStarfieldBackground
import com.example.ui.components.StarShape
import com.example.ui.components.ThemeSelectorSheet
import com.example.ui.stopwatch.StopwatchScreen
import com.example.ui.theme.ChronoDynamicTheme
import com.example.ui.theme.LocalTimeAtmosphere
import com.example.ui.theme.ThemeMode
import com.example.ui.timer.TimerScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE) }

            var themeMode by rememberSaveable {
                val savedModeName = prefs.getString("theme_mode", ThemeMode.AUTO.name)
                mutableStateOf(ThemeMode.entries.find { it.name == savedModeName } ?: ThemeMode.AUTO)
            }

            var isDarkTheme by rememberSaveable {
                mutableStateOf(prefs.getBoolean("is_dark_theme", true))
            }

            var starTransparency by rememberSaveable {
                mutableStateOf(prefs.getFloat("star_transparency", 0.5f))
            }

            var starShape by rememberSaveable {
                val savedShape = prefs.getString("star_shape", StarShape.FOUR_POINT.name)
                mutableStateOf(StarShape.entries.find { it.name == savedShape } ?: StarShape.FOUR_POINT)
            }

            var isStarTwinkle by rememberSaveable {
                mutableStateOf(prefs.getBoolean("star_twinkle", true))
            }

            // Notification permission launcher for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* Result handled */ }

                LaunchedEffect(Unit) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            ChronoDynamicTheme(
                themeMode = themeMode,
                isDarkTheme = isDarkTheme
            ) {
                com.example.ui.components.YouTubeAudioHost()
                MainAppContent(
                    themeMode = themeMode,
                    isDarkTheme = isDarkTheme,
                    starTransparency = starTransparency,
                    starShape = starShape,
                    isStarTwinkle = isStarTwinkle,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        prefs.edit().putString("theme_mode", mode.name).apply()
                    },
                    onDarkThemeChange = { dark ->
                        isDarkTheme = dark
                        prefs.edit().putBoolean("is_dark_theme", dark).apply()
                    },
                    onStarTransparencyChange = { value ->
                        starTransparency = value
                        prefs.edit().putFloat("star_transparency", value).apply()
                    },
                    onStarShapeChange = { shape ->
                        starShape = shape
                        prefs.edit().putString("star_shape", shape.name).apply()
                    },
                    onStarTwinkleToggle = { twinkle ->
                        isStarTwinkle = twinkle
                        prefs.edit().putBoolean("star_twinkle", twinkle).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun MainAppContent(
    themeMode: ThemeMode,
    isDarkTheme: Boolean,
    starTransparency: Float,
    starShape: StarShape,
    isStarTwinkle: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onStarTransparencyChange: (Float) -> Unit,
    onStarShapeChange: (StarShape) -> Unit,
    onStarTwinkleToggle: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val atmosphere = LocalTimeAtmosphere.current
    val alarmViewModel: AlarmViewModel = viewModel()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var availableRelease by remember { mutableStateOf<com.example.data.update.AppReleaseInfo?>(null) }

    // Check for updates on startup
    LaunchedEffect(Unit) {
        val result = com.example.data.update.GitHubUpdateManager.checkForUpdates(context)
        result.getOrNull()?.let { release ->
            val installed = com.example.data.update.GitHubUpdateManager.getInstalledVersionDisplay(context)
                .removePrefix("v").removePrefix("V").trim()
            val remote = release.tagName
                .removePrefix("v").removePrefix("V").trim()
            val isDifferent = !installed.equals(remote, ignoreCase = true)

            if (release.isNewer || isDifferent) {
                availableRelease = release
            }
        }
    }

    val tabs = remember {
        listOf(
            TabItem("Alarme", Icons.Filled.Alarm, Icons.Outlined.Alarm, "alarms_tab"),
            TabItem("Relógio", Icons.Filled.Schedule, Icons.Outlined.Schedule, "clock_tab"),
            TabItem("Cronômetro", Icons.Filled.Timer, Icons.Outlined.Timer, "stopwatch_tab"),
            TabItem("Timer", Icons.Filled.HourglassEmpty, Icons.Outlined.HourglassEmpty, "timer_tab")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(atmosphere.gradientBrush)
    ) {
        // Delicate stars background adjusting dynamically to the active theme, period & user style preferences
        AmbientStarfieldBackground(
            atmosphere = atmosphere,
            isDarkTheme = isDarkTheme,
            starTransparency = starTransparency,
            starShape = starShape,
            isStarTwinkle = isStarTwinkle,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            topBar = {
                // Clean top bar: no app name, no time-of-day tags, only theme action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showThemeSheet = true },
                        modifier = Modifier.testTag("theme_selector_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Temas Dinâmicos",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            bottomBar = {
                // Animated floating pill navigation bar isolated with hardware layer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        tonalElevation = 8.dp,
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.dp, atmosphere.glowColor.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .testTag("floating_pill_navigation_bar")
                            .graphicsLayer {
                                shadowElevation = 10f
                                shape = CircleShape
                                clip = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                NavPillItem(
                                    tab = tab,
                                    isSelected = selectedTab == index,
                                    onClick = { selectedTab = index }
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                    },
                    label = "tab_transition",
                    modifier = Modifier.fillMaxSize()
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> AlarmScreen(viewModel = alarmViewModel)
                        1 -> ClockScreen(onOpenThemeSelector = { showThemeSheet = true })
                        2 -> StopwatchScreen()
                        3 -> TimerScreen()
                    }
                }
            }
        }
    }

    if (showThemeSheet) {
        ThemeSelectorSheet(
            selectedMode = themeMode,
            isDarkTheme = isDarkTheme,
            starTransparency = starTransparency,
            starShape = starShape,
            isStarTwinkle = isStarTwinkle,
            onModeSelected = onThemeModeChange,
            onDarkThemeToggle = onDarkThemeChange,
            onStarTransparencyChange = onStarTransparencyChange,
            onStarShapeChange = onStarShapeChange,
            onStarTwinkleToggle = onStarTwinkleToggle,
            onShowUpdateDialog = { release ->
                availableRelease = release
            },
            onDismiss = { showThemeSheet = false }
        )
    }

    if (availableRelease != null) {
        com.example.ui.components.AppUpdateDialog(
            releaseInfo = availableRelease!!,
            onDismiss = { availableRelease = null }
        )
    }
}

@Composable
private fun NavPillItem(
    tab: TabItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val pillBackground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "pill_bg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "icon_color"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "icon_scale"
    )

    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(pillBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = if (isSelected) 16.dp else 12.dp)
            .testTag(tab.tag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.title,
                tint = iconColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)),
                exit = fadeOut(animationSpec = tween(120)) + shrinkHorizontally(animationSpec = tween(120))
            ) {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
            }
        }
    }
}

data class TabItem(
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val tag: String
)
