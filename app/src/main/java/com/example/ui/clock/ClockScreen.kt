package com.example.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalTimeAtmosphere
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WorldCity(
    val name: String,
    val country: String,
    val timeZoneId: String,
    val flagEmoji: String
)

val AvailableCities = listOf(
    WorldCity("Brasília", "Brasil", "America/Sao_Paulo", "🇧🇷"),
    WorldCity("Lisboa", "Portugal", "Europe/Lisbon", "🇵🇹"),
    WorldCity("Nova York", "EUA", "America/New_York", "🇺🇸"),
    WorldCity("Londres", "Reino Unido", "Europe/London", "🇬🇧"),
    WorldCity("Tóquio", "Japão", "Asia/Tokyo", "🇯🇵"),
    WorldCity("Sydney", "Austrália", "Australia/Sydney", "🇦🇺"),
    WorldCity("Paris", "França", "Europe/Paris", "🇫🇷"),
    WorldCity("Los Angeles", "EUA", "America/Los_Angeles", "🇺🇸"),
    WorldCity("Dubai", "EAU", "Asia/Dubai", "🇦🇪")
)

@Composable
fun ClockScreen(
    onOpenThemeSelector: () -> Unit
) {
    val atmosphere = LocalTimeAtmosphere.current
    var currentTime by remember { mutableStateOf(Date()) }
    var showAddCityDialog by remember { mutableStateOf(false) }

    val savedCities = remember {
        mutableStateListOf(
            AvailableCities[0], // Brasília
            AvailableCities[1], // Lisboa
            AvailableCities[2], // Nova York
            AvailableCities[4]  // Tóquio
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000L)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val secondsFormat = remember { SimpleDateFormat("ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Hero Digital Clock Area
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_clock_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(atmosphere.cardGradient)
                        .border(1.dp, atmosphere.glowColor.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = timeFormat.format(currentTime),
                                fontSize = 76.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1.5).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 76.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = secondsFormat.format(currentTime),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = atmosphere.glowColor,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dateFormat.format(currentTime).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        // Timezone pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Fuso Local: " + TimeZone.getDefault().displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // World Clock Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Fusos Horários",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Fusos Horários Globais",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = { showAddCityDialog = true },
                    modifier = Modifier.testTag("add_world_clock_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adicionar")
                }
            }
        }

        // World Clock Items
        items(savedCities, key = { it.timeZoneId }) { city ->
            val cityCal = Calendar.getInstance(TimeZone.getTimeZone(city.timeZoneId))
            val cityHour = cityCal.get(Calendar.HOUR_OF_DAY)
            val cityMinute = cityCal.get(Calendar.MINUTE)
            val isCityDay = cityHour in 6..18

            val localCal = Calendar.getInstance()
            val diffHours = ((cityCal.timeInMillis - localCal.timeInMillis) / (1000 * 60 * 60)).toInt()
            val diffText = when {
                diffHours == 0 -> "Mesmo horário"
                diffHours > 0 -> "+$diffHours h à frente"
                else -> "$diffHours h atrás"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("world_clock_item_${city.name.lowercase()}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = city.flagEmoji, fontSize = 24.sp)
                        Column {
                            Text(
                                text = city.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${city.country} • $diffText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format("%02d:%02d", cityHour, cityMinute),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isCityDay) "☀️ Dia" else "🌙 Noite",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCityDay) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                            )
                        }

                        if (savedCities.size > 1) {
                            IconButton(
                                onClick = { savedCities.remove(city) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remover cidade",
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    if (showAddCityDialog) {
        AlertDialog(
            onDismissRequest = { showAddCityDialog = false },
            title = { Text("Adicionar Cidade Global", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvailableCities.filter { it !in savedCities }.forEach { city ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    savedCities.add(city)
                                    showAddCityDialog = false
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = city.flagEmoji, fontSize = 22.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = city.name, fontWeight = FontWeight.Bold)
                                    Text(text = city.country, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddCityDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
