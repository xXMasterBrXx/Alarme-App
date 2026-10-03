package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import java.util.Calendar

enum class TimePeriod(
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val hourRange: String
) {
    DAWN("Amanhecer", "Despertar suave & luz rosada", "🌅", "05:00 - 08:00"),
    DAY("Dia Ensolarado", "Energia plena & foco radiante", "☀️", "08:00 - 17:00"),
    SUNSET("Entardecer", "Crepúsculo rubro & horizonte avermelhado", "🌇", "17:00 - 20:00"),
    NIGHT("Noite Estrelada", "Descanso profundo & serenidade", "🌙", "20:00 - 05:00")
}

enum class ThemeMode(val title: String, val iconEmoji: String) {
    AUTO("Automático (Horário Real)", "⏱️"),
    DAWN("Amanhecer Fixo", "🌅"),
    DAY("Dia Fixo", "☀️"),
    SUNSET("Entardecer Fixo", "🌇"),
    NIGHT("Noite Fixa", "🌙")
}

fun getTimePeriodForHour(hour: Int): TimePeriod {
    return when (hour) {
        in 5..7 -> TimePeriod.DAWN
        in 8..16 -> TimePeriod.DAY
        in 17..19 -> TimePeriod.SUNSET
        else -> TimePeriod.NIGHT
    }
}

// ---- AMANHECER (DAWN) PALETTES (ROSA / PINK DAWN) ----
val DawnDarkScheme = darkColorScheme(
    primary = Color(0xFFF472B6), // Radiant morning rose pink
    onPrimary = Color(0xFF4C0027),
    primaryContainer = Color(0xFF70043D),
    onPrimaryContainer = Color(0xFFFFD8E7),
    secondary = Color(0xFFF9A8D4), // Soft blush pink
    onSecondary = Color(0xFF4E1135),
    secondaryContainer = Color(0xFF6B204B),
    onSecondaryContainer = Color(0xFFFFD8ED),
    tertiary = Color(0xFFE879F9), // Ethereal orchid lilac
    onTertiary = Color(0xFF440058),
    background = Color(0xFF150A17), // Deep morning twilight violet-rose
    onBackground = Color(0xFFFDE8F3),
    surface = Color(0xFF200F23),
    onSurface = Color(0xFFFDE8F3),
    surfaceVariant = Color(0xFF36183B),
    onSurfaceVariant = Color(0xFFE5C0D7),
    outline = Color(0xFF985E89)
)

val DawnLightScheme = lightColorScheme(
    primary = Color(0xFFDB2777), // Deep vibrant dawn rose pink
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE7F3),
    onPrimaryContainer = Color(0xFF4C0026),
    secondary = Color(0xFFBE185D), // Rich petal pink
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDF2F8),
    onSecondaryContainer = Color(0xFF440623),
    tertiary = Color(0xFF9333EA), // Sunrise orchid
    onTertiary = Color.White,
    background = Color(0xFFFFF5F8), // Soft pink morning glow
    onBackground = Color(0xFF2B0A1F),
    surface = Color(0xFFFCE7F3),
    onSurface = Color(0xFF2B0A1F),
    surfaceVariant = Color(0xFFFBCFE8),
    onSurfaceVariant = Color(0xFF6A1E48),
    outline = Color(0xFFA65381)
)

// ---- DIA (DAY) PALETTES ----
val DayDarkScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF00354E),
    primaryContainer = Color(0xFF004D6E),
    onPrimaryContainer = Color(0xFFC3E8FF),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF432C00),
    secondaryContainer = Color(0xFF614000),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF003823),
    background = Color(0xFF08121E),
    onBackground = Color(0xFFE2EDF8),
    surface = Color(0xFF0F1E31),
    onSurface = Color(0xFFE2EDF8),
    surfaceVariant = Color(0xFF1C314C),
    onSurfaceVariant = Color(0xFFB5CADB),
    outline = Color(0xFF58728C)
)

val DayLightScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = Color(0xFF001F2F),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF2E1700),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF0F9FF),
    onBackground = Color(0xFF0C1929),
    surface = Color(0xFFE0F2FE),
    onSurface = Color(0xFF0C1929),
    surfaceVariant = Color(0xFFBAE6FD),
    onSurfaceVariant = Color(0xFF264964),
    outline = Color(0xFF688FA9)
)

// ---- ENTARDECER / POR DO SOL (SUNSET) PALETTES (RUBRO / REDDISH CRIMSON) ----
val SunsetDarkScheme = darkColorScheme(
    primary = Color(0xFFEF4444), // Intense twilight crimson red
    onPrimary = Color(0xFF450004),
    primaryContainer = Color(0xFF7F1D1D),
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFFF87171), // Sunset ruby red
    onSecondary = Color(0xFF4C0B0E),
    secondaryContainer = Color(0xFF991B1B),
    onSecondaryContainer = Color(0xFFFFDAD9),
    tertiary = Color(0xFFFB923C), // Ember sunset orange
    onTertiary = Color(0xFF4E1600),
    background = Color(0xFF180709), // Deep crimson-wine twilight
    onBackground = Color(0xFFFFECEB),
    surface = Color(0xFF240C0F),
    onSurface = Color(0xFFFFECEB),
    surfaceVariant = Color(0xFF3B1519),
    onSurfaceVariant = Color(0xFFE5BFC2),
    outline = Color(0xFF9B5B61)
)

val SunsetLightScheme = lightColorScheme(
    primary = Color(0xFFB91C1C), // Deep crimson ruby red
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2E2),
    onPrimaryContainer = Color(0xFF410003),
    secondary = Color(0xFFDC2626), // Vivid sunset red
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEE2E2),
    onSecondaryContainer = Color(0xFF3E0003),
    tertiary = Color(0xFFC2410C), // Ember terracotta
    onTertiary = Color.White,
    background = Color(0xFFFFF5F5), // Soft reddish sunset atmosphere
    onBackground = Color(0xFF2E090D),
    surface = Color(0xFFFFEAEA),
    onSurface = Color(0xFF2E090D),
    surfaceVariant = Color(0xFFFDD4D5),
    onSurfaceVariant = Color(0xFF6A1E24),
    outline = Color(0xFFA5545B)
)

// ---- NOITE (NIGHT) PALETTES ----
val NightDarkScheme = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004F5C),
    onPrimaryContainer = Color(0xFFA5EEFD),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF121B66),
    secondaryContainer = Color(0xFF242C80),
    onSecondaryContainer = Color(0xFFDEE0FF),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF2C1567),
    background = Color(0xFF050811),
    onBackground = Color(0xFFE0E5F5),
    surface = Color(0xFF0C1324),
    onSurface = Color(0xFFE0E5F5),
    surfaceVariant = Color(0xFF17233F),
    onSurfaceVariant = Color(0xFFBDC7E3),
    outline = Color(0xFF4A5A82)
)

val NightLightScheme = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF002026),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF0B104D),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFEEF2F6),
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFCBD5E1),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF64748B)
)

data class TimeAtmosphere(
    val period: TimePeriod,
    val gradientBrush: Brush,
    val glowColor: Color,
    val cardGradient: Brush
)

val LocalTimeAtmosphere = compositionLocalOf {
    TimeAtmosphere(
        period = TimePeriod.DAY,
        gradientBrush = Brush.verticalGradient(listOf(Color(0xFF08121E), Color(0xFF0F1E31))),
        glowColor = Color(0xFF38BDF8),
        cardGradient = Brush.verticalGradient(listOf(Color(0xFF1C314C), Color(0xFF0F1E31)))
    )
}

