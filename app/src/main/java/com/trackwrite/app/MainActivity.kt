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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
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
import com.trackwrite.app.recording.RecordingSnapshot
import com.trackwrite.app.recording.RecordingStateStore
import com.trackwrite.app.recording.RecordingStatus
import com.trackwrite.app.recording.TrackingService
import com.trackwrite.app.settings.AppSettings
import com.trackwrite.app.settings.AppSettingsStore
import com.trackwrite.app.settings.AppearanceMode
import com.trackwrite.app.settings.RecordingFrequency
import com.trackwrite.app.ui.TrackWriteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var repository: TrackRepository
    private lateinit var stateStore: RecordingStateStore
    private lateinit var geotagging: PhotoGeotagging
    private lateinit var gpx: GpxFileActions
    private lateinit var settingsStore: AppSettingsStore

    private var selectedTrackId: String? = null
    private var selectedPhotos: List<PhotoCandidate> = emptyList()
    private var matchResults: List<PhotoMatchResult> = emptyList()
    private var pendingManualPhotoIndex: Int? = null
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
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            log(geotagging.exportCopies(matchResults, uri).joinToString("\n"))
            refresh()
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
        uiState = uiState.copy(selectedTab = MainTab.Match)
        matchSelectedPhotos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    onImportGpx = { gpxImportLauncher.launch(arrayOf("application/gpx+xml", "text/xml", "*/*")) },
                    onExportGpx = { promptExport() },
                    onShareTrack = { shareSelectedTrack() },
                    onRenameTrack = { selectedTrack()?.let { uiState = uiState.copy(renameDialog = RenameDialogState(it.id, it.name)) } },
                    onDeleteTrack = { selectedTrack()?.let { uiState = uiState.copy(deleteDialog = it) } },
                    onTrackSelected = {
                        selectedTrackId = it
                        matchSelectedPhotos()
                    },
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
                    onExportCopies = { exportFolderLauncher.launch(null) },
                    onWriteOriginals = {
                        if (uiState.settings.confirmOriginalWrites) {
                            uiState = uiState.copy(showWriteDialog = true)
                        } else {
                            writeOriginals()
                        }
                    },
                    onSettingsChanged = { newSettings ->
                        persistSettings(newSettings)
                    },
                    onDismissDialog = { dismissDialogs() },
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
                        selectedTrackId = null
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
        if (selectedTrackId == null || tracks.none { it.id == selectedTrackId }) {
            selectedTrackId = tracks.firstOrNull()?.id
        }
        val settings = settingsStore.current()
        matchResults = matchResults.ifEmpty {
            selectedPhotos.map { PhotoMatchResult(it, null) }
        }
        uiState = uiState.copy(
            recording = stateStore.current(),
            tracks = tracks,
            selectedTrackId = selectedTrackId,
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
        settingsStore.setConfirmOriginalWrites(settings.confirmOriginalWrites)
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
        ContextCompat.startForegroundService(this, TrackingService.command(this, action, name))
        window.decorView.postDelayed({ refresh() }, 500)
    }

    private fun importGpx(uri: Uri) {
        runCatching {
            val xml = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read GPX")
            val track = gpx.importTrack(UUID.randomUUID().toString(), xml)
            repository.saveTrack(track)
            selectedTrackId = track.id
            log("Imported ${track.name}")
            refresh()
        }.onFailure { log("Import failed: ${it.message}") }
    }

    private fun promptExport() {
        val track = selectedTrack() ?: return log("Select or create a track first.")
        gpxExportLauncher.launch("${track.name}.gpx")
    }

    private fun exportSelectedTrack(uri: Uri) {
        val track = selectedTrack() ?: return
        contentResolver.openOutputStream(uri, "w").use { output ->
            requireNotNull(output).write(gpx.encode(track).toByteArray())
        }
        log("Exported ${track.name}")
    }

    private fun shareSelectedTrack() {
        val track = selectedTrack() ?: return log("Select or create a track first.")
        startActivity(Intent.createChooser(gpx.shareIntent(track), getString(R.string.share)))
    }

    private fun matchSelectedPhotos() {
        val track = selectedTrack()
        val options = uiState.settings.toMatchOptions()
        matchResults = if (track == null) {
            selectedPhotos.map { PhotoMatchResult(it, null) }
        } else {
            geotagging.matchPhotos(selectedPhotos, track, options)
        }
        refresh()
    }

    private fun writeOriginals() {
        log(geotagging.writeInPlace(matchResults).joinToString("\n"))
        refresh()
    }

    private fun selectedTrack(): Track? =
        selectedTrackId?.let(repository::getTrack) ?: repository.listTracks().firstOrNull()

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
    val selectedTrackId: String? = null,
    val photos: List<PhotoCandidate> = emptyList(),
    val matches: List<PhotoMatchResult> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val logMessage: String = "",
    val startDialogName: String? = null,
    val renameDialog: RenameDialogState? = null,
    val deleteDialog: Track? = null,
    val showWriteDialog: Boolean = false,
)

