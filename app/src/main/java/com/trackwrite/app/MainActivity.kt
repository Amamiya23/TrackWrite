package com.trackwrite.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.trackwrite.app.data.TrackRepository
import com.trackwrite.app.domain.GeoPoint
import com.trackwrite.app.domain.MatchOptions
import com.trackwrite.app.domain.MatchSource
import com.trackwrite.app.domain.PhotoMatch
import com.trackwrite.app.domain.Track
import com.trackwrite.app.domain.stats
import com.trackwrite.app.io.GpxFileActions
import com.trackwrite.app.map.ManualLocationActivity
import com.trackwrite.app.media.PhotoCandidate
import com.trackwrite.app.media.PhotoGeotagging
import com.trackwrite.app.media.PhotoMatchResult
import com.trackwrite.app.media.PhotoWriteOutcome
import com.trackwrite.app.recording.RecordingSnapshot
import com.trackwrite.app.recording.RecordingIssue
import com.trackwrite.app.recording.RecordingStateStore
import com.trackwrite.app.recording.RecordingStatus
import com.trackwrite.app.recording.TrackingService
import com.trackwrite.app.settings.AppSettings
import com.trackwrite.app.settings.AppSettingsStore
import com.trackwrite.app.settings.AppearanceMode
import com.trackwrite.app.settings.RecordingFrequency
import com.trackwrite.app.ui.TrackAlpha
import com.trackwrite.app.ui.TrackShape
import com.trackwrite.app.ui.TrackSpacing
import com.trackwrite.app.ui.TrackWriteTheme
import com.trackwrite.app.update.GitHubReleaseUpdateSource
import com.trackwrite.app.update.InstalledAppVersion
import com.trackwrite.app.update.MalformedUpdateReleaseException
import com.trackwrite.app.update.NoUpdateReleaseException
import com.trackwrite.app.update.UpdateCandidate
import com.trackwrite.app.update.UpdateDecision
import com.trackwrite.app.update.UpdateMetadataParser
import com.trackwrite.app.update.UpdateNetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.min

private const val MAX_GPX_IMPORT_BYTES = 10 * 1024 * 1024
private const val MAX_GPX_IMPORT_POINTS = 50_000
private val RecordingDockHeight = 76.dp
private val RecordingDockReservedSpace = 104.dp

class MainActivity : ComponentActivity() {
    private lateinit var repository: TrackRepository
    private lateinit var stateStore: RecordingStateStore
    private lateinit var geotagging: PhotoGeotagging
    private lateinit var gpx: GpxFileActions
    private lateinit var settingsStore: AppSettingsStore
    private val updateSource = GitHubReleaseUpdateSource()

