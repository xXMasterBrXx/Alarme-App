package com.example.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.alarm.AlarmSoundPlayer
import com.example.alarm.SystemRingtoneHelper
import com.example.data.model.AlarmEntity
import com.example.data.youtube.YouTubeSoundService
import com.example.data.youtube.YouTubeVideoSound
import com.example.ui.components.YouTubeAudioHost
import com.example.ui.theme.LocalTimeAtmosphere
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditAlarmSheet(
    alarm: AlarmEntity?,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val atmosphere = LocalTimeAtmosphere.current

    YouTubeAudioHost()

    var hour by remember { mutableIntStateOf(alarm?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 0) }
    var label by remember { mutableStateOf(alarm?.label ?: "Alarme") }
    var volume by remember { mutableFloatStateOf(alarm?.volume ?: 0.8f) }
    var specificDateMillis by remember { mutableStateOf<Long?>(alarm?.specificDateMillis) }
    var vibrate by remember { mutableStateOf(alarm?.vibrate ?: true) }
    var vibrationOnly by remember { mutableStateOf(alarm?.vibrationOnly ?: false) }
    var soundTone by remember { mutableStateOf(alarm?.soundTone ?: "default") }
    var snoozeMinutes by remember { mutableIntStateOf(alarm?.snoozeMinutes ?: 10) }
    var mathMission by remember { mutableStateOf(alarm?.mathMission ?: false) }

    var previewingTone by remember { mutableStateOf<String?>(null) }
    var showSoundsDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // Sounds dialog internal state
    var selectedSoundTab by remember { mutableIntStateOf(if (soundTone.startsWith("youtube://")) 1 else 0) }
    var youtubeSearchQuery by remember { mutableStateOf("") }
    var isSearchingYoutube by remember { mutableStateOf(false) }
    var youtubeResults by remember { mutableStateOf<List<YouTubeVideoSound>>(YouTubeSoundService.POPULAR_PRESETS) }

    val systemTones = remember { SystemRingtoneHelper.getAvailableTones(context) }
    val dateDisplayFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("pt", "BR")) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val selectedUri = uri?.toString() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString() ?: "default"
            soundTone = selectedUri
            previewingTone = selectedUri
            AlarmSoundPlayer.playPreview(context, selectedUri, volume) {
                if (previewingTone == selectedUri) {
                    previewingTone = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AlarmSoundPlayer.stopPreview()
        }
    }

    // Selected days: 1=Seg, 2=Ter, 3=Qua, 4=Qui, 5=Sex, 6=Sáb, 7=Dom
    val selectedDays = remember {
        mutableStateListOf<Int>().apply {
            if (alarm != null && alarm.specificDateMillis == null) {
                addAll(alarm.getDaysList())
            }
        }
    }

    val dayNames = listOf(
        Pair(7, "Dom"),
        Pair(1, "Seg"),
        Pair(2, "Ter"),
        Pair(3, "Qua"),
        Pair(4, "Qui"),
        Pair(5, "Sex"),
        Pair(6, "Sáb")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (alarm == null) "Novo Alarme" else "Editar Alarme",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Time Selector Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { hour = (hour + 1) % 24 },
                            modifier = Modifier.testTag("increment_hour_button")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Aumentar hora", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = String.format("%02d", hour),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("alarm_hour_text")
                        )
                        IconButton(
                            onClick = { hour = if (hour - 1 < 0) 23 else hour - 1 },
                            modifier = Modifier.testTag("decrement_hour_button")
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Diminuir hora", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Text(
                        text = ":",
                        fontSize = 50.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Minutes column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { minute = (minute + 5) % 60 },
                            modifier = Modifier.testTag("increment_minute_button")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Aumentar minuto", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = String.format("%02d", minute),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("alarm_minute_text")
                        )
                        IconButton(
                            onClick = { minute = if (minute - 5 < 0) 55 else minute - 5 },
                            modifier = Modifier.testTag("decrement_minute_button")
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Diminuir minuto", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Label Input
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Nome do Alarme") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alarm_label_input"),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 1. ESCOLHA DE DATA ESPECÍFICA NO CALENDÁRIO
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (specificDateMillis != null)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Data no Calendário",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (specificDateMillis != null) "Toca em um dia determinado" else "Sem data fixa (regras semanais)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (specificDateMillis != null) {
                            IconButton(
                                onClick = { specificDateMillis = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover data específica",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (specificDateMillis != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable { showDatePickerDialog = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateDisplayFormat.format(Date(specificDateMillis!!)).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Alterar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showDatePickerDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_alarm_date_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Escolher data no calendário")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. DIAS DA SEMANA (Apenas se não houver data específica)
            if (specificDateMillis == null) {
                Text(
                    text = "Repetir",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dayNames.forEach { (isoDay, name) ->
                        val isSelected = isoDay in selectedDays
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    if (isSelected) selectedDays.remove(isoDay)
                                    else selectedDays.add(isoDay)
                                }
                                .testTag("day_button_$isoDay"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.first().toString(),
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick day presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            selectedDays.clear()
                            selectedDays.addAll(listOf(1, 2, 3, 4, 5))
                        }
                    ) {
                        Text("Dias úteis", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = {
                            selectedDays.clear()
                            selectedDays.addAll(listOf(6, 7))
                        }
                    ) {
                        Text("Fim de semana", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = {
                            selectedDays.clear()
                            selectedDays.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
                        }
                    ) {
                        Text("Todos", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. CONTROLE DE VOLUME DO ALARME
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (volume < 0.4f) Icons.Default.VolumeDown else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Volume do Alarme",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Text(
                            text = "${(volume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                        },
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alarm_volume_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sound Selector Row / Button (hidden if vibration-only is enabled)
            if (!vibrationOnly) {
                val currentTitle = remember(soundTone) {
                    SystemRingtoneHelper.getToneTitle(context, soundTone)
                }
                val isYouTubeSound = soundTone.startsWith("youtube://")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showSoundsDialog = true }
                        .testTag("sounds_selection_button"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isYouTubeSound) Color(0xFFFF0000).copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isYouTubeSound) Icons.Default.VideoLibrary else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isYouTubeSound) Color(0xFFFF0000) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isYouTubeSound) "Som do YouTube" else "Toque do Alarme",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (previewingTone == soundTone) {
                                        AlarmSoundPlayer.stopPreview()
                                        previewingTone = null
                                    } else {
                                        previewingTone = soundTone
                                        AlarmSoundPlayer.playPreview(context, soundTone, volume) {
                                            if (previewingTone == soundTone) {
                                                previewingTone = null
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (previewingTone == soundTone) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Ouvir som atual",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Abrir lista de sons",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Vibration & Math Mission
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Modo Apenas Vibração
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Apenas Vibração", fontWeight = FontWeight.Bold)
                                Text("Silencioso, sem som de toque", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = vibrationOnly,
                            onCheckedChange = {
                                vibrationOnly = it
                                if (it) vibrate = true
                            },
                            modifier = Modifier.testTag("vibration_only_switch")
                        )
                    }

                    if (!vibrationOnly) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                Text("Vibração junto com som", fontWeight = FontWeight.Medium)
                            }
                            Switch(
                                checked = vibrate,
                                onCheckedChange = { vibrate = it },
                                modifier = Modifier.testTag("vibrate_switch")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column {
                                Text("Missão para Acordar", fontWeight = FontWeight.Medium)
                                Text("Desafio matemático para desligar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = mathMission,
                            onCheckedChange = { mathMission = it },
                            modifier = Modifier.testTag("math_mission_switch")
                        )
                    }

                    // Snooze duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tempo de Soneca", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(5, 10, 15).forEach { mins ->
                                FilterChip(
                                    selected = snoozeMinutes == mins,
                                    onClick = { snoozeMinutes = mins },
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = {
                        val daysString = if (specificDateMillis == null) selectedDays.sorted().joinToString(",") else ""
                        val newAlarm = (alarm ?: AlarmEntity(hour = hour, minute = minute)).copy(
                            hour = hour,
                            minute = minute,
                            label = label.ifBlank { "Alarme" },
                            daysOfWeek = daysString,
                            vibrate = vibrate || vibrationOnly,
                            vibrationOnly = vibrationOnly,
                            soundTone = soundTone,
                            snoozeMinutes = snoozeMinutes,
                            mathMission = mathMission,
                            volume = volume,
                            specificDateMillis = specificDateMillis,
                            isEnabled = true
                        )
                        onSave(newAlarm)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("save_alarm_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Salvar")
                }
            }
        }
    }

    // Material 3 Date Picker Dialog
    if (showDatePickerDialog) {
        val initialDate = specificDateMillis ?: run {
            val cal = Calendar.getInstance()
            cal.timeInMillis
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate
        )

        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedMillis ->
                            specificDateMillis = pickedMillis
                            selectedDays.clear() // Mutually exclusive with recurring weekly days
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_date_picker_button")
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = "Selecione a data do alarme",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    }

    // Sounds List & YouTube Search Dialog
    if (showSoundsDialog) {
        AlertDialog(
            onDismissRequest = {
                AlarmSoundPlayer.stopPreview()
                previewingTone = null
                showSoundsDialog = false
            },
            title = {
                Column {
                    Text(
                        text = "Escolher Som do Alarme",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toque para ouvir e definir como toque do alarme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                ) {
                    // Top Tabs: Toques Locais vs YouTube Sounds
                    PrimaryTabRow(
                        selectedTabIndex = selectedSoundTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedSoundTab == 0,
                            onClick = { selectedSoundTab = 0 },
                            text = { Text("Toques Locais 🔔", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        )
                        Tab(
                            selected = selectedSoundTab == 1,
                            onClick = { selectedSoundTab = 1 },
                            text = { Text("YouTube 🔴", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedSoundTab == 0) {
                        // TAB 1: TOQUES LOCAIS
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(systemTones) { tone ->
                                val isSelected = soundTone == tone.uriString
                                val isPreviewing = previewingTone == tone.uriString

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            soundTone = tone.uriString
                                            previewingTone = tone.uriString
                                            AlarmSoundPlayer.playPreview(context, tone.uriString, volume) {
                                                if (previewingTone == tone.uriString) {
                                                    previewingTone = null
                                                }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                        .testTag("sound_item_${tone.title}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            soundTone = tone.uriString
                                            previewingTone = tone.uriString
                                            AlarmSoundPlayer.playPreview(context, tone.uriString, volume) {
                                                if (previewingTone == tone.uriString) {
                                                    previewingTone = null
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = tone.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isPreviewing) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Tocando",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Open native phone picker
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Selecionar toque do telefone")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    if (soundTone.startsWith("content://")) {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundTone))
                                    }
                                }
                                try {
                                    ringtonePickerLauncher.launch(intent)
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_system_ringtone_picker_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mais toques no aparelho...", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        // TAB 2: YOUTUBE SOUNDS SEARCH & PRESETS
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            // Search bar
                            OutlinedTextField(
                                value = youtubeSearchQuery,
                                onValueChange = { youtubeSearchQuery = it },
                                placeholder = { Text("Buscar vídeo ou música no YouTube...", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFFFF0000), modifier = Modifier.size(20.dp))
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (youtubeSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                youtubeSearchQuery = ""
                                                youtubeResults = YouTubeSoundService.POPULAR_PRESETS
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Limpar busca", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                if (youtubeSearchQuery.isNotBlank()) {
                                                    isSearchingYoutube = true
                                                    scope.launch {
                                                        youtubeResults = YouTubeSoundService.searchVideos(youtubeSearchQuery)
                                                        isSearchingYoutube = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("submit_youtube_search_button")
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = "Buscar",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        if (youtubeSearchQuery.isNotBlank()) {
                                            isSearchingYoutube = true
                                            scope.launch {
                                                youtubeResults = YouTubeSoundService.searchVideos(youtubeSearchQuery)
                                                isSearchingYoutube = false
                                            }
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("youtube_search_input"),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick preset chips
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val quickChips = listOf("🌿 Natureza", "☕ Lofi", "🐦 Pássaros", "🎻 Clássica", "⚡ Energético", "🌊 Mar")
                                items(quickChips) { chipText ->
                                    val cleanQuery = chipText.substringAfter(" ")
                                    SuggestionChip(
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            youtubeSearchQuery = cleanQuery
                                            isSearchingYoutube = true
                                            scope.launch {
                                                youtubeResults = YouTubeSoundService.searchVideos(cleanQuery)
                                                isSearchingYoutube = false
                                            }
                                        },
                                        label = { Text(chipText, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (isSearchingYoutube) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Buscando no YouTube...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(youtubeResults, key = { it.videoId }) { video ->
                                        val encodedTone = YouTubeSoundService.encodeSoundTone(video)
                                        val isSelected = soundTone == encodedTone
                                        val isPreviewing = previewingTone == encodedTone

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    keyboardController?.hide()
                                                    focusManager.clearFocus()
                                                    soundTone = encodedTone
                                                    previewingTone = encodedTone
                                                    AlarmSoundPlayer.playPreview(context, encodedTone, volume) {
                                                        if (previewingTone == encodedTone) {
                                                            previewingTone = null
                                                        }
                                                    }
                                                }
                                                .testTag("youtube_item_${video.videoId}"),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                // Thumbnail
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 72.dp, height = 50.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.DarkGray)
                                                ) {
                                                    AsyncImage(
                                                        model = video.thumbnailUrl,
                                                        contentDescription = video.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.matchParentSize()
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(topStart = 4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = video.durationFormatted,
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                // Info
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = video.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = video.author,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }

                                                // Preview / Play Button
                                                IconButton(
                                                    onClick = {
                                                        if (previewingTone == encodedTone) {
                                                            AlarmSoundPlayer.stopPreview()
                                                            previewingTone = null
                                                        } else {
                                                            previewingTone = encodedTone
                                                            soundTone = encodedTone
                                                            AlarmSoundPlayer.playPreview(context, encodedTone, volume) {
                                                                if (previewingTone == encodedTone) {
                                                                    previewingTone = null
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                        contentDescription = if (isPreviewing) "Parar" else "Ouvir",
                                                        tint = if (isPreviewing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selecionado",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AlarmSoundPlayer.stopPreview()
                        previewingTone = null
                        showSoundsDialog = false
                    },
                    modifier = Modifier.testTag("confirm_sound_selection_button")
                ) {
                    Text("Concluir")
                }
            }
        )
    }
}
