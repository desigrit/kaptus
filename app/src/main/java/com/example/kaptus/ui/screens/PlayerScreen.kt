package com.example.kaptus.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness2
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ScreenLockRotation
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kaptus.PlayerUiState
import com.example.kaptus.R
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.settings.AppSettings
import com.example.kaptus.sync.AcquisitionPhase
import com.example.kaptus.sync.ReacquisitionReason
import com.example.kaptus.sync.SyncErrorKind
import com.example.kaptus.sync.SyncState
import com.example.kaptus.ui.theme.CaptionWhite
import com.example.kaptus.ui.theme.CinemaBlack
import com.example.kaptus.ui.theme.CinemaColorScheme
import com.example.kaptus.ui.theme.KaptusTheme
import com.example.kaptus.ui.theme.MutedGray
import com.example.kaptus.ui.theme.PanelBlack
import com.example.kaptus.ui.theme.SyncAmber
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    player: PlayerUiState,
    settings: AppSettings,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onAdjustCaption: (Long) -> Unit,
    onStartSynchronization: () -> Unit,
    onResync: () -> Unit,
    onMicrophoneDenied: () -> Unit,
    onStopForBackground: () -> Unit,
    onResumeAfterBackground: () -> Unit,
    onCaptionSizeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    autoRequestMicrophone: Boolean = true,
    microphonePermissionOverride: Boolean? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accessibilityManager = LocalAccessibilityManager.current
    val touchExplorationEnabled = rememberTouchExplorationEnabled()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var orientationLocked by rememberSaveable { mutableStateOf(false) }
    var showMicrophoneExplanation by rememberSaveable { mutableStateOf(false) }
    var interactionRevision by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var showSettledSyncStatus by remember { mutableStateOf(false) }
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var entryStartHandled by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var microphoneGranted by remember(microphonePermissionOverride) {
        mutableStateOf(microphonePermissionOverride ?: (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ))
    }
    var backgrounded by remember { mutableStateOf(false) }
    var localBrightness by remember(settings.theaterBrightness) {
        mutableFloatStateOf(settings.theaterBrightness)
    }
    val currentModelReady by rememberUpdatedState(player.modelReady)
    val currentStart by rememberUpdatedState(onStartSynchronization)
    val currentStop by rememberUpdatedState(onStopForBackground)
    val currentResume by rememberUpdatedState(onResumeAfterBackground)
    val interact = { interactionRevision++; controlsVisible = true }
    val synchronized = player.syncState as? SyncState.Synchronized
    val showSyncStatus = synchronized == null || synchronized.checking || showSettledSyncStatus

    LaunchedEffect(synchronized?.lastConfirmedRealtimeMs, synchronized?.checking) {
        if (synchronized != null && !synchronized.checking) {
            showSettledSyncStatus = true
            delay(4_000L)
            showSettledSyncStatus = false
        } else {
            showSettledSyncStatus = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
        if (granted) startAfterPermission = true else onMicrophoneDenied()
    }

    LaunchedEffect(autoRequestMicrophone, player.modelReady, foreground, startAfterPermission) {
        // A model may finish preparing, or permission may return, while the activity is paused.
        if (player.modelReady && foreground) {
            if (startAfterPermission && microphoneGranted) {
                startAfterPermission = false
                entryStartHandled = true
                currentStart()
            } else if (autoRequestMicrophone && !entryStartHandled) {
                entryStartHandled = true
                if (microphoneGranted) currentStart() else showMicrophoneExplanation = true
            }
        }
    }

    LaunchedEffect(
        controlsVisible, player.isPlaying, interactionRevision, scrubbing,
        showAppearance, showMicrophoneExplanation, touchExplorationEnabled
    ) {
        if (controlsVisible && player.isPlaying && !scrubbing && !showAppearance &&
            !showMicrophoneExplanation && !touchExplorationEnabled
        ) {
            delay(accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 4_000L, containsText = true, containsControls = true
            ) ?: 4_000L)
            controlsVisible = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    foreground = false
                    if (activity?.isChangingConfigurations != true) {
                        backgrounded = true
                        currentStop()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    foreground = true
                    val granted = microphonePermissionOverride ?: (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        )
                    microphoneGranted = granted
                    if (backgrounded) {
                        backgrounded = false
                        if (granted && currentModelReady && !startAfterPermission) currentResume()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) currentStop()
        }
    }

    DisposableEffect(activity, view) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness
        val previousOrientation = activity?.requestedOrientation
        val screenWasKeptOn = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = insetsController?.isAppearanceLightStatusBars
        val previousLightNavigationBars = insetsController?.isAppearanceLightNavigationBars
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (window != null) {
                if (!screenWasKeptOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply {
                    screenBrightness = previousBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                insetsController?.apply {
                    isAppearanceLightStatusBars = previousLightStatusBars ?: false
                    isAppearanceLightNavigationBars = previousLightNavigationBars ?: false
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
            if (activity != null && previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }

    SideEffect {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = localBrightness }
        }
    }

    val surfaceAction = stringResource(
        if (controlsVisible) R.string.player_hide_controls else R.string.tap_to_show_controls
    )
    MaterialTheme(colorScheme = CinemaColorScheme) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(CinemaBlack)
                .testTag("player_surface")
                .semantics {
                    contentDescription = surfaceAction
                    onClick(label = surfaceAction) {
                        controlsVisible = !controlsVisible
                        interactionRevision++
                        true
                    }
                }
                .pointerInput(Unit) {
                    // Child buttons consume their gestures; a tap on the empty canvas toggles controls.
                    detectTapGestures {
                        controlsVisible = !controlsVisible
                        interactionRevision++
                    }
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val compact = maxHeight < 480.dp
            val inlineStatus = compact && maxWidth >= 600.dp && LocalDensity.current.fontScale < 1.6f
            val dockMaxHeight = maxHeight * if (compact) 0.52f else 0.46f
            val explanation = syncExplanation(player)
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (controlsVisible) {
                    PlayerHeader(
                        title = player.title,
                        player = player,
                        microphoneGranted = microphoneGranted,
                        inlineStatus = inlineStatus,
                        showSyncStatus = showSyncStatus,
                        onBack = onBack,
                        onSettings = { interact(); showAppearance = true }
                    )
                }
                AnimatedVisibility(
                    visible = showSyncStatus && (!controlsVisible || !inlineStatus),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SyncStatus(
                        player = player,
                        microphoneGranted = microphoneGranted,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = if (controlsVisible) 4.dp else 16.dp)
                    )
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = player.currentCaption?.text.orEmpty(),
                        color = CaptionWhite,
                        fontSize = settings.captionSizeSp.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = (settings.captionSizeSp * 1.25f).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 920.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                    )
                }
                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    PlayerDock(
                        player = player,
                        explanation = explanation,
                        maxHeight = dockMaxHeight,
                        onPlayPause = { interact(); onPlayPause() },
                        onSliderChange = { scrubbing = true; interact(); onSliderChange(it) },
                        onSliderChangeFinished = { scrubbing = false; interact(); onSliderChangeFinished() },
                        onAdjustCaption = { interact(); onAdjustCaption(it) },
                        onSync = {
                            interact()
                            if (!microphoneGranted) showMicrophoneExplanation = true
                            else if (player.syncState is SyncState.Idle) {
                                onStartSynchronization()
                            } else onResync()
                        }
                    )
                }
                if (explanation != null && !controlsVisible) {
                    Text(
                        text = explanation,
                        color = MutedGray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 560.dp)
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = player.captionDelayMs != 0L,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(start = 16.dp, top = if (controlsVisible) 68.dp else 16.dp)
            ) {
                CaptionOffsetBadge(player.captionDelayMs)
            }
        }

        if (showAppearance) {
            PlayerAppearanceSheet(
                player = player,
                settings = settings,
                brightness = localBrightness,
                orientationLocked = orientationLocked,
                onDismiss = { showAppearance = false; interact() },
                onCaptionSizeChange = onCaptionSizeChange,
                onBrightnessChange = { localBrightness = it; onBrightnessChange(it) },
                onOrientationToggle = {
                    orientationLocked = !orientationLocked
                    activity?.requestedOrientation = if (orientationLocked) {
                        ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            )
        }

        if (showMicrophoneExplanation) {
            AlertDialog(
                onDismissRequest = { showMicrophoneExplanation = false; interact() },
                icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                title = { Text(stringResource(R.string.microphone_permission_title)) },
                text = { Text(stringResource(R.string.microphone_permission_body)) },
                confirmButton = {
                    Button(onClick = {
                        showMicrophoneExplanation = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(stringResource(R.string.continue_label)) }
                },
                dismissButton = {
                    TextButton(onClick = { showMicrophoneExplanation = false; interact() }) {
                        Text(stringResource(R.string.not_now))
                    }
                }
            )
        }
    }
}

@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var enabled by remember(manager) { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

@Composable
private fun syncExplanation(player: PlayerUiState): String? = if (!player.modelReady) {
    stringResource(R.string.model_unavailable)
} else when (val sync = player.syncState) {
    SyncState.Listening -> stringResource(R.string.sync_hearing_detail)
    is SyncState.Acquiring -> acquisitionExplanation(sync.phase)
    is SyncState.Reacquiring -> when (sync.phase) {
        AcquisitionPhase.HEARING_DIALOGUE -> stringResource(when (sync.reason) {
            ReacquisitionReason.USER_REQUEST -> R.string.sync_hearing_detail
            ReacquisitionReason.BACKGROUNDED -> R.string.reacquiring_backgrounded
            ReacquisitionReason.TIMELINE_MOVED -> R.string.reacquiring_timeline_moved
            ReacquisitionReason.DIALOGUE_MISMATCH -> R.string.reacquiring_dialogue_mismatch
        })
        else -> acquisitionExplanation(sync.phase)
    }
    is SyncState.Error -> stringResource(when (sync.kind) {
        SyncErrorKind.NO_MATCH -> R.string.sync_error_no_match
        SyncErrorKind.MICROPHONE_PERMISSION -> R.string.sync_error_microphone
        SyncErrorKind.RECOGNIZER -> R.string.sync_error_recognizer
    })
    is SyncState.Synchronized -> if (sync.pendingLargeCorrection) {
        stringResource(R.string.correction_at_next_cue)
    } else null
    else -> null
}

@Composable
private fun SyncStatus(player: PlayerUiState, microphoneGranted: Boolean, modifier: Modifier = Modifier) {
    val state = player.syncState
    val label = if (!player.modelReady || (!microphoneGranted && state is SyncState.Idle)) {
        stringResource(R.string.needs_attention)
    } else if (!player.isPlaying && state is SyncState.Idle) {
        stringResource(R.string.player_paused)
    } else when (state) {
        SyncState.Idle, SyncState.Listening -> stringResource(R.string.sync_hearing_dialogue)
        is SyncState.Acquiring -> acquisitionLabel(state.phase)
        is SyncState.Reacquiring -> acquisitionLabel(state.phase)
        is SyncState.Synchronized -> stringResource(if (state.checking) R.string.checking_sync else R.string.synced)
        is SyncState.Error -> stringResource(R.string.needs_attention)
    }
    val settled = state is SyncState.Synchronized && !state.checking
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (settled) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = MutedGray, modifier = Modifier.size(16.dp))
        } else {
            Box(Modifier.size(6.dp).background(SyncAmber, CircleShape))
        }
        Text(label, color = if (settled) MutedGray else SyncAmber, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun acquisitionLabel(phase: AcquisitionPhase): String = stringResource(
    when (phase) {
        AcquisitionPhase.HEARING_DIALOGUE -> R.string.sync_hearing_dialogue
        AcquisitionPhase.TRANSCRIBING -> R.string.sync_transcribing
        AcquisitionPhase.COMPARING_CAPTIONS -> R.string.sync_comparing_captions
    }
)

@Composable
private fun acquisitionExplanation(phase: AcquisitionPhase): String = stringResource(
    when (phase) {
        AcquisitionPhase.HEARING_DIALOGUE -> R.string.sync_hearing_detail
        AcquisitionPhase.TRANSCRIBING -> R.string.sync_transcribing_detail
        AcquisitionPhase.COMPARING_CAPTIONS -> R.string.sync_comparing_detail
    }
)

@Composable
private fun PlayerHeader(
    title: String,
    player: PlayerUiState,
    microphoneGranted: Boolean,
    inlineStatus: Boolean,
    showSyncStatus: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.widthIn(max = 1120.dp).fillMaxWidth()
            .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = CaptionWhite)
        }
        Text(
            title, modifier = Modifier.weight(1f), color = CaptionWhite,
            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (inlineStatus) {
            AnimatedVisibility(visible = showSyncStatus, enter = fadeIn(), exit = fadeOut()) {
                SyncStatus(player, microphoneGranted)
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = CaptionWhite)
        }
    }
}

