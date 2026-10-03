package com.example.ui.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.AlarmSoundPlayer
import com.example.ui.theme.LocalTimeAtmosphere
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val atmosphere = LocalTimeAtmosphere.current

    var selectedHours by remember { mutableIntStateOf(0) }
    var selectedMinutes by remember { mutableIntStateOf(5) }
    var selectedSeconds by remember { mutableIntStateOf(0) }

    var totalDurationMillis by remember { mutableLongStateOf(0L) }
    var remainingMillis by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var isConfiguring by remember { mutableStateOf(true) }
    var showFinishedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, remainingMillis) {
        if (isRunning && remainingMillis > 0) {
            val start = System.currentTimeMillis()
            delay(100L)
            val elapsed = System.currentTimeMillis() - start
            remainingMillis = (remainingMillis - elapsed).coerceAtLeast(0L)
            if (remainingMillis == 0L) {
                isRunning = false
                isConfiguring = true
                showFinishedDialog = true
                AlarmSoundPlayer.play(context, "birds", true)
            }
        }
    }

    val presets = listOf(
        Pair("1 min", 60L),
        Pair("3 min", 180L),
        Pair("5 min", 300L),
        Pair("10 min", 600L),
        Pair("15 min", 900L),
        Pair("25m Pomodoro", 1500L),
        Pair("30 min", 1800L),
        Pair("1 hora", 3600L)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (isConfiguring) {
            // Configuration mode
            Text(
                text = "Definir Temporizador",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Time Selector Card (Hours, Minutes, Seconds)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedHours = (selectedHours + 1) % 24 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Mais horas", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = String.format("%02d", selectedHours),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { selectedHours = if (selectedHours - 1 < 0) 23 else selectedHours - 1 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Menos horas", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("Horas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }

                    Text(":", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)

                    // Minutes
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedMinutes = (selectedMinutes + 1) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Mais minutos", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = String.format("%02d", selectedMinutes),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { selectedMinutes = if (selectedMinutes - 1 < 0) 59 else selectedMinutes - 1 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Menos minutos", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("Minutos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }

                    Text(":", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)

                    // Seconds
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedSeconds = (selectedSeconds + 5) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Mais segundos", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = String.format("%02d", selectedSeconds),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { selectedSeconds = if (selectedSeconds - 5 < 0) 55 else selectedSeconds - 5 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Menos segundos", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("Segundos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Preset chips
            Text(
                text = "Predefinições Rápidas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (name, totalSecs) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            selectedHours = (totalSecs / 3600).toInt()
                            selectedMinutes = ((totalSecs % 3600) / 60).toInt()
                            selectedSeconds = (totalSecs % 60).toInt()
                        },
                        label = { Text(name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Start Button
            val durationSecs = selectedHours * 3600L + selectedMinutes * 60L + selectedSeconds
            Button(
                onClick = {
                    if (durationSecs > 0) {
                        totalDurationMillis = durationSecs * 1000L
                        remainingMillis = totalDurationMillis
                        isConfiguring = false
                        isRunning = true
                    }
                },
                enabled = durationSecs > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_timer_button"),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("INICIAR TEMPORIZADOR", fontWeight = FontWeight.Bold)
            }

        } else {
            // Active Countdown Mode
            val remSecs = (remainingMillis / 1000)
            val h = remSecs / 3600
            val m = (remSecs % 3600) / 60
            val s = remSecs % 60
            val progress = if (totalDurationMillis > 0) remainingMillis.toFloat() / totalDurationMillis else 0f

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                val glowColor = atmosphere.glowColor

                Canvas(modifier = Modifier.size(270.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2f

                    // Track
                    drawCircle(
                        color = outlineColor,
                        radius = radius,
                        style = Stroke(width = strokeWidth)
                    )

                    // Remaining progress arc
                    drawArc(
                        brush = Brush.sweepGradient(listOf(primaryColor, glowColor, primaryColor)),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s),
                        fontSize = if (h > 0) 40.sp else 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(progress * 100).toInt()}% restante",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bonus +1 min button
            OutlinedButton(
                onClick = {
                    remainingMillis += 60000L
                    totalDurationMillis += 60000L
                },
                modifier = Modifier.testTag("add_minute_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+1 minuto")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Timer controls (Cancel, Pause/Resume)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel
                OutlinedButton(
                    onClick = {
                        isRunning = false
                        isConfiguring = true
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Cancelar", modifier = Modifier.size(26.dp))
                }

                // Pause / Resume
                Button(
                    onClick = { isRunning = !isRunning },
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pausar" else "Continuar",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }

    if (showFinishedDialog) {
        AlertDialog(
            onDismissRequest = {
                AlarmSoundPlayer.stop()
                showFinishedDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Tempo Esgotado!", fontWeight = FontWeight.Bold)
                }
            },
            text = { Text("O temporizador chegou ao fim com sucesso!") },
            confirmButton = {
                Button(onClick = {
                    AlarmSoundPlayer.stop()
                    showFinishedDialog = false
                }) {
                    Text("OK / Parar Alarme")
                }
            }
        )
    }
}
