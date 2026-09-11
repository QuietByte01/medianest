package com.medianest.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.medianest.studio.ui.FullStudioAddOnScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val mediaUri = intent?.getStringExtra("extra_media_uri") ?: intent?.data?.toString()
        val initialTab = intent?.getStringExtra("extra_initial_tab") ?: "CONVERT"

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    primary = Color(0xFF6366F1)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    FullStudioAddOnScreen(
                        action = action,
                        mediaUriString = mediaUri,
                        initialTabStr = initialTab,
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}