    private var recordTrackId: String? = null
    private var matchTrackId: String? = null
    private var selectedPhotos: List<PhotoCandidate> = emptyList()
    private var matchResults: List<PhotoMatchResult> = emptyList()
    private var pendingManualPhotoIndex: Int? = null
    private var pendingExportMode: ExportFolderMode? = null
    private var pendingGpxExportTrackId: String? = null
    private var pendingMediaLocationAction: (() -> Unit)? = null
    private var pendingRecordingPermissionAction: (() -> Unit)? = null
    private var uiState by mutableStateOf(MainUiState())
    private val isBulkOperationRunning: Boolean
        get() = uiState.bulkOperation != null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val action = pendingRecordingPermissionAction
        pendingRecordingPermissionAction = null
        if (hasRecordingLocationPermission()) {
            action?.invoke()
        } else {
            log(getString(R.string.recording_location_permission_required))
        }
        refresh()
    }

    private val mediaLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingMediaLocationAction
        pendingMediaLocationAction = null
        if (granted) {
            action?.invoke()
        } else {
            log(getString(R.string.media_location_permission_required))
        }
    }

    private val gpxImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importGpx(uri)
    }

    private val gpxExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        val trackId = pendingGpxExportTrackId
        pendingGpxExportTrackId = null
        if (uri != null && trackId != null) exportTrack(trackId, uri)
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        loadPickedPhotos(uris)
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            loadFolderPhotos(uri)
        }
    }

    private val exportFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val mode = pendingExportMode
        pendingExportMode = null
        if (uri != null && mode != null) {
            persistTreePermission(uri)
            when (mode) {
                ExportFolderMode.SaveDefault -> {
                    settingsStore.setDefaultExportFolderUri(uri.toString())
                    uiState = uiState.copy(
                        settings = settingsStore.current(),
                        logMessage = getString(R.string.export_folder_saved),
                    )
                }
                ExportFolderMode.WriteCopies -> {
                    settingsStore.setDefaultExportFolderUri(uri.toString())
                    uiState = uiState.copy(settings = settingsStore.current())
                    writeCopies(uri)
                }
            }
        } else if (mode == ExportFolderMode.WriteCopies) {
            log(getString(R.string.export_folder_required))
        }
    }

    private val manualLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val index = pendingManualPhotoIndex
        pendingManualPhotoIndex = null
        if (result.resultCode != Activity.RESULT_OK || index == null) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val latitude = data.getDoubleExtra(ManualLocationActivity.EXTRA_LATITUDE, Double.NaN)
        val longitude = data.getDoubleExtra(ManualLocationActivity.EXTRA_LONGITUDE, Double.NaN)
        val label = data.getStringExtra(ManualLocationActivity.EXTRA_LABEL).orEmpty()
        if (latitude.isNaN() || longitude.isNaN()) {
            log(getString(R.string.manual_location_invalid))
            return@registerForActivityResult
        }
        val point = runCatching { GeoPoint(latitude, longitude) }.getOrElse {
            log(it.message ?: getString(R.string.manual_location_out_of_range))
            return@registerForActivityResult
        }
        selectedPhotos = selectedPhotos.mapIndexed { photoIndex, photo ->
            if (photoIndex == index) photo.copy(manualLocation = point) else photo
        }
        log(
            if (label.isBlank()) {
                getString(R.string.manual_location_bound, index + 1)
            } else {
                getString(R.string.manual_location_bound_with_label, index + 1, label)
            },
        )
        uiState = uiState.copy(selectedTab = MainTab.Match, showPhotoBatchSheet = true, highlightedPhotoIndex = index)
        matchSelectedPhotos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = TrackRepository(this)
        stateStore = RecordingStateStore(this)
        geotagging = PhotoGeotagging(this)
        gpx = GpxFileActions(this)
        settingsStore = AppSettingsStore(this)
        refresh()
        setContent {
            TrackWriteTheme(uiState.settings.appearance) {
                TrackWriteApp(
                    state = uiState,
                    onTabSelected = { uiState = uiState.copy(selectedTab = it, showSettings = false) },
                    onSettings = { uiState = uiState.copy(showSettings = true) },
                    onCloseSettings = { uiState = uiState.copy(showSettings = false) },
                    onStartRecording = { uiState = uiState.copy(startDialogName = defaultTrackName()) },
                    onPause = { command(TrackingService.ACTION_PAUSE) },
                    onResume = { requestRecordingPermissionsThen { command(TrackingService.ACTION_RESUME) } },
                    onStop = { uiState = uiState.copy(showStopRecordingDialog = true) },
                    onRefreshRecording = { refresh() },
                    onImportGpx = { gpxImportLauncher.launch(arrayOf("application/gpx+xml", "text/xml", "*/*")) },
                    onExportGpx = { promptExport() },
                    onRenameTrack = { recordTrack()?.let { uiState = uiState.copy(renameDialog = RenameDialogState(it.id, it.name)) } },
                    onDeleteTrack = { recordTrack()?.let { uiState = uiState.copy(deleteDialog = it) } },
                    onRecordTrackSelected = { recordTrackId = it; refresh() },
                    onMatchTrackSelected = {
                        matchTrackId = it
                        matchSelectedPhotos()
                    },
                    onShowTrackHistory = { uiState = uiState.copy(showTrackHistorySheet = true) },
                    onDismissTrackHistory = { uiState = uiState.copy(showTrackHistorySheet = false) },
                    onShowTrackSource = { uiState = uiState.copy(showTrackSourceSheet = true) },
                    onDismissTrackSource = { uiState = uiState.copy(showTrackSourceSheet = false) },
                    onShowPhotoBatch = { uiState = uiState.copy(showPhotoBatchSheet = true) },
                    onDismissPhotoBatch = { uiState = uiState.copy(showPhotoBatchSheet = false) },
                    onHighlightConsumed = { uiState = uiState.copy(highlightedPhotoIndex = null) },
                    onSelectPhotos = { photoPickerLauncher.launch(arrayOf("image/*")) },
                    onSelectFolder = { folderPickerLauncher.launch(null) },
                    onSetManualLocation = { index ->
                        if (!isBulkOperationRunning) {
                            pendingManualPhotoIndex = index
                            manualLocationLauncher.launch(Intent(this, ManualLocationActivity::class.java))
                        }
                    },
                    onClearManualLocation = { index ->
                        if (!isBulkOperationRunning) {
                            selectedPhotos = selectedPhotos.mapIndexed { photoIndex, photo ->
                                if (photoIndex == index) photo.copy(manualLocation = null) else photo
                            }
                            log(getString(R.string.manual_location_cleared, index + 1))
                            matchSelectedPhotos()
                        }
                    },
                    onWriteDefault = { writeDefault() },
                    onSettingsChanged = { newSettings ->
                        persistSettings(newSettings)
                    },
                    onChooseExportFolder = {
                        pendingExportMode = ExportFolderMode.SaveDefault
                        exportFolderLauncher.launch(null)
                    },
                    onCheckForUpdates = { checkForUpdates() },
                    onOpenReleasePage = ::openReleasePage,
                    onDismissUpdateDialog = { uiState = uiState.copy(updateDialogCandidate = null) },
                    onLogMessageConsumed = { uiState = uiState.copy(logMessage = "") },
                    onDismissDialog = { dismissDialogs() },
                    onDismissWriteResult = { uiState = uiState.copy(writeResult = null) },
                    onConfirmStart = { name ->
                        uiState = uiState.copy(startDialogName = null)
                        requestRecordingPermissionsThen { command(TrackingService.ACTION_START, name) }
                    },
                    onConfirmRename = { trackId, name ->
                        repository.renameTrack(trackId, name)
                        uiState = uiState.copy(renameDialog = null)
                        refresh()
                    },
                    onConfirmDelete = { track ->
                        val recording = stateStore.current()
                        if (recording.trackId == track.id && recording.status != RecordingStatus.Stopped) {
                            uiState = uiState.copy(
                                deleteDialog = null,
                                logMessage = getString(R.string.delete_active_recording_blocked),
                            )
                        } else {
                            repository.deleteTrack(track.id)
                            if (recordTrackId == track.id) recordTrackId = null
                            if (matchTrackId == track.id) matchTrackId = null
                            uiState = uiState.copy(deleteDialog = null)
                            refresh()
                        }
                    },
                    onConfirmWrite = {
                        uiState = uiState.copy(showWriteDialog = false)
                        requestMediaLocationPermissionThen {
                            writeOriginals()
                        }
                    },
                    onConfirmStop = {
                        uiState = uiState.copy(showStopRecordingDialog = false)
                        command(TrackingService.ACTION_STOP)
                    },
                )
            }
        }
    }

    private fun refresh() {
        val tracks = repository.listTracks()
        if (recordTrackId == null || tracks.none { it.id == recordTrackId }) {
            recordTrackId = stateStore.current().trackId?.takeIf { id -> tracks.any { it.id == id } }
                ?: tracks.firstOrNull()?.id
        }
        if (matchTrackId != null && tracks.none { it.id == matchTrackId }) {
            matchTrackId = null
        }
        val settings = settingsStore.current()
        matchResults = matchResults.ifEmpty {
            selectedPhotos.map { PhotoMatchResult(it, null) }
        }
        uiState = uiState.copy(
            recording = stateStore.current(),
            tracks = tracks,
            recordTrackId = recordTrackId,
            matchTrackId = matchTrackId,
            photos = selectedPhotos,
            matches = matchResults,
            settings = settings,
            bulkOperation = uiState.bulkOperation,
            recordingClockMillis = System.currentTimeMillis(),
        )
    }

    private fun loadPickedPhotos(uris: List<Uri>) {
        loadPhotos(BulkOperation.LoadingPhotos) {
            persistPhotoPermissions(uris)
            geotagging.loadPhotos(uris)
        }
    }

    private fun loadFolderPhotos(uri: Uri) {
        loadPhotos(BulkOperation.LoadingFolderPhotos) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            geotagging.loadPhotosFromFolder(uri)
        }
    }

    private fun loadPhotos(operation: BulkOperation, loadBlock: () -> List<PhotoCandidate>) {
        if (isBulkOperationRunning) return
        uiState = uiState.copy(selectedTab = MainTab.Match, bulkOperation = operation)
        lifecycleScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) {
                    loadBlock()
                }
                selectedPhotos = photos
                matchSelectedPhotos()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log(getString(R.string.photo_load_failed, error.message.orEmpty()))
            } finally {
                uiState = uiState.copy(bulkOperation = null)
            }
        }
    }

    private fun persistSettings(settings: AppSettings) {
        settingsStore.setAppearance(settings.appearance)
        settingsStore.setRecordingFrequency(settings.recordingFrequency)
        settingsStore.setCameraOffset(settings.cameraOffset)
        settingsStore.setMaxPhotoTimeDifference(settings.maxPhotoTimeDifference)
        settingsStore.setAllowStartFallback(settings.allowStartFallback)
        settingsStore.setAllowEndFallback(settings.allowEndFallback)
        settingsStore.setPreferExportCopies(settings.preferExportCopies)
        settingsStore.setDefaultExportFolderUri(settings.defaultExportFolderUri)
        uiState = uiState.copy(settings = settings, logMessage = getString(R.string.settings_saved))
        matchSelectedPhotos()
    }

    private fun checkForUpdates() {
        if (uiState.updateState.isBusy) return
        uiState = uiState.copy(
            updateState = AppUpdateUiState.Checking,
            updateDialogCandidate = null,
        )
        lifecycleScope.launch {
            try {
                val candidate = withContext(Dispatchers.IO) {
                    updateSource.fetchLatestUpdate()
                }
                uiState = when (val decision = UpdateMetadataParser.decide(uiState.installedVersion, candidate)) {
                    is UpdateDecision.Available -> uiState.copy(
                        updateState = AppUpdateUiState.Available(decision.candidate),
                        updateDialogCandidate = decision.candidate,
                    )
                    is UpdateDecision.UpToDate -> uiState.copy(
                        updateState = AppUpdateUiState.UpToDate(decision.installedVersion),
                        updateDialogCandidate = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: NoUpdateReleaseException) {
                showUpdateError(getString(R.string.update_error_no_release))
            } catch (_: MalformedUpdateReleaseException) {
                showUpdateError(getString(R.string.update_error_malformed))
            } catch (_: UpdateNetworkException) {
                showUpdateError(getString(R.string.update_error_network))
            } catch (error: Exception) {
                showUpdateError(getString(R.string.update_error_generic, error.updateDetail()))
            }
        }
    }

    private fun showUpdateError(message: String) {
        uiState = uiState.copy(
            updateState = AppUpdateUiState.Error(message),
            updateDialogCandidate = null,
            logMessage = message,
        )
    }

    private fun openReleasePage(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showUpdateError(getString(R.string.update_error_browser_unavailable))
        } catch (_: SecurityException) {
            showUpdateError(getString(R.string.update_error_browser_unavailable))
        }
    }

    private fun dismissDialogs() {
        uiState = uiState.copy(
            startDialogName = null,
            renameDialog = null,
            deleteDialog = null,
            showStopRecordingDialog = false,
            showWriteDialog = false,
            updateDialogCandidate = null,
        )
    }

    private fun defaultTrackName(): String = formatDefaultTrackName(Instant.now())

    private fun command(action: String, name: String? = null) {
        val stoppedTrackId = if (action == TrackingService.ACTION_STOP) stateStore.current().trackId else null
        ContextCompat.startForegroundService(this, TrackingService.command(this, action, name))
        window.decorView.postDelayed({
            if (action == TrackingService.ACTION_STOP && matchTrackId == null) {
                matchTrackId = stoppedTrackId?.takeIf { repository.getTrack(it) != null }
            }
            refresh()
            if (action == TrackingService.ACTION_STOP) {
                log(getString(R.string.recording_saved_to_history))
            }
        }, 500)
    }

    private fun importGpx(uri: Uri) {
        lifecycleScope.launch {
            try {
                val track = withContext(Dispatchers.IO) {
                    val xml = readGpxTextWithLimit(uri)
                    gpx.importTrack(
                        id = UUID.randomUUID().toString(),
                        xml = xml,
                        maxTrackPoints = MAX_GPX_IMPORT_POINTS,
                    ).also(repository::saveTrack)
                }
                matchTrackId = track.id
                log(getString(R.string.gpx_imported, track.name))
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log(getString(R.string.gpx_import_failed, error.message.orEmpty()))
            }
        }
    }

    private fun readGpxTextWithLimit(uri: Uri): String {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { getString(R.string.gpx_read_failed) }
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                totalBytes += count
                check(totalBytes <= MAX_GPX_IMPORT_BYTES) {
                    getString(R.string.gpx_file_too_large, MAX_GPX_IMPORT_BYTES / (1024 * 1024))
                }
                output.write(chunk, 0, count)
            }
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private fun promptExport() {
        val track = recordTrack() ?: return log(getString(R.string.select_track_first))
        pendingGpxExportTrackId = track.id
        gpxExportLauncher.launch("${track.name}.gpx")
    }

    private fun exportTrack(trackId: String, uri: Uri) {
        val track = repository.getTrack(trackId) ?: return
        contentResolver.openOutputStream(uri, "w").use { output ->
            requireNotNull(output).write(gpx.encode(track).toByteArray())
        }
        log(getString(R.string.gpx_exported, track.name))
    }

    private fun matchSelectedPhotos() {
        val track = matchTrack()
        val options = uiState.settings.toMatchOptions()
        matchResults = if (track == null) {
            selectedPhotos.map { PhotoMatchResult(it, null) }
        } else {
            geotagging.matchPhotos(selectedPhotos, track, options)
        }
        refresh()
    }

    private fun writeDefault() {
        if (isBulkOperationRunning) return
        val summary = writeReadiness(matchResults)
        if (summary.writeable == 0) {
            log(getString(R.string.write_missing_location_prompt))
            return
        }
        if (uiState.settings.preferExportCopies) {
            writeCopiesUsingDefaultFolder()
        } else {
            uiState = uiState.copy(showWriteDialog = true)
        }
    }

    private fun writeCopiesUsingDefaultFolder() {
        val folderUri = uiState.settings.defaultExportFolderUri?.let(Uri::parse)
        if (folderUri != null && isUsableTreeUri(folderUri)) {
            writeCopies(folderUri)
            return
        }
        pendingExportMode = ExportFolderMode.WriteCopies
        exportFolderLauncher.launch(null)
    }

    private fun writeCopies(uri: Uri) {
        writePhotos(BulkOperation.WritingCopies, WriteMode.Copies) { results, onProgress ->
            geotagging.exportCopies(results, uri, onProgress)
        }
    }

    private fun writeOriginals() {
        writePhotos(BulkOperation.WritingOriginals, WriteMode.Originals) { results, onProgress ->
            geotagging.writeInPlace(results, onProgress)
        }
    }

    private fun writePhotos(
        operation: BulkOperation,
        mode: WriteMode,
        writeBlock: (List<PhotoMatchResult>, (Int) -> Unit) -> List<PhotoWriteOutcome>,
    ) {
        if (isBulkOperationRunning) return
        val results = matchResults
        uiState = uiState.copy(
            bulkOperation = operation,
            writeProgress = WriteProgressState(processed = 0, total = results.size),
        )
        lifecycleScope.launch {
            try {
                val outcomes = withContext(Dispatchers.IO) {
                    writeBlock(results) { processed ->
                        launch(Dispatchers.Main) {
                            uiState = uiState.copy(writeProgress = WriteProgressState(processed, results.size))
                        }
                    }
                }
                uiState = uiState.copy(writeResult = WriteResultState.from(outcomes, mode))
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log(getString(R.string.photo_write_failed, error.message.orEmpty()))
            } finally {
                uiState = uiState.copy(bulkOperation = null, writeProgress = null)
            }
        }
    }

    private fun isUsableTreeUri(uri: Uri): Boolean =
        DocumentFile.fromTreeUri(this, uri)?.canWrite() == true

    private fun persistTreePermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun recordTrack(): Track? =
        recordTrackId?.let(repository::getTrack) ?: stateStore.current().trackId?.let(repository::getTrack)

    private fun matchTrack(): Track? =
        matchTrackId?.let(repository::getTrack)

    private fun requestRecordingPermissionsThen(block: () -> Unit) {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isEmpty()) {
            block()
        } else {
            pendingRecordingPermissionAction = block
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun hasRecordingLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestMediaLocationPermissionThen(block: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            block()
            return
        }
        pendingMediaLocationAction = block
        mediaLocationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    private fun persistPhotoPermissions(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.recoverCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun log(message: String) {
        uiState = uiState.copy(logMessage = message)
    }
}

private fun Exception.updateDetail(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName

private data class MainUiState(
    val selectedTab: MainTab = MainTab.Record,
    val showSettings: Boolean = false,
    val recording: RecordingSnapshot = RecordingSnapshot(null, RecordingStatus.Stopped),
    val tracks: List<Track> = emptyList(),
    val recordTrackId: String? = null,
    val matchTrackId: String? = null,
    val photos: List<PhotoCandidate> = emptyList(),
    val matches: List<PhotoMatchResult> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val installedVersion: InstalledAppVersion = InstalledAppVersion(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
    ),
    val updateState: AppUpdateUiState = AppUpdateUiState.Idle,
    val updateDialogCandidate: UpdateCandidate? = null,
    val logMessage: String = "",
    val showTrackHistorySheet: Boolean = false,
    val showTrackSourceSheet: Boolean = false,
    val showPhotoBatchSheet: Boolean = false,
    val highlightedPhotoIndex: Int? = null,
    val writeResult: WriteResultState? = null,
    val startDialogName: String? = null,
    val renameDialog: RenameDialogState? = null,
    val deleteDialog: Track? = null,
    val showStopRecordingDialog: Boolean = false,
    val showWriteDialog: Boolean = false,
    val bulkOperation: BulkOperation? = null,
    val writeProgress: WriteProgressState? = null,
    val recordingClockMillis: Long = System.currentTimeMillis(),
)

private sealed interface AppUpdateUiState {
    val isBusy: Boolean
        get() = this is AppUpdateUiState.Checking

    object Idle : AppUpdateUiState
    object Checking : AppUpdateUiState
    data class UpToDate(val installedVersion: InstalledAppVersion) : AppUpdateUiState
    data class Available(val candidate: UpdateCandidate) : AppUpdateUiState
    data class Error(val message: String) : AppUpdateUiState
}

private data class RenameDialogState(
    val trackId: String,
    val name: String,
)

private data class WriteReadiness(
    val writeable: Int,
    val skipped: Int,
)

private data class WriteProgressState(
    val processed: Int,
    val total: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else processed.toFloat() / total.toFloat()

    val percent: Int
        get() = (fraction * 100).toInt().coerceIn(0, 100)
}

private data class WriteResultState(
    val mode: WriteMode,
    val written: Int,
    val skipped: Int,
    val failed: List<PhotoWriteOutcome>,
) {
    companion object {
        fun from(outcomes: List<PhotoWriteOutcome>, mode: WriteMode): WriteResultState =
            WriteResultState(
                mode = mode,
                written = outcomes.count { it.status == PhotoWriteOutcome.Status.Written },
                skipped = outcomes.count { it.status == PhotoWriteOutcome.Status.Skipped },
                failed = outcomes.filter { it.status == PhotoWriteOutcome.Status.Failed },
            )
    }
}

private enum class MainTab {
    Record,
    Match,
}

private enum class ExportFolderMode {
    SaveDefault,
    WriteCopies,
}

private enum class WriteMode {
    Copies,
    Originals,
}

private enum class BulkOperation {
    LoadingPhotos,
    LoadingFolderPhotos,
    WritingCopies,
    WritingOriginals,
}

private enum class PhotoBatchFilter {
    All,
    Unmatched,
    Manual,
    Writeable,
}

private fun AppSettings.toMatchOptions(): MatchOptions =
    MatchOptions(
        cameraOffset = cameraOffset,
        maxTimeDifference = maxPhotoTimeDifference,
        allowStartFallback = allowStartFallback,
        allowEndFallback = allowEndFallback,
    )

@Composable
private fun TrackWriteApp(
    state: MainUiState,
    onTabSelected: (MainTab) -> Unit,
    onSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onStartRecording: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRefreshRecording: () -> Unit,
    onImportGpx: () -> Unit,
    onExportGpx: () -> Unit,
    onRenameTrack: () -> Unit,
    onDeleteTrack: () -> Unit,
    onRecordTrackSelected: (String) -> Unit,
    onMatchTrackSelected: (String) -> Unit,
    onShowTrackHistory: () -> Unit,
    onDismissTrackHistory: () -> Unit,
    onShowTrackSource: () -> Unit,
    onDismissTrackSource: () -> Unit,
    onShowPhotoBatch: () -> Unit,
    onDismissPhotoBatch: () -> Unit,
    onHighlightConsumed: () -> Unit,
    onSelectPhotos: () -> Unit,
    onSelectFolder: () -> Unit,
    onSetManualLocation: (Int) -> Unit,
    onClearManualLocation: (Int) -> Unit,
    onWriteDefault: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onChooseExportFolder: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onLogMessageConsumed: () -> Unit,
    onDismissDialog: () -> Unit,
    onDismissWriteResult: () -> Unit,
    onConfirmStart: (String) -> Unit,
    onConfirmRename: (String, String) -> Unit,
    onConfirmDelete: (Track) -> Unit,
    onConfirmWrite: () -> Unit,
    onConfirmStop: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.logMessage) {
        val message = state.logMessage
        if (message.isNotBlank()) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
            onLogMessageConsumed()
        }
    }

    Scaffold(
        topBar = {
            TrackWriteTopBar(
                title = stringResource(
                    when {
                        state.showSettings -> R.string.settings
                        state.selectedTab == MainTab.Record -> R.string.tab_record
                        else -> R.string.tab_match
                    },
                ),
                showSettingsAction = !state.showSettings,
                onSettings = onSettings,
            )
        },
        bottomBar = {
            TrackWriteBottomBar(
                selectedTab = state.selectedTab,
                settingsSelected = state.showSettings,
                onRecord = { onTabSelected(MainTab.Record) },
                onMatch = { onTabSelected(MainTab.Match) },
                onSettings = onSettings,
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                SystemToastSnackbar(data.visuals.message)
            }
        },
    ) { padding ->
        if (state.showSettings) {
            BackHandler { onCloseSettings() }
            SettingsScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                settings = state.settings,
                installedVersion = state.installedVersion,
                updateState = state.updateState,
                updateDialogCandidate = state.updateDialogCandidate,
                onSettingsChanged = onSettingsChanged,
                onChooseExportFolder = onChooseExportFolder,
                onCheckForUpdates = onCheckForUpdates,
                onOpenReleasePage = onOpenReleasePage,
                onDismissUpdateDialog = onDismissUpdateDialog,
            )
        } else {
            when (state.selectedTab) {
                MainTab.Record -> RecordScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onStartRecording = onStartRecording,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop,
                    onRefreshRecording = onRefreshRecording,
                    onExportGpx = onExportGpx,
                    onRenameTrack = onRenameTrack,
                    onDeleteTrack = onDeleteTrack,
                    onRecordTrackSelected = onRecordTrackSelected,
                    onShowTrackHistory = onShowTrackHistory,
                    onDismissTrackHistory = onDismissTrackHistory,
                )
                MainTab.Match -> MatchScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onImportGpx = onImportGpx,
                    onMatchTrackSelected = onMatchTrackSelected,
                    onShowTrackSource = onShowTrackSource,
                    onDismissTrackSource = onDismissTrackSource,
                    onShowPhotoBatch = onShowPhotoBatch,
                    onDismissPhotoBatch = onDismissPhotoBatch,
                    onHighlightConsumed = onHighlightConsumed,
                    onSelectPhotos = onSelectPhotos,
                    onSelectFolder = onSelectFolder,
                    onSetManualLocation = onSetManualLocation,
                    onClearManualLocation = onClearManualLocation,
                    onWriteDefault = onWriteDefault,
                )
            }
        }
    }

    StartRecordingDialog(state.startDialogName, onDismissDialog, onConfirmStart)
    RenameTrackDialog(state.renameDialog, onDismissDialog, onConfirmRename)
    DeleteTrackDialog(state.deleteDialog, onDismissDialog, onConfirmDelete)
    StopRecordingDialog(state.showStopRecordingDialog, onDismissDialog, onConfirmStop)
    WriteOriginalsDialog(state.showWriteDialog, onDismissDialog, onConfirmWrite)
    WriteProgressDialog(state.bulkOperation, state.writeProgress)
    WriteResultSheet(state.writeResult, onDismissWriteResult)
}

