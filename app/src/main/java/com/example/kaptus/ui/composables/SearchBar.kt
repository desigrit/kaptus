// app/src/main/java/com/example/kaptus/ui/composables/SearchBar.kt

package com.example.kaptus.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    searchQuery: String,
    searchResults: Pair<Int, Int>, // current index, total results
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onNextResult: () -> Unit,
    onPreviousResult: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            onCloseSearch()
            keyboardController?.hide()
        }) {
            Icon(Icons.Default.Close, contentDescription = "Close Search")
        }
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search subtitles...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            colors = TextFieldDefaults.colors( // Corrected function name
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            )
        )
        if (searchQuery.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (searchResults.second > 0) "${searchResults.first + 1}/${searchResults.second}" else "0/0",
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = onPreviousResult, enabled = searchResults.second > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Previous Result")
                }
                IconButton(onClick = onNextResult, enabled = searchResults.second > 0) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Next Result")
                }
            }
        }
    }
}
