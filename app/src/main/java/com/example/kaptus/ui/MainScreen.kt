package com.example.kaptus.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kaptus.KaptusApplication
import com.example.kaptus.PlaybackViewModel
import com.example.kaptus.PreparationState
import com.example.kaptus.R
import com.example.kaptus.sync.AutoSeekCapabilities
import com.example.kaptus.sync.AutoSeekMode
import com.example.kaptus.ui.screens.LanguageSelection
import com.example.kaptus.ui.screens.HomeScreen
import com.example.kaptus.ui.screens.MovieSearchScreen
import com.example.kaptus.ui.screens.PlayerScreen
import com.example.kaptus.ui.screens.SettingsScreen
import com.example.kaptus.ui.screens.PreparationStatus

private object Routes {
    const val Home = "home"
    const val Search = "search"
    const val Settings = "settings"
    const val Player = "player"
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    initialSrtUri: Uri? = null,
    onInitialSrtConsumed: () -> Unit = {},
    playbackViewModel: PlaybackViewModel = viewModel(
        factory = PlaybackViewModel.factory(
            (LocalContext.current.applicationContext as KaptusApplication).container
        )
    )
) {
    val state by playbackViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var importLanguage by remember { mutableStateOf("und") }
    var importSpokenLanguage by remember(state.settings.spokenLanguage) { mutableStateOf(state.settings.spokenLanguage) }
    val openSrt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            importLanguage = "und"
            pendingImport = it
        }
    }

    LaunchedEffect(initialSrtUri) {
        initialSrtUri?.let {
            importLanguage = "und"
            pendingImport = it
            onInitialSrtConsumed()
        }
    }

    LaunchedEffect(state.playerVersion) {
        if (state.playerVersion > 0L) {
            navController.navigate(Routes.Player) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(state.notice) {
        state.notice?.let { notice ->
            snackbarHostState.showSnackbar(notice.message)
            playbackViewModel.consumeNotice(notice.id)
        }
    }

    val navigateBack: () -> Unit = {
        if (state.preparation !is PreparationState.Idle) playbackViewModel.dismissPreparation()
        navController.navigateUp()
        Unit
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PreparationStatus(
                state = state.preparation,
                onDismiss = playbackViewModel::dismissPreparation,
                onRetry = playbackViewModel::retryPreparation,
                onSettings = {
                    playbackViewModel.dismissPreparation()
                    navController.navigate(Routes.Settings) { launchSingleTop = true }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    providerConfigured = state.providerConfigured,
                    preparedMovies = state.preparedMovies,
                    onFindMovie = { navController.navigate(Routes.Search) },
                    onOpenSrt = { openSrt.launch(arrayOf("application/x-subrip", "text/plain")) },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    onOpenPreparedMovie = { playbackViewModel.openPreparedMovie(it) },
                    onOpenPreparedCaptions = {
                        playbackViewModel.openPreparedMovie(it.featureId, it.captionLanguage, it.spokenLanguage)
                    }
                )
            }
            composable(Routes.Search) {
                MovieSearchScreen(
                    query = state.searchQuery,
                    results = state.searchResults,
                    isSearching = state.isSearching,
                    error = state.searchError,
                    providerConfigured = state.providerConfigured,
                    onBack = navigateBack,
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    onQueryChange = playbackViewModel::onSearchQueryChange,
                    onSelectMovie = { playbackViewModel.requestPreparation(it, false) },
                    onPrepareForTheater = { playbackViewModel.requestPreparation(it, true) },
                    languages = state.languages,
                    captionLanguage = state.settings.captionLanguage,
                    spokenLanguage = state.settings.spokenLanguage,
                    onCaptionLanguageChange = { playbackViewModel.setLanguages(it, state.settings.spokenLanguage) },
                    onSpokenLanguageChange = { playbackViewModel.setLanguages(state.settings.captionLanguage, it) },
                    languageSelectionEnabled = state.preparation is PreparationState.Idle
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    credentials = state.providerCredentials,
                    settings = state.settings,
                    isTesting = state.isTestingProvider,
                    onBack = navigateBack,
                    onSaveCredentials = playbackViewModel::saveCredentials,
                    onTestConnection = playbackViewModel::testProviderConnection,
                    onCaptionSizeChange = playbackViewModel::setCaptionSize,
                    onBrightnessChange = playbackViewModel::setTheaterBrightness,
                    languages = state.languages,
                    onLanguagesChange = playbackViewModel::setLanguages,
                    modelStatus = state.modelStatus,
                    onDownloadModel = playbackViewModel::downloadMultilingualModel,
                    onCancelModel = playbackViewModel::cancelModelDownload,
                    onDeleteModel = playbackViewModel::deleteMultilingualModel
                )
            }
            composable(Routes.Player) {
                val player = state.player
                if (player == null) {
                    LaunchedEffect(Unit) { navController.navigateUp() }
                } else {
                    PlayerScreen(
                        player = player,
                        settings = state.settings,
                        onBack = navigateBack,
                        onPlayPause = playbackViewModel::playPause,
                        onSliderChange = playbackViewModel::onSliderChange,
                        onSliderChangeFinished = playbackViewModel::onSliderChangeFinished,
                        onAdjustCaption = playbackViewModel::adjustCaptionOffset,
                        onStartSynchronization = playbackViewModel::startSynchronization,
                        onResync = playbackViewModel::resync,
                        onMicrophoneDenied = playbackViewModel::reportMicrophoneDenied,
                        onStopForBackground = playbackViewModel::stopForBackground,
                        onResumeAfterBackground = playbackViewModel::resumeAfterBackground,
                        onCaptionSizeChange = playbackViewModel::setCaptionSize,
                        onBrightnessChange = playbackViewModel::setTheaterBrightness
                    )
                }
            }
        }
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_language_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.import_language_description))
                    LanguageSelection(state.languages, importLanguage, importSpokenLanguage,
                        { importLanguage = it }, { importSpokenLanguage = it }, allowUnknown = true)
                }
            },
            confirmButton = { TextButton(onClick = {
                pendingImport = null
                playbackViewModel.loadLocalSubtitles(context, uri, importLanguage, importSpokenLanguage)
            }) { Text(stringResource(R.string.open_captions)) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
    state.preparationReview?.let { review ->
        val automatic = AutoSeekCapabilities.evaluate(review.spokenLanguage, review.captionLanguage, true).mode == AutoSeekMode.AUTOMATIC
        val downloads = (if (review.forTheater && automatic) 3 else 1) + if (automatic && review.spokenLanguage != "en") 1 else 0
        AlertDialog(
            onDismissRequest = playbackViewModel::dismissPreparationReview,
            title = { Text(review.movie.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LanguageSelection(state.languages, review.captionLanguage, review.spokenLanguage,
                        { playbackViewModel.updatePreparationLanguages(it, review.spokenLanguage) },
                        { playbackViewModel.updatePreparationLanguages(review.captionLanguage, it) })
                    Text(pluralStringResource(R.plurals.preparation_quota, downloads, downloads))
                    if (automatic && review.spokenLanguage != "en") Text(stringResource(R.string.preparation_helper_cost))
                    else if (!automatic) Text(stringResource(R.string.preparation_manual_cost))
                }
            },
            confirmButton = { TextButton(onClick = playbackViewModel::confirmPreparation) {
                Text(stringResource(if (review.forTheater) R.string.prepare_offline else R.string.watch_now))
            } },
            dismissButton = { TextButton(onClick = playbackViewModel::dismissPreparationReview) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
    // Register after navigation so Back cancels in-progress preparation first.
    BackHandler(enabled = state.preparation !is PreparationState.Idle) {
        playbackViewModel.dismissPreparation()
    }
}