private data class RenameDialogState(
    val trackId: String,
    val name: String,
)

private enum class MainTab {
    Record,
    Match,
    Library,
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
    onImportGpx: () -> Unit,
    onExportGpx: () -> Unit,
    onShareTrack: () -> Unit,
    onRenameTrack: () -> Unit,
    onDeleteTrack: () -> Unit,
    onTrackSelected: (String) -> Unit,
    onSelectPhotos: () -> Unit,
    onSelectFolder: () -> Unit,
    onSetManualLocation: (Int) -> Unit,
    onClearManualLocation: (Int) -> Unit,
    onExportCopies: () -> Unit,
    onWriteOriginals: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismissDialog: () -> Unit,
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
                            text = stringResource(if (state.showSettings) R.string.settings else R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!state.showSettings) {
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                    NavigationItem(
                        selected = state.selectedTab == MainTab.Record,
                        icon = Icons.Default.MyLocation,
                        label = stringResource(R.string.tab_record),
                        onClick = { onTabSelected(MainTab.Record) },
                    )
                    NavigationItem(
                        selected = state.selectedTab == MainTab.Match,
                        icon = Icons.Default.PhotoLibrary,
                        label = stringResource(R.string.tab_match),
                        onClick = { onTabSelected(MainTab.Match) },
                    )
                    NavigationItem(
                        selected = state.selectedTab == MainTab.Library,
                        icon = Icons.AutoMirrored.Filled.Article,
                        label = stringResource(R.string.tab_library),
                        onClick = { onTabSelected(MainTab.Library) },
                    )
                }
            }
        },
    ) { padding ->
        if (state.showSettings) {
            SettingsScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                settings = state.settings,
                onSettingsChanged = onSettingsChanged,
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
                    onImportGpx = onImportGpx,
                    onExportGpx = onExportGpx,
                    onTrackSelected = onTrackSelected,
                )
                MainTab.Match -> MatchScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onSelectPhotos = onSelectPhotos,
                    onSelectFolder = onSelectFolder,
                    onSetManualLocation = onSetManualLocation,
                    onClearManualLocation = onClearManualLocation,
                    onExportCopies = onExportCopies,
                    onWriteOriginals = onWriteOriginals,
                )
                MainTab.Library -> LibraryScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onTrackSelected = onTrackSelected,
                    onExportGpx = onExportGpx,
                    onShareTrack = onShareTrack,
                    onRenameTrack = onRenameTrack,
                    onDeleteTrack = onDeleteTrack,
                )
            }
        }
    }

    StartRecordingDialog(state.startDialogName, onDismissDialog, onConfirmStart)
    RenameTrackDialog(state.renameDialog, onDismissDialog, onConfirmRename)
    DeleteTrackDialog(state.deleteDialog, onDismissDialog, onConfirmDelete)
    WriteOriginalsDialog(state.showWriteDialog, onDismissDialog, onConfirmWrite)
}

