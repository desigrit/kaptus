// app/src/main/java/com/example/kaptus/ui/composables/KaptusTopAppBar.kt

package com.example.kaptus.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaptusTopAppBar(
    fileName: String?,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onChangeFileClick: () -> Unit,
    showActions: Boolean
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    "Kaptus",
                    style = MaterialTheme.typography.headlineSmall, // Larger font size
                    fontWeight = FontWeight.Bold // Bolder text
                )
                fileName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            if (showActions) {
                IconButton(onClick = { onShowMenuChange(!showMenu) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onShowMenuChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Change SRT File") },
                        onClick = {
                            onShowMenuChange(false)
                            onChangeFileClick()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent // Remove background color
        )
    )
}