@Composable
private fun TrackWriteTopBar(
    title: String,
    showSettingsAction: Boolean,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 3.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (showSettingsAction) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(TrackShape.control)
                        .clickable(onClick = onSettings),
                    shape = TrackShape.control,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        TrackWriteLineIcon(
                            icon = TrackWriteIcon.Settings,
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = stringResource(R.string.settings),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(44.dp))
            }
        }
    }
}

@Composable
private fun TrackWriteBottomBar(
    selectedTab: MainTab,
    settingsSelected: Boolean,
    onRecord: () -> Unit,
    onMatch: () -> Unit,
    onSettings: () -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f)
    val materialWash = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .background(materialWash),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(start = 24.dp, end = 24.dp, top = 7.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItem(
                selected = !settingsSelected && selectedTab == MainTab.Record,
                icon = TrackWriteIcon.Record,
                label = stringResource(R.string.tab_record),
                onClick = onRecord,
                modifier = Modifier.weight(1f),
            )
            BottomNavItem(
                selected = !settingsSelected && selectedTab == MainTab.Match,
                icon = TrackWriteIcon.Photo,
                label = stringResource(R.string.tab_match),
                onClick = onMatch,
                modifier = Modifier.weight(1f),
            )
            BottomNavItem(
                selected = settingsSelected,
                icon = TrackWriteIcon.Settings,
                label = stringResource(R.string.settings),
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    icon: TrackWriteIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .height(45.dp)
            .clip(TrackShape.card)
            .clickable(onClick = onClick),
        shape = TrackShape.card,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f) else Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TrackWriteLineIcon(
                icon = icon,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class TrackWriteIcon(val imageVector: ImageVector) {
    Target(Icons.Rounded.MyLocation),
    Satellite(Icons.Rounded.SatelliteAlt),
    Pause(Icons.Rounded.Pause),
    Play(Icons.Rounded.PlayArrow),
    Stop(Icons.Rounded.Stop),
    Warning(Icons.Rounded.Warning),
    Lock(Icons.Rounded.Lock),
    History(Icons.Rounded.History),
    Route(Icons.Rounded.Route),
    Record(Icons.Rounded.RadioButtonUnchecked),
    Photo(Icons.Rounded.PhotoCamera),
    Folder(Icons.Rounded.FolderOpen),
    Settings(Icons.Rounded.Settings),
    File(Icons.Rounded.Description),
    Empty(Icons.Rounded.DeleteOutline),
    Close(Icons.Rounded.Close),
    More(Icons.Rounded.MoreVert),
}

@Composable
private fun TrackWriteLineIcon(
    icon: TrackWriteIcon,
    tint: Color,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = icon.imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

@Composable
private fun RecordScreen(
    modifier: Modifier,
    state: MainUiState,
    onStartRecording: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRefreshRecording: () -> Unit,
    onExportGpx: () -> Unit,
    onRenameTrack: () -> Unit,
    onDeleteTrack: () -> Unit,
    onRecordTrackSelected: (String) -> Unit,
    onShowTrackHistory: () -> Unit,
    onDismissTrackHistory: () -> Unit,
) {
    var showRecordingDetails by remember { mutableStateOf(false) }
    LaunchedEffect(state.recording.status, state.recording.trackId) {
        while (state.recording.status == RecordingStatus.Recording && state.recording.trackId != null) {
            onRefreshRecording()
            delay(1_000)
        }
    }
    LaunchedEffect(state.recording.status, state.recording.trackId) {
        if (state.recording.status == RecordingStatus.Stopped || state.recording.trackId == null) {
            showRecordingDetails = false
        }
    }
    val activeTrack = state.recording.trackId?.let { id -> state.tracks.firstOrNull { it.id == id } }
    val activeTrackId = state.recording.trackId.takeIf { state.recording.status != RecordingStatus.Stopped }
    val historicalTracks = historicalTracksForRecording(state.tracks, state.recording)
    val latestTrack = historicalTracks.firstOrNull()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 6.dp,
                end = 18.dp,
                bottom = RecordingDockReservedSpace,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                TrackHistoryButton(
                    trackCount = historicalTracks.size,
                    latestTrack = latestTrack,
                    onClick = onShowTrackHistory,
                )
            }
        }

        RecordingControlDock(
            state = state,
            activeTrack = activeTrack,
            onStartRecording = onStartRecording,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            onShowDetails = { showRecordingDetails = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .height(RecordingDockHeight),
        )
    }

    if (state.showTrackHistorySheet) {
        TrackHistorySheet(
            tracks = historicalTracks,
            activeTrackId = activeTrackId,
            onTrackSelected = onRecordTrackSelected,
            onExportGpx = onExportGpx,
            onRenameTrack = onRenameTrack,
            onDeleteTrack = onDeleteTrack,
            onDismiss = onDismissTrackHistory,
        )
    }

    if (showRecordingDetails && state.recording.status != RecordingStatus.Stopped) {
        RecordingDetailsSheet(
            state = state,
            activeTrack = activeTrack,
            onDismiss = { showRecordingDetails = false },
        )
    }
}

internal fun historicalTracksForRecording(
    tracks: List<Track>,
    recording: RecordingSnapshot,
): List<Track> {
    val activeTrackId = recording.trackId.takeIf { recording.status != RecordingStatus.Stopped }
    return if (activeTrackId == null) tracks else tracks.filterNot { it.id == activeTrackId }
}

@Composable
private fun RecordingControlDock(
    state: MainUiState,
    activeTrack: Track?,
    onStartRecording: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = state.recording.status,
        modifier = modifier,
        animationSpec = tween(durationMillis = 180),
        label = "recording-dock-state",
    ) { status ->
        when (status) {
            RecordingStatus.Stopped -> RecordingStartDock(onStartRecording)
            RecordingStatus.Recording,
            RecordingStatus.Paused,
            -> RecordingActiveDock(
                state = state,
                activeTrack = activeTrack,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onShowDetails = onShowDetails,
            )
        }
    }
}

