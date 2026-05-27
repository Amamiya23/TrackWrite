package com.trackwrite.app.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.json.JSONObject

class ManualLocationActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var selectedText: TextView
    private lateinit var queryInput: EditText
    private lateinit var latInput: EditText
    private lateinit var lonInput: EditText

    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var selectedLabel: String = "Manual location"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = amapWebKey()
        val securityJsCode = amapSecurityJsCode()

        queryInput = EditText(this).apply {
            hint = "Search a place"
            setSingleLine(true)
            isEnabled = key.isNotBlank()
        }
        selectedText = TextView(this).apply {
            text = "No location selected"
            textSize = 15f
            setPadding(0, 10, 0, 10)
        }
        latInput = EditText(this).apply {
            hint = "Latitude"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        lonInput = EditText(this).apply {
            hint = "Longitude"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(MapBridge(), "TrackWrite")
            visibility = if (key.isBlank()) View.GONE else View.VISIBLE
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "Set photo location"
            textSize = 24f
        })
        root.addView(queryInput)
        root.addView(row(
            button("Search") { search() }.apply { isEnabled = key.isNotBlank() },
            button("Use typed") { useTypedCoordinates() },
        ))
        root.addView(row(latInput, lonInput))
        root.addView(selectedText)
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        root.addView(row(
            button("Confirm") { confirm() },
            button("Cancel") { setResult(Activity.RESULT_CANCELED); finish() },
        ))
        if (key.isBlank()) {
            root.addView(TextView(this).apply {
                text = "AMap Web key is not configured. Enter coordinates directly."
                textSize = 13f
            })
        }

        setContentView(root)
        if (key.isNotBlank()) webView.loadDataWithBaseURL(
            "https://webapi.amap.com/",
            mapHtml(key, securityJsCode),
            "text/html",
            "UTF-8",
            null,
        )
    }

    private fun search() {
        if (!queryInput.isEnabled) {
            selectedText.text = "AMap Web key is not configured. Enter coordinates directly."
            return
        }
        val query = JSONObject.quote(queryInput.text.toString())
        webView.evaluateJavascript("window.trackwriteSearch($query);", null)
    }

    private fun useTypedCoordinates() {
        val lat = latInput.text.toString().toDoubleOrNull()
        val lon = lonInput.text.toString().toDoubleOrNull()
        if (lat == null || lon == null) {
            selectedText.text = "Enter valid latitude and longitude"
            return
        }
        selectWgs84(lat, lon, "Typed WGS84 coordinates")
    }

    private fun confirm() {
        val lat = selectedLatitude
        val lon = selectedLongitude
        if (lat == null || lon == null) {
            selectedText.text = "Select or type a location first"
            return
        }
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_LATITUDE, lat)
                .putExtra(EXTRA_LONGITUDE, lon)
                .putExtra(EXTRA_LABEL, selectedLabel),
        )
        finish()
    }

    private fun selectWgs84(latitude: Double, longitude: Double, label: String) {
        selectedLatitude = latitude
        selectedLongitude = longitude
        selectedLabel = label
        latInput.setText(latitude.toString())
        lonInput.setText(longitude.toString())
        selectedText.text = "$label\n$latitude, $longitude"
    }

    private fun amapWebKey(): String {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString("com.trackwrite.amap.web_key").orEmpty()
    }

    private fun amapSecurityJsCode(): String {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString("com.trackwrite.amap.security_js_code").orEmpty()
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun row(vararg views: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        }

    private inner class MapBridge {
        @JavascriptInterface
        fun select(latitude: Double, longitude: Double, label: String?) {
            val wgs84 = AmapCoordinateConverter.gcj02ToWgs84(latitude, longitude)
            runOnUiThread {
                this@ManualLocationActivity.selectWgs84(
                    wgs84.latitude,
                    wgs84.longitude,
                    label ?: "AMap selection",
                )
            }
        }

        @JavascriptInterface
        fun error(message: String?) {
            runOnUiThread {
                selectedText.text = message ?: "AMap error"
            }
        }
    }

    companion object {
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_LABEL = "label"
    }
}
