// app/src/main/java/com/example/kaptus/ui/composables/KaptusTopAppBar.kt

package com.example.kaptus.ui.composables

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaptusTopAppBar(
    fileName: String?,
    showActions: Boolean,
    orientation: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    searchResults: Pair<Int, Int>,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onNextResult: () -> Unit,
    onPreviousResult: () -> Unit,
    onFlipOrientationClick: () -> Unit,
) {
    TopAppBar(
        title = {
            if (!isSearchActive) {
                Column {
                    Text(
                        "Kaptus",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        fileName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        actions = {
            if (showActions) {
                if (isSearchActive) {
                    SearchBar(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        onSearchQueryChange = onSearchQueryChange,
                        onCloseSearch = {
                            onSearchActiveChange(false)
                            onSearchQueryChange("")
                        },
                        onNextResult = onNextResult,
                        onPreviousResult = onPreviousResult
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onSearchActiveChange(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onFlipOrientationClick) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Flip Orientation")
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}
