package com.medianest.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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

enum class LogSource {
    APP_LOGS,
    ADB_LOGCAT
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogViewerDialog(onDismiss: () -> Unit) {
    val logs by Logger.logs.collectAsState()
    var currentSource by remember { mutableStateOf(LogSource.APP_LOGS) }
    var selectedFilter by remember { mutableStateOf<LogLevel?>(null) } // null = ALL
    var adbFilterText by remember { mutableStateOf("") }

    var adbLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingAdb by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reloadAdbLogs() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isLoadingAdb = true
            val newAdbLogs = Logger.getProcessLogcatLogs(
                filterTagOrKeyword = adbFilterText,
                minLevel = selectedFilter
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                adbLogs = newAdbLogs
                isLoadingAdb = false
            }
        }
    }

    LaunchedEffect(currentSource, selectedFilter, adbFilterText) {
        if (currentSource == LogSource.ADB_LOGCAT) {
            reloadAdbLogs()
        }
    }

    val filteredAppLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.level == selectedFilter }
    }

    val groupedAppLogs = remember(filteredAppLogs) {
        filteredAppLogs.groupBy { it.dateGroup }
    }

    val collapsedDateGroups = remember { mutableStateMapOf<String, Boolean>() }

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
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentSource == LogSource.APP_LOGS) "Application Logs" else "ADB Process Logcat",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentSource == LogSource.ADB_LOGCAT) {
                        IconButton(onClick = { reloadAdbLogs() }, enabled = !isLoadingAdb) {
                            if (isLoadingAdb) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF818CF8),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh ADB Logs",
                                    tint = Color.LightGray
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (currentSource == LogSource.APP_LOGS) {
                            Logger.clear()
                        } else {
                            Logger.clearLogcatBuffer()
                            reloadAdbLogs()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = Color.LightGray
                        )
                    }
                }
            }

            // Log Source Selector (App Logs vs ADB / Logcat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentSource == LogSource.APP_LOGS,
                    onClick = { currentSource = LogSource.APP_LOGS },
                    label = { Text("App Logs (${logs.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0x1AFFFFFF),
                        labelColor = Color.White.copy(alpha = 0.7f),
                        selectedContainerColor = Color(0xFF6366F1).copy(alpha = 0.3f),
                        selectedLabelColor = Color(0xFF818CF8)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = currentSource == LogSource.APP_LOGS,
                        borderColor = Color(0x33FFFFFF),
                        selectedBorderColor = Color(0xFF818CF8)
                    )
                )

                FilterChip(
                    selected = currentSource == LogSource.ADB_LOGCAT,
                    onClick = { currentSource = LogSource.ADB_LOGCAT },
                    label = { Text("ADB / Logcat", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0x1AFFFFFF),
                        labelColor = Color.White.copy(alpha = 0.7f),
                        selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.3f),
                        selectedLabelColor = Color(0xFF34D399)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = currentSource == LogSource.ADB_LOGCAT,
                        borderColor = Color(0x33FFFFFF),
                        selectedBorderColor = Color(0xFF34D399)
                    )
                )
            }

            // ADB Filter TextField (when ADB Logcat is selected)
            if (currentSource == LogSource.ADB_LOGCAT) {
                OutlinedTextField(
                    value = adbFilterText,
                    onValueChange = { adbFilterText = it },
                    placeholder = { Text("Filter tag / keyword (e.g. FFmpeg, Media3)...", fontSize = 12.sp, color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    trailingIcon = {
                        if (adbFilterText.isNotEmpty()) {
                            IconButton(onClick = { adbFilterText = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Filter",
                                    tint = Color.Gray
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Filter",
                                tint = Color.Gray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF34D399),
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedContainerColor = Color(0x1AFFFFFF),
                        unfocusedContainerColor = Color(0x0DFFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
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

            if (currentSource == LogSource.APP_LOGS) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedAppLogs.forEach { (dateGroup, entries) ->
                        val isCollapsed = collapsedDateGroups[dateGroup] == true
                        val headerColor = Color(0xFF38BDF8)
                        val headerBg = if (!isCollapsed) headerColor.copy(alpha = 0.3f) else Color(0xFF1E293B)

                        stickyHeader {
                            Surface(
                                color = headerBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        collapsedDateGroups[dateGroup] = !isCollapsed
                                    },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                            contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = dateGroup,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "${entries.size} logs",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        if (!isCollapsed) {
                            items(entries) { log ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
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
            } else {
                // ADB Logcat View
                if (adbLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLoadingAdb) "Loading ADB logcat..." else "No logcat entries match filter",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(adbLogs) { line ->
                            val lineColor = when {
                                line.contains(" E ") || line.contains(" E/") -> Color(0xFFEF4444)
                                line.contains(" W ") || line.contains(" W/") -> Color(0xFFFBBF24)
                                line.contains(" I ") || line.contains(" I/") -> Color(0xFF60A5FA)
                                line.contains(" D ") || line.contains(" D/") -> Color(0xFF94A3B8)
                                else -> Color.White.copy(alpha = 0.85f)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(4.dp))
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = line,
                                    fontSize = 10.5.sp,
                                    color = lineColor,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