@Composable
private fun RecordingStartDock(
    onStartRecording: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(TrackShape.card)
            .clickable(onClick = onStartRecording),
        shape = TrackShape.card,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.start_recording),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun RecordingActiveDock(
    state: MainUiState,
    activeTrack: Track?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onShowDetails: () -> Unit,
) {
    val tone = recordingProofTone(state.recording, state.settings, state.recordingClockMillis)
    val stats = activeTrack?.stats()
    val duration = activeRecordingDuration(state, activeTrack) ?: stats?.duration ?: Duration.ZERO
    val statusText = stringResource(
        R.string.recording_dock_title,
        statusLabel(state.recording.status),
        formatCompactDuration(duration),
    )
    val evidence = recordingEvidenceTitle(state.recording, state.recordingClockMillis)
    val detailText = stringResource(
        R.string.recording_dock_detail,
        activeTrack?.points?.size ?: 0,
        stringResource(R.string.points_short),
        evidence,
    )
    val stateColor = recordingToneColor(tone)
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = TrackShape.card,
        color = if (tone == PillTone.Success) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = androidx.compose.foundation.BorderStroke(1.dp, toneBorderColor(tone)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TrackSpacing.x3, vertical = TrackSpacing.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrackSpacing.x2),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(TrackShape.control)
                    .clickable(
                        onClickLabel = stringResource(R.string.open_recording_details),
                        onClick = onShowDetails,
                    )
                    .padding(horizontal = TrackSpacing.x1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrackSpacing.x2),
            ) {
                TrackWriteLineIcon(
                    icon = recordingEvidenceIcon(state.recording),
                    tint = stateColor,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(TrackSpacing.x1))
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tone == PillTone.Error || tone == PillTone.Warning) {
                            stateColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            RecordingDockIconButton(
                icon = if (state.recording.status == RecordingStatus.Paused) TrackWriteIcon.Play else TrackWriteIcon.Pause,
                contentDescription = stringResource(
                    if (state.recording.status == RecordingStatus.Paused) R.string.resume else R.string.pause,
                ),
                onClick = if (state.recording.status == RecordingStatus.Paused) onResume else onPause,
            )
            RecordingDockIconButton(
                icon = TrackWriteIcon.Stop,
                contentDescription = stringResource(R.string.stop_recording),
                onClick = onStop,
                danger = true,
            )
        }
    }
}

@Composable
private fun RecordingDockIconButton(
    icon: TrackWriteIcon,
    contentDescription: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clip(TrackShape.control)
            .clickable(onClick = onClick),
        shape = TrackShape.control,
        color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (danger) MaterialTheme.colorScheme.error.copy(alpha = TrackAlpha.faint) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            TrackWriteLineIcon(
                icon = icon,
                tint = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingDetailsSheet(
    state: MainUiState,
    activeTrack: Track?,
    onDismiss: () -> Unit,
) {
    val tone = recordingProofTone(state.recording, state.settings, state.recordingClockMillis)
    val stats = activeTrack?.stats()
    val duration = activeRecordingDuration(state, activeTrack) ?: stats?.duration ?: Duration.ZERO
    val provider = state.recording.provider?.uppercase(Locale.ROOT) ?: stringResource(R.string.provider_unknown)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            DrawerHeader(
                title = stringResource(R.string.recording_details),
                subtitle = activeTrack?.name,
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(TrackSpacing.x4))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = TrackShape.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, toneBorderColor(tone)),
            ) {
                Column(Modifier.padding(TrackSpacing.x5)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TrackSpacing.x3),
                    ) {
                        TrackWriteLineIcon(
                            icon = recordingEvidenceIcon(state.recording),
                            tint = recordingToneColor(tone),
                            modifier = Modifier.size(24.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = recordingProofTitle(state.recording, state.settings, state.recordingClockMillis),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(TrackSpacing.x1))
                            Text(
                                text = recordingConfidenceText(state.recording, state.settings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(TrackSpacing.x5))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CompactMetric(
                            label = stringResource(R.string.points_captured),
                            value = (activeTrack?.points?.size ?: 0).toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricDivider()
                        CompactMetric(
                            label = stringResource(R.string.duration),
                            value = formatCompactDuration(duration),
                            modifier = Modifier.weight(1f),
                        )
                        MetricDivider()
                        CompactMetric(
                            label = stringResource(R.string.distance),
                            value = formatDistance(stats?.distanceMeters ?: 0.0),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(TrackSpacing.x5))
                    RecordingDetailRow(
                        label = stringResource(R.string.latest_track_point),
                        value = recordingEvidenceTitle(state.recording, state.recordingClockMillis),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = TrackSpacing.x3),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    RecordingDetailRow(
                        label = stringResource(R.string.location_source),
                        value = provider,
                    )
                    if (activeTrack == null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = TrackSpacing.x3),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        RecordingDetailRow(
                            label = stringResource(R.string.active_track),
                            value = stringResource(R.string.recording_track_unavailable),
                        )
                    }
                }
            }
            Spacer(Modifier.height(TrackSpacing.x6))
        }
    }
}

