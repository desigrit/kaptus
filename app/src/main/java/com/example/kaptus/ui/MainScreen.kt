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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kaptus.KaptusApplication
import com.example.kaptus.PlaybackViewModel
import com.example.kaptus.PreparationState
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
    val openSrt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            playbackViewModel.loadLocalSubtitles(context, it)
        }
    }

    LaunchedEffect(initialSrtUri) {
        initialSrtUri?.let {
            playbackViewModel.loadLocalSubtitles(context, it)
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
                    onOpenPreparedMovie = playbackViewModel::openPreparedMovie
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
                    onSelectMovie = { playbackViewModel.prepareMovie(it, false) },
                    onPrepareForTheater = { playbackViewModel.prepareMovie(it, true) }
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
                    onBrightnessChange = playbackViewModel::setTheaterBrightness
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
    // Register after navigation so Back cancels in-progress preparation first.
    BackHandler(enabled = state.preparation !is PreparationState.Idle) {
        playbackViewModel.dismissPreparation()
    }
}
