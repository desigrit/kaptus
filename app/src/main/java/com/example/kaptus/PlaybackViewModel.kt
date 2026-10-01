package com.example.kaptus

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.kaptus.data.CaptionTrack
import com.example.kaptus.data.CaptionLanguage
import com.example.kaptus.data.CaptionTrackRole
import com.example.kaptus.data.RecognitionTask
import com.example.kaptus.data.LocalCaptionTrack
import com.example.kaptus.data.MovieCandidate
import com.example.kaptus.data.PreparedMovie
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.settings.AppSettings
import com.example.kaptus.data.settings.ProviderCredentials
import com.example.kaptus.speech.WhisperSpeechRecognitionEngine
import com.example.kaptus.speech.SpeechModelStatus
import com.example.kaptus.speech.SpeechRecognitionConfiguration
import com.example.kaptus.sync.AutoSeekCapabilities
import com.example.kaptus.sync.AutoSeekMode
import com.example.kaptus.sync.AutoSeekPathPolicy
import com.example.kaptus.sync.SupportedAutoSeekPaths
import com.example.kaptus.sync.CaptionMappingStore
import com.example.kaptus.sync.MatchingPath
import com.example.kaptus.sync.LocalSceneMatcher
import com.example.kaptus.sync.MonotonicPlaybackClock
import com.example.kaptus.sync.SyncCoordinator
import com.example.kaptus.sync.SyncErrorKind
import com.example.kaptus.sync.SyncState
import com.example.kaptus.utils.getFileName
import com.example.kaptus.utils.parseSrtFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class PlaybackViewModel(
    private val container: AppContainer,
    private val pathPolicy: AutoSeekPathPolicy = SupportedAutoSeekPaths
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        UiState(
            providerCredentials = container.credentials.load(),
            providerConfigured = container.credentials.load().isConfigured
        )
    )
    val uiState: StateFlow<UiState> = mutableUiState.asStateFlow()

    private val clock = MonotonicPlaybackClock(viewModelScope)
    private val speechEngine = WhisperSpeechRecognitionEngine(container.applicationContext)
    private val mappings = CaptionMappingStore(container.applicationContext.noBackupFilesDir.resolve("caption_mappings"))
    private val syncCoordinator = SyncCoordinator(
        scope = viewModelScope,
        engine = speechEngine,
        matcher = LocalSceneMatcher(),
        clock = clock
    )
    private var searchJob: Job? = null
    private var preparationJob: Job? = null
    private var resumeAfterScrub = false
    private var currentMovie: MovieCandidate? = null
    private var candidateTracks: List<CaptionTrack> = emptyList()
    private val localCandidates = mutableMapOf<Long, LocalCaptionTrack>()
    private var currentTrackIndex = -1
    private var helperTrack: LocalCaptionTrack? = null
    private var activeCaptionLanguage = "en"
    private var activeSpokenLanguage = "en"
    private var modelDownloadJob: Job? = null
    private var synchronizationRequested = false
    private var lastPreparationRequest: (() -> Unit)? = null
    private val noticeIds = AtomicLong()

    init {
        refreshLanguages()
        viewModelScope.launch {
            container.speechModels.state.collect { status ->
                mutableUiState.value = mutableUiState.value.copy(modelStatus = status)
                val player = mutableUiState.value.player ?: return@collect
                if (player.spokenLanguage != "en") {
                    syncCoordinator.updateModelPath(container.speechModels.multilingualModelPath)
                    val available = capability(player.captionLanguage, player.spokenLanguage)
                    mutableUiState.value = mutableUiState.value.copy(
                        player = mutableUiState.value.player?.copy(
                            autoSeekAvailable = available.mode != AutoSeekMode.MANUAL,
                            modelReady = available.canListen
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            clock.positionMs.collect { position ->
                val player = mutableUiState.value.player ?: return@collect
                mutableUiState.value = mutableUiState.value.copy(
                    player = player.copy(
                        currentTimeMs = position,
                        currentCaption = activeCaptionAt(
                            player.subtitles,
                            position - player.captionDelayMs
                        )
                    )
                )
            }
        }
        viewModelScope.launch {
            clock.isPlaying.collect { playing ->
                mutableUiState.value.player?.let { player ->
                    mutableUiState.value = mutableUiState.value.copy(player = player.copy(isPlaying = playing))
                }
            }
        }
        viewModelScope.launch {
            syncCoordinator.state.collect { state ->
                mutableUiState.value.player?.let { player ->
                    mutableUiState.value = mutableUiState.value.copy(player = player.copy(syncState = state))
                }
            }
        }
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                mutableUiState.value = mutableUiState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            container.subtitles.observePreparedMovies().collect { movies ->
                mutableUiState.value = mutableUiState.value.copy(preparedMovies = movies)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        mutableUiState.value = mutableUiState.value.copy(
            searchQuery = query,
            searchError = null,
            searchResults = emptyList(),
            isSearching = query.trim().length >= 2
        )
        searchJob?.cancel()
        if (query.trim().length < 2) {
            mutableUiState.value = mutableUiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350L)
            mutableUiState.value = mutableUiState.value.copy(isSearching = true)
            container.subtitles.searchMovies(query).also {
                currentCoroutineContext().ensureActive()
            }.fold(
                onSuccess = { results ->
                    mutableUiState.value = mutableUiState.value.copy(
                        searchResults = results,
                        isSearching = false,
                        searchError = if (results.isEmpty()) text(R.string.search_no_movies) else null
                    )
                },
                onFailure = { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        searchError = error.userMessage()
                    )
                }
            )
        }
    }

    fun refreshLanguages() {
        viewModelScope.launch {
            container.subtitles.getLanguages().onSuccess { languages ->
                mutableUiState.value = mutableUiState.value.copy(languages = languages)
            }
        }
    }

    fun setLanguages(captionLanguage: String, spokenLanguage: String) {
        viewModelScope.launch { container.settings.setLanguages(captionLanguage, spokenLanguage) }
    }

    fun requestPreparation(movie: MovieCandidate, forTheater: Boolean) {
        val settings = mutableUiState.value.settings
        mutableUiState.value = mutableUiState.value.copy(
            preparationReview = PreparationReview(movie, forTheater, settings.captionLanguage, settings.spokenLanguage)
        )
    }

    fun dismissPreparationReview() {
        mutableUiState.value = mutableUiState.value.copy(preparationReview = null)
    }

    fun confirmPreparation() {
        val review = mutableUiState.value.preparationReview ?: return
        dismissPreparationReview()
        prepareMovie(review.movie, review.forTheater, review.captionLanguage, review.spokenLanguage)
    }

    fun updatePreparationLanguages(captionLanguage: String, spokenLanguage: String) {
        mutableUiState.value.preparationReview?.let {
            mutableUiState.value = mutableUiState.value.copy(
                preparationReview = it.copy(captionLanguage = captionLanguage, spokenLanguage = spokenLanguage)
            )
        }
    }

    fun downloadMultilingualModel() {
        if (modelDownloadJob?.isActive == true) return
        modelDownloadJob = viewModelScope.launch { container.speechModels.download() }
    }

    fun cancelModelDownload() {
        container.speechModels.cancel()
        modelDownloadJob?.cancel()
    }

    fun deleteMultilingualModel() {
        viewModelScope.launch { container.speechModels.delete() }
    }

    fun prepareMovie(movie: MovieCandidate, forTheater: Boolean,
                     captionLanguage: String = mutableUiState.value.settings.captionLanguage,
                     spokenLanguage: String = mutableUiState.value.settings.spokenLanguage) {
        lastPreparationRequest = { prepareMovie(movie, forTheater, captionLanguage, spokenLanguage) }
        preparationJob?.cancel()
        preparationJob = viewModelScope.launch {
            container.settings.setLanguages(captionLanguage, spokenLanguage)
            activeCaptionLanguage = captionLanguage
            activeSpokenLanguage = spokenLanguage
            helperTrack = null
            val selectedMovie = movie.copy(spokenLanguage = spokenLanguage)
            val selectedCapability = capability(captionLanguage, spokenLanguage)
            val eligible = selectedCapability.mode != AutoSeekMode.MANUAL
            mutableUiState.value = mutableUiState.value.copy(
                preparation = PreparationState.Working(
                    text(R.string.preparation_finding_captions),
                    0,
                    if (forTheater) 3 else 1
                ),
                searchError = null
            )
            val tracks = container.subtitles.findTracks(selectedMovie, captionLanguage).also {
                currentCoroutineContext().ensureActive()
            }.getOrElse { error ->
                mutableUiState.value = mutableUiState.value.copy(
                    preparation = PreparationState.Failed(error.userMessage())
                )
                return@launch
            }
            if (tracks.isEmpty()) {
                mutableUiState.value = mutableUiState.value.copy(
                    preparation = PreparationState.Failed(text(R.string.language_no_captions,
                        com.example.kaptus.ui.screens.captionLanguageName(captionLanguage, mutableUiState.value.languages)))
                )
                return@launch
            }
            currentMovie = selectedMovie
            candidateTracks = tracks.take(3)
            localCandidates.clear()
            val cachedTracks = container.subtitles.loadPrepared(movie.id, captionLanguage).also {
                currentCoroutineContext().ensureActive()
            }.getOrDefault(emptyList()).associateBy { it.track.fileId }
            val requested = if (forTheater && eligible) candidateTracks.size else 1
            val downloaded = mutableListOf<LocalCaptionTrack>()
            var downloadError: String? = null
            candidateTracks.take(requested).forEachIndexed { index, track ->
                mutableUiState.value = mutableUiState.value.copy(
                    preparation = PreparationState.Working(
                        if (forTheater) {
                            text(R.string.preparation_caption_progress, index + 1, requested)
                        } else {
                            text(R.string.preparation_downloading_best)
                        },
                        index,
                        requested
                    )
                )
                (cachedTracks[track.fileId]?.let { Result.success(it) }
                    ?: container.subtitles.download(track)).also {
                    currentCoroutineContext().ensureActive()
                }.onSuccess {
                    downloaded += it
                    localCandidates[it.track.fileId] = it
                }.onFailure {
                    downloadError = it.userMessage()
                }
            }
            if (downloaded.isEmpty()) {
                mutableUiState.value = mutableUiState.value.copy(
                    preparation = PreparationState.Failed(
                        downloadError ?: text(R.string.error_caption_downloads_failed)
                    )
                )
                return@launch
            }
            if (eligible && MatchingPath.MATCHING_HELPER in selectedCapability.paths) {
                helperTrack = container.subtitles.loadPrepared(movie.id, helperLanguage(spokenLanguage), CaptionTrackRole.MATCHING_HELPER)
                    .getOrDefault(emptyList()).firstOrNull()
                if (helperTrack == null) {
                    val helper = container.subtitles.findTracks(selectedMovie, helperLanguage(spokenLanguage))
                        .getOrDefault(emptyList()).firstOrNull()?.copy(role = CaptionTrackRole.MATCHING_HELPER)
                    helperTrack = helper?.let { container.subtitles.download(it).getOrNull() }
                }
            }
            val modelAvailable = selectedCapability.canListen
            val modelPrepared = if (modelAvailable && forTheater) {
                mutableUiState.value = mutableUiState.value.copy(
                    preparation = PreparationState.Working(
                        text(R.string.preparation_checking_model),
                        requested,
                        requested
                    )
                )
                runCatching { prepareSelectedSpeech() }.also {
                    currentCoroutineContext().ensureActive()
                }.isSuccess
            } else {
                if (modelAvailable) {
                    val ready = selectedSpeechReady()
                    currentCoroutineContext().ensureActive()
                    ready
                } else false
            }
            if (forTheater && modelAvailable && !modelPrepared) {
                mutableUiState.value = mutableUiState.value.copy(
                    preparation = PreparationState.Failed(text(R.string.error_model_prepare_failed))
                )
                return@launch
            }
            currentTrackIndex = candidateTracks.indexOfFirst { it.fileId == downloaded.first().track.fileId }
            openTrack(
                downloaded.first(),
                theaterPrepared = forTheater && downloaded.size == requested,
                modelReady = modelPrepared
            )
            mutableUiState.value = mutableUiState.value.copy(
                preparation = PreparationState.Idle,
                notice = notice(
                    if (forTheater && downloaded.size == requested && modelPrepared) text(R.string.auto_seek_ready)
                    else if (forTheater && eligible) text(R.string.captions_saved_model_needed)
                    else if (forTheater) text(R.string.captions_saved_manual)
                    else text(R.string.captions_ready_notice)
                )
            )
        }
    }

    fun openPreparedMovie(featureId: String,
                          captionLanguage: String = mutableUiState.value.settings.captionLanguage,
                          spokenLanguage: String? = null) {
        lastPreparationRequest = { openPreparedMovie(featureId, captionLanguage, spokenLanguage) }
        preparationJob?.cancel()
        preparationJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                preparation = PreparationState.Working(text(R.string.preparation_opening_captions), 0, 1)
            )
            container.subtitles.loadPrepared(featureId, captionLanguage).also {
                currentCoroutineContext().ensureActive()
            }.fold(
                onSuccess = { tracks ->
                    val first = tracks.firstOrNull()
                    if (first == null) {
                        mutableUiState.value = mutableUiState.value.copy(
                            preparation = PreparationState.Failed(text(R.string.error_downloaded_caption_missing))
                        )
                    } else {
                        activeCaptionLanguage = first.track.language
                        activeSpokenLanguage = spokenLanguage ?: first.movie.spokenLanguage
                        helperTrack = if (activeSpokenLanguage != "en")
                            container.subtitles.loadPrepared(featureId, helperLanguage(activeSpokenLanguage), CaptionTrackRole.MATCHING_HELPER)
                                .getOrDefault(emptyList()).firstOrNull() else null
                        val eligible = capability(activeCaptionLanguage, activeSpokenLanguage).mode == AutoSeekMode.AUTOMATIC
                        val modelPrepared = eligible && runCatching { selectedSpeechReady() }.also {
                            currentCoroutineContext().ensureActive()
                        }.getOrDefault(false)
                        currentMovie = first.movie
                        candidateTracks = tracks.map { it.track }
                        localCandidates.clear()
                        tracks.forEach { localCandidates[it.track.fileId] = it }
                        currentTrackIndex = 0
                        openTrack(
                            first,
                            theaterPrepared = true,
                            modelReady = modelPrepared
                        )
                        mutableUiState.value = mutableUiState.value.copy(preparation = PreparationState.Idle)
                    }
                },
                onFailure = { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        preparation = PreparationState.Failed(error.userMessage())
                    )
                }
            )
        }
    }

    fun loadLocalSubtitles(context: Context, uri: Uri, captionLanguage: String = "und",
                           spokenLanguage: String = mutableUiState.value.settings.spokenLanguage) {
        val applicationContext = context.applicationContext
        lastPreparationRequest = { loadLocalSubtitles(applicationContext, uri, captionLanguage, spokenLanguage) }
        preparationJob?.cancel()
        preparationJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                preparation = PreparationState.Working(text(R.string.preparation_reading_srt), 0, 1)
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    parseSrtFile(context, uri) to getFileName(context, uri)
                }
            }.also { currentCoroutineContext().ensureActive() }.fold(
                onSuccess = { (entries, fileName) ->
                    if (entries.isEmpty()) {
                        mutableUiState.value = mutableUiState.value.copy(
                            preparation = PreparationState.Failed(text(R.string.error_unreadable_srt))
                        )
                    } else {
                        activeCaptionLanguage = captionLanguage
                        activeSpokenLanguage = spokenLanguage
                        helperTrack = null
                        val eligible = capability(captionLanguage, spokenLanguage).mode == AutoSeekMode.AUTOMATIC
                        val modelPrepared = eligible && runCatching { selectedSpeechReady() }.also {
                            currentCoroutineContext().ensureActive()
                        }.getOrDefault(false)
                        currentMovie = null
                        candidateTracks = emptyList()
                        localCandidates.clear()
                        currentTrackIndex = -1
                        openEntries(
                            entries,
                            fileName,
                            fileName,
                            theaterPrepared = true,
                            modelReady = modelPrepared
                        )
                        mutableUiState.value = mutableUiState.value.copy(preparation = PreparationState.Idle)
                    }
                },
                onFailure = { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        preparation = PreparationState.Failed(
                            text(
                                R.string.error_opening_srt,
                                error.message ?: text(R.string.unknown_error)
                            )
                        )
                    )
                }
            )
        }
    }

    fun saveCredentials(apiKey: String, username: String, password: String) {
        val credentials = ProviderCredentials(apiKey.trim(), username.trim(), password)
        container.credentials.save(credentials)
        container.subtitles.resetSession()
        refreshLanguages()
        mutableUiState.value = mutableUiState.value.copy(
            providerCredentials = credentials,
            providerConfigured = credentials.isConfigured,
            notice = notice(
                text(
                    if (credentials.isConfigured) {
                        R.string.provider_settings_saved
                    } else {
                        R.string.provider_settings_cleared
                    }
                )
            )
        )
    }

    fun testProviderConnection() {
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isTestingProvider = true)
            container.subtitles.searchMovies("Big Buck Bunny").fold(
                onSuccess = {
                    mutableUiState.value = mutableUiState.value.copy(
                        isTestingProvider = false,
                        notice = notice(text(R.string.provider_connection_works))
                    )
                },
                onFailure = { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isTestingProvider = false,
                        notice = notice(error.userMessage())
                    )
                }
            )
        }
    }

    fun dismissPreparation() {
        preparationJob?.cancel()
        preparationJob = null
        mutableUiState.value = mutableUiState.value.copy(preparation = PreparationState.Idle)
    }

    fun retryPreparation() {
        lastPreparationRequest?.invoke()
    }

    fun startSynchronization() {
        if (!canStartSynchronization()) return
        synchronizationRequested = true
        syncCoordinator.start()
    }

    fun resync() {
        if (!canStartSynchronization()) return
        synchronizationRequested = true
        syncCoordinator.resync()
    }

    fun stopForBackground() {
        if (synchronizationRequested) syncCoordinator.stop(backgrounded = true)
    }

    fun resumeAfterBackground() {
        // The anchored clock advances without capture. Only Re-sync starts a new attempt.
    }

    fun playPause() {
        if (clock.isPlaying.value) {
            syncCoordinator.setPlaybackEnabled(false)
            clock.pause()
        } else {
            syncCoordinator.setPlaybackEnabled(true)
            clock.play()
        }
    }

    fun seekBy(offsetMs: Long) = clock.seekTo(clock.positionMs.value + offsetMs)

    fun onSliderChange(positionMs: Float) {
        val currentPlayer = mutableUiState.value.player.orEmpty()
        if (!currentPlayer.isScrubbing) {
            resumeAfterScrub = clock.isPlaying.value
            if (currentPlayer.syncState is SyncState.Listening ||
                currentPlayer.syncState is SyncState.Acquiring ||
                currentPlayer.syncState is SyncState.Reacquiring
            ) {
                synchronizationRequested = false
                syncCoordinator.stop()
            }
            clock.pause()
        }
        clock.seekTo(positionMs.toLong())
        mutableUiState.value.player?.let { player ->
            mutableUiState.value = mutableUiState.value.copy(player = player.copy(isScrubbing = true))
        }
    }

    fun onSliderChangeFinished() {
        mutableUiState.value.player?.let { player ->
            mutableUiState.value = mutableUiState.value.copy(player = player.copy(isScrubbing = false))
        }
        if (resumeAfterScrub) clock.play()
    }

    fun adjustCaptionOffset(offsetMs: Long) {
        val player = mutableUiState.value.player ?: return
        val delayMs = (player.captionDelayMs + offsetMs).coerceIn(-10_000L, 10_000L)
        mutableUiState.value = mutableUiState.value.copy(
            player = player.copy(
                captionDelayMs = delayMs,
                currentCaption = activeCaptionAt(player.subtitles, player.currentTimeMs - delayMs)
            )
        )
    }

    fun setCaptionSize(sizeSp: Float) {
        viewModelScope.launch { container.settings.setCaptionSize(sizeSp) }
    }

    fun setTheaterBrightness(brightness: Float) {
        viewModelScope.launch { container.settings.setTheaterBrightness(brightness) }
    }

    fun reportMicrophoneDenied() {
        synchronizationRequested = false
        mutableUiState.value.player?.let { player ->
            mutableUiState.value = mutableUiState.value.copy(
                player = player.copy(
                    syncState = SyncState.Error(SyncErrorKind.MICROPHONE_PERMISSION)
                )
            )
        }
    }

    fun consumeNotice(id: Long) {
        if (mutableUiState.value.notice?.id == id) {
            mutableUiState.value = mutableUiState.value.copy(notice = null)
        }
    }

    private fun openTrack(
        local: LocalCaptionTrack,
        theaterPrepared: Boolean,
        modelReady: Boolean = speechEngine.isReady
    ) {
        activeCaptionLanguage = local.track.language
        openEntries(
            entries = local.entries,
            title = local.movie.title,
            sourceName = local.track.fileName,
            theaterPrepared = theaterPrepared,
            modelReady = modelReady,
            displayHash = local.contentHash
        )
    }

    private fun openEntries(
        entries: List<SubtitleEntry>,
        title: String,
        sourceName: String,
        theaterPrepared: Boolean,
        modelReady: Boolean = speechEngine.isReady,
        displayHash: String = ""
    ) {
        synchronizationRequested = false
        clock.setRate(1.0)
        clock.setDuration(entries.lastOrNull()?.endTimeMs ?: 0L)
        clock.seekTo(0L)
        syncCoordinator.load(entries)
        val helperHash = helperTrack?.contentHash.orEmpty()
        syncCoordinator.configureLanguages(
            spokenLanguage = activeSpokenLanguage,
            captionLanguage = activeCaptionLanguage,
            modelPath = if (activeSpokenLanguage == "en") null else container.speechModels.multilingualModelPath,
            helperCaptions = helperTrack?.entries.orEmpty(),
            displayHash = displayHash,
            helperHash = helperHash,
            pathPolicy = pathPolicy,
            savedMapping = mappings.load(displayHash, helperHash),
            onValidatedMapping = { mapping ->
                viewModelScope.launch(Dispatchers.IO) { runCatching { mappings.save(mapping) } }
            }
        )
        val available = capability(activeCaptionLanguage, activeSpokenLanguage)
        val autoSeek = available.mode != AutoSeekMode.MANUAL
        mutableUiState.value = mutableUiState.value.copy(
            player = PlayerUiState(
                title = title,
                sourceName = sourceName,
                subtitles = entries,
                totalDurationMs = entries.lastOrNull()?.endTimeMs ?: 0L,
                theaterPrepared = theaterPrepared,
                modelReady = available.canListen && modelReady,
                captionLanguage = activeCaptionLanguage,
                spokenLanguage = activeSpokenLanguage,
                autoSeekAvailable = autoSeek
            ),
            playerVersion = mutableUiState.value.playerVersion + 1
        )
    }

    private fun activeCaptionAt(captions: List<SubtitleEntry>, timeMs: Long): SubtitleEntry? {
        var low = 0
        var high = captions.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val caption = captions[middle]
            when {
                timeMs < caption.startTimeMs -> high = middle - 1
                timeMs >= caption.endTimeMs -> low = middle + 1
                else -> return caption
            }
        }
        return null
    }

    private fun notice(message: String) = UiNotice(noticeIds.incrementAndGet(), message)

    private fun capability(captionLanguage: String, spokenLanguage: String) =
        AutoSeekCapabilities.evaluate(spokenLanguage, captionLanguage,
            if (spokenLanguage == "en") true else container.speechModels.multilingualModelPath != null,
            pathPolicy)

    private fun canStartSynchronization(): Boolean {
        val player = mutableUiState.value.player ?: return false
        if (!player.autoSeekAvailable || !player.modelReady ||
            !capability(player.captionLanguage, player.spokenLanguage).canListen) return false
        syncCoordinator.updateModelPath(
            if (player.spokenLanguage == "en") null else container.speechModels.multilingualModelPath
        )
        return true
    }

    private fun helperLanguage(spokenLanguage: String) = if (spokenLanguage == "zh") "zh-cn" else spokenLanguage

    private fun selectedSpeechConfiguration() = SpeechRecognitionConfiguration(
            sourceLanguage = activeSpokenLanguage,
            task = if (activeSpokenLanguage == "en" || helperTrack != null) RecognitionTask.TRANSCRIBE else RecognitionTask.TRANSLATE,
            modelPath = if (activeSpokenLanguage == "en") null else container.speechModels.multilingualModelPath,
            includeTranslation = activeSpokenLanguage != "en" && helperTrack != null
        )

    private suspend fun prepareSelectedSpeech() {
        speechEngine.configure(selectedSpeechConfiguration())
        speechEngine.prepare()
    }

    private suspend fun selectedSpeechReady(): Boolean {
        speechEngine.configure(selectedSpeechConfiguration())
        return speechEngine.isReady
    }

    private fun text(resourceId: Int, vararg arguments: Any): String =
        container.applicationContext.getString(resourceId, *arguments)

    private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() }
        ?: text(R.string.error_generic)

    override fun onCleared() {
        clock.close()
        container.applicationScope.launch { runCatching { syncCoordinator.close() } }
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlaybackViewModel(container) }
        }
    }
}

