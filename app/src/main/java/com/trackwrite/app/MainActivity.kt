package com.trackwrite.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.trackwrite.app.ui.TrackWriteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var repository: TrackRepository
    private lateinit var stateStore: RecordingStateStore
    private lateinit var geotagging: PhotoGeotagging
    private lateinit var gpx: GpxFileActions
    private lateinit var settingsStore: AppSettingsStore

    private var recordTrackId: String? = null
    private var matchTrackId: String? = null
    private var selectedPhotos: List<PhotoCandidate> = emptyList()
    private var matchResults: List<PhotoMatchResult> = emptyList()
    private var pendingManualPhotoIndex: Int? = null
    private var pendingExportMode: ExportFolderMode? = null
    private var uiState by mutableStateOf(MainUiState())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh() }

    private val gpxImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importGpx(uri)
    }

    private val gpxExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        if (uri != null) exportSelectedTrack(uri)
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        persistPhotoPermissions(uris)
        selectedPhotos = geotagging.loadPhotos(uris)
        uiState = uiState.copy(selectedTab = MainTab.Match)
        matchSelectedPhotos()
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            selectedPhotos = geotagging.loadPhotosFromFolder(uri)
            uiState = uiState.copy(selectedTab = MainTab.Match)
            matchSelectedPhotos()
        }
    }

    private val exportFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val mode = pendingExportMode
        pendingExportMode = null
        if (uri != null && mode != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            settingsStore.setDefaultExportFolderUri(uri.toString())
            when (mode) {
                ExportFolderMode.SaveDefault -> {
                    uiState = uiState.copy(
                        settings = settingsStore.current(),
                        logMessage = getString(R.string.export_folder_saved),
                    )
                }
                ExportFolderMode.WriteCopies -> writeCopies(uri)
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
            log("Manual location result was invalid.")
            return@registerForActivityResult
        }
        val point = runCatching { GeoPoint(latitude, longitude) }.getOrElse {
            log(it.message ?: "Manual location result was out of range.")
            return@registerForActivityResult
        }
        selectedPhotos = selectedPhotos.mapIndexed { photoIndex, photo ->
            if (photoIndex == index) photo.copy(manualLocation = point) else photo
        }
        log("Manual location bound to photo ${index + 1}${if (label.isBlank()) "" else ": $label"}")
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
                    onTabSelected = { uiState = uiState.copy(selectedTab = it) },
                    onSettings = { uiState = uiState.copy(showSettings = true) },
                    onCloseSettings = { uiState = uiState.copy(showSettings = false) },
                    onStartRecording = { uiState = uiState.copy(startDialogName = defaultTrackName()) },
                    onPause = { command(TrackingService.ACTION_PAUSE) },
                    onResume = { requestRecordingPermissionsThen { command(TrackingService.ACTION_RESUME) } },
                    onStop = { command(TrackingService.ACTION_STOP) },
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
                        pendingManualPhotoIndex = index
                        manualLocationLauncher.launch(Intent(this, ManualLocationActivity::class.java))
                    },
                    onClearManualLocation = { index ->
                        selectedPhotos = selectedPhotos.mapIndexed { photoIndex, photo ->
                            if (photoIndex == index) photo.copy(manualLocation = null) else photo
                        }
                        log("Manual location cleared for photo ${index + 1}.")
                        matchSelectedPhotos()
                    },
                    onWriteDefault = { writeDefault() },
                    onSettingsChanged = { newSettings ->
                        persistSettings(newSettings)
                    },
                    onChooseExportFolder = {
                        pendingExportMode = ExportFolderMode.SaveDefault
                        exportFolderLauncher.launch(null)
                    },
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
                        repository.deleteTrack(track.id)
                        if (recordTrackId == track.id) recordTrackId = null
                        if (matchTrackId == track.id) matchTrackId = null
                        uiState = uiState.copy(deleteDialog = null)
                        refresh()
                    },
                    onConfirmWrite = {
                        uiState = uiState.copy(showWriteDialog = false)
                        writeOriginals()
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
        )
    }

    private fun persistSettings(settings: AppSettings) {
        settingsStore.setAppearance(settings.appearance)
        settingsStore.setRecordingFrequency(settings.recordingFrequency)
        settingsStore.setCameraOffset(settings.cameraOffset)
        settingsStore.setMaxPhotoTimeDifference(settings.maxPhotoTimeDifference)
        settingsStore.setAllowStartFallback(settings.allowStartFallback)
        settingsStore.setAllowEndFallback(settings.allowEndFallback)
        settingsStore.setPreferExportCopies(settings.preferExportCopies)
        uiState = uiState.copy(settings = settings, logMessage = getString(R.string.settings_saved))
        matchSelectedPhotos()
    }

    private fun dismissDialogs() {
        uiState = uiState.copy(
            startDialogName = null,
            renameDialog = null,
            deleteDialog = null,
            showWriteDialog = false,
        )
    }

    private fun defaultTrackName(): String =
        "${getString(R.string.track_name_default)} ${Instant.now()}"

    private fun command(action: String, name: String? = null) {
        val stoppedTrackId = if (action == TrackingService.ACTION_STOP) stateStore.current().trackId else null
        ContextCompat.startForegroundService(this, TrackingService.command(this, action, name))
        window.decorView.postDelayed({
            if (action == TrackingService.ACTION_STOP && matchTrackId == null) {
                matchTrackId = stoppedTrackId?.takeIf { repository.getTrack(it) != null }
            }
            refresh()
        }, 500)
    }

    private fun importGpx(uri: Uri) {
        runCatching {
            val xml = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read GPX")
            val track = gpx.importTrack(UUID.randomUUID().toString(), xml)
            repository.saveTrack(track)
            matchTrackId = track.id
            log("Imported ${track.name}")
            refresh()
        }.onFailure { log("Import failed: ${it.message}") }
    }

    private fun promptExport() {
        val track = recordTrack() ?: return log(getString(R.string.select_track_first))
        gpxExportLauncher.launch("${track.name}.gpx")
    }

    private fun exportSelectedTrack(uri: Uri) {
        val track = recordTrack() ?: return
        contentResolver.openOutputStream(uri, "w").use { output ->
            requireNotNull(output).write(gpx.encode(track).toByteArray())
        }
        log("Exported ${track.name}")
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
        val outcomes = geotagging.exportCopies(matchResults, uri)
        uiState = uiState.copy(writeResult = WriteResultState.from(outcomes, WriteMode.Copies))
        refresh()
    }

    private fun writeOriginals() {
        val outcomes = geotagging.writeInPlace(matchResults)
        uiState = uiState.copy(writeResult = WriteResultState.from(outcomes, WriteMode.Originals))
        refresh()
    }

    private fun isUsableTreeUri(uri: Uri): Boolean =
        DocumentFile.fromTreeUri(this, uri)?.canWrite() == true

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
            permissionLauncher.launch(permissions.toTypedArray())
        }
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
    val logMessage: String = "",
    val showTrackHistorySheet: Boolean = false,
    val showTrackSourceSheet: Boolean = false,
    val showPhotoBatchSheet: Boolean = false,
    val highlightedPhotoIndex: Int? = null,
    val writeResult: WriteResultState? = null,
    val startDialogName: String? = null,
    val renameDialog: RenameDialogState? = null,
    val deleteDialog: Track? = null,
    val showWriteDialog: Boolean = false,
)

