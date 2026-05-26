package com.trackwrite.app.io

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.trackwrite.app.domain.GpxCodec
import com.trackwrite.app.domain.Track
import java.io.File

class GpxFileActions(private val context: Context) {
    private val codec = GpxCodec()

    fun importTrack(id: String, xml: String): Track =
        codec.decode(id, xml)

    fun encode(track: Track): String =
        codec.encode(track)

    fun shareIntent(track: Track): Intent {
        val file = File(context.cacheDir, "${safeName(track.name)}.gpx")
        file.writeText(encode(track))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "trackwrite-track" }
}
