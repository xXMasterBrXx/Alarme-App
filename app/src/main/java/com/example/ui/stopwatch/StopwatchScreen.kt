package com.example.ui.stopwatch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalTimeAtmosphere
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class LapRecord(
    val lapIndex: Int,
    val lapTimeMillis: Long,
    val overallTimeMillis: Long
)

@Composable
fun StopwatchScreen() {
    val atmosphere = LocalTimeAtmosphere.current
    var isRunning by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableLongStateOf(0L) }
    var startTime by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<LapRecord>() }
    var lastLapTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            startTime = System.currentTimeMillis() - elapsedTime
            while (isActive && isRunning) {
                elapsedTime = System.currentTimeMillis() - startTime
                delay(16L) // ~60fps
            }
        }
    }

    val minutes = (elapsedTime / 60000) % 60
    val seconds = (elapsedTime / 1000) % 60
    val millisHundredths = (elapsedTime % 1000) / 10

    // Fastest and slowest laps calculations
    val fastestLapTime = laps.minOfOrNull { it.lapTimeMillis }
    val slowestLapTime = if (laps.size > 1) laps.maxOfOrNull { it.lapTimeMillis } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Circular Stopwatch Display
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(270.dp)
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            val glowColor = atmosphere.glowColor

            // Animated progress ring
            Canvas(modifier = Modifier.size(260.dp)) {
                val strokeWidth = 8.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val sweepAngle = ((elapsedTime % 60000) / 60000f) * 360f

                // Background track
                drawCircle(
                    color = outlineColor,
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )

                // Active sweeping arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, glowColor, primaryColor)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Big Digital Time Counter
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        fontSize = 50.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = String.format(".%02d", millisHundredths),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = atmosphere.glowColor,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (laps.isNotEmpty()) {
                    Text(
                        text = "Volta ${laps.size + 1}: ${formatTime(elapsedTime - lastLapTime)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset Button
            OutlinedButton(
                onClick = {
                    isRunning = false
                    elapsedTime = 0L
                    lastLapTime = 0L
                    laps.clear()
                },
                enabled = elapsedTime > 0L,
                modifier = Modifier
                    .size(64.dp)
                    .testTag("reset_stopwatch_button"),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reiniciar", modifier = Modifier.size(24.dp))
            }

            // Main Play/Pause Button
            Button(
                onClick = { isRunning = !isRunning },
                modifier = Modifier
                    .size(80.dp)
                    .testTag("play_pause_stopwatch_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Pausar" else "Iniciar",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Lap Button
            OutlinedButton(
                onClick = {
                    val lapDuration = elapsedTime - lastLapTime
                    laps.add(
                        0,
                        LapRecord(
                            lapIndex = laps.size + 1,
                            lapTimeMillis = lapDuration,
                            overallTimeMillis = elapsedTime
                        )
                    )
                    lastLapTime = elapsedTime
                },
                enabled = isRunning,
                modifier = Modifier
                    .size(64.dp)
                    .testTag("lap_stopwatch_button"),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Flag, contentDescription = "Registrar Volta", modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Laps List
        if (laps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Volta", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text("Tempo da Volta", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text("Tempo Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(laps) { _, record ->
                    val isFastest = laps.size > 1 && record.lapTimeMillis == fastestLapTime
                    val isSlowest = laps.size > 1 && record.lapTimeMillis == slowestLapTime

                    val badgeColor = when {
                        isFastest -> Color(0xFF10B981) // Emerald
                        isSlowest -> Color(0xFFEF4444) // Red
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lap_item_${record.lapIndex}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Volta ${record.lapIndex}" + if (isFastest) " ⚡" else if (isSlowest) " ⏳" else "",
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor
                            )
                            Text(
                                text = formatTime(record.lapTimeMillis),
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                            Text(
                                text = formatTime(record.overallTimeMillis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

private fun formatTime(millis: Long): String {
    val m = (millis / 60000) % 60
    val s = (millis / 1000) % 60
    val c = (millis % 1000) / 10
    return String.format("%02d:%02d.%02d", m, s, c)
}