data class UiState(
    val providerConfigured: Boolean = false,
    val providerCredentials: ProviderCredentials = ProviderCredentials(),
    val preparedMovies: List<PreparedMovie> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<MovieCandidate> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val preparation: PreparationState = PreparationState.Idle,
    val player: PlayerUiState? = null,
    val playerVersion: Long = 0L,
    val settings: AppSettings = AppSettings(),
    val isTestingProvider: Boolean = false,
    val notice: UiNotice? = null,
    val languages: List<CaptionLanguage> = listOf(CaptionLanguage("en", "English")),
    val modelStatus: SpeechModelStatus = SpeechModelStatus.NotDownloaded,
    val preparationReview: PreparationReview? = null
)

data class PlayerUiState(
    val title: String,
    val sourceName: String,
    val subtitles: List<SubtitleEntry>,
    val currentCaption: SubtitleEntry? = null,
    val currentTimeMs: Long = 0L,
    val totalDurationMs: Long,
    val isPlaying: Boolean = false,
    val isScrubbing: Boolean = false,
    val captionDelayMs: Long = 0L,
    val syncState: SyncState = SyncState.Idle,
    val theaterPrepared: Boolean,
    val modelReady: Boolean,
    val captionLanguage: String = "en",
    val spokenLanguage: String = "en",
    val autoSeekAvailable: Boolean = captionLanguage == "en" && spokenLanguage == "en"
)

private fun PlayerUiState?.orEmpty(): PlayerUiState = this ?: PlayerUiState(
    title = "",
    sourceName = "",
    subtitles = emptyList(),
    totalDurationMs = 0L,
    theaterPrepared = false,
    modelReady = false
)

sealed interface PreparationState {
    data object Idle : PreparationState
    data class Working(val message: String, val completed: Int, val total: Int) : PreparationState
    data class Failed(val message: String) : PreparationState
}

data class UiNotice(val id: Long, val message: String)

data class PreparationReview(val movie: MovieCandidate, val forTheater: Boolean,
                             val captionLanguage: String, val spokenLanguage: String)
