package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.TimeAtmosphere
import com.example.ui.theme.TimePeriod
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class StarShape(val title: String, val iconEmoji: String) {
    FOUR_POINT("4 Pontas", "✦"),
    EIGHT_POINT("8 Pontas", "✴️"),
    DOT("Pontos", "●"),
    DIAMOND("Diamantes", "◆")
}

private data class StarParticle(
    val normalizedX: Float,
    val normalizedY: Float,
    val sizeMultiplier: Float,
    val baseAlpha: Float,
    val pulseFrequency: Float,
    val phaseOffset: Float,
    val isProminentHero: Boolean
)

@Composable
fun AmbientStarfieldBackground(
    atmosphere: TimeAtmosphere,
    isDarkTheme: Boolean,
    starTransparency: Float = 0.5f,
    starShape: StarShape = StarShape.FOUR_POINT,
    isStarTwinkle: Boolean = true,
    modifier: Modifier = Modifier
) {
    // If transparency is practically zero, skip computation completely
    if (starTransparency <= 0.01f) {
        return
    }

    // 1. Generate a stable, aesthetically distributed constellation of 38 stars (optimized count)
    val stars = remember {
        val random = Random(42) // Fixed seed for stable, beautiful sky map
        List(38) { index ->
            StarParticle(
                normalizedX = random.nextFloat(),
                normalizedY = random.nextFloat(),
                sizeMultiplier = if (index % 6 == 0) random.nextFloat() * 1.3f + 2.0f else random.nextFloat() * 0.8f + 1.0f,
                baseAlpha = random.nextFloat() * 0.25f + 0.30f,
                pulseFrequency = random.nextFloat() * 2.2f + 1.1f,
                phaseOffset = random.nextFloat() * (2f * PI.toFloat()),
                isProminentHero = index % 8 == 0
            )
        }
    }

    // 2. Adjust visibility & subtle master opacity according to the time period, theme, and user slider
    val basePeriodAlpha = when (atmosphere.period) {
        TimePeriod.NIGHT -> if (isDarkTheme) 0.65f else 0.35f
        TimePeriod.DAWN -> if (isDarkTheme) 0.35f else 0.16f
        TimePeriod.SUNSET -> if (isDarkTheme) 0.42f else 0.20f
        TimePeriod.DAY -> if (isDarkTheme) 0.16f else 0.06f
    }

    val targetMasterAlpha = (basePeriodAlpha * starTransparency * 1.5f).coerceIn(0f, 1f)

    val animatedMasterAlpha by animateFloatAsState(
        targetValue = targetMasterAlpha,
        animationSpec = tween(durationMillis = 500),
        label = "starfield_master_alpha"
    )

    // Primary star tint adjusted to atmosphere
    val primaryStarColor = when (atmosphere.period) {
        TimePeriod.NIGHT -> Color(0xFFF0F9FF) // Cool cosmic starlight
        TimePeriod.DAWN -> Color(0xFFFFF0F5) // Gentle soft rose morning twilight
        TimePeriod.SUNSET -> Color(0xFFFFF1F2) // Sunset lavender/blush
        TimePeriod.DAY -> Color(0xFFFFFFFF) // Pure ethereal spark
    }

    // 3. Reusable paths for pointed star drawing (allocated once)
    val mainStarPath = remember { Path() }
    val secondaryStarPath = remember { Path() }

    // 4. Organic twinkling loop only when twinkling is enabled
    val timeTick: Float = if (isStarTwinkle) {
        val infiniteTransition = rememberInfiniteTransition(label = "star_twinkle_loop")
        val animatedTick by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "star_time_tick"
        )
        animatedTick
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Hardware layer isolation prevents invalidating other UI components
                clip = false
            }
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f || animatedMasterAlpha <= 0.005f) return@Canvas

        val densityFactor = density * 2.4f
        val strokeWidthPx = density * 0.9f
        val cos45 = 0.70710678f

        stars.forEach { star ->
            val x = star.normalizedX * width
            val y = star.normalizedY * height

            // Compute twinkle factor (animated sin wave or static 1.0)
            val twinkleMultiplier = if (isStarTwinkle) {
                val wave = sin((timeTick * star.pulseFrequency + star.phaseOffset).toDouble()).toFloat()
                0.35f + 0.65f * ((wave + 1f) * 0.5f)
            } else {
                1.0f
            }

            val dynamicAlpha = (star.baseAlpha * twinkleMultiplier * animatedMasterAlpha).coerceIn(0f, 1f)

            if (dynamicAlpha > 0.015f) {
                val armLength = star.sizeMultiplier * densityFactor

                when (starShape) {
                    StarShape.FOUR_POINT -> {
                        // Pointed 4-point celestial star (concave curved sides)
                        mainStarPath.reset()
                        mainStarPath.moveTo(x, y - armLength)
                        mainStarPath.quadraticTo(x, y, x + armLength, y)
                        mainStarPath.quadraticTo(x, y, x, y + armLength)
                        mainStarPath.quadraticTo(x, y, x - armLength, y)
                        mainStarPath.quadraticTo(x, y, x, y - armLength)
                        mainStarPath.close()

                        drawPath(
                            path = mainStarPath,
                            color = primaryStarColor.copy(alpha = (dynamicAlpha * 0.75f).coerceIn(0f, 1f))
                        )
                    }

                    StarShape.EIGHT_POINT -> {
                        // 8-point sparkle: main 4 points + 45-degree secondary cross
                        mainStarPath.reset()
                        mainStarPath.moveTo(x, y - armLength)
                        mainStarPath.quadraticTo(x, y, x + armLength, y)
                        mainStarPath.quadraticTo(x, y, x, y + armLength)
                        mainStarPath.quadraticTo(x, y, x - armLength, y)
                        mainStarPath.quadraticTo(x, y, x, y - armLength)
                        mainStarPath.close()

                        drawPath(
                            path = mainStarPath,
                            color = primaryStarColor.copy(alpha = (dynamicAlpha * 0.75f).coerceIn(0f, 1f))
                        )

                        val diagArm = armLength * 0.48f
                        val d = diagArm * cos45

                        secondaryStarPath.reset()
                        secondaryStarPath.moveTo(x + d, y - d)
                        secondaryStarPath.quadraticTo(x, y, x + d, y + d)
                        secondaryStarPath.quadraticTo(x, y, x - d, y + d)
                        secondaryStarPath.quadraticTo(x, y, x - d, y - d)
                        secondaryStarPath.quadraticTo(x, y, x + d, y - d)
                        secondaryStarPath.close()

                        drawPath(
                            path = secondaryStarPath,
                            color = primaryStarColor.copy(alpha = (dynamicAlpha * 0.50f).coerceIn(0f, 1f))
                        )

                        if (star.isProminentHero && dynamicAlpha > 0.20f) {
                            val flareLen = armLength * 1.5f
                            val flareColor = primaryStarColor.copy(alpha = (dynamicAlpha * 0.30f).coerceIn(0f, 1f))
                            drawLine(
                                color = flareColor,
                                start = Offset(x - flareLen, y),
                                end = Offset(x + flareLen, y),
                                strokeWidth = strokeWidthPx,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = flareColor,
                                start = Offset(x, y - flareLen),
                                end = Offset(x, y + flareLen),
                                strokeWidth = strokeWidthPx,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    StarShape.DOT -> {
                        // Smooth celestial circle points
                        drawCircle(
                            color = primaryStarColor.copy(alpha = (dynamicAlpha * 0.80f).coerceIn(0f, 1f)),
                            radius = armLength * 0.45f,
                            center = Offset(x, y)
                        )
                    }

                    StarShape.DIAMOND -> {
                        // Sharp diamond star (losango)
                        mainStarPath.reset()
                        mainStarPath.moveTo(x, y - armLength)
                        mainStarPath.lineTo(x + armLength * 0.55f, y)
                        mainStarPath.lineTo(x, y + armLength)
                        mainStarPath.lineTo(x - armLength * 0.55f, y)
                        mainStarPath.close()

                        drawPath(
                            path = mainStarPath,
                            color = primaryStarColor.copy(alpha = (dynamicAlpha * 0.75f).coerceIn(0f, 1f))
                        )
                    }
                }

                // Tiny sparkling starlight center
                drawCircle(
                    color = Color.White.copy(alpha = (dynamicAlpha * 0.75f).coerceIn(0f, 1f)),
                    radius = (armLength * 0.16f).coerceAtLeast(0.7f),
                    center = Offset(x, y)
                )
            }
        }
    }
}
