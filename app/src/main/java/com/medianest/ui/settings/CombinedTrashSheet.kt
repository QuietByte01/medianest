package com.medianest.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.components.AdaptiveBottomSheet
import com.medianest.ui.components.GlassSurface
import com.medianest.util.TrashedMediaItem
import com.medianest.util.TrashManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombinedTrashSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var trashedItems by remember { mutableStateOf(TrashManager.getTrashedItems(context)) }

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Recycle Bin (Trash)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${trashedItems.size} items total",
                            fontSize = 12.sp,
                            color = Color(0xFF8E95A5)
                        )
                    }
                }

                if (trashedItems.isNotEmpty()) {
                    Button(
                        onClick = {
                            TrashManager.emptyTrash(context)
                            trashedItems = emptyList()
                            Toast.makeText(context, "Recycle Bin emptied!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Empty Bin",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Empty Bin",
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (trashedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFF4A5060),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Recycle Bin is Empty",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E95A5)
                        )
                        Text(
                            text = "Deleted media files will appear here before permanent removal",
                            fontSize = 12.sp,
                            color = Color(0xFF5A6072)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(trashedItems, key = { it.id }) { item ->
                        TrashItemRow(
                            item = item,
                            onRestore = {
                                if (TrashManager.restoreItem(context, item)) {
                                    trashedItems = TrashManager.getTrashedItems(context)
                                    Toast.makeText(context, "Restored '${item.title}'", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeletePermanently = {
                                if (TrashManager.deletePermanently(context, item)) {
                                    trashedItems = TrashManager.getTrashedItems(context)
                                    Toast.makeText(context, "Permanently deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashItemRow(
    item: TrashedMediaItem,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val dateStr = remember(item.trashedTimestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        sdf.format(Date(item.trashedTimestamp))
    }

    val typeLabel = remember(item.mimeType) {
        when {
            item.mimeType.startsWith("video") -> "VIDEO"
            item.mimeType.startsWith("audio") -> "AUDIO"
            else -> "IMAGE"
        }
    }

    val icon = when (typeLabel) {
        "VIDEO" -> Icons.Default.Movie
        "AUDIO" -> Icons.Default.MusicNote
        else -> Icons.Default.Image
    }

    GlassSurface(
        shape = RoundedCornerShape(14.dp),
        backgroundColor = Color(0x221C1F2B),
        borderColor = Color(0x28FFFFFF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$dateStr • $typeLabel",
                        fontSize = 11.sp,
                        color = Color(0xFF8E95A5)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onRestore,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDeletePermanently,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete permanently",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