private data class RenameDialogState(
    val trackId: String,
    val name: String,
)

private data class WriteReadiness(
    val writeable: Int,
    val skipped: Int,
)

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

private fun AppSettings.toMatchOptions(): MatchOptions =
    MatchOptions(
        cameraOffset = cameraOffset,
        maxTimeDifference = maxPhotoTimeDifference,
        allowStartFallback = allowStartFallback,
        allowEndFallback = allowEndFallback,
    )

@OptIn(ExperimentalMaterial3Api::class)
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
    onDismissDialog: () -> Unit,
    onDismissWriteResult: () -> Unit,
    onConfirmStart: (String) -> Unit,
    onConfirmRename: (String, String) -> Unit,
    onConfirmDelete: (Track) -> Unit,
    onConfirmWrite: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                when {
                                    state.showSettings -> R.string.settings
                                    state.selectedTab == MainTab.Record -> R.string.tab_record
                                    else -> R.string.tab_match
                                },
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    if (state.showSettings) {
                        TextButton(onClick = onCloseSettings) {
                            Text(stringResource(R.string.back))
                        }
                    } else {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (!state.showSettings) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = state.selectedTab == MainTab.Record,
                        onClick = { onTabSelected(MainTab.Record) },
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.tab_record)) },
                        label = { Text(stringResource(R.string.tab_record)) },
                    )
                    NavigationBarItem(
                        selected = state.selectedTab == MainTab.Match,
                        onClick = { onTabSelected(MainTab.Match) },
                        icon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.tab_match)) },
                        label = { Text(stringResource(R.string.tab_match)) },
                    )
                }
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
                onSettingsChanged = onSettingsChanged,
                onChooseExportFolder = onChooseExportFolder,
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
    WriteOriginalsDialog(state.showWriteDialog, onDismissDialog, onConfirmWrite)
    WriteResultSheet(state.writeResult, onDismissWriteResult)
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
    LaunchedEffect(state.recording.status) {
        while (state.recording.status == RecordingStatus.Recording) {
            delay(2_000)
            onRefreshRecording()
        }
    }
    val activeTrack = state.recording.trackId?.let { id -> state.tracks.firstOrNull { it.id == id } }
    val selectedTrack = state.tracks.firstOrNull { it.id == state.recordTrackId }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            RecordingPanel(state, activeTrack, onStartRecording, onPause, onResume, onStop)
        }
        item {
            TrackHistoryButton(
                trackCount = state.tracks.size,
                selectedTrack = selectedTrack,
                onClick = onShowTrackHistory,
            )
        }
    }

    if (state.showTrackHistorySheet) {
        TrackHistorySheet(
            tracks = state.tracks,
            selectedTrackId = state.recordTrackId,
            onTrackSelected = onRecordTrackSelected,
            onExportGpx = onExportGpx,
            onRenameTrack = onRenameTrack,
            onDeleteTrack = onDeleteTrack,
            onDismiss = onDismissTrackHistory,
        )
    }
}

