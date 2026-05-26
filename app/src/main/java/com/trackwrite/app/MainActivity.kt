package com.trackwrite.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.trackwrite.app.data.TrackRepository
import com.trackwrite.app.domain.GeoPoint
import com.trackwrite.app.domain.MatchOptions
import com.trackwrite.app.domain.Track
import com.trackwrite.app.domain.stats
import com.trackwrite.app.io.GpxFileActions
import com.trackwrite.app.media.PhotoCandidate
import com.trackwrite.app.media.PhotoGeotagging
import com.trackwrite.app.media.PhotoMatchResult
import com.trackwrite.app.recording.RecordingStateStore
import com.trackwrite.app.recording.RecordingStatus
import com.trackwrite.app.recording.TrackingService
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var repository: TrackRepository
    private lateinit var stateStore: RecordingStateStore
    private lateinit var geotagging: PhotoGeotagging
    private lateinit var gpx: GpxFileActions
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var trackText: TextView
    private lateinit var photoText: TextView
    private lateinit var logText: TextView

    private var selectedTrackId: String? = null
    private var selectedPhotos: List<PhotoCandidate> = emptyList()
    private var matchResults: List<PhotoMatchResult> = emptyList()
    private var matchOptions = MatchOptions()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TrackRepository(this)
        stateStore = RecordingStateStore(this)
        geotagging = PhotoGeotagging(this)
        gpx = GpxFileActions(this)
        setContentView(buildUi())
        refresh()
    }

    private fun buildUi(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
        }
        statusText = text(size = 16f)
        trackText = text(size = 14f)
        photoText = text(size = 14f)
        logText = text(size = 13f)

        root.addView(text("TrackWrite", 30f))
        root.addView(text("Reliable GPS tracks and photo geotagging.", 15f))
        root.addView(section("Recording"))
        root.addView(statusText)
        root.addView(row(
            button("Start") { requestRecordingPermissionsThen { startRecording() } },
            button("Pause") { command(TrackingService.ACTION_PAUSE) },
            button("Resume") { requestRecordingPermissionsThen { command(TrackingService.ACTION_RESUME) } },
            button("Stop") { command(TrackingService.ACTION_STOP) },
        ))
        root.addView(section("Tracks"))
        root.addView(row(
            button("Import GPX") { gpxImportLauncher.launch(arrayOf("application/gpx+xml", "text/xml", "*/*")) },
            button("Export GPX") { promptExport() },
            button("Share GPX") { shareSelectedTrack() },
        ))
        root.addView(row(
            button("Select track") { promptTrackSelection() },
            button("Rename") { renameSelectedTrack() },
            button("Delete") { deleteSelectedTrack() },
            button("Refresh") { refresh() },
        ))
        root.addView(trackText)
        root.addView(section("Photos"))
        root.addView(row(
            button("Match settings") { promptMatchSettings() },
        ))
        root.addView(row(
            button("Select photos") { photoPickerLauncher.launch(arrayOf("image/*")) },
            button("Select folder") { folderPickerLauncher.launch(null) },
        ))
        root.addView(row(
            button("Match") { matchSelectedPhotos() },
            button("Manual point") { promptManualPoint() },
            button("AMap search") { showAmapSearchNotice() },
            button("Clear manual") { clearManualPoint() },
        ))
        root.addView(row(
            button("Export copies") { exportFolderLauncher.launch(null) },
            button("Write originals") { confirmWriteOriginals() },
        ))
        root.addView(photoText)
        root.addView(section("Log"))
        root.addView(logText)

        return ScrollView(this).apply { addView(root) }
    }

    private fun refresh() {
        val state = stateStore.current()
        val tracks = repository.listTracks()
        if (selectedTrackId == null || tracks.none { it.id == selectedTrackId }) {
            selectedTrackId = tracks.firstOrNull()?.id
        }
        statusText.text = buildString {
            append("State: ${state.status}")
            if (state.trackId != null) append("\nActive track: ${state.trackId}")
            append("\nRecovery: app/process recovery only; reboot and force-stop auto-resume are out of scope.")
            append("\nMatch: offset ${matchOptions.cameraOffset.toMinutes()} min, max ${matchOptions.maxTimeDifference.toMinutes()} min, ")
            append("start fallback ${matchOptions.allowStartFallback}, end fallback ${matchOptions.allowEndFallback}")
        }
        trackText.text = if (tracks.isEmpty()) {
            "No tracks yet."
        } else {
            tracks.joinToString("\n\n") { track ->
                val marker = if (track.id == selectedTrackId) "*" else "-"
                val stats = track.stats()
                "$marker ${track.name}\n${track.points.size} points | ${formatDuration(stats.duration)} | " +
                    "${stats.distanceMeters.toInt()} m | ${"%.2f".format(stats.averageSpeedMetersPerSecond)} m/s\n" +
                    "Start: ${track.startTime ?: "n/a"}"
            }
        }
        renderPhotoResults()
    }

    private fun startRecording() {
        val input = EditText(this).apply {
            hint = "Track name"
            setText("Recording ${Instant.now()}")
        }
        AlertDialog.Builder(this)
            .setTitle("Start GPS recording")
            .setView(input)
            .setPositiveButton("Start") { _, _ ->
                command(TrackingService.ACTION_START, input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun command(action: String, name: String? = null) {
        ContextCompat.startForegroundService(this, TrackingService.command(this, action, name))
        root.postDelayed({ refresh() }, 500)
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
        startActivity(Intent.createChooser(gpx.shareIntent(track), "Share GPX"))
    }

    private fun renameSelectedTrack() {
        val track = selectedTrack() ?: return log("Select or create a track first.")
        val input = EditText(this).apply { setText(track.name) }
        AlertDialog.Builder(this)
            .setTitle("Rename track")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                repository.renameTrack(track.id, input.text.toString())
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedTrack() {
        val track = selectedTrack() ?: return log("Select or create a track first.")
        AlertDialog.Builder(this)
            .setTitle("Delete ${track.name}?")
            .setMessage("This removes the local track history.")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteTrack(track.id)
                selectedTrackId = null
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun matchSelectedPhotos() {
        val track = selectedTrack()
        matchResults = if (track == null) {
            selectedPhotos.map { PhotoMatchResult(it, null) }
        } else {
            geotagging.matchPhotos(selectedPhotos, track, matchOptions)
        }
        refresh()
    }

    private fun promptMatchSettings() {
        val input = EditText(this).apply {
            hint = "offset minutes, max minutes, start fallback, end fallback"
            setText(
                "${matchOptions.cameraOffset.toMinutes()}," +
                    "${matchOptions.maxTimeDifference.toMinutes()}," +
                    "${matchOptions.allowStartFallback}," +
                    matchOptions.allowEndFallback,
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Photo match settings")
            .setMessage("Format: camera offset minutes, max difference minutes, start fallback true/false, end fallback true/false.")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val parts = input.text.toString().split(",").map { it.trim() }
                if (parts.size < 4) return@setPositiveButton log("Use: offset,max,startFallback,endFallback")
                val offset = parts[0].toLongOrNull() ?: return@setPositiveButton log("Invalid offset")
                val max = parts[1].toLongOrNull() ?: return@setPositiveButton log("Invalid max difference")
                matchOptions = MatchOptions(
                    cameraOffset = Duration.ofMinutes(offset),
                    maxTimeDifference = Duration.ofMinutes(max),
                    allowStartFallback = parts[2].toBooleanStrictOrNull() ?: return@setPositiveButton log("Invalid start fallback"),
                    allowEndFallback = parts[3].toBooleanStrictOrNull() ?: return@setPositiveButton log("Invalid end fallback"),
                )
                matchSelectedPhotos()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptManualPoint() {
        if (selectedPhotos.isEmpty()) return log("Select photos first.")
        val input = EditText(this).apply {
            hint = "photo index, latitude, longitude"
            setText("1,30.000000,120.000000")
        }
        AlertDialog.Builder(this)
            .setTitle("Set manual location")
            .setMessage("AMap search/map SDK integration remains behind the provider boundary; this MVP field supports direct map-coordinate fallback.")
            .setView(input)
            .setPositiveButton("Bind") { _, _ ->
                val parts = input.text.toString().split(",").map { it.trim() }
                if (parts.size < 3) return@setPositiveButton log("Use: index, latitude, longitude")
                val index = parts[0].toIntOrNull()?.minus(1) ?: return@setPositiveButton log("Invalid index")
                val lat = parts[1].toDoubleOrNull() ?: return@setPositiveButton log("Invalid latitude")
                val lon = parts[2].toDoubleOrNull() ?: return@setPositiveButton log("Invalid longitude")
            selectedPhotos = selectedPhotos.mapIndexed { photoIndex, photo ->
                    if (photoIndex == index) photo.copy(manualLocation = GeoPoint(lat, lon)) else photo
                }
                matchSelectedPhotos()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearManualPoint() {
        selectedPhotos = selectedPhotos.map { it.copy(manualLocation = null) }
        matchSelectedPhotos()
    }

    private fun showAmapSearchNotice() {
        AlertDialog.Builder(this)
            .setTitle("AMap provider boundary")
            .setMessage(
                "The app is wired for AMap key/permissions, but the AMap SDK artifacts are not present in the local Gradle cache. " +
                    "Manual fallback is available through direct coordinates now; adding the SDK can replace this action with search results and map-tap binding.",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmWriteOriginals() {
        AlertDialog.Builder(this)
            .setTitle("Write original photos?")
            .setMessage("This attempts in-place EXIF mutation using your Android write grants. Exported copies are safer.")
            .setPositiveButton("Write") { _, _ ->
                log(geotagging.writeInPlace(matchResults).joinToString("\n"))
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderPhotoResults() {
        photoText.text = if (matchResults.isEmpty()) {
            "No photos selected."
        } else {
            matchResults.mapIndexed { index, result ->
                val match = result.match
                val status = when {
                    result.photo.manualLocation != null -> "manual ${result.photo.manualLocation}"
                    match == null -> "no capture time or no selected track"
                    match is com.trackwrite.app.domain.PhotoMatch.Matched -> "${match.source} ${match.position}"
                    match is com.trackwrite.app.domain.PhotoMatch.Unmatched -> "unmatched ${match.reason}"
                    else -> "unknown"
                }
                "${index + 1}. ${result.photo.displayName}\n${result.photo.capturedAt ?: "no EXIF time"}\n$status"
            }.joinToString("\n\n")
        }
    }

    private fun selectedTrack(): Track? =
        selectedTrackId?.let(repository::getTrack) ?: repository.listTracks().firstOrNull()

    private fun promptTrackSelection() {
        val tracks = repository.listTracks()
        if (tracks.isEmpty()) return log("No tracks yet.")
        AlertDialog.Builder(this)
            .setTitle("Select track")
            .setItems(tracks.map { "${it.name} (${it.points.size} points)" }.toTypedArray()) { _, which ->
                selectedTrackId = tracks[which].id
                matchSelectedPhotos()
                refresh()
            }
            .show()
    }

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
        logText.text = message
    }

    private fun text(value: String = "", size: Float): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(R.color.trackwrite_text))
            setPadding(0, 8, 0, 8)
        }

    private fun section(value: String): TextView =
        text(value, 20f).apply {
            setPadding(0, 28, 0, 10)
        }

    private fun button(value: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = value
            setOnClickListener { onClick() }
        }

    private fun row(vararg children: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            children.forEach { child ->
                addView(child, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}