@Composable
private fun RowScope.NavigationItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = color,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, color = contentColor, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecordScreen(
    modifier: Modifier,
    state: MainUiState,
    onStartRecording: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onImportGpx: () -> Unit,
    onExportGpx: () -> Unit,
    onTrackSelected: (String) -> Unit,
) {
    val selectedTrack = state.tracks.firstOrNull { it.id == state.selectedTrackId }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            RecordingPanel(state, selectedTrack, onStartRecording, onPause, onResume, onStop)
        }
        item {
            SectionHeader(stringResource(R.string.track_source), Icons.Default.Map)
            Spacer(Modifier.height(10.dp))
            ActionRow {
                PrimaryActionButton(stringResource(R.string.import_gpx), Icons.Default.FileUpload, onImportGpx)
                SecondaryActionButton(stringResource(R.string.export_gpx), Icons.Default.FileDownload, onExportGpx)
            }
        }
        item {
            TrackList(
                tracks = state.tracks,
                selectedTrackId = state.selectedTrackId,
                onTrackSelected = onTrackSelected,
                compact = true,
            )
        }
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
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.record_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StatusPill(statusLabel(state.recording.status), statusTone(state.recording.status))
            }
            Icon(
                imageVector = Icons.Default.MyLocation,
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
        ActionRow {
            PrimaryActionButton(stringResource(R.string.start_recording), Icons.Default.PlayArrow, onStartRecording)
            SecondaryActionButton(stringResource(R.string.pause), Icons.Default.Pause, onPause)
        }
        Spacer(Modifier.height(10.dp))
        ActionRow {
            SecondaryActionButton(stringResource(R.string.resume), Icons.Default.PlayArrow, onResume)
            DangerActionButton(stringResource(R.string.stop), Icons.Default.Stop, onStop)
        }
    }
}

