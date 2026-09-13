package com.medianest.ui.playstore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.theme.AccentViolet
import com.medianest.ui.theme.AccentVioletDark
import com.medianest.ui.theme.AccentVioletLight
import com.medianest.ui.theme.DarkBackground
import com.medianest.ui.theme.DarkSurface
import com.medianest.ui.theme.GlassBorderDark

/**
 * Premium Google Play Store Screenshot Container Layout.
 * Standard 9:16 aspect ratio canvas styled with ambient glowing background gradients,
 * high-impact headlines, feature badges, and a realistic mobile phone device frame.
 */
@Composable
fun PlayStoreScreenshotContainer(
    badgeText: String,
    titleText: String,
    subtitleText: String,
    modifier: Modifier = Modifier,
    accentGlowColor: Color = AccentViolet,
    badgeIcon: @Composable (() -> Unit)? = null,
    mockupContent: @Composable () -> Unit
) {
    // 9:16 Aspect Ratio Canvas (Standard Play Store 1080x1920 preview ratio)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .background(DarkBackground)
    ) {
        // Ambient Background Glow Circles
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentGlowColor.copy(alpha = 0.35f),
                            accentGlowColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentVioletLight.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Main Content Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section: Badge & Titles
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Feature Pill / Badge
                Surface(
                    color = accentGlowColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        accentGlowColor.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (badgeIcon != null) {
                            badgeIcon()
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = accentGlowColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = badgeText.uppercase(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                // Title Headline
                Text(
                    text = titleText,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = subtitleText,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Phone Mockup Frame
            PhoneDeviceFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                mockupContent()
            }
        }
    }
}

/**
 * Realistic Phone Device Bezel Frame in Jetpack Compose.
 */
@Composable
fun PhoneDeviceFrame(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(24.dp, shape = RoundedCornerShape(cornerRadius), ambientColor = AccentVioletDark, spotColor = Color.Black)
            .background(DarkSurface, shape = RoundedCornerShape(cornerRadius))
            .border(
                width = 3.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassBorderDark,
                        Color.White.copy(alpha = 0.3f),
                        GlassBorderDark
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(4.dp) // Outer edge bezel
    ) {
        // Inner Display Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius - 4.dp))
                .background(DarkBackground)
        ) {
            // App UI Mockup Screen
            content()

            // Punch Hole Camera Notch Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(width = 70.dp, height = 18.dp)
                    .background(Color.Black, shape = CircleShape)
            )
        }
    }
}
