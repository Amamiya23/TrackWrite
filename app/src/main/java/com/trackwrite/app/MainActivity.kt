package com.trackwrite.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.trackwrite.app.data.TrackRepository
import com.trackwrite.app.domain.GeoPoint
import com.trackwrite.app.domain.MatchOptions
import com.trackwrite.app.domain.PhotoMatch
import com.trackwrite.app.domain.Track
import com.trackwrite.app.domain.stats
import com.trackwrite.app.io.GpxFileActions
import com.trackwrite.app.map.ManualLocationActivity
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

    private lateinit var recordingStatusRow: LinearLayout
    private lateinit var recordingStatsText: TextView
    private lateinit var tracksContainer: LinearLayout
    private lateinit var trackActionsRow: LinearLayout
    private lateinit var photosContainer: LinearLayout
    private lateinit var matchOptionsText: TextView
    private lateinit var logText: TextView

    private var selectedTrackId: String? = null
    private var selectedPhotos: List<PhotoCandidate> = emptyList()
    private var matchResults: List<PhotoMatchResult> = emptyList()
    private var matchOptions = MatchOptions()
    private var pendingManualPhotoIndex: Int? = null

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
        matchSelectedPhotos()
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun cardLayout(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundResource(R.drawable.card_background)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(16))
            }
            layoutParams = params
        }

    private fun sectionTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text.uppercase()
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.trackwrite_accent))
            setPadding(0, 0, 0, dp(12))
        }

    private fun bodyText(text: String, size: Float = 14f, color: Int = 0xFF18201C.toInt(), isBold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (isBold) {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

    private fun customButton(
        text: String,
        bgDrawableId: Int,
        textColor: Int,
        onClick: () -> Unit = {}
    ): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setBackgroundResource(bgDrawableId)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun statusTag(text: String, bgDrawableId: Int, textColor: Int): TextView =
        TextView(this).apply {
            this.text = text.uppercase()
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setBackgroundResource(bgDrawableId)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            gravity = Gravity.CENTER
        }

    private fun row(vararg children: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            children.forEachIndexed { i, child ->
                if (i > 0) {
                    addView(Space(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(8), LinearLayout.LayoutParams.MATCH_PARENT)
                    })
                }
                addView(child, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(getColor(R.color.trackwrite_background))
        }

        // Header
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        val titleText = TextView(this).apply {
            text = "TrackWrite"
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.trackwrite_text))
        }
        val subtitleText = TextView(this).apply {
            text = "Reliable GPS tracks and photo geotagging."
            textSize = 14f
            setTextColor(0xFF5A6760.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        headerLayout.addView(titleText)
        headerLayout.addView(subtitleText)
        root.addView(headerLayout)

        // 1. Recording Card
        val recCard = cardLayout()
        recCard.addView(sectionTitle("GPS Recording"))
        recordingStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        recCard.addView(recordingStatusRow)
        recordingStatsText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF5A6760.toInt())
            setPadding(0, 0, 0, dp(16))
        }
        recCard.addView(recordingStatsText)
        val recControlRow = row(
            customButton("Start", R.drawable.button_primary, 0xFFFFFFFF.toInt()) {
                requestRecordingPermissionsThen { startRecording() }
            },
            customButton("Pause", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) {
                command(TrackingService.ACTION_PAUSE)
            },
            customButton("Resume", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) {
                requestRecordingPermissionsThen { command(TrackingService.ACTION_RESUME) }
            },
            customButton("Stop", R.drawable.button_danger, 0xFFA93226.toInt()) {
                command(TrackingService.ACTION_STOP)
            }
        )
        recCard.addView(recControlRow)
        root.addView(recCard)

        // 2. Tracks Card
        val tracksCard = cardLayout()
        val tracksHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        tracksHeaderRow.addView(
            sectionTitle("Tracks").apply { setPadding(0, 0, 0, 0) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        tracksHeaderRow.addView(
            customButton("Import GPX", R.drawable.button_primary, 0xFFFFFFFF.toInt()) {
                gpxImportLauncher.launch(arrayOf("application/gpx+xml", "text/xml", "*/*"))
            }
        )
        tracksCard.addView(tracksHeaderRow)

        tracksContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        tracksCard.addView(tracksContainer)

        trackActionsRow = row(
            customButton("Export GPX", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) { promptExport() },
            customButton("Share", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) { shareSelectedTrack() },
            customButton("Rename", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) { renameSelectedTrack() },
            customButton("Delete", R.drawable.button_danger, 0xFFA93226.toInt()) { deleteSelectedTrack() }
        )
        tracksCard.addView(trackActionsRow)
        root.addView(tracksCard)

        // 3. Photos Card
        val photosCard = cardLayout()
        photosCard.addView(sectionTitle("Photos & Geotagging"))
        matchOptionsText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF5A6760.toInt())
            setPadding(0, 0, 0, dp(12))
        }
        photosCard.addView(matchOptionsText)
        photosCard.addView(row(
            customButton("Select Photos", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) {
                photoPickerLauncher.launch(arrayOf("image/*"))
            },
            customButton("Select Folder", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) {
                folderPickerLauncher.launch(null)
            },
            customButton("Match Rules", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) {
                promptMatchSettings()
            }
        ).apply { setPadding(0, 0, 0, dp(16)) })

        photosContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        photosCard.addView(photosContainer)
        root.addView(photosCard)

        // 4. Save/Write Card
        val saveCard = cardLayout()
        saveCard.addView(sectionTitle("Save Location Metadata"))
        val saveRow = row(
            customButton("Export Copies (Safe)", R.drawable.button_primary, 0xFFFFFFFF.toInt()) {
                exportFolderLauncher.launch(null)
            },
            customButton("Write Originals (In-place)", R.drawable.button_danger, 0xFFA93226.toInt()) {
                confirmWriteOriginals()
            }
        )
        saveCard.addView(saveRow)
        val warningLabel = TextView(this).apply {
            text = "Caution: Writing to originals directly modifies the files in-place using Android write grants. Export copies is safer."
            textSize = 11f
            setTextColor(0xFFA93226.toInt())
            setPadding(0, dp(8), 0, 0)
        }
        saveCard.addView(warningLabel)
        root.addView(saveCard)

        // 5. Logs Card
        val logsCard = cardLayout()
        logsCard.addView(sectionTitle("System Log"))
        logText = TextView(this).apply {
            text = "Ready."
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF5A6760.toInt())
        }
        logsCard.addView(logText)
        root.addView(logsCard)

        return ScrollView(this).apply {
            addView(root)
            isFillViewport = true
        }
    }

    private fun refresh() {
        val state = stateStore.current()
        val tracks = repository.listTracks()
        if (selectedTrackId == null || tracks.none { it.id == selectedTrackId }) {
            selectedTrackId = tracks.firstOrNull()?.id
        }

        // Update recording status tag and stats
        recordingStatusRow.removeAllViews()
        val statusLabel = TextView(this).apply {
            text = "Status: "
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.trackwrite_text))
        }
        recordingStatusRow.addView(statusLabel)
        
        val tagView = when (state.status) {
            RecordingStatus.Recording -> statusTag("Recording", R.drawable.tag_recording, getColor(R.color.trackwrite_accent))
            RecordingStatus.Paused -> statusTag("Paused", R.drawable.tag_paused, 0xFFC67B1E.toInt())
            RecordingStatus.Stopped -> statusTag("Stopped", R.drawable.tag_stopped, 0xFF5A6760.toInt())
        }
        recordingStatusRow.addView(tagView)

        recordingStatsText.text = buildString {
            if (state.trackId != null) {
                val activeTrack = repository.getTrack(state.trackId)
                append("Active Track ID: ${state.trackId}\n")
                if (activeTrack != null) {
                    append("Points Captured: ${activeTrack.points.size}\n")
                }
            }
            append("Recovery: App/process recovery only. Reboot & force-stop auto-resume are out of scope.")
        }

        // Update match settings text
        matchOptionsText.text = buildString {
            append("Rules: Offset ${matchOptions.cameraOffset.toMinutes()} min | Max Diff ${matchOptions.maxTimeDifference.toMinutes()} min\n")
            append("Start Fallback: ${matchOptions.allowStartFallback} | End Fallback: ${matchOptions.allowEndFallback}")
        }

        // Render tracks list
        renderTracks(tracks)

        // Render photo match results list
        renderPhotoResults()
    }

    private fun renderTracks(tracks: List<Track>) {
        tracksContainer.removeAllViews()
        if (tracks.isEmpty()) {
            tracksContainer.addView(bodyText("No tracks yet.", color = 0xFF5A6760.toInt()))
            trackActionsRow.visibility = View.GONE
            return
        }

        trackActionsRow.visibility = View.VISIBLE

        tracks.forEach { track ->
            val isSelected = track.id == selectedTrackId
            val stats = track.stats()

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundResource(
                    if (isSelected) R.drawable.card_selected_background
                    else R.drawable.card_background
                )

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(10))
                }
                layoutParams = params

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedTrackId = track.id
                    matchSelectedPhotos()
                    refresh()
                }
            }

            val nameRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameText = TextView(this).apply {
                text = track.name
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(getColor(R.color.trackwrite_text))
            }

            nameRow.addView(nameText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (isSelected) {
                val activeTag = statusTag("Active", R.drawable.tag_recording, getColor(R.color.trackwrite_accent))
                nameRow.addView(activeTag)
            }

            itemLayout.addView(nameRow)

            val statsText = TextView(this).apply {
                text = "${track.points.size} points  |  ${formatDuration(stats.duration)}  |  " +
                        "${(stats.distanceMeters / 1000.0).let { "%.2f".format(it) }} km  |  " +
                        "${"%.2f".format(stats.averageSpeedMetersPerSecond)} m/s"
                textSize = 12f
                setTextColor(0xFF5A6760.toInt())
                setPadding(0, dp(6), 0, 0)
            }
            itemLayout.addView(statsText)

            val timeText = TextView(this).apply {
                text = "Start: ${track.startTime ?: "n/a"}"
                textSize = 11f
                setTextColor(0xFF8A9590.toInt())
                setPadding(0, dp(4), 0, 0)
            }
            itemLayout.addView(timeText)

            tracksContainer.addView(itemLayout)
        }
    }

    private fun renderPhotoResults() {
        photosContainer.removeAllViews()
        if (matchResults.isEmpty()) {
            photosContainer.addView(bodyText("No photos selected.", color = 0xFF5A6760.toInt()))
            return
        }

        matchResults.forEachIndexed { index, result ->
            val photo = result.photo
            val match = result.match

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundResource(R.drawable.card_background)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(10))
                }
                layoutParams = params
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameText = TextView(this).apply {
                text = "${index + 1}. ${photo.displayName}"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(getColor(R.color.trackwrite_text))
            }
            headerRow.addView(nameText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val statusTagView = when {
                photo.manualLocation != null -> {
                    statusTag("Manual", R.drawable.tag_paused, 0xFFC67B1E.toInt())
                }
                match is PhotoMatch.Matched -> {
                    statusTag("Matched", R.drawable.tag_recording, getColor(R.color.trackwrite_accent))
                }
                match is PhotoMatch.Unmatched -> {
                    statusTag("Unmatched", R.drawable.tag_stopped, 0xFF8A9590.toInt())
                }
                else -> {
                    statusTag("No Track", R.drawable.tag_stopped, 0xFF8A9590.toInt())
                }
            }
            headerRow.addView(statusTagView)
            itemLayout.addView(headerRow)

            val timeText = TextView(this).apply {
                text = "Captured: ${photo.capturedAt ?: "no EXIF time"}"
                textSize = 12f
                setTextColor(0xFF5A6760.toInt())
                setPadding(0, dp(4), 0, dp(4))
            }
            itemLayout.addView(timeText)

            val statusDetailRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, 0)
            }

            val statusDetailText = TextView(this).apply {
                text = when {
                    photo.manualLocation != null -> {
                        "Location: ${"%.6f".format(photo.manualLocation.latitude)}, ${"%.6f".format(photo.manualLocation.longitude)}"
                    }
                    match is PhotoMatch.Matched -> {
                        "${match.source}  |  ${"%.6f".format(match.position.latitude)}, ${"%.6f".format(match.position.longitude)}"
                    }
                    match is PhotoMatch.Unmatched -> {
                        "Reason: ${match.reason}"
                    }
                    else -> {
                        "No selected track or time out of bounds."
                    }
                }
                textSize = 12f
                setTextColor(getColor(R.color.trackwrite_text))
            }
            statusDetailRow.addView(statusDetailText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            itemLayout.addView(statusDetailRow)

            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(8), 0, 0)
            }

            val setLocationBtn = customButton(
                text = "Set Location",
                bgDrawableId = R.drawable.button_secondary,
                textColor = getColor(R.color.trackwrite_accent)
            ) {
                pendingManualPhotoIndex = index
                manualLocationLauncher.launch(Intent(this@MainActivity, ManualLocationActivity::class.java))
            }
            buttonRow.addView(setLocationBtn)

            if (photo.manualLocation != null) {
                val clearBtn = customButton(
                    text = "Clear",
                    bgDrawableId = R.drawable.button_danger,
                    textColor = 0xFFA93226.toInt()
                ).apply {
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(dp(8), 0, 0, 0)
                    }
                    layoutParams = lp
                }
                clearBtn.setOnClickListener {
                    selectedPhotos = selectedPhotos.mapIndexed { photoIndex, p ->
                        if (photoIndex == index) p.copy(manualLocation = null) else p
                    }
                    log("Manual location cleared for photo ${index + 1}.")
                    matchSelectedPhotos()
                }
                buttonRow.addView(clearBtn)
            }

            itemLayout.addView(buttonRow)
            photosContainer.addView(itemLayout)
        }
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
        // Run a small delayed refresh to let service state persist first
        tracksContainer.postDelayed({ refresh() }, 500)
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
        logText.text = message
    }

    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}
