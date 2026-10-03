package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorSheet(
    selectedMode: ThemeMode,
    isDarkTheme: Boolean,
    starTransparency: Float,
    starShape: StarShape,
    isStarTwinkle: Boolean,
    onModeSelected: (ThemeMode) -> Unit,
    onDarkThemeToggle: (Boolean) -> Unit,
    onStarTransparencyChange: (Float) -> Unit,
    onStarShapeChange: (StarShape) -> Unit,
    onStarTwinkleToggle: (Boolean) -> Unit,
    onShowUpdateDialog: (com.example.data.update.AppReleaseInfo) -> Unit = {},
    onDismiss: () -> Unit
) {
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
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Temas & Visual do Dia",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "Personalize o tema dinâmico, o modo escuro e as estrelas no céu de fundo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Dark/Light toggle
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = if (isDarkTheme) "Modo Escuro" else "Modo Claro",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isDarkTheme) "Ideal para o relógio de cabeceira" else "Cores vivas e luminosas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onDarkThemeToggle,
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }
            }

            // ---- PERSONALIZAÇÃO DAS ESTRELAS DE FUNDO ----
            Text(
                text = "Estrelas do Fundo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Slider de Transparência
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "✨", fontSize = 18.sp)
                                Text(
                                    text = "Transparência das Estrelas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (starTransparency <= 0.02f) "Desativado" else "${(starTransparency * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = starTransparency,
                            onValueChange = onStarTransparencyChange,
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("star_transparency_slider")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 2. Formato das Estrelas
                    Column {
                        Text(
                            text = "Formato das Estrelas",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StarShape.entries.forEach { shape ->
                                val isShapeSelected = starShape == shape
                                FilterChip(
                                    selected = isShapeSelected,
                                    onClick = { onStarShapeChange(shape) },
                                    label = {
                                        Text("${shape.iconEmoji} ${shape.title}", style = MaterialTheme.typography.labelSmall)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("star_shape_chip_${shape.name.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 3. Cintilação: Animadas vs Estáticas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = if (isStarTwinkle) "🌟" else "⭐", fontSize = 20.sp)
                            Column {
                                Text(
                                    text = if (isStarTwinkle) "Cintilação Ativa (Animadas)" else "Estrelas Estáticas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isStarTwinkle) "Pulsam suavemente no céu" else "Brilho fixo e tranquilo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isStarTwinkle,
                            onCheckedChange = onStarTwinkleToggle,
                            modifier = Modifier.testTag("star_twinkle_switch")
                        )
                    }
                }
            }

            // ---- MODO DE TEMA DINÂMICO ----
            Text(
                text = "Momento do Dia",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val themeOptions = listOf(
                Pair(ThemeMode.AUTO, "Muda automaticamente com a hora do dia (Amanhecer, Dia, Pôr do Sol, Noite)"),
                Pair(ThemeMode.DAWN, "05:00 - 08:00 • Tons de rosa suave, pétala e aurora rosada"),
                Pair(ThemeMode.DAY, "08:00 - 17:00 • Céu azul radiante e energia solar"),
                Pair(ThemeMode.SUNSET, "17:00 - 20:00 • Crepúsculo rubro, horizonte avermelhado e brasas"),
                Pair(ThemeMode.NIGHT, "20:00 - 05:00 • Índigo profundo e céu estrelado")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                themeOptions.forEach { (mode, desc) ->
                    val isSelected = selectedMode == mode

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onModeSelected(mode)
                                onDismiss()
                            }
                            .testTag("theme_option_${mode.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = if (isSelected)
                            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = mode.iconEmoji, fontSize = 24.sp)
                                Column {
                                    Text(
                                        text = mode.title,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- ATUALIZAÇÕES DO APP (GITHUB RELEASES) ----
            UpdateSettingsCard(
                onShowUpdateDialog = { release ->
                    onDismiss()
                    onShowUpdateDialog(release)
                }
            )
        }
    }
}