@Composable
private fun MatchScreen(
    modifier: Modifier,
    state: MainUiState,
    onSelectPhotos: () -> Unit,
    onSelectFolder: () -> Unit,
    onSetManualLocation: (Int) -> Unit,
    onClearManualLocation: (Int) -> Unit,
    onExportCopies: () -> Unit,
    onWriteOriginals: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SectionHeader(stringResource(R.string.photo_matching), Icons.Default.CameraAlt)
            Spacer(Modifier.height(10.dp))
            ActionRow {
                PrimaryActionButton(stringResource(R.string.select_photos), Icons.Default.Image, onSelectPhotos)
                SecondaryActionButton(stringResource(R.string.select_folder), Icons.Default.FolderOpen, onSelectFolder)
            }
            Spacer(Modifier.height(10.dp))
            MatchSettingsSummary(state.settings)
        }
        if (state.matches.isEmpty()) {
            item { EmptyPanel(stringResource(R.string.no_photos)) }
        } else {
            itemsIndexed(state.matches) { index, result ->
                PhotoMatchRow(
                    index = index,
                    result = result,
                    onSetManualLocation = { onSetManualLocation(index) },
                    onClearManualLocation = { onClearManualLocation(index) },
                )
            }
            item {
                ReviewWritePanel(
                    settings = state.settings,
                    onExportCopies = onExportCopies,
                    onWriteOriginals = onWriteOriginals,
                )
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    state: MainUiState,
    onTrackSelected: (String) -> Unit,
    onExportGpx: () -> Unit,
    onShareTrack: () -> Unit,
    onRenameTrack: () -> Unit,
    onDeleteTrack: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            TrackList(
                tracks = state.tracks,
                selectedTrackId = state.selectedTrackId,
                onTrackSelected = onTrackSelected,
                compact = false,
            )
        }
        item {
            ActionRow {
                SecondaryActionButton(stringResource(R.string.export_gpx), Icons.Default.FileDownload, onExportGpx)
                SecondaryActionButton(stringResource(R.string.share), Icons.Default.Share, onShareTrack)
            }
            Spacer(Modifier.height(10.dp))
            ActionRow {
                SecondaryActionButton(stringResource(R.string.rename), Icons.Default.Edit, onRenameTrack)
                DangerActionButton(stringResource(R.string.delete), Icons.Default.Delete, onDeleteTrack)
            }
        }
        item {
            SurfaceCard {
                SectionHeader(stringResource(R.string.system_log), Icons.Default.MoreHoriz)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.logMessage.ifBlank { stringResource(R.string.ready) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    selectedTrackId: String?,
    onTrackSelected: (String) -> Unit,
    compact: Boolean,
) {
    if (tracks.isEmpty()) {
        EmptyPanel(stringResource(R.string.no_tracks))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tracks.forEach { track ->
            TrackRow(
                track = track,
                selected = track.id == selectedTrackId,
                compact = compact,
                onClick = { onTrackSelected(track.id) },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val stats = track.stats()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp),
            ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) StatusPill(stringResource(R.string.selected), PillTone.Success)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${track.points.size} ${stringResource(R.string.points_short)} · ${formatDuration(stats.duration)} · ${formatDistance(stats.distanceMeters)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.startTime?.toString() ?: stringResource(R.string.not_available),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoMatchRow(
    index: Int,
    result: PhotoMatchResult,
    onSetManualLocation: () -> Unit,
    onClearManualLocation: () -> Unit,
) {
    val photo = result.photo
    val match = result.match
    SurfaceCard {
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
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
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
            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onExportCopies: () -> Unit,
    onWriteOriginals: () -> Unit,
) {
    SurfaceCard {
        SectionHeader(stringResource(R.string.review_write), Icons.Default.Save)
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.write_originals_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        if (settings.preferExportCopies) {
            ActionRow {
                PrimaryActionButton(stringResource(R.string.export_copies), Icons.Default.FileDownload, onExportCopies)
                DangerActionButton(stringResource(R.string.write_originals), Icons.Default.Warning, onWriteOriginals)
            }
        } else {
            ActionRow {
                DangerFilledButton(stringResource(R.string.write_originals), Icons.Default.Warning, onWriteOriginals)
                SecondaryActionButton(stringResource(R.string.export_copies), Icons.Default.FileDownload, onExportCopies)
            }
        }
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
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
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
                if (row.size == 1) Spacer(Modifier.weight(1f))
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
) {
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SettingsSection(title = stringResource(R.string.appearance)) {
                AppearanceMode.entries.forEach { mode ->
                    SettingChoiceRow(
                        title = appearanceLabel(mode),
                        selected = settings.appearance == mode,
                        onClick = { onSettingsChanged(settings.copy(appearance = mode)) },
                    )
                }
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.recording_frequency)) {
                RecordingFrequency.entries.forEach { frequency ->
                    SettingChoiceRow(
                        title = frequencyLabel(frequency),
                        subtitle = frequencyDescription(frequency),
                        selected = settings.recordingFrequency == frequency,
                        onClick = { onSettingsChanged(settings.copy(recordingFrequency = frequency)) },
                    )
                }
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.photo_match_settings)) {
                SettingStepper(
                    title = stringResource(R.string.camera_offset_minutes),
                    value = settings.cameraOffset.toMinutes(),
                    range = AppSettingsStore.MIN_CAMERA_OFFSET_MINUTES..AppSettingsStore.MAX_CAMERA_OFFSET_MINUTES,
                    onValueChange = { onSettingsChanged(settings.copy(cameraOffset = Duration.ofMinutes(it))) },
                )
                HorizontalDivider()
                SettingStepper(
                    title = stringResource(R.string.max_time_difference_minutes),
                    value = settings.maxPhotoTimeDifference.toMinutes(),
                    range = AppSettingsStore.MIN_PHOTO_TIME_DIFFERENCE_MINUTES..AppSettingsStore.MAX_PHOTO_TIME_DIFFERENCE_MINUTES,
                    onValueChange = { onSettingsChanged(settings.copy(maxPhotoTimeDifference = Duration.ofMinutes(it))) },
                )
                HorizontalDivider()
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
        item {
            SettingsSection(title = stringResource(R.string.export_settings)) {
                SettingSwitchRow(
                    title = stringResource(R.string.prefer_export_copies),
                    checked = settings.preferExportCopies,
                    onCheckedChange = { onSettingsChanged(settings.copy(preferExportCopies = it)) },
                )
                SettingSwitchRow(
                    title = stringResource(R.string.confirm_original_writes),
                    checked = settings.confirmOriginalWrites,
                    onCheckedChange = { onSettingsChanged(settings.copy(confirmOriginalWrites = it)) },
                )
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.about)) {
                Text(stringResource(R.string.version_name), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
            onClick = { onValueChange((value - 1).coerceIn(range.first, range.last)) },
            enabled = value > range.first,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("-")
        }
        Text(
            text = value.toString(),
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        OutlinedButton(
            onClick = { onValueChange((value + 1).coerceIn(range.first, range.last)) },
            enabled = value < range.last,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("+")
        }
    }
}

@Composable
private fun MatchSettingsSummary(settings: AppSettings) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "${stringResource(R.string.camera_offset_minutes)}: ${settings.cameraOffset.toMinutes()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${stringResource(R.string.max_time_difference_minutes)}: ${settings.maxPhotoTimeDifference.toMinutes()}",
                style = MaterialTheme.typography.bodySmall,
            )
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