fun getAtmosphereForPeriod(period: TimePeriod, isDark: Boolean): TimeAtmosphere {
    return when (period) {
        TimePeriod.DAWN -> {
            if (isDark) {
                TimeAtmosphere(
                    period = period,
                    gradientBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF2D1130), Color(0xFF1F0B22), Color(0xFF130516))
                    ),
                    glowColor = Color(0xFFF472B6),
                    cardGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF3F1944).copy(alpha = 0.85f), Color(0xFF260D2B).copy(alpha = 0.85f))
                    )
                )
            } else {
                TimeAtmosphere(
                    period = period,
                    gradientBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFCE7F3), Color(0xFFFFF0F5), Color(0xFFFFFFFF))
                    ),
                    glowColor = Color(0xFFDB2777),
                    cardGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFFFCE7F3).copy(alpha = 0.9f), Color(0xFFFDF4F8).copy(alpha = 0.9f))
                    )
                )
            }
        }
        TimePeriod.DAY -> {
            if (isDark) {
                TimeAtmosphere(
                    period = period,
                    gradientBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF08121E), Color(0xFF0D1B2D), Color(0xFF060B12))
                    ),
                    glowColor = Color(0xFF38BDF8),
                    cardGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF1C314C).copy(alpha = 0.85f), Color(0xFF102135).copy(alpha = 0.85f))
                    )
                )
            } else {
                TimeAtmosphere(
                    period = period,
                    gradientBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFE0F2FE), Color(0xFFF0F9FF), Color(0xFFFFFFFF))
                    ),
                    glowColor = Color(0xFF0284C7),
                    cardGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFFBAE6FD).copy(alpha = 0.8f), Color(0xFFE0F2FE).copy(alpha = 0.8f))
                    )
                )
            }
        }
        TimePeriod.SUNSET -> {
            if (isDark) {
                TimeAtmosphere(
                    period = period,
                    gradientBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF300E12), Color(0xFF20080B), Color(0xFF120305))
                    ),
                    glowColor = Color(0xFFEF4444),
                    cardGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF44161C).copy(alpha = 0.85f), Color(0xFF290B0F).copy(alpha = 0.85f))
                    )
                )
            } else {
                TimeAtmosphere(
                    period = period,
                    gradientBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFE4E4), Color(0xFFFFF0F0), Color(0xFFFFFFFF))
                    ),
                    glowColor = Color(0xFFB91C1C),
                    cardGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFD4D4).copy(alpha = 0.85f), Color(0xFFFFE8E8).copy(alpha = 0.85f))
                    )
                )
            }
        }
        TimePeriod.NIGHT -> {
            if (isDark) {
                TimeAtmosphere(
                    period = period,
                    gradientBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF080D1A), Color(0xFF050811), Color(0xFF020308))
                    ),
                    glowColor = Color(0xFF22D3EE),
                    cardGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF151D33).copy(alpha = 0.85f), Color(0xFF0C1324).copy(alpha = 0.85f))
                    )
                )
            } else {
                TimeAtmosphere(
                    period = period,
                    gradientBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9), Color(0xFFFFFFFF))
                    ),
                    glowColor = Color(0xFF0891B2),
                    cardGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFFCBD5E1).copy(alpha = 0.8f), Color(0xFFE2E8F0).copy(alpha = 0.8f))
                    )
                )
            }
        }
    }
}

@Composable
fun ChronoDynamicTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    var currentHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }

    // Auto update time period periodically
    LaunchedEffect(themeMode) {
        if (themeMode == ThemeMode.AUTO) {
            while (true) {
                currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                delay(60000L) // Check every minute
            }
        }
    }

    val activePeriod = when (themeMode) {
        ThemeMode.AUTO -> getTimePeriodForHour(currentHour)
        ThemeMode.DAWN -> TimePeriod.DAWN
        ThemeMode.DAY -> TimePeriod.DAY
        ThemeMode.SUNSET -> TimePeriod.SUNSET
        ThemeMode.NIGHT -> TimePeriod.NIGHT
    }

    val colorScheme = when (activePeriod) {
        TimePeriod.DAWN -> if (isDarkTheme) DawnDarkScheme else DawnLightScheme
        TimePeriod.DAY -> if (isDarkTheme) DayDarkScheme else DayLightScheme
        TimePeriod.SUNSET -> if (isDarkTheme) SunsetDarkScheme else SunsetLightScheme
        TimePeriod.NIGHT -> if (isDarkTheme) NightDarkScheme else NightLightScheme
    }

    val atmosphere = getAtmosphereForPeriod(activePeriod, isDarkTheme)

    CompositionLocalProvider(LocalTimeAtmosphere provides atmosphere) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
