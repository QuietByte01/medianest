package com.medianest.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.settings.SettingsManager
import com.medianest.ui.components.debug.HardwarePipelineDiagnosticsCard
import com.medianest.util.LogLevel
import com.medianest.util.Logger
import kotlinx.coroutines.launch

@Composable
fun DeveloperSettingsSection(
    settingsManager: SettingsManager,
    onDisableDevMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val developerModeEnabled by settingsManager.developerModeEnabled.collectAsState(initial = false)
    val verboseLogging by settingsManager.verboseLoggingEnabled.collectAsState(initial = false)
    val enableAnalytics by settingsManager.enableAnalyticsTab.collectAsState(initial = true)
    val showPlayerDebug by settingsManager.showPlayerDebugInfo.collectAsState(initial = false)
    val showImageDebug by settingsManager.showImageDebugInfo.collectAsState(initial = false)
    val showAudioDebug by settingsManager.showAudioDebugInfo.collectAsState(initial = false)

    if (!developerModeEnabled) return

    var showLogs by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGlassCard(title = "DEVICE INFORMATION") {
            val deviceModel = android.os.Build.MODEL
            val androidVersion = android.os.Build.VERSION.RELEASE
            val sdkVersion = android.os.Build.VERSION.SDK_INT
            val manufacturer = android.os.Build.MANUFACTURER

            Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DeviceInfoRow("Model", deviceModel)
                DeviceInfoRow("Manufacturer", manufacturer)
                DeviceInfoRow("Android Version", "$androidVersion (SDK $sdkVersion)")
            }
        }

        // Hardware Pipeline Diagnostics Component
        HardwarePipelineDiagnosticsCard(context = context)

        SettingsGlassCard(title = "PLAYER & ENGINE DEBUGGING") {
            SettingsRowItem(
                title = "Media3 Debug Overlay",
                subtitle = "Show real-time video codec, bitrate, and drop-frame stats in player",
                control = {
                    Switch(
                        checked = showPlayerDebug,
                        onCheckedChange = { scope.launch { settingsManager.setShowPlayerDebugInfo(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF6366F1))
                    )
                }
            )

            SettingsRowItem(
                title = "ImageViewer Debug Info",
                subtitle = "Overlay image resolution, scale, and memory usage",
                control = {
                    Switch(
                        checked = showImageDebug,
                        onCheckedChange = { scope.launch { settingsManager.setShowImageDebugInfo(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF6366F1))
                    )
                }
            )

            SettingsRowItem(
                title = "AudioPlayer Debug Info",
                subtitle = "Show codec, session ID and native DSP states",
                control = {
                    Switch(
                        checked = showAudioDebug,
                        onCheckedChange = { scope.launch { settingsManager.setShowAudioDebugInfo(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF6366F1))
                    )
                }
            )

            SettingsRowItem(
                title = "Verbose Logging",
                subtitle = "Enable detailed logging for FFmpeg & Media3 events",
                control = {
                    Switch(
                        checked = verboseLogging,
                        onCheckedChange = { scope.launch { settingsManager.setVerboseLoggingEnabled(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF6366F1))
                    )
                }
            )
        }

        SettingsGlassCard(title = "DEVELOPER TOOLS") {
            SettingsRowItem(
                title = "In-App Log Viewer",
                subtitle = "View application logs and filter by level",
                control = {
                    Button(
                        onClick = { showLogs = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x336366F1))
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Logs", fontSize = 12.sp)
                    }
                }
            )

            SettingsRowItem(
                title = "Force Crash",
                subtitle = "Trigger a RuntimeException for crash reporting tests",
                control = {
                    Button(
                        onClick = { throw RuntimeException("Manual Developer Crash") },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444))
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Crash App", fontSize = 12.sp, color = Color(0xFFEF4444))
                    }
                }
            )

            SettingsRowItem(
                title = "Reset Developer Mode",
                subtitle = "Disable all hidden developer settings",
                control = {
                    Button(
                        onClick = {
                            scope.launch {
                                settingsManager.setDeveloperModeEnabled(false)
                                onDisableDevMode()
                                Toast.makeText(context, "Developer Mode Disabled", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444))
                    ) {
                        Text("Disable", fontSize = 12.sp, color = Color(0xFFEF4444))
                    }
                }
            )
        }
    }

    if (showLogs) {
        LogViewerDialog(onDismiss = { showLogs = false })
    }
}

@Composable
fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF8E95A5))
        Text(text = value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogViewerDialog(onDismiss: () -> Unit) {
    val logs by Logger.logs.collectAsState()
    var selectedFilter by remember { mutableStateOf<LogLevel?>(null) } // null = ALL

    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.level == selectedFilter }
    }

    val groupedLogs = remember(filteredLogs) {
        filteredLogs.groupBy { it.dateGroup }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1015),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Application Logs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = { Logger.clear() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = Color.LightGray)
                }
            }

            // Log Level Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf<Pair<String, LogLevel?>>(
                    "ALL" to null,
                    "ERROR" to LogLevel.ERROR,
                    "WARN" to LogLevel.WARN,
                    "INFO" to LogLevel.INFO,
                    "DEBUG" to LogLevel.DEBUG
                )

                filters.forEach { (label, level) ->
                    val isSelected = selectedFilter == level
                    val chipColor = when (level) {
                        LogLevel.ERROR -> Color(0xFFEF4444)
                        LogLevel.WARN -> Color(0xFFFBBF24)
                        LogLevel.INFO -> Color(0xFF60A5FA)
                        LogLevel.DEBUG -> Color(0xFF94A3B8)
                        null -> Color(0xFF818CF8)
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = level },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0x1AFFFFFF),
                            labelColor = Color.White.copy(alpha = 0.7f),
                            selectedContainerColor = chipColor.copy(alpha = 0.3f),
                            selectedLabelColor = chipColor
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0x33FFFFFF),
                            selectedBorderColor = chipColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedLogs.forEach { (dateGroup, entries) ->
                    stickyHeader {
                        Surface(
                            color = Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = dateGroup,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    items(entries) { log ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1AFFFFFF))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "[${log.timestamp}] ${log.level}",
                                    fontSize = 10.sp,
                                    color = when (log.level) {
                                        LogLevel.ERROR -> Color(0xFFEF4444)
                                        LogLevel.WARN -> Color(0xFFFBBF24)
                                        LogLevel.INFO -> Color(0xFF60A5FA)
                                        LogLevel.DEBUG -> Color(0xFF94A3B8)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = log.tag,
                                    fontSize = 10.sp,
                                    color = Color(0xFF818CF8),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = log.message,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