@Composable
private fun CaptionOffsetBadge(delayMs: Long) {
    val value = formatCaptionOffset(delayMs)
    val description = stringResource(R.string.caption_timing_offset, value)
    Surface(
        color = CaptionWhite.copy(alpha = 0.07f),
        contentColor = MutedGray,
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerDock(
    player: PlayerUiState,
    explanation: String?,
    maxHeight: Dp,
    onPlayPause: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onAdjustCaption: (Long) -> Unit,
    onSync: () -> Unit
) {
    val earlier = stringResource(R.string.caption_earlier)
    val later = stringResource(R.string.caption_later)
    val timelineLabel = stringResource(R.string.player_timeline)
    val timelinePosition = stringResource(
        R.string.player_timeline_position, formatTime(player.currentTimeMs), formatTime(player.totalDurationMs)
    )
    Surface(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().heightIn(max = maxHeight)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        color = PanelBlack,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(player.currentTimeMs), color = MutedGray, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = player.currentTimeMs.toFloat().coerceIn(0f, player.totalDurationMs.coerceAtLeast(1L).toFloat()),
                    onValueChange = onSliderChange,
                    onValueChangeFinished = onSliderChangeFinished,
                    enabled = player.totalDurationMs > 0L,
                    valueRange = 0f..player.totalDurationMs.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = timelineLabel
                        stateDescription = timelinePosition
                    }
                )
                Text(formatTime(player.totalDurationMs), color = MutedGray, style = MaterialTheme.typography.labelSmall)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { onAdjustCaption(-500L) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = earlier },
                    shape = CircleShape,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = CaptionWhite.copy(alpha = 0.06f),
                        contentColor = MutedGray
                    )
                ) { Text(stringResource(R.string.minus_500)) }
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                    Icon(
                        if (player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(if (player.isPlaying) R.string.pause else R.string.play),
                        modifier = Modifier.size(28.dp)
                    )
                }
                TextButton(
                    onClick = { onAdjustCaption(500L) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = later },
                    shape = CircleShape,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = CaptionWhite.copy(alpha = 0.06f),
                        contentColor = MutedGray
                    )
                ) { Text(stringResource(R.string.plus_500)) }
                OutlinedButton(onClick = onSync, enabled = player.modelReady, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(
                        if (player.syncState is SyncState.Idle) R.string.start_sync
                        else R.string.resync
                    ))
                }
            }
            if (explanation != null) {
                Text(
                    explanation, color = MutedGray, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun formatCaptionOffset(delayMs: Long): String {
    val seconds = String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(delayMs) / 1_000.0)
    return "${if (delayMs > 0L) "+" else "−"}${seconds}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerAppearanceSheet(
    player: PlayerUiState,
    settings: AppSettings,
    brightness: Float,
    orientationLocked: Boolean,
    onDismiss: () -> Unit,
    onCaptionSizeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onOrientationToggle: () -> Unit
) {
    val sizeLabel = stringResource(R.string.text_size)
    val sizeValue = stringResource(R.string.caption_size_short, settings.captionSizeSp.roundToInt())
    val brightnessLabel = stringResource(R.string.player_screen_brightness)
    val brightnessValue = stringResource(R.string.brightness_short, (brightness * 100).roundToInt())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PanelBlack
    ) {
        // Modal sheets own a window, so keep the same dim level while its controls are open.
        val sheetWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            sheetWindow?.let { window ->
                window.attributes = window.attributes.apply { screenBrightness = brightness }
            }
        }
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.player_viewing_options), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.close))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingValue(sizeLabel, sizeValue)
                Slider(
                    value = settings.captionSizeSp.coerceIn(24f, 44f), onValueChange = onCaptionSizeChange,
                    valueRange = 24f..44f, steps = 9,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = sizeLabel
                        stateDescription = sizeValue
                    }
                )
                Text(
                    stringResource(R.string.player_caption_preview), color = CaptionWhite,
                    fontSize = settings.captionSizeSp.sp, lineHeight = (settings.captionSizeSp * 1.25f).sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().background(CinemaBlack, MaterialTheme.shapes.medium).padding(20.dp)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingValue(brightnessLabel, brightnessValue)
                Slider(
                    value = brightness.coerceIn(0.01f, 1f), onValueChange = onBrightnessChange, valueRange = 0.01f..1f,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = brightnessLabel
                        stateDescription = brightnessValue
                    }
                )
                TextButton(onClick = { onBrightnessChange(0.01f) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Brightness2, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.player_theater_dim))
                }
                Text(
                    stringResource(R.string.player_brightness_restored), color = MutedGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = orientationLocked, role = Role.Switch, onValueChange = { onOrientationToggle() }
                ).heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    if (orientationLocked) Icons.Outlined.ScreenLockRotation else Icons.Outlined.ScreenRotation,
                    contentDescription = null, tint = MutedGray
                )
                Text(stringResource(R.string.lock_orientation), modifier = Modifier.weight(1f))
                Switch(checked = orientationLocked, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (player.theaterPrepared && player.modelReady) {
                    Text(stringResource(R.string.prepared_for_theater), color = SyncAmber, style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    stringResource(R.string.subtitle_source, player.sourceName), color = MutedGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SettingValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(value, color = MutedGray, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 800, heightDp = 400)
@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892, fontScale = 2f)
@Composable
private fun PlayerPreview() {
    PreviewPlayer(SyncState.Synchronized(0.89f, 0L))
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
private fun PlayerAcquiringPreview() {
    PreviewPlayer(SyncState.Acquiring())
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
private fun PlayerNeedsAttentionPreview() {
    PreviewPlayer(SyncState.Error(SyncErrorKind.NO_MATCH))
}

@Composable
private fun PreviewPlayer(state: SyncState) {
    KaptusTheme {
        PlayerScreen(
            player = PlayerUiState(
                title = stringResource(R.string.player_preview_title),
                sourceName = stringResource(R.string.player_preview_source),
                subtitles = emptyList(),
                currentCaption = if (state is SyncState.Synchronized) {
                    SubtitleEntry(1, 0, 4_000, stringResource(R.string.player_caption_preview))
                } else null,
                currentTimeMs = 46_250,
                totalDurationMs = 6_920_000,
                isPlaying = true,
                syncState = state,
                theaterPrepared = true,
                modelReady = true
            ),
            settings = AppSettings(),
            onBack = {}, onPlayPause = {}, onSliderChange = {}, onSliderChangeFinished = {},
            onAdjustCaption = {}, onStartSynchronization = {}, onResync = {}, onMicrophoneDenied = {},
            onStopForBackground = {}, onResumeAfterBackground = {}, onCaptionSizeChange = {}, onBrightnessChange = {},
            autoRequestMicrophone = false, microphonePermissionOverride = true
        )
    }
}
