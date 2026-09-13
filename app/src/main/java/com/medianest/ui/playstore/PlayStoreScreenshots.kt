package com.medianest.ui.playstore

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.theme.*

// ==============================================================================
// PLAY STORE SCREENSHOT 1: MEDIA HUB & VIDEO CATALOG
// ==============================================================================
@Composable
fun PlayStoreScreen1_MediaHub(
    modifier: Modifier = Modifier
) {
    PlayStoreScreenshotContainer(
        badgeText = "MediaNest • All-In-One",
        titleText = "ALL-IN-ONE MEDIA HUB",
        subtitleText = "Organize & stream videos, music & photos in sleek glass UI",
        accentGlowColor = AccentViolet,
        badgeIcon = {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(16.dp))
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(top = 28.dp, start = 12.dp, end = 12.dp, bottom = 8.dp)
        ) {
            // Top App Bar Mockup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(AccentViolet, AccentVioletDark)),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MediaNest",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Selector Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MockTabPill("Videos", isSelected = true, icon = Icons.Default.PlayArrow)
                MockTabPill("Music", isSelected = false, icon = Icons.Default.MusicNote)
                MockTabPill("Photos", isSelected = false, icon = Icons.Default.Image)
                MockTabPill("Stats", isSelected = false, icon = Icons.Default.Analytics)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Featured Hero Card (Video Category)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                        )
                    )
                    .border(1.dp, GlassBorderDark, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Surface(
                        color = AccentViolet.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "PREMIUM 4K",
                            color = AccentViolet,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Nature Landscapes 4K HDR",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "12 Videos • 3.4 GB",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.CenterEnd)
                        .background(AccentViolet, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Recent Videos",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Video Grid (2 Columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MockVideoCard(
                    title = "Cyberpunk City Nights",
                    duration = "04:15",
                    badge = "4K HDR",
                    color = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f)
                )
                MockVideoCard(
                    title = "Mountain Drone Footage",
                    duration = "12:40",
                    badge = "1080p",
                    color = Color(0xFF0D9488),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MockVideoCard(
                    title = "Sunset Coastline 60FPS",
                    duration = "08:22",
                    badge = "60 FPS",
                    color = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f)
                )
                MockVideoCard(
                    title = "Urban Architecture Tour",
                    duration = "15:00",
                    badge = "HEVC",
                    color = Color(0xFFDB2777),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Floating Mini Player Bar
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentVioletDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Solaris Odyssey", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Synthwave Chill • 03:45", color = TextSecondaryDark, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.Pause, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ==============================================================================
// PLAY STORE SCREENSHOT 2: AUDIO PLAYER & EQUALIZER
// ==============================================================================
@Composable
fun PlayStoreScreen2_AudioPlayer(
    modifier: Modifier = Modifier
) {
    PlayStoreScreenshotContainer(
        badgeText = "Studio Audio Engine",
        titleText = "POWERFUL MUSIC PLAYER",
        subtitleText = "Real-time audio visualizer, synced lyrics & studio DSP equalizer",
        accentGlowColor = Color(0xFF38BDF8),
        badgeIcon = {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(top = 28.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Player Top Nav Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                Text("PLAYING FROM PLAYLIST", color = TextSecondaryDark, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Album Artwork with Ambient Shadow
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF1E1B4B))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Song Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Midnight Horizons", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Aetherwave • Synthwave Chillout", color = AccentViolet, fontSize = 12.sp)
                }
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Visualizer Spectrum Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val barHeights = listOf(0.4f, 0.7f, 0.3f, 0.9f, 0.6f, 0.85f, 0.5f, 1.0f, 0.65f, 0.4f, 0.8f, 0.55f, 0.95f, 0.35f, 0.75f, 0.45f)
                barHeights.forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight(fraction)
                            .background(
                                brush = Brush.verticalGradient(listOf(AccentViolet, AccentVioletDark)),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Wavy Seekbar / Progress
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(DarkSurfaceVariant, shape = RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .fillMaxHeight()
                            .background(AccentViolet, shape = RoundedCornerShape(3.dp))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("02:45", color = TextSecondaryDark, fontSize = 10.sp)
                    Text("04:12", color = TextSecondaryDark, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(20.dp))
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(AccentViolet, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                Icon(Icons.Default.Repeat, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Equalizer DSP Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MockEqPill("BASS BOOST", active = true, modifier = Modifier.weight(1f))
                MockEqPill("3D SURROUND", active = true, modifier = Modifier.weight(1f))
                MockEqPill("VOCAL CLEAR", active = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ==============================================================================
// PLAY STORE SCREENSHOT 3: PHOTO GALLERY & ULTRA HDR
// ==============================================================================
@Composable
fun PlayStoreScreen3_PhotoGallery(
    modifier: Modifier = Modifier
) {
    PlayStoreScreenshotContainer(
        badgeText = "Ultra HDR Gallery",
        titleText = "STUNNING PHOTO GALLERY",
        subtitleText = "Hardware gainmap HDR, gesture physics & smart album sorting",
        accentGlowColor = Color(0xFF10B981),
        badgeIcon = {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(top = 28.dp, start = 12.dp, end = 12.dp, bottom = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Photos & Albums", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row {
                    Icon(Icons.Default.FilterList, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Default.GridView, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MockFilterChip("All Photos", selected = true)
                MockFilterChip("Ultra HDR", selected = false)
                MockFilterChip("Camera", selected = false)
                MockFilterChip("Favorites", selected = false)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Photo Grid (3 Columns)
            val photoGradients = listOf(
                listOf(Color(0xFF059669), Color(0xFF10B981)),
                listOf(Color(0xFF2563EB), Color(0xFF38BDF8)),
                listOf(Color(0xFFD97706), Color(0xFFF59E0B)),
                listOf(Color(0xFF7C3AED), Color(0xFFA855F7)),
                listOf(Color(0xFFDC2626), Color(0xFFF87171)),
                listOf(Color(0xFF4F46E5), Color(0xFF6366F1)),
                listOf(Color(0xFF0891B2), Color(0xFF06B6D4)),
                listOf(Color(0xFFBE185D), Color(0xFFF43F5E)),
                listOf(Color(0xFF15803D), Color(0xFF22C55E))
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0..2) {
                            val index = row * 3 + col
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        brush = Brush.linearGradient(photoGradients[index % photoGradients.size])
                                    )
                                    .border(1.dp, GlassBorderDark, RoundedCornerShape(10.dp))
                            ) {
                                if (index == 0 || index == 3) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            "HDR",
                                            color = Color(0xFFF59E0B),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Batch Action Toolbar Mockup
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(20.dp))
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ==============================================================================
// PLAY STORE SCREENSHOT 4: STORAGE ANALYTICS
// ==============================================================================
@Composable
fun PlayStoreScreen4_StorageAnalytics(
    modifier: Modifier = Modifier
) {
    PlayStoreScreenshotContainer(
        badgeText = "Smart Storage Insights",
        titleText = "DEEP MEDIA ANALYTICS",
        subtitleText = "Analyze disk usage, format breakdown & duplicate files",
        accentGlowColor = Color(0xFFF59E0B),
        badgeIcon = {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(top = 28.dp, start = 14.dp, end = 14.dp, bottom = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Storage Dashboard", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Storage Ring Progress Card
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Gauge Mockup
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(DarkSurfaceVariant, shape = CircleShape)
                            .border(6.dp, AccentViolet, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("65%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("USED", color = TextSecondaryDark, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text("164.8 GB of 256 GB", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• 4,820 Total Media Files", color = TextSecondaryDark, fontSize = 11.sp)
                        Text("• 12.4 GB Recoverable Space", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Format Breakdown", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(8.dp))

            // Format Progress Bars
            MockFormatRow("MP4 Video", "84.2 GB", 0.65f, Color(0xFF2563EB))
            MockFormatRow("FLAC Audio", "32.4 GB", 0.35f, Color(0xFF38BDF8))
            MockFormatRow("RAW & HDR Photos", "28.1 GB", 0.28f, Color(0xFF10B981))
            MockFormatRow("MKV Cinema", "20.1 GB", 0.20f, Color(0xFFF59E0B))

            Spacer(modifier = Modifier.height(14.dp))

            // Duplicate File Detection Banner
            Surface(
                color = Color(0xFFFEF3C7).copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Duplicate Detector", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Found 18 duplicate videos (3.8 GB)", color = TextSecondaryDark, fontSize = 10.sp)
                    }
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Clean", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==============================================================================
// PLAY STORE SCREENSHOT 5: PRIVATE VAULT & THEMES
// ==============================================================================
@Composable
fun PlayStoreScreen5_VaultAndThemes(
    modifier: Modifier = Modifier
) {
    PlayStoreScreenshotContainer(
        badgeText = "Privacy & Security",
        titleText = "SECURE PRIVATE VAULT",
        subtitleText = "PIN & biometric protection with ambient glassmorphism themes",
        accentGlowColor = Color(0xFFA855F7),
        badgeIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(top = 28.dp, start = 14.dp, end = 14.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Security Vault", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Vault Lock Illustration
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFFA855F7).copy(alpha = 0.15f), shape = CircleShape)
                    .border(2.dp, Color(0xFFA855F7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(44.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Protected Folder Vault", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("AES-256 Encrypted Local Storage", color = TextSecondaryDark, fontSize = 11.sp)

            Spacer(modifier = Modifier.height(18.dp))

            // Security PIN Pad / Biometric Mockup
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ENTER SECURITY PIN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // PIN Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0..3) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(
                                        if (i < 3) Color(0xFFA855F7) else DarkSurfaceVariant,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(26.dp))
                        Text("Biometric Unlock Enabled", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Theme Options Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MockThemeCard("Obsidian Dark", active = true, color = Color(0xFF0F1015), modifier = Modifier.weight(1f))
                MockThemeCard("Glass Glow", active = false, color = Color(0xFF1E1B4B), modifier = Modifier.weight(1f))
            }
        }
    }
}

// ==============================================================================
// HELPER MOCKUP COMPONENTS
// ==============================================================================

@Composable
fun MockTabPill(title: String, isSelected: Boolean, icon: ImageVector) {
    Surface(
        color = if (isSelected) AccentViolet else DarkSurfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else TextSecondaryDark,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else TextSecondaryDark,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun MockVideoCard(
    title: String,
    duration: String,
    badge: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    duration,
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Surface(
                color = AccentViolet,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MockEqPill(title: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (active) AccentViolet.copy(alpha = 0.2f) else DarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) AccentViolet else GlassBorderDark),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title,
                color = if (active) AccentViolet else TextSecondaryDark,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MockFilterChip(label: String, selected: Boolean) {
    Surface(
        color = if (selected) Color(0xFF10B981) else DarkSurfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextSecondaryDark,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun MockFormatRow(label: String, size: String, progress: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(size, color = TextSecondaryDark, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(DarkSurfaceVariant, shape = RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(color, shape = RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun MockThemeCard(title: String, active: Boolean, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Color(0xFFA855F7) else GlassBorderDark),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color, shape = CircleShape)
                    .border(1.dp, GlassBorderDark, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ==============================================================================
// COMPOSE PREVIEWS FOR ANDROID STUDIO
// ==============================================================================

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PlayStoreScreen1Preview() {
    MediaNestTheme {
        PlayStoreScreen1_MediaHub()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PlayStoreScreen2Preview() {
    MediaNestTheme {
        PlayStoreScreen2_AudioPlayer()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PlayStoreScreen3Preview() {
    MediaNestTheme {
        PlayStoreScreen3_PhotoGallery()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PlayStoreScreen4Preview() {
    MediaNestTheme {
        PlayStoreScreen4_StorageAnalytics()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PlayStoreScreen5Preview() {
    MediaNestTheme {
        PlayStoreScreen5_VaultAndThemes()
    }
}
