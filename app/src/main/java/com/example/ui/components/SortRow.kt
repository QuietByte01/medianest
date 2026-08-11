package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalDarkTheme

@Composable
fun rememberSortRevealConnection(): Pair<MutableState<Boolean>, NestedScrollConnection> {
    val isVisible = remember { mutableStateOf(false) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15 && isVisible.value) {
                    isVisible.value = false
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
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
    options: List<String> = listOf("Date", "Name", "Type", "Size")
) {
    var showSortMenu by remember { mutableStateOf(false) }

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
    ) {
        Row(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x221C1F2B))
                        .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
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

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    containerColor = if (LocalDarkTheme.current) Color(0xEE08090E) else Color(0xBFFFFFFF),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    options.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(field, color = if (LocalDarkTheme.current) Color.White else Color.Black) },
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
