package com.medianest.ui.library.video

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.medianest.data.db.AppDatabase
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.ui.components.BackdropBlurState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.LocalBackdropState
import com.medianest.ui.components.backdropReceiver
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.CategoryIconUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AddVideosToCategoryDialog(
    allVideoCategories: List<MediaCategory>,
    selectedUris: Set<String>,
    db: AppDatabase,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onClearSelection: () -> Unit,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    var selectedTargetCategory by remember { mutableStateOf<MediaCategory?>(allVideoCategories.firstOrNull()) }
    var isCreatingNewCat by remember { mutableStateOf(allVideoCategories.isEmpty()) }
    var newCatNameState by remember { mutableStateOf("") }
    var newCatIconState by remember { mutableStateOf("Category") }
    val isDark = LocalDarkTheme.current
    val shape = RoundedCornerShape(24.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.let { w ->
                w.setDimAmount(0.12f)
                w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val cardModifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = false
                ) {}
                .clip(shape)
                .then(
                    if (backdropState != null) {
                        Modifier.backdropReceiver(
                            state = backdropState,
                            blurRadius = 24.dp,
                            tint = if (isDark) Color(0x1F2A324B) else Color(0x80FFFFFF),
                            baseColor = Color.Transparent,
                            showTopBorder = false
                        )
                    } else Modifier
                )

            GlassSurface(
                modifier = cardModifier,
                shape = shape,
                backgroundColor = if (backdropState != null) Color.Transparent else if (isDark) Color(0x2E2A324B) else Color(0xBFFFFFFF),
                borderColor = if (isDark) Color(0x28FFFFFF) else Color(0x28000000)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Icon in Glowing White Container
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0x25FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Title
                    Text(
                        text = "Add Videos to Category",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (allVideoCategories.isNotEmpty()) {
                            Text(
                                text = "Select Category:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            allVideoCategories.forEach { cat ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isCreatingNewCat = false
                                            selectedTargetCategory = cat
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = !isCreatingNewCat && selectedTargetCategory?.id == cat.id,
                                        onClick = {
                                            isCreatingNewCat = false
                                            selectedTargetCategory = cat
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color.White,
                                            unselectedColor = Color.White.copy(alpha = 0.60f)
                                        )
                                    )
                                    Text(
                                        text = cat.name,
                                        modifier = Modifier.padding(start = 8.dp),
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No existing categories.",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.70f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (!isCreatingNewCat) {
                            TextButton(
                                onClick = { isCreatingNewCat = true },
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create New Category", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("New Category", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = newCatNameState,
                                    onValueChange = { newCatNameState = it },
                                    placeholder = { Text("Category Name", color = Color.White.copy(alpha = 0.60f)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.60f),
                                        focusedLabelColor = Color.White,
                                        unfocusedLabelColor = Color.White.copy(alpha = 0.70f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Choose Icon:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(CategoryIconUtils.AVAILABLE_ICONS) { (iconKey, vector) ->
                                        val isSelected = newCatIconState == iconKey
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { newCatIconState = iconKey },
                                            label = { Text(iconKey, fontSize = 11.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = vector,
                                                    contentDescription = iconKey,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0x40FFFFFF),
                                                selectedLabelColor = Color.White,
                                                containerColor = Color(0x1AFFFFFF),
                                                labelColor = Color.White
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = Color.White.copy(alpha = 0.40f),
                                                selectedBorderColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action Buttons (All White Border & Text)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.80f)
                            )
                        }

                        Button(
                            enabled = (isCreatingNewCat && newCatNameState.isNotBlank()) ||
                                    (!isCreatingNewCat && selectedTargetCategory != null),
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val catId = if (isCreatingNewCat) {
                                        db.categoryDao().insertCategory(
                                            MediaCategory(name = newCatNameState.trim(), type = "VIDEO", iconName = newCatIconState)
                                        )
                                    } else if (selectedTargetCategory!!.id < 0) {
                                        db.categoryDao().insertCategory(
                                            MediaCategory(name = selectedTargetCategory!!.name, type = "VIDEO", iconName = selectedTargetCategory!!.iconName)
                                        )
                                    } else {
                                        selectedTargetCategory!!.id
                                    }

                                    val refs = selectedUris.map { uriStr ->
                                        CategoryMediaCrossRef(categoryId = catId, mediaUri = uriStr)
                                    }
                                    db.categoryDao().insertCategoryCrossRefs(refs)

                                    onDismiss()
                                    onClearSelection()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x40FFFFFF),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0x1AFFFFFF),
                                disabledContentColor = Color.White.copy(alpha = 0.40f)
                            )
                        ) {
                            Text(
                                text = "Add",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