@Composable
private fun RecordingPanel(
    state: MainUiState,
    selectedTrack: Track?,
    onStartRecording: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    SectionBlock {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.record_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StatusPill(statusLabel(state.recording.status), statusTone(state.recording.status))
                Spacer(Modifier.height(10.dp))
                RecordingConfidenceLine(state.recording, state.settings)
            }
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        MetricGrid(
            listOf(
                stringResource(R.string.active_track) to (selectedTrack?.name ?: stringResource(R.string.none)),
                stringResource(R.string.points_captured) to ((selectedTrack?.points?.size ?: 0).toString()),
                stringResource(R.string.duration) to formatDuration(selectedTrack?.stats()?.duration ?: Duration.ZERO),
                stringResource(R.string.distance) to formatDistance(selectedTrack?.stats()?.distanceMeters ?: 0.0),
            ),
        )
        Spacer(Modifier.height(18.dp))
        when (state.recording.status) {
            RecordingStatus.Stopped -> {
                ActionRow {
                    PrimaryActionButton(stringResource(R.string.start_recording), Icons.Default.PlayArrow, onStartRecording)
                }
            }
            RecordingStatus.Recording -> {
                ActionRow {
                    SecondaryActionButton(stringResource(R.string.pause), Icons.Default.Pause, onPause)
                    DangerActionButton(stringResource(R.string.stop), Icons.Default.Stop, onStop)
                }
            }
            RecordingStatus.Paused -> {
                ActionRow {
                    PrimaryActionButton(stringResource(R.string.resume), Icons.Default.PlayArrow, onResume)
                    DangerActionButton(stringResource(R.string.stop), Icons.Default.Stop, onStop)
                }
            }
        }
    }
}

