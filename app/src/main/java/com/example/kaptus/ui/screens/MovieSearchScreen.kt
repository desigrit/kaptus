package com.example.kaptus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kaptus.R
import com.example.kaptus.data.MovieCandidate
import com.example.kaptus.ui.theme.KaptusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieSearchScreen(
    query: String,
    results: List<MovieCandidate>,
    isSearching: Boolean,
    error: String?,
    providerConfigured: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectMovie: (MovieCandidate) -> Unit,
    onPrepareForTheater: (MovieCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMovieId by rememberSaveable(query) { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val emptyMessage = stringResource(R.string.search_no_movies)
    val searchError = error?.takeUnless { it == emptyMessage }
    val searchPending = isSearching || (query.trim().length >= 2 && results.isEmpty() && error == null)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.find_movie)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    placeholder = { Text(stringResource(R.string.discovery_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.discovery_clear_search))
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onQueryChange(query)
                        keyboard?.hide()
                        focus.clearFocus()
                    })
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!providerConfigured) {
                        item { ProviderSetupRow(onOpenSettings) }
                        item { SearchGuidance(Icons.Outlined.Movie, stringResource(R.string.discovery_search_setup_title), stringResource(R.string.discovery_search_setup_description)) }
                    } else if (searchPending) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.searching), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        when {
                            searchError != null -> item { SearchError(searchError, { onQueryChange(query) }, onOpenSettings, query.trim().length >= 2) }
                            query.trim().length < 2 -> item { SearchGuidance(Icons.Outlined.Movie, stringResource(R.string.discovery_search_initial_title), stringResource(R.string.discovery_search_initial_description)) }
                            error == emptyMessage -> item { SearchGuidance(Icons.Outlined.SearchOff, stringResource(R.string.discovery_search_empty_title), emptyMessage) }
                        }
                        if (results.isNotEmpty()) {
                            item {
                                Text(
                                    pluralStringResource(R.plurals.discovery_movie_count, results.size, results.size),
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp).semantics { heading() }
                                )
                            }
                            items(results, key = { it.id }) { movie ->
                                MovieResultRow(
                                    movie, selectedMovieId == movie.id,
                                    onExpand = {
                                        keyboard?.hide()
                                        focus.clearFocus()
                                        selectedMovieId = movie.id.takeUnless { it == selectedMovieId }
                                    },
                                    onSelect = { onSelectMovie(movie) }, onPrepare = { onPrepareForTheater(movie) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGuidance(icon: ImageVector, title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SearchError(message: String, onRetry: () -> Unit, onSettings: () -> Unit, canRetry: Boolean) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.discovery_search_error_title), style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (canRetry) TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.retry))
            }
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.discovery_provider_settings)) }
        }
    }
}

@Composable
private fun MovieResultRow(movie: MovieCandidate, expanded: Boolean, onExpand: () -> Unit, onSelect: () -> Unit, onPrepare: () -> Unit) {
    val expansionDescription = stringResource(if (expanded) R.string.discovery_movie_expanded else R.string.discovery_movie_collapsed)
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            ListItem(
                modifier = Modifier.testTag("movie_result_${movie.id}").clickable(role = Role.Button, onClick = onExpand).semantics { stateDescription = expansionDescription },
                headlineContent = { Text(movie.title, style = MaterialTheme.typography.titleMedium) },
                supportingContent = {
                    Text(movie.year?.toString() ?: stringResource(R.string.year_unknown), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                },
                leadingContent = {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                trailingContent = { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 8.dp))
                    Text(stringResource(R.string.discovery_best_captions), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onSelect, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(stringResource(R.string.start_listening))
                    }
                    TextButton(onClick = onPrepare, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.prepare_offline))
                    }
                    Text(stringResource(R.string.theater_quota_cost), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MovieSearchPreview() {
    KaptusTheme {
        MovieSearchScreen(
            query = "Arrival", results = listOf(MovieCandidate("1", "Arrival", 2016, 2543164, 329865), MovieCandidate("2", "The Arrival", 1996, null, null)),
            isSearching = false, error = null, providerConfigured = true, onBack = {}, onOpenSettings = {}, onQueryChange = {}, onSelectMovie = {}, onPrepareForTheater = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MovieSearchInitialPreview() {
    KaptusTheme {
        MovieSearchScreen("", emptyList(), false, null, true, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ExpandedMoviePreview() {
    KaptusTheme { MovieResultRow(MovieCandidate("1", "Arrival", 2016, 2543164, 329865), true, {}, {}, {}) }
}
