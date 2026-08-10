package com.example.ui.library.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryMediaCrossRef
import com.example.data.db.MediaCategory
import com.example.data.db.AppDatabase
import com.example.util.CategoryIconUtils
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
    onClearSelection: () -> Unit
) {
    var selectedTargetCategory by remember { mutableStateOf<MediaCategory?>(allVideoCategories.firstOrNull()) }
    var isCreatingNewCat by remember { mutableStateOf(allVideoCategories.isEmpty()) }
    var newCatNameState by remember { mutableStateOf("") }
    var newCatIconState by remember { mutableStateOf("Category") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Add Videos to Category") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (allVideoCategories.isNotEmpty()) {
                    Text("Select Category:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                }
                            )
                            Text(cat.name, modifier = Modifier.padding(start = 8.dp), fontSize = 15.sp)
                        }
                    }
                } else {
                    Text("No existing categories.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isCreatingNewCat) {
                    TextButton(
                        onClick = { isCreatingNewCat = true },
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Category", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("New Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newCatNameState,
                            onValueChange = { newCatNameState = it },
                            placeholder = { Text("Category Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Choose Icon:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(CategoryIconUtils.AVAILABLE_ICONS) { (iconKey, vector) ->
                                FilterChip(
                                    selected = newCatIconState == iconKey,
                                    onClick = { newCatIconState = iconKey },
                                    label = { Text(iconKey, fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(vector, contentDescription = iconKey, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
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
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}