package com.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.settings.SettingsManager
import kotlinx.coroutines.launch

@Composable
fun HardwareAccelerationSetting(
    settingsManager: SettingsManager,
    onModeChange: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val hwAccelEnabled by settingsManager.hardwareAccelerationEnabled.collectAsState(initial = true)
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = "AUTO")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Master Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hardware Acceleration",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Use device GPU/DSP for high-performance rendering",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            AppSwitch(
                checked = hwAccelEnabled,
                onCheckedChange = { scope.launch { settingsManager.setHardwareAccelerationEnabled(it); onModeChange() } },
                style = AppSwitchStyle.Glass
            )
        }

        // Mode Selector
        if (hwAccelEnabled) {
            var expanded by remember { mutableStateOf(false) }
            val currentLabel = when (decoderMode) {
                "HARDWARE" -> "Hardware Only"
                "SOFTWARE" -> "Software Only"
                else -> "Auto (HW + SW Fallback)"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Decoder Strategy",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Box {
                    Button(
                        onClick = { expanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(currentLabel, color = Color.White, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1A1C1E))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto (Recommended)", color = Color.White) },
                            onClick = {
                                scope.launch { settingsManager.setDecoderMode("AUTO"); onModeChange() }
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Hardware Only", color = Color.White) },
                            onClick = {
                                scope.launch { settingsManager.setDecoderMode("HARDWARE"); onModeChange() }
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Software Only", color = Color.White) },
                            onClick = {
                                scope.launch { settingsManager.setDecoderMode("SOFTWARE"); onModeChange() }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HdrPlaybackSetting(
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Check hardware support
    val displayManager = remember { context.getSystemService(android.content.Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager }
    val defaultDisplay = remember { displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY) }
    val isHdrSupported = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val caps = defaultDisplay?.hdrCapabilities
                caps != null && (caps.supportedHdrTypes?.isNotEmpty() == true)
            } catch (_: Exception) { true }
        } else true
    }
    
    val hdrEnabled by settingsManager.hdrPlaybackEnabled.collectAsState(initial = isHdrSupported)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "HDR Video Playback",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isHdrSupported) "Enhanced color range and peak brightness" else "Not supported by this display",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        AppSwitch(
            checked = hdrEnabled && isHdrSupported,
            enabled = isHdrSupported,
            onCheckedChange = { scope.launch { settingsManager.setHdrPlaybackEnabled(it) } },
            style = AppSwitchStyle.Glossy,
            accentColor = Color(0xFF38BDF8)
        )
    }
}
