package com.example.kaptus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kaptus.R
import com.example.kaptus.data.PreparedMovie
import com.example.kaptus.ui.theme.KaptusTheme
import com.example.kaptus.ui.theme.KaptusVisualStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    providerConfigured: Boolean,
    preparedMovies: List<PreparedMovie>,
    onFindMovie: () -> Unit,
    onOpenSrt: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPreparedMovie: (String) -> Unit,
    modifier: Modifier = Modifier,
    visualStyle: KaptusVisualStyle = KaptusVisualStyle.CinemaAmber
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (visualStyle == KaptusVisualStyle.WarmPaper) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier.fillMaxWidth().navigationBarsPadding(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            Modifier.widthIn(max = 640.dp).fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FindMovieButton(onFindMovie)
                            OutlinedButton(
                                onClick = onOpenSrt,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                Spacer(Modifier.size(12.dp))
                                Text(stringResource(R.string.open_srt))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (visualStyle) {
                    KaptusVisualStyle.CinemaAmber -> {
                        item { HomeIntroduction(R.string.discovery_home_title, R.string.discovery_home_description) }
                        item { FindMovieButton(onFindMovie) }
                        item { ImportButton(onOpenSrt) }
                        if (!providerConfigured) item { ProviderSetupRow(onOpenSettings) }
                        item { LibraryHeading(R.string.prepared_movies) }
                        preparedItems(preparedMovies, onOpenPreparedMovie)
                    }
                    KaptusVisualStyle.MaterialSage -> {
                        item { HomeIntroduction(R.string.discovery_sage_title, R.string.discovery_sage_description) }
                        item {
                            Surface(
                                onClick = onFindMovie,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Row(
                                    Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(Icons.Outlined.Search, contentDescription = null)
                                    Text(stringResource(R.string.find_movie), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                                }
                            }
                        }
                        item {
                            TextButton(onClick = onOpenSrt, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                Spacer(Modifier.size(12.dp))
                                Text(stringResource(R.string.open_srt))
                            }
                        }
                        if (!providerConfigured) item { ProviderSetupRow(onOpenSettings) }
                        item { LibraryHeading(R.string.discovery_sage_library) }
                        if (preparedMovies.isEmpty()) item { EmptyPreparedLibrary() }
                        else item {
                            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column {
                                    preparedMovies.forEachIndexed { index, movie ->
                                        PreparedMovieRow(movie, { onOpenPreparedMovie(movie.featureId) }, grouped = true)
                                        if (index < preparedMovies.lastIndex) HorizontalDivider(
                                            modifier = Modifier.padding(start = 56.dp, end = 20.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    KaptusVisualStyle.WarmPaper -> {
                        item { HomeIntroduction(R.string.discovery_paper_title, R.string.discovery_paper_description) }
                        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp)) }
                        item { LibraryHeading(R.string.discovery_paper_library, generousSpacing = false) }
                        if (preparedMovies.isEmpty()) item { EmptyPreparedLibrary() }
                        else items(preparedMovies, key = { it.featureId }) { movie ->
                            PreparedMovieRow(movie, { onOpenPreparedMovie(movie.featureId) }, editorial = true)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (!providerConfigured) item { ProviderSetupRow(onOpenSettings) }
                    }
                    KaptusVisualStyle.MidnightBlue -> {
                        item { HomeIntroduction(R.string.discovery_blue_title, R.string.discovery_blue_description) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DashboardAction(Icons.Outlined.Search, stringResource(R.string.find_movie), onFindMovie, Modifier.weight(1f), primary = true)
                                DashboardAction(Icons.Outlined.FolderOpen, stringResource(R.string.open_srt), onOpenSrt, Modifier.weight(1f))
                            }
                        }
                        if (!providerConfigured) item { ProviderSetupRow(onOpenSettings) }
                        item { LibraryHeading(R.string.discovery_blue_library) }
                        if (preparedMovies.isEmpty()) item { EmptyPreparedLibrary() }
                        else {
                            item {
                                val movie = preparedMovies.first()
                                FeaturedMovie(movie, onClick = { onOpenPreparedMovie(movie.featureId) })
                            }
                            preparedItems(preparedMovies.drop(1), onOpenPreparedMovie, showEmpty = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeIntroduction(title: Int, description: Int) {
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(description), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FindMovieButton(onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null)
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.find_movie), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.open_srt))
    }
}

@Composable
internal fun ProviderSetupRow(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpenSettings, modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.discovery_connect_provider), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.discovery_connect_provider_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryHeading(title: Int, generousSpacing: Boolean = true) {
    Text(
        stringResource(title), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = if (generousSpacing) 16.dp else 0.dp, bottom = 4.dp).semantics { heading() }
    )
}

private fun LazyListScope.preparedItems(movies: List<PreparedMovie>, onOpen: (String) -> Unit, showEmpty: Boolean = true) {
    if (movies.isEmpty() && showEmpty) item { EmptyPreparedLibrary() }
    else items(movies, key = { it.featureId }) { movie -> PreparedMovieRow(movie, onClick = { onOpen(movie.featureId) }) }
}

@Composable
private fun EmptyPreparedLibrary() {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.discovery_library_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.discovery_library_empty_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreparedMovieRow(movie: PreparedMovie, onClick: () -> Unit, grouped: Boolean = false, editorial: Boolean = false) {
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        color = when { editorial -> MaterialTheme.colorScheme.background; grouped -> MaterialTheme.colorScheme.surfaceContainerLow; else -> MaterialTheme.colorScheme.surfaceContainer },
        shape = if (grouped || editorial) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.large
    ) {
        Row(
            Modifier.padding(horizontal = if (editorial) 0.dp else 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!editorial) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    movie.year?.let { stringResource(R.string.discovery_movie_title_year, movie.title, it) } ?: movie.title,
                    style = if (editorial) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
                Text(
                    pluralStringResource(R.plurals.offline_tracks, movie.trackCount, movie.trackCount),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 124.dp), shape = MaterialTheme.shapes.extraLarge,
        color = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FeaturedMovie(movie: PreparedMovie, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(movie.title, style = MaterialTheme.typography.headlineSmall)
                movie.year?.let { Text(it.toString(), style = MaterialTheme.typography.titleSmall) }
                Text(pluralStringResource(R.plurals.offline_tracks, movie.trackCount, movie.trackCount), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HomeScreenPreview() {
    KaptusTheme {
        HomeScreen(false, listOf(PreparedMovie("1", "Arrival", 2016, 3, 0L)), {}, {}, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, fontScale = 2f)
@Composable
private fun EmptyHomeLargeTextPreview() {
    KaptusTheme { HomeScreen(true, emptyList(), {}, {}, {}, {}) }
}

@Preview(showBackground = true, name = "Material Sage")
@Composable
private fun SageHomePreview() = HomeDirectionPreview(KaptusVisualStyle.MaterialSage)

@Preview(showBackground = true, name = "Warm Paper")
@Composable
private fun PaperHomePreview() = HomeDirectionPreview(KaptusVisualStyle.WarmPaper)

@Preview(showBackground = true, name = "Midnight Blue")
@Composable
private fun BlueHomePreview() = HomeDirectionPreview(KaptusVisualStyle.MidnightBlue)

@Composable
private fun HomeDirectionPreview(style: KaptusVisualStyle) {
    KaptusTheme(style) {
        HomeScreen(
            providerConfigured = true,
            preparedMovies = listOf(
                PreparedMovie("1", "Arrival", 2016, 3, 0L),
                PreparedMovie("2", "The Grand Budapest Hotel", 2014, 3, 0L),
                PreparedMovie("3", "Interstellar", 2014, 1, 0L)
            ),
            onFindMovie = {}, onOpenSrt = {}, onOpenSettings = {}, onOpenPreparedMovie = {}, visualStyle = style
        )
    }
}
