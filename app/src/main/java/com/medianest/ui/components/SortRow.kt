package com.medianest.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun rememberSortRevealConnection(): Pair<MutableState<Boolean>, NestedScrollConnection> {
    val isVisible = remember { mutableStateOf(false) }
    
    // Auto-hide after 10 seconds of inactivity
    LaunchedEffect(isVisible.value) {
        if (isVisible.value) {
            kotlinx.coroutines.delay(10000)
            isVisible.value = false
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If scrolling up, hide immediately
                if (available.y < -15 && isVisible.value) {
                    isVisible.value = false
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // If pulling down at the top, show row
                if (available.y > 50 && !isVisible.value) {
                    isVisible.value = true
                }
                return Offset.Zero
            }
        }
    }
    return isVisible to connection
}

@Composable
fun SortRow(
    sortField: String,
    onSortFieldChange: (String) -> Unit,
    isAscending: Boolean,
    onIsAscendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    options: List<String> = listOf("Date", "Name", "Type", "Size"),
    onBack: (() -> Unit)? = null,
    backLabel: String? = null
) {
    var showSortMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Optional Back Button
            if (onBack != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x221C1F2B))
                        .border(0.5.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    if (!backLabel.isNullOrBlank()) {
                        Text(
                            text = backLabel,
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    } else {
                        Text(
                            text = "Back",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Right Side: Sort Button
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x221C1F2B))
                        .border(0.5.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
                        .clickable { showSortMenu = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = Color(0xFF9EA3B0),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = sortField,
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isAscending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = if (isAscending) "Ascending" else "Descending",
                        tint = Color(0xFF9EA3B0),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onIsAscendingChange(!isAscending) }
                    )
                }

                GlassDropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    options.forEach { field ->
                        val isSelected = sortField == field
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = field, 
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            onClick = {
                                onSortFieldChange(field)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