@Composable
private fun RecordingConfidenceLine(recording: RecordingSnapshot, settings: AppSettings) {
    val detail = recordingConfidenceText(recording, settings)
    Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = when (recordingConfidenceTone(recording)) {
            PillTone.Error -> MaterialTheme.colorScheme.error
            PillTone.Warning -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun TrackHistoryButton(
    trackCount: Int,
    selectedTrack: Track?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.track_history_count, trackCount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (selectedTrack != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = selectedTrack.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackHistorySheet(
    tracks: List<Track>,
    selectedTrackId: String?,
    onTrackSelected: (String) -> Unit,
    onExportGpx: () -> Unit,
    onRenameTrack: () -> Unit,
    onDeleteTrack: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.track_history_count, tracks.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (tracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_tracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                tracks.forEach { track ->
                    val selected = track.id == selectedTrackId
                    val stats = track.stats()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (selected) {
                                        Spacer(Modifier.width(8.dp))
                                        StatusPill(stringResource(R.string.selected), PillTone.Success)
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${track.points.size} ${stringResource(R.string.points_short)} · ${formatDuration(stats.duration)} · ${formatDistance(stats.distanceMeters)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row {
                                IconButton(onClick = { onTrackSelected(track.id); onExportGpx() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export_gpx), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onTrackSelected(track.id); onRenameTrack() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onTrackSelected(track.id); onDeleteTrack() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

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
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                TrackSourceButton(
                    selectedTrack = selectedTrack,
                    onClick = onShowTrackSource,
                )
            }
            item {
                ActionRow {
                    PrimaryActionButton(stringResource(R.string.select_photos), Icons.Default.Search, onSelectPhotos)
                    SecondaryActionButton(stringResource(R.string.select_folder), Icons.Default.Share, onSelectFolder)
                }
            }
            if (state.matches.isEmpty()) {
                item { EmptyPanel(stringResource(R.string.no_photos)) }
            } else {
                item {
                    PhotoBatchButton(
                        matches = state.matches,
                        onClick = onShowPhotoBatch,
                    )
                }
                item {
                    ReviewWritePanel(
                        settings = state.settings,
                        readiness = writeReadiness(state.matches),
                    )
                }
            }
        }
        if (state.matches.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onWriteDefault,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                shape = RoundedCornerShape(12.dp),
                containerColor = if (state.settings.preferExportCopies) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (state.settings.preferExportCopies) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(
                    if (state.settings.preferExportCopies) Icons.Default.Share else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.settings.preferExportCopies) stringResource(R.string.write_copies) else stringResource(R.string.write_originals),
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (selectedTrack != null) {
                    val stats = selectedTrack.stats()
                    Text(
                        text = selectedTrack.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${selectedTrack.points.size} ${stringResource(R.string.points_short)} · ${formatDuration(stats.duration)} · ${formatDistance(stats.distanceMeters)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.match_track_source),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.no_match_track_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.match_track_source),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (tracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_tracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                tracks.forEach { track ->
                    val selected = track.id == selectedTrackId
                    val stats = track.stats()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTrackSelected(track.id); onDismiss() },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (selected) {
                                        Spacer(Modifier.width(8.dp))
                                        StatusPill(stringResource(R.string.selected), PillTone.Success)
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${track.points.size} ${stringResource(R.string.points_short)} · ${formatDuration(stats.duration)} · ${formatDistance(stats.distanceMeters)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onImportGpx() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_gpx))
            }
            Spacer(Modifier.height(16.dp))
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.photo_batch_count, matches.size),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.photo_batch_stats, matchedCount, unmatchedCount, manualCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (unmatchedCount > 0) {
                StatusPill(stringResource(R.string.photos_need_attention, unmatchedCount), PillTone.Warning)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.photo_batch_count, matches.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(matches) { index, result ->
                    PhotoMatchRow(
                        index = index,
                        result = result,
                        highlighted = index == highlightedPhotoIndex,
                        onSetManualLocation = { onSetManualLocation(index) },
                        onClearManualLocation = { onClearManualLocation(index) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoThumbnail(photo.uri)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. ${photo.displayName}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MatchPill(photo, match)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${stringResource(R.string.captured)}: ${photo.capturedAt ?: stringResource(R.string.no_exif_time)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = matchDetail(photo, match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSetManualLocation) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.set_location))
                    }
                    if (photo.manualLocation != null) {
                        OutlinedButton(
                            onClick = onClearManualLocation,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
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
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MatchPill(photo: PhotoCandidate, match: PhotoMatch?) {
    when {
        photo.manualLocation != null -> StatusPill(stringResource(R.string.manual), PillTone.Warning)
        match is PhotoMatch.Matched -> StatusPill(stringResource(R.string.matched), PillTone.Success)
        match is PhotoMatch.Unmatched -> StatusPill(stringResource(R.string.unmatched), PillTone.Neutral)
        else -> StatusPill(stringResource(R.string.no_track), PillTone.Neutral)
    }
}

@Composable
private fun ReviewWritePanel(
    settings: AppSettings,
    readiness: WriteReadiness,
) {
    SectionBlock {
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.write_readiness, readiness.writeable, readiness.skipped),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun PrimaryActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun SecondaryActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun DangerActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun DangerFilledButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun SectionBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        content = content,
    )
}

@Composable
private fun SurfaceCard(
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                color = MaterialTheme.colorScheme.surfaceVariant,
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
                        color = MaterialTheme.colorScheme.surfaceVariant,
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
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.second,
        )
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onChooseExportFolder: () -> Unit,
) {
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }

    if (showAppearanceDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.appearance),
            options = AppearanceMode.entries.map { appearanceLabel(it) },
            selectedIndex = AppearanceMode.entries.indexOf(settings.appearance),
            onSelect = { index ->
                onSettingsChanged(settings.copy(appearance = AppearanceMode.entries[index]))
                showAppearanceDialog = false
            },
            onDismiss = { showAppearanceDialog = false },
        )
    }
    if (showFrequencyDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.recording_frequency),
            options = RecordingFrequency.entries.map { "${frequencyLabel(it)}\n${frequencyDescription(it)}" },
            selectedIndex = RecordingFrequency.entries.indexOf(settings.recordingFrequency),
            onSelect = { index ->
                onSettingsChanged(settings.copy(recordingFrequency = RecordingFrequency.entries[index]))
                showFrequencyDialog = false
            },
            onDismiss = { showFrequencyDialog = false },
        )
    }

    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            SettingsGroup {
                SettingNavigationRow(
                    title = stringResource(R.string.appearance),
                    value = appearanceLabel(settings.appearance),
                    onClick = { showAppearanceDialog = true },
                )
            }
        }
        item {
            SettingsGroup {
                SettingNavigationRow(
                    title = stringResource(R.string.recording_frequency),
                    value = frequencyLabel(settings.recordingFrequency),
                    onClick = { showFrequencyDialog = true },
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsSectionHeader(stringResource(R.string.photo_match_settings))
                SettingsGroup {
                    SettingStepper(
                        title = stringResource(R.string.camera_offset_minutes),
                        value = settings.cameraOffset.toMinutes(),
                        range = AppSettingsStore.MIN_CAMERA_OFFSET_MINUTES..AppSettingsStore.MAX_CAMERA_OFFSET_MINUTES,
                        onValueChange = { onSettingsChanged(settings.copy(cameraOffset = Duration.ofMinutes(it))) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingStepper(
                        title = stringResource(R.string.max_time_difference_minutes),
                        value = settings.maxPhotoTimeDifference.toMinutes(),
                        range = AppSettingsStore.MIN_PHOTO_TIME_DIFFERENCE_MINUTES..AppSettingsStore.MAX_PHOTO_TIME_DIFFERENCE_MINUTES,
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsSectionHeader(stringResource(R.string.export_settings))
                SettingsGroup {
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
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = { onSelect(index) })
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingNavigationRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingChoiceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ExportModeSelector(
    preferCopies: Boolean,
    onSelectCopies: () -> Unit,
    onSelectOriginals: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.export_settings),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row {
                val copiesBg = if (preferCopies) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                val copiesContent = if (preferCopies) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .clickable(onClick = onSelectCopies),
                    color = copiesBg,
                ) {
                    Text(
                        text = stringResource(R.string.write_copies),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = copiesContent,
                    )
                }
                val originalsBg = if (!preferCopies) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                val originalsContent = if (!preferCopies) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .clickable(onClick = onSelectOriginals),
                    color = originalsBg,
                ) {
                    Text(
                        text = stringResource(R.string.write_originals),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = originalsContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportFolderRow(
    folderUri: String?,
    onChooseExportFolder: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChooseExportFolder)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.default_export_folder),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.Search,
            contentDescription = stringResource(R.string.choose_folder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingStepper(
    title: String,
    value: Long,
    range: LongRange,
    onValueChange: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChange((value - 1).coerceIn(range.first, range.last)) },
                    enabled = value > range.first,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text("−", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = value.toString(),
                    modifier = Modifier.widthIn(min = 36.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(
                    onClick = { onValueChange((value + 1).coerceIn(range.first, range.last)) },
                    enabled = value < range.last,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StartRecordingDialog(
    initialName: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (initialName == null) return
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.start_recording)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.track_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
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
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringResource(R.string.close))
            }
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
private fun statusLabel(status: RecordingStatus): String =
    when (status) {
        RecordingStatus.Recording -> stringResource(R.string.status_recording)
        RecordingStatus.Paused -> stringResource(R.string.status_paused)
        RecordingStatus.Stopped -> stringResource(R.string.status_stopped)
    }

private fun statusTone(status: RecordingStatus): PillTone =
    when (status) {
        RecordingStatus.Recording -> PillTone.Success
        RecordingStatus.Paused -> PillTone.Warning
        RecordingStatus.Stopped -> PillTone.Neutral
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
