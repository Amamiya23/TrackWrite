package com.trackwrite.app.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.trackwrite.app.R
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
            hint = "Search a place (e.g. Forbidden City)"
            setSingleLine(true)
            isEnabled = key.isNotBlank()
            setBackgroundResource(R.drawable.card_background)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            textSize = 14f
        }
        selectedText = TextView(this).apply {
            text = "No location selected"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.trackwrite_accent))
            setPadding(0, dp(4), 0, dp(4))
        }
        latInput = EditText(this).apply {
            hint = "Latitude"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setBackgroundResource(R.drawable.card_background)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        lonInput = EditText(this).apply {
            hint = "Longitude"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setBackgroundResource(R.drawable.card_background)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
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
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(getColor(R.color.trackwrite_background))
        }

        // Header
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(20))
        }
        val titleText = TextView(this).apply {
            text = "Set Photo Location"
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.trackwrite_text))
        }
        val subtitleText = TextView(this).apply {
            text = "Select a point on the map or type WGS84 coordinates."
            textSize = 13f
            setTextColor(0xFF5A6760.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        headerLayout.addView(titleText)
        headerLayout.addView(subtitleText)
        root.addView(headerLayout)

        // 1. Search Card (if key configured)
        if (key.isNotBlank()) {
            val searchCard = cardLayout()
            searchCard.addView(sectionTitle("Search AMap Places"))
            searchCard.addView(queryInput)
            val searchButton = customButton("Search AMap", R.drawable.button_primary, 0xFFFFFFFF.toInt()) { search() }
            val buttonContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(10), 0, 0)
                addView(searchButton)
            }
            searchCard.addView(buttonContainer)
            root.addView(searchCard)
        }

        // 2. Direct Coordinates Entry Card
        val coordCard = cardLayout()
        coordCard.addView(sectionTitle("Direct WGS84 Coordinates"))
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(latInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Space(this@ManualLocationActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12), LinearLayout.LayoutParams.MATCH_PARENT)
            })
            addView(lonInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        coordCard.addView(inputRow)
        val useTypedBtn = customButton("Use Typed Coordinates", R.drawable.button_secondary, getColor(R.color.trackwrite_accent)) {
            useTypedCoordinates()
        }
        val coordButtonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
            addView(useTypedBtn)
        }
        coordCard.addView(coordButtonContainer)
        root.addView(coordCard)

        // 3. Selection Status Card
        val statusCard = cardLayout()
        statusCard.addView(sectionTitle("Selected Location"))
        statusCard.addView(selectedText)
        root.addView(statusCard)

        // 4. Map Card (if key configured)
        if (key.isNotBlank()) {
            val mapCard = cardLayout().apply {
                // Give map section solid height inside layouts
                val params = layoutParams as LinearLayout.LayoutParams
                params.height = dp(320)
                layoutParams = params
            }
            mapCard.addView(sectionTitle("Map View (AMap JSAPI)"))
            val webViewFrame = FrameLayout(this).apply {
                setBackgroundResource(R.drawable.card_background)
                setPadding(dp(1), dp(1), dp(1), dp(1))
                addView(webView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }
            mapCard.addView(webViewFrame, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            root.addView(mapCard)
        } else {
            root.addView(cardLayout().apply {
                val warningLabel = TextView(this@ManualLocationActivity).apply {
                    text = "AMap Web Key is not configured in local.properties. Map search and interactive selection are unavailable. Please type WGS84 coordinates directly."
                    textSize = 12f
                    setTextColor(0xFFA93226.toInt())
                }
                addView(warningLabel)
            })
        }

        // 5. Actions Card
        val actionsCard = cardLayout()
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val confirmBtn = customButton("Confirm Selection", R.drawable.button_primary, 0xFFFFFFFF.toInt()) { confirm() }
            val cancelBtn = customButton("Cancel", R.drawable.button_danger, 0xFFA93226.toInt()) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }.apply {
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dp(8), 0)
                }
                layoutParams = lp
            }
            addView(cancelBtn)
            addView(confirmBtn)
        }
        actionsCard.addView(actionsRow)
        root.addView(actionsCard)

        val mainScroll = ScrollView(this).apply {
            addView(root)
            isFillViewport = true
        }
        setContentView(mainScroll)

        if (key.isNotBlank()) {
            webView.loadDataWithBaseURL(
                "https://webapi.amap.com/",
                mapHtml(key, securityJsCode),
                "text/html",
                "UTF-8",
                null,
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun cardLayout(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundResource(R.drawable.card_background)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(14))
            }
            layoutParams = params
        }

    private fun sectionTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text.uppercase()
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.trackwrite_accent))
            setPadding(0, 0, 0, dp(8))
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