@Composable
private fun RecordingDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TrackSpacing.x4),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = TrackSpacing.x2),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun TrackHistoryButton(
    trackCount: Int,
    latestTrack: Track?,
    onClick: () -> Unit,
) {
    val latestLabel = latestTrack?.let { formatTrackHistoryLabel(it.name, it.startTime) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackShape.card)
            .clickable(onClick = onClick),
        shape = TrackShape.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = TrackShape.control,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                TrackWriteLineIcon(
                    icon = TrackWriteIcon.History,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.track_history_count, trackCount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                )
                if (latestTrack != null && latestLabel != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = latestLabel.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val stats = latestTrack.stats()
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${latestTrack.points.size} ${stringResource(R.string.points_short)} · ${formatCompactDuration(stats.duration)} · ${formatDistance(stats.distanceMeters)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TrackAlpha.faint),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DrawerHeader(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clip(TrackShape.control)
                .clickable(onClick = onDismiss),
            shape = TrackShape.control,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                TrackWriteLineIcon(
                    icon = TrackWriteIcon.Close,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackHistorySheet(
    tracks: List<Track>,
    activeTrackId: String?,
    onTrackSelected: (String) -> Unit,
    onExportGpx: () -> Unit,
    onRenameTrack: () -> Unit,
    onDeleteTrack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expandedTrackId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tracks) {
        if (tracks.none { it.id == expandedTrackId }) expandedTrackId = null
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            DrawerHeader(
                title = stringResource(R.string.track_history_count, tracks.size),
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(14.dp))
            if (tracks.isEmpty()) {
                EmptyStateBlock(
                    title = stringResource(R.string.no_tracks),
                    body = stringResource(R.string.track_history_empty_help),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(tracks) { _, track ->
                        TrackManagementRow(
                            track = track,
                            expanded = track.id == expandedTrackId,
                            deleteEnabled = track.id != activeTrackId,
                            onToggleActions = {
                                expandedTrackId = track.id.takeUnless { expandedTrackId == track.id }
                            },
                            onExportGpx = { onTrackSelected(track.id); onExportGpx() },
                            onRenameTrack = { onTrackSelected(track.id); onRenameTrack() },
                            onDeleteTrack = { onTrackSelected(track.id); onDeleteTrack() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TrackManagementRow(
    track: Track,
    expanded: Boolean,
    deleteEnabled: Boolean,
    onToggleActions: () -> Unit,
    onExportGpx: () -> Unit,
    onRenameTrack: () -> Unit,
    onDeleteTrack: () -> Unit,
) {
    val stats = track.stats()
    val historyLabel = formatTrackHistoryLabel(track.name, track.startTime)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackShape.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = historyLabel.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    historyLabel.subtitle?.let { subtitle ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${track.points.size} ${stringResource(R.string.points_short)} · ${formatCompactDuration(stats.duration)} · ${formatDistance(stats.distanceMeters)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(TrackShape.control)
                        .clickable(onClick = onToggleActions),
                    shape = TrackShape.control,
                    color = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        TrackWriteLineIcon(
                            icon = TrackWriteIcon.More,
                            tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = stringResource(R.string.track_actions, historyLabel.title),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SoftActionButton(
                        text = stringResource(R.string.export_gpx),
                        onClick = onExportGpx,
                        modifier = Modifier.weight(1f),
                    )
                    SoftActionButton(
                        text = stringResource(R.string.rename),
                        onClick = onRenameTrack,
                        modifier = Modifier.weight(1f),
                    )
                    SoftActionButton(
                        text = stringResource(R.string.delete),
                        onClick = onDeleteTrack,
                        modifier = Modifier.weight(1f),
                        danger = true,
                        enabled = deleteEnabled,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchScreen(
    modifier: Modifier,
    state: MainUiState,
    onImportGpx: () -> Unit,
    onMatchTrackSelected: (String) -> Unit,
    onShowTrackSource: () -> Unit,
    onDismissTrackSource: () -> Unit,
    onShowPhotoBatch: () -> Unit,
    onDismissPhotoBatch: () -> Unit,
    onHighlightConsumed: () -> Unit,
    onSelectPhotos: () -> Unit,
    onSelectFolder: () -> Unit,
    onSetManualLocation: (Int) -> Unit,
    onClearManualLocation: (Int) -> Unit,
    onWriteDefault: () -> Unit,
) {
    val selectedTrack = state.matchTrackId?.let { id -> state.tracks.firstOrNull { it.id == id } }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 18.dp, top = 6.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TrackSourceButton(
                selectedTrack = selectedTrack,
                onClick = onShowTrackSource,
            )
        }
        if (state.matches.isEmpty()) {
            item {
                PhotoInputPanel(
                    bulkOperation = state.bulkOperation,
                    onSelectPhotos = onSelectPhotos,
                    onSelectFolder = onSelectFolder,
                )
            }
        } else {
            item {
                PhotoBatchButton(
                    matches = state.matches,
                    onClick = onShowPhotoBatch,
                )
            }
            item {
                WriteActionPanel(
                    settings = state.settings,
                    readiness = writeReadiness(state.matches),
                    bulkOperation = state.bulkOperation,
                    onWriteDefault = onWriteDefault,
                )
            }
        }
        when (val operation = state.bulkOperation) {
            BulkOperation.LoadingPhotos,
            BulkOperation.LoadingFolderPhotos,
            -> item { BulkOperationPanel(operation) }
            else -> Unit
        }
    }

    if (state.showTrackSourceSheet) {
        TrackSourceSheet(
            tracks = state.tracks,
            selectedTrackId = state.matchTrackId,
            onTrackSelected = onMatchTrackSelected,
            onImportGpx = onImportGpx,
            onDismiss = onDismissTrackSource,
        )
    }
    if (state.showPhotoBatchSheet) {
        PhotoBatchSheet(
            matches = state.matches,
            highlightedPhotoIndex = state.highlightedPhotoIndex,
            onSetManualLocation = onSetManualLocation,
            onClearManualLocation = onClearManualLocation,
            onDismiss = onDismissPhotoBatch,
        )
    }
}

@Composable
private fun TrackSourceButton(
    selectedTrack: Track?,
    onClick: () -> Unit,
) {
    if (selectedTrack == null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TrackShape.card)
                .clickable(onClick = onClick),
            shape = TrackShape.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrackWriteLineIcon(
                    icon = TrackWriteIcon.Route,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.match_choose_track),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.no_match_track_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val stats = selectedTrack.stats()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackShape.card)
            .clickable(onClick = onClick),
        shape = TrackShape.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.match_track_source),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TrackAlpha.faint),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = selectedTrack.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactMetric(
                    label = stringResource(R.string.points_short),
                    value = selectedTrack.points.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                CompactMetric(
                    label = stringResource(R.string.duration),
                    value = formatDuration(stats.duration),
                    modifier = Modifier.weight(1f),
                )
                CompactMetric(
                    label = stringResource(R.string.distance),
                    value = formatDistance(stats.distanceMeters),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PhotoInputPanel(
    bulkOperation: BulkOperation?,
    onSelectPhotos: () -> Unit,
    onSelectFolder: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PhotoInputButton(
            text = stringResource(R.string.select_photos),
            icon = TrackWriteIcon.Photo,
            onClick = onSelectPhotos,
            enabled = bulkOperation == null,
            modifier = Modifier.weight(1f),
        )
        PhotoInputButton(
            text = stringResource(R.string.select_folder),
            icon = TrackWriteIcon.Folder,
            onClick = onSelectFolder,
            enabled = bulkOperation == null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PhotoInputButton(
    text: String,
    icon: TrackWriteIcon,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 62.dp)
            .clip(TrackShape.control)
            .clickable(enabled = enabled, onClick = onClick),
        shape = TrackShape.control,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
        ) {
            TrackWriteLineIcon(
                icon = icon,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else TrackAlpha.disabled),
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else TrackAlpha.disabled),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackSourceSheet(
    tracks: List<Track>,
    selectedTrackId: String?,
    onTrackSelected: (String) -> Unit,
    onImportGpx: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            DrawerHeader(
                title = stringResource(R.string.match_track_source),
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(14.dp))
            SoftActionButton(
                text = stringResource(R.string.import_gpx),
                onClick = { onImportGpx() },
                modifier = Modifier.fillMaxWidth(),
                icon = TrackWriteIcon.File,
                minHeight = 48.dp,
            )
            Spacer(Modifier.height(14.dp))
            if (tracks.isEmpty()) {
                EmptyStateBlock(
                    title = stringResource(R.string.no_tracks),
                    body = stringResource(R.string.no_match_track_help),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(tracks) { _, track ->
                        SelectableTrackRow(
                            track = track,
                            selected = track.id == selectedTrackId,
                            onClick = { onTrackSelected(track.id); onDismiss() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SelectableTrackRow(
    track: Track,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val stats = track.stats()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackShape.card)
            .clickable(onClick = onClick),
        shape = TrackShape.card,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${track.points.size} ${stringResource(R.string.points_short)} · ${formatDuration(stats.duration)} · ${formatDistance(stats.distanceMeters)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PhotoBatchButton(
    matches: List<PhotoMatchResult>,
    onClick: () -> Unit,
) {
    val manualCount = matches.count { it.photo.manualLocation != null }
    val matchedCount = matches.count { it.selectedPosition != null }
    val unmatchedCount = matches.size - matchedCount
    val readiness = writeReadiness(matches)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackShape.card)
            .clickable(onClick = onClick),
        shape = TrackShape.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.photo_batch_count, matches.size),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TrackAlpha.faint),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactMetric(stringResource(R.string.matched), matchedCount.toString(), Modifier.weight(1f))
                CompactMetric(stringResource(R.string.unmatched), unmatchedCount.toString(), Modifier.weight(1f))
                CompactMetric(stringResource(R.string.manual), manualCount.toString(), Modifier.weight(1f))
                CompactMetric(stringResource(R.string.skipped_count), readiness.skipped.toString(), Modifier.weight(1f))
            }
            if (unmatchedCount > 0) {
                Spacer(Modifier.height(12.dp))
                StatusPill(stringResource(R.string.photos_need_attention, unmatchedCount), PillTone.Warning)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoBatchSheet(
    matches: List<PhotoMatchResult>,
    highlightedPhotoIndex: Int?,
    onSetManualLocation: (Int) -> Unit,
    onClearManualLocation: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var selectedFilter by remember(matches) { mutableStateOf(PhotoBatchFilter.All) }
        val visibleMatches = remember(matches, selectedFilter) {
            matches.withIndex()
                .filter { (_, result) -> photoBatchFilterMatches(selectedFilter, result) }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            val manualCount = matches.count { it.photo.manualLocation != null }
            val matchedCount = matches.count { it.selectedPosition != null }
            val unmatchedCount = matches.size - matchedCount
            DrawerHeader(
                title = stringResource(R.string.photo_batch_count, matches.size),
                subtitle = stringResource(R.string.photo_batch_stats, matchedCount, unmatchedCount, manualCount),
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(14.dp))
            if (matches.isNotEmpty()) {
                PhotoBatchFilterRow(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (matches.isEmpty()) {
                EmptyStateBlock(
                    title = stringResource(R.string.no_photos),
                    body = stringResource(R.string.photo_input_help),
                )
            } else if (visibleMatches.isEmpty()) {
                EmptyStateBlock(
                    title = stringResource(R.string.photo_filter_empty_title),
                    body = stringResource(R.string.photo_filter_empty_body),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 470.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(visibleMatches) { _, indexedResult ->
                        val index = indexedResult.index
                        val result = indexedResult.value
                        PhotoMatchRow(
                            index = index,
                            result = result,
                            highlighted = index == highlightedPhotoIndex,
                            onSetManualLocation = { onSetManualLocation(index) },
                            onClearManualLocation = { onClearManualLocation(index) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoBatchFilterRow(
    selectedFilter: PhotoBatchFilter,
    onFilterSelected: (PhotoBatchFilter) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhotoBatchFilter.entries.forEach { filter ->
            PhotoBatchFilterChip(
                label = photoBatchFilterLabel(filter),
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun PhotoBatchFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(TrackShape.pill)
            .clickable(onClick = onClick),
        shape = TrackShape.pill,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WriteActionPanel(
    settings: AppSettings,
    readiness: WriteReadiness,
    bulkOperation: BulkOperation?,
    onWriteDefault: () -> Unit,
) {
    val preferCopies = settings.preferExportCopies
    val modeTone = if (preferCopies) PillTone.Success else PillTone.Error
    val modeLabel = stringResource(if (preferCopies) R.string.safe_default else R.string.dangerous_action)
    SurfaceCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (preferCopies) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.write_area_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusPill(modeLabel, modeTone)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = if (readiness.writeable == 0) {
                stringResource(R.string.write_no_writeable)
            } else {
                stringResource(R.string.write_readiness, readiness.writeable, readiness.skipped)
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
        if (!preferCopies) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = stringResource(R.string.write_originals_default_desc),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (preferCopies) {
            PrimaryActionButton(
                text = stringResource(R.string.write_copies),
                onClick = onWriteDefault,
                modifier = Modifier.fillMaxWidth(),
                enabled = bulkOperation == null,
            )
        } else {
            DangerActionButton(
                text = stringResource(R.string.write_originals),
                onClick = onWriteDefault,
                modifier = Modifier.fillMaxWidth(),
                enabled = bulkOperation == null,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoMatchRow(
    index: Int,
    result: PhotoMatchResult,
    highlighted: Boolean,
    onSetManualLocation: () -> Unit,
    onClearManualLocation: () -> Unit,
) {
    val photo = result.photo
    val match = result.match
    SurfaceCard(
        containerColor = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PhotoThumbnail(photo.uri)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. ${photo.displayName}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MatchPill(photo, match)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.captured)}: ${photo.capturedAt ?: stringResource(R.string.no_exif_time)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = matchDetail(photo, match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                ActionRow {
                    SoftActionButton(
                        text = stringResource(R.string.set_location),
                        onClick = onSetManualLocation,
                        minHeight = 44.dp,
                    )
                    if (photo.manualLocation != null) {
                        SoftActionButton(
                            text = stringResource(R.string.clear),
                            onClick = onClearManualLocation,
                            danger = true,
                            minHeight = 44.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(uri: Uri) {
    val context = LocalContext.current
    val targetSizePx = with(LocalDensity.current) { 70.dp.roundToPx() }
    var bitmap by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                decodeSampledThumbnail(context.contentResolver, uri, targetSizePx)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            TrackWriteLineIcon(
                icon = TrackWriteIcon.Photo,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun decodeSampledThumbnail(
    resolver: ContentResolver,
    uri: Uri,
    targetSizePx: Int,
): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    val sampleSize = calculateInSampleSize(bounds, targetSizePx, targetSizePx)
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)?.asImageBitmap()
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    targetWidth: Int,
    targetHeight: Int,
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var sampleSize = 1
    if (height > targetHeight || width > targetWidth) {
        var halfHeight = height / 2
        var halfWidth = width / 2
        while (halfHeight / sampleSize >= targetHeight && halfWidth / sampleSize >= targetWidth) {
            sampleSize *= 2
        }
    }
    return sampleSize.coerceAtLeast(1)
}

@Composable
private fun MatchPill(photo: PhotoCandidate, match: PhotoMatch?) {
    when {
        photo.manualLocation != null -> StatusPill(stringResource(R.string.manual), PillTone.Warning)
        match is PhotoMatch.Matched -> StatusPill(stringResource(R.string.matched), PillTone.Success)
        match is PhotoMatch.Unmatched -> StatusPill(stringResource(R.string.unmatched), PillTone.Warning)
        else -> StatusPill(stringResource(R.string.no_track), PillTone.Neutral)
    }
}

@Composable
private fun BulkOperationPanel(operation: BulkOperation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = bulkOperationLabel(operation),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun bulkOperationLabel(operation: BulkOperation): String =
    when (operation) {
        BulkOperation.LoadingPhotos -> stringResource(R.string.loading_photos)
        BulkOperation.LoadingFolderPhotos -> stringResource(R.string.loading_folder_photos)
        BulkOperation.WritingCopies -> stringResource(R.string.writing_copies_progress)
        BulkOperation.WritingOriginals -> stringResource(R.string.writing_originals_progress)
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(TrackShape.control)
            .clickable(enabled = enabled, onClick = onClick),
        shape = TrackShape.control,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else TrackAlpha.disabled),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DangerActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .widthIn(min = 72.dp)
            .heightIn(min = 44.dp)
            .clip(TrackShape.control)
            .clickable(enabled = enabled, onClick = onClick),
        shape = TrackShape.control,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (enabled) 1f else TrackAlpha.disabled),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = if (enabled) 1f else TrackAlpha.disabled),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SoftActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    icon: TrackWriteIcon? = null,
    minHeight: Dp = 44.dp,
    enabled: Boolean = true,
) {
    val contentColor = if (danger) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val containerColor = if (danger) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (danger) {
        MaterialTheme.colorScheme.error.copy(alpha = TrackAlpha.subtle)
    } else {
        MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = modifier
            .height(minHeight)
            .clip(TrackShape.control)
            .clickable(enabled = enabled, onClick = onClick),
        shape = TrackShape.control,
        color = containerColor.copy(alpha = if (enabled) 1f else TrackAlpha.disabled),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = if (enabled) 1f else TrackAlpha.disabled)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                TrackWriteLineIcon(
                    icon = icon,
                    tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = if (enabled) 1f else TrackAlpha.disabled),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SystemToastSnackbar(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 3.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SurfaceCard(
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    borderColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackShape.card,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = TrackAlpha.faint)),
    ) {
        Column(
            modifier = Modifier.padding(TrackSpacing.x5),
            content = content,
        )
    }
}

@Composable
private fun EmptyStateBlock(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = TrackShape.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TrackWriteLineIcon(
                icon = TrackWriteIcon.Empty,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MetricGrid(metrics: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (metrics.isNotEmpty()) {
            val (label, value) = metrics.first()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        val remaining = metrics.drop(1)
        if (remaining.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                remaining.forEach { (label, value) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 72.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private enum class PillTone {
    Success,
    Warning,
    Neutral,
    Error,
}

@Composable
private fun StatusPill(label: String, tone: PillTone) {
    val colors = when (tone) {
        PillTone.Success -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PillTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PillTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        PillTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = colors.first) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(TrackShape.pill)
                    .background(colors.second),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.second,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    installedVersion: InstalledAppVersion,
    updateState: AppUpdateUiState,
    updateDialogCandidate: UpdateCandidate?,
    onSettingsChanged: (AppSettings) -> Unit,
    onChooseExportFolder: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismissUpdateDialog: () -> Unit,
) {
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showFrequencySheet by remember { mutableStateOf(false) }
    val settingsGroupColor = MaterialTheme.colorScheme.surfaceContainerLow

    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column {
                SettingsSectionHeader(stringResource(R.string.general_settings))
                SettingsGroup(containerColor = settingsGroupColor) {
                    SettingNavigationRow(
                        title = stringResource(R.string.appearance),
                        value = appearanceLabel(settings.appearance),
                        onClick = { showAppearanceSheet = true },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    SettingNavigationRow(
                        title = stringResource(R.string.recording_frequency),
                        value = frequencyLabel(settings.recordingFrequency),
                        subtitle = frequencyDescription(settings.recordingFrequency),
                        onClick = { showFrequencySheet = true },
                    )
                }
            }
        }
        item {
            Column {
                SettingsSectionHeader(stringResource(R.string.photo_match_settings))
                SettingsGroup(containerColor = settingsGroupColor) {
                    SettingStepper(
                        title = stringResource(R.string.camera_offset_minutes),
                        value = settings.cameraOffset.toMinutes(),
                        range = AppSettingsStore.MIN_CAMERA_OFFSET_MINUTES..AppSettingsStore.MAX_CAMERA_OFFSET_MINUTES,
                        unit = stringResource(R.string.unit_minutes),
                        onValueChange = { onSettingsChanged(settings.copy(cameraOffset = Duration.ofMinutes(it))) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingStepper(
                        title = stringResource(R.string.max_time_difference_minutes),
                        value = settings.maxPhotoTimeDifference.toMinutes(),
                        range = AppSettingsStore.MIN_PHOTO_TIME_DIFFERENCE_MINUTES..AppSettingsStore.MAX_PHOTO_TIME_DIFFERENCE_MINUTES,
                        unit = stringResource(R.string.unit_minutes),
                        onValueChange = { onSettingsChanged(settings.copy(maxPhotoTimeDifference = Duration.ofMinutes(it))) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.allow_start_fallback),
                        checked = settings.allowStartFallback,
                        onCheckedChange = { onSettingsChanged(settings.copy(allowStartFallback = it)) },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.allow_end_fallback),
                        checked = settings.allowEndFallback,
                        onCheckedChange = { onSettingsChanged(settings.copy(allowEndFallback = it)) },
                    )
                }
            }
        }
        item {
            Column {
                SettingsSectionHeader(stringResource(R.string.export_settings))
                SettingsGroup(containerColor = settingsGroupColor) {
                    ExportModeSelector(
                        preferCopies = settings.preferExportCopies,
                        onSelectCopies = { onSettingsChanged(settings.copy(preferExportCopies = true)) },
                        onSelectOriginals = { onSettingsChanged(settings.copy(preferExportCopies = false)) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    ExportFolderRow(
                        folderUri = settings.defaultExportFolderUri,
                        onChooseExportFolder = onChooseExportFolder,
                    )
                }
            }
        }
        item {
            Column {
                SettingsSectionHeader(stringResource(R.string.about))
                SettingsGroup(containerColor = settingsGroupColor) {
                    UpdateSettingsRow(
                        installedVersion = installedVersion,
                        updateState = updateState,
                        onCheckForUpdates = onCheckForUpdates,
                    )
                }
            }
        }
    }

    if (showAppearanceSheet) {
        SettingChoiceSheet(
            title = stringResource(R.string.appearance),
            onDismiss = { showAppearanceSheet = false },
        ) {
            AppearanceMode.entries.forEach { mode ->
                SettingChoiceRow(
                    title = appearanceLabel(mode),
                    selected = settings.appearance == mode,
                    onClick = {
                        onSettingsChanged(settings.copy(appearance = mode))
                        showAppearanceSheet = false
                    },
                )
            }
        }
    }

    if (showFrequencySheet) {
        SettingChoiceSheet(
            title = stringResource(R.string.recording_frequency),
            onDismiss = { showFrequencySheet = false },
        ) {
            RecordingFrequency.entries.forEach { freq ->
                SettingChoiceRow(
                    title = frequencyLabel(freq),
                    subtitle = frequencyDescription(freq),
                    selected = settings.recordingFrequency == freq,
                    onClick = {
                        onSettingsChanged(settings.copy(recordingFrequency = freq))
                        showFrequencySheet = false
                    },
                )
            }
        }
    }

    updateDialogCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = onDismissUpdateDialog,
            title = { Text(stringResource(R.string.update_available_dialog_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.update_available_dialog_body,
                        installedVersion.versionName,
                        installedVersion.versionCode,
                        candidate.metadata.versionName,
                        candidate.metadata.versionCode,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = candidate.releasePageUrl != null,
                    onClick = {
                        val releasePageUrl = candidate.releasePageUrl ?: return@TextButton
                        onDismissUpdateDialog()
                        onOpenReleasePage(releasePageUrl)
                    },
                ) {
                    Text(stringResource(R.string.open_release_page))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdateDialog) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )
    }
}

@Composable
private fun UpdateSettingsRow(
    installedVersion: InstalledAppVersion,
    updateState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !updateState.isBusy, onClick = onCheckForUpdates)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.check_for_updates),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = updateStatusText(updateState, installedVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = if (updateState is AppUpdateUiState.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = updateActionText(updateState),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when (updateState) {
                AppUpdateUiState.Checking,
                is AppUpdateUiState.UpToDate -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun updateStatusText(updateState: AppUpdateUiState, installedVersion: InstalledAppVersion): String =
    when (updateState) {
        AppUpdateUiState.Idle -> stringResource(
            R.string.update_status_idle,
            installedVersion.versionName,
            installedVersion.versionCode,
        )
        AppUpdateUiState.Checking -> stringResource(R.string.update_status_checking)
        is AppUpdateUiState.UpToDate -> stringResource(
            R.string.update_status_up_to_date,
            updateState.installedVersion.versionName,
        )
        is AppUpdateUiState.Available -> stringResource(
            R.string.update_status_available,
            updateState.candidate.metadata.versionName,
            updateState.candidate.metadata.versionCode,
        )
        is AppUpdateUiState.Error -> updateState.message
    }

@Composable
private fun updateActionText(updateState: AppUpdateUiState): String =
    when (updateState) {
        AppUpdateUiState.Idle -> stringResource(R.string.update_action_check)
        AppUpdateUiState.Checking -> stringResource(R.string.update_action_checking)
        is AppUpdateUiState.UpToDate -> stringResource(R.string.update_action_up_to_date)
        is AppUpdateUiState.Available -> stringResource(R.string.update_action_available)
        is AppUpdateUiState.Error -> stringResource(R.string.update_action_retry)
    }

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingsGroup(
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackShape.card,
        color = containerColor,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingNavigationRow(
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TrackAlpha.faint),
                modifier = Modifier.size(18.dp),
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingChoiceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackShape.card)
            .clickable(onClick = onClick),
        shape = TrackShape.card,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier.size(20.dp),
                shape = TrackShape.control,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingChoiceSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 20.dp),
        ) {
            DrawerHeader(
                title = title,
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MockupSwitch(checked = checked)
    }
}

@Composable
private fun MockupSwitch(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(TrackShape.control)
            .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
    ) {
        Surface(
            modifier = Modifier
                .size(20.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
            shape = TrackShape.control,
            color = MaterialTheme.colorScheme.surface,
        ) {}
    }
}

@Composable
private fun ExportModeSelector(
    preferCopies: Boolean,
    onSelectCopies: () -> Unit,
    onSelectOriginals: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.prefer_export_copies),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TrackShape.control)
                .clip(TrackShape.control),
        ) {
            SegmentOption(
                text = stringResource(R.string.write_copies),
                selected = preferCopies,
                onClick = onSelectCopies,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            SegmentOption(
                text = stringResource(R.string.write_originals),
                selected = !preferCopies,
                onClick = onSelectOriginals,
                modifier = Modifier.weight(1f),
            )
        }
        if (!preferCopies) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = stringResource(R.string.write_originals_default_desc),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun SegmentOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExportFolderRow(
    folderUri: String?,
    onChooseExportFolder: () -> Unit,
) {
    val folderLabel = remember(folderUri) { folderUri?.let(::displayTreeUri) }
    val subtitle = folderLabel ?: stringResource(R.string.export_folder_unconfigured)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChooseExportFolder)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.default_export_folder),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (folderLabel == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(R.string.choose_folder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TrackAlpha.faint),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun SettingStepper(
    title: String,
    value: Long,
    range: LongRange,
    unit: String,
    onValueChange: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = TrackShape.control,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.decrease_setting, title),
                    enabled = value > range.first,
                    onClick = { onValueChange((value - 1).coerceIn(range.first, range.last)) },
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Text(
                    text = "$value $unit",
                    modifier = Modifier
                        .widthIn(min = 60.dp)
                        .padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                StepperButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.increase_setting, title),
                    enabled = value < range.last,
                    onClick = { onValueChange((value + 1).coerceIn(range.first, range.last)) },
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else TrackAlpha.disabled),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun StartRecordingDialog(
    initialName: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (initialName == null) return
    val focusRequester = remember { FocusRequester() }
    var name by remember(initialName) {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(0, initialName.length),
            ),
        )
    }
    LaunchedEffect(initialName) {
        focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.start_recording)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.track_name)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text) }) {
                Text(stringResource(R.string.start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RenameTrackDialog(
    state: RenameDialogState?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    if (state == null) return
    var name by remember(state) { mutableStateOf(state.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.track_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.trackId, name) }) {
                Text(stringResource(R.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteTrackDialog(
    track: Track?,
    onDismiss: () -> Unit,
    onConfirm: (Track) -> Unit,
) {
    if (track == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(track.name) },
        confirmButton = {
            TextButton(onClick = { onConfirm(track) }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun StopRecordingDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stop_recording_title)) },
        text = { Text(stringResource(R.string.stop_recording_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.stop_recording), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun WriteOriginalsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.write_originals_title)) },
        text = { Text(stringResource(R.string.write_originals_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.write), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun WriteProgressDialog(operation: BulkOperation?, progress: WriteProgressState?) {
    val writeOperation = when (operation) {
        BulkOperation.WritingCopies,
        BulkOperation.WritingOriginals,
        -> operation
        else -> return
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(bulkOperationLabel(writeOperation)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.write_progress_wait),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress?.fraction ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = progress?.let {
                            stringResource(R.string.write_progress_count, it.processed, it.total)
                        }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.write_progress_percent, progress?.percent ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteResultSheet(
    result: WriteResultState?,
    onDismiss: () -> Unit,
) {
    if (result == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (result.mode == WriteMode.Copies) R.string.write_copies_result else R.string.write_originals_result,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            MetricGrid(
                listOf(
                    stringResource(R.string.written_count) to result.written.toString(),
                    stringResource(R.string.skipped_count) to result.skipped.toString(),
                    stringResource(R.string.failed_count) to result.failed.size.toString(),
                ),
            )
            if (result.failed.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.failed_items),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                result.failed.forEach { item ->
                    Text(
                        text = "${item.fileName}: ${item.reason.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            PrimaryActionButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun appearanceLabel(mode: AppearanceMode): String =
    when (mode) {
        AppearanceMode.System -> stringResource(R.string.appearance_system)
        AppearanceMode.Light -> stringResource(R.string.appearance_light)
        AppearanceMode.Dark -> stringResource(R.string.appearance_dark)
    }

@Composable
private fun frequencyLabel(frequency: RecordingFrequency): String =
    when (frequency) {
        RecordingFrequency.Efficient -> stringResource(R.string.frequency_efficient)
        RecordingFrequency.Balanced -> stringResource(R.string.frequency_balanced)
        RecordingFrequency.Precise -> stringResource(R.string.frequency_precise)
    }

@Composable
private fun frequencyDescription(frequency: RecordingFrequency): String =
    when (frequency) {
        RecordingFrequency.Efficient -> stringResource(R.string.frequency_efficient_desc)
        RecordingFrequency.Balanced -> stringResource(R.string.frequency_balanced_desc)
        RecordingFrequency.Precise -> stringResource(R.string.frequency_precise_desc)
    }

@Composable
private fun recordingProofTitle(
    recording: RecordingSnapshot,
    settings: AppSettings,
    nowMillis: Long,
): String =
    when {
        recording.issue == RecordingIssue.PermissionMissing -> stringResource(R.string.recording_unavailable_title)
        recording.issue == RecordingIssue.LocationDisabled -> stringResource(R.string.recording_unavailable_title)
        recording.status == RecordingStatus.Paused -> stringResource(R.string.recording_paused_title)
        recording.status == RecordingStatus.Stopped -> stringResource(R.string.recording_start_title)
        recording.lastPointRecordedAtMillis == null -> stringResource(R.string.recording_waiting_title)
        isRecordingStale(recording, settings, nowMillis) -> stringResource(R.string.recording_risk_title)
        else -> stringResource(R.string.recording_active_title)
    }

@Composable
private fun recordingEvidenceTitle(recording: RecordingSnapshot, nowMillis: Long): String =
    when {
        recording.issue == RecordingIssue.PermissionMissing -> stringResource(R.string.recording_unavailable_title)
        recording.issue == RecordingIssue.LocationDisabled -> stringResource(R.string.recording_unavailable_title)
        recording.status == RecordingStatus.Paused -> stringResource(R.string.recording_no_new_points)
        recording.lastPointRecordedAtMillis == null -> stringResource(R.string.recording_no_points)
        else -> stringResource(R.string.recording_evidence_recent, lastPointAgeSeconds(recording, nowMillis) ?: 0L)
    }

private fun recordingEvidenceIcon(recording: RecordingSnapshot): TrackWriteIcon =
    when {
        recording.issue == RecordingIssue.PermissionMissing -> TrackWriteIcon.Lock
        recording.issue == RecordingIssue.LocationDisabled -> TrackWriteIcon.Warning
        recording.status == RecordingStatus.Paused -> TrackWriteIcon.Pause
        recording.status == RecordingStatus.Recording && recording.lastPointRecordedAtMillis == null -> TrackWriteIcon.Satellite
        recording.lastPointRecordedAtMillis == null -> TrackWriteIcon.Target
        else -> TrackWriteIcon.Target
    }

@Composable
private fun statusLabel(status: RecordingStatus): String =
    when (status) {
        RecordingStatus.Recording -> stringResource(R.string.status_recording)
        RecordingStatus.Paused -> stringResource(R.string.status_paused)
        RecordingStatus.Stopped -> stringResource(R.string.status_stopped)
    }

private fun recordingConfidenceTone(recording: RecordingSnapshot): PillTone =
    when {
        recording.issue == RecordingIssue.PermissionMissing -> PillTone.Error
        recording.issue == RecordingIssue.LocationDisabled -> PillTone.Error
        recording.status == RecordingStatus.Paused -> PillTone.Warning
        recording.status == RecordingStatus.Recording && recording.issue == RecordingIssue.WaitingForFix -> PillTone.Warning
        recording.status == RecordingStatus.Recording && recording.lastPointRecordedAtMillis != null -> PillTone.Success
        else -> PillTone.Neutral
    }

private fun recordingProofTone(
    recording: RecordingSnapshot,
    settings: AppSettings,
    nowMillis: Long,
): PillTone =
    when {
        recording.issue == RecordingIssue.PermissionMissing -> PillTone.Error
        recording.issue == RecordingIssue.LocationDisabled -> PillTone.Error
        recording.status == RecordingStatus.Paused -> PillTone.Warning
        recording.status == RecordingStatus.Recording && recording.issue == RecordingIssue.WaitingForFix -> PillTone.Warning
        recording.status == RecordingStatus.Recording && isRecordingStale(recording, settings, nowMillis) -> PillTone.Warning
        recording.status == RecordingStatus.Recording && recording.lastPointRecordedAtMillis != null -> PillTone.Success
        else -> PillTone.Neutral
    }

@Composable
private fun toneBorderColor(tone: PillTone): Color =
    when (tone) {
        PillTone.Success -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        PillTone.Warning -> MaterialTheme.colorScheme.tertiary.copy(alpha = TrackAlpha.subtle)
        PillTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = TrackAlpha.subtle)
        PillTone.Neutral -> MaterialTheme.colorScheme.outlineVariant
    }

@Composable
private fun recordingToneColor(tone: PillTone): Color =
    when (tone) {
        PillTone.Success -> MaterialTheme.colorScheme.primary
        PillTone.Warning -> MaterialTheme.colorScheme.tertiary
        PillTone.Error -> MaterialTheme.colorScheme.error
        PillTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun activeRecordingDuration(state: MainUiState, track: Track?): Duration? {
    if (state.recording.status != RecordingStatus.Recording) return null
    if (state.recording.trackId == null || state.recording.trackId != track?.id) return null
    val startTime = track.startTime ?: return Duration.ZERO
    val elapsed = Duration.between(startTime, Instant.ofEpochMilli(state.recordingClockMillis))
    return if (elapsed.isNegative) Duration.ZERO else elapsed
}

@Composable
private fun recordingConfidenceText(recording: RecordingSnapshot, settings: AppSettings): String {
    val provider = recording.provider?.uppercase(Locale.ROOT) ?: stringResource(R.string.provider_unknown)
    return when {
        recording.status == RecordingStatus.Stopped -> stringResource(R.string.record_confidence_stopped)
        recording.status == RecordingStatus.Paused -> stringResource(R.string.record_confidence_paused)
        recording.issue == RecordingIssue.PermissionMissing -> stringResource(R.string.record_confidence_permission)
        recording.issue == RecordingIssue.LocationDisabled -> stringResource(R.string.record_confidence_location_disabled)
        recording.lastPointRecordedAtMillis == null -> {
            stringResource(R.string.record_confidence_waiting, provider)
        }
        else -> {
            val ageSeconds = ((System.currentTimeMillis() - recording.lastPointRecordedAtMillis) / 1000L).coerceAtLeast(0L)
            if (ageSeconds > staleRecordingThresholdSeconds(settings)) {
                stringResource(R.string.record_confidence_stale, ageSeconds, provider)
            } else {
                stringResource(R.string.record_confidence_recent, ageSeconds, provider)
            }
        }
    }
}

private fun staleRecordingThresholdSeconds(settings: AppSettings): Long =
    ((settings.recordingFrequency.intervalMs * 3L) / 1000L).coerceAtLeast(10L)

private fun lastPointAgeSeconds(recording: RecordingSnapshot, nowMillis: Long): Long? =
    recording.lastPointRecordedAtMillis?.let { lastPointMillis ->
        ((nowMillis - lastPointMillis) / 1000L).coerceAtLeast(0L)
    }

private fun isRecordingStale(
    recording: RecordingSnapshot,
    settings: AppSettings,
    nowMillis: Long,
): Boolean {
    val lastPointMillis = recording.lastPointRecordedAtMillis ?: return false
    val ageSeconds = ((nowMillis - lastPointMillis) / 1000L).coerceAtLeast(0L)
    return ageSeconds > staleRecordingThresholdSeconds(settings)
}

@Composable
private fun matchDetail(photo: PhotoCandidate, match: PhotoMatch?): String =
    when {
        photo.manualLocation != null -> {
            "${stringResource(R.string.location_prefix)}: ${"%.6f".format(photo.manualLocation.latitude)}, ${"%.6f".format(photo.manualLocation.longitude)}"
        }
        match is PhotoMatch.Matched -> {
            "${sourceLabel(match.source)} · ${"%.6f".format(match.position.latitude)}, ${"%.6f".format(match.position.longitude)}"
        }
        match is PhotoMatch.Unmatched -> {
            "${stringResource(R.string.reason_prefix)}: ${match.reason}"
        }
        else -> stringResource(R.string.no_selected_track_detail)
    }

@Composable
private fun photoBatchFilterLabel(filter: PhotoBatchFilter): String =
    when (filter) {
        PhotoBatchFilter.All -> stringResource(R.string.photo_filter_all)
        PhotoBatchFilter.Unmatched -> stringResource(R.string.photo_filter_unmatched)
        PhotoBatchFilter.Manual -> stringResource(R.string.photo_filter_manual)
        PhotoBatchFilter.Writeable -> stringResource(R.string.photo_filter_writeable)
    }

private fun photoBatchFilterMatches(filter: PhotoBatchFilter, result: PhotoMatchResult): Boolean =
    when (filter) {
        PhotoBatchFilter.All -> true
        PhotoBatchFilter.Unmatched -> result.selectedPosition == null
        PhotoBatchFilter.Manual -> result.photo.manualLocation != null
        PhotoBatchFilter.Writeable -> result.selectedPosition != null
    }

@Composable
private fun sourceLabel(source: MatchSource): String =
    when (source) {
        MatchSource.ExactTrackPoint -> stringResource(R.string.source_exact_point)
        MatchSource.InterpolatedTrackSegment -> stringResource(R.string.source_interpolated)
        MatchSource.StartFallback -> stringResource(R.string.source_start_fallback)
        MatchSource.EndFallback -> stringResource(R.string.source_end_fallback)
    }

private fun writeReadiness(matches: List<PhotoMatchResult>): WriteReadiness =
    WriteReadiness(
        writeable = matches.count { it.selectedPosition != null },
        skipped = matches.count { it.selectedPosition == null },
    )

private fun displayTreeUri(value: String): String =
    runCatching {
        val uri = Uri.parse(value)
        (uri.pathSegments.dropWhile { it != "tree" }.drop(1).firstOrNull() ?: uri.lastPathSegment ?: value)
            .removePrefix("primary:")
            .replace(':', '/')
            .ifBlank { uri.lastPathSegment ?: value }
    }.getOrDefault(value)

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val seconds = duration.toSecondsPart()
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatDistance(distanceMeters: Double): String =
    if (distanceMeters >= 1000.0) {
        "%.2f km".format(distanceMeters / 1000.0)
    } else {
        "%.0f m".format(distanceMeters)
    }
