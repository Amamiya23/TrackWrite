package com.trackwrite.app.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnLayout
import com.trackwrite.app.R
import com.trackwrite.app.settings.AppSettingsStore
import com.trackwrite.app.ui.TrackWriteTheme
import org.json.JSONObject

class ManualLocationActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var selectedLabel: String = ""
    private var uiState by mutableStateOf(ManualLocationUiState())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = amapWebKey()
        val securityJsCode = amapSecurityJsCode()
        uiState = uiState.copy(hasAmapKey = key.isNotBlank())

        val root = FrameLayout(this).apply {
            setBackgroundColor(themeBackgroundColor())
        }
        if (key.isNotBlank()) {
            root.addView(
                createMapWebView(key, securityJsCode),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        val searchControls = composeOverlay {
            ManualLocationSearchPanel(
                state = uiState,
                onQueryChanged = { uiState = uiState.copy(query = it) },
                onSearch = { search() },
            )
        }
        root.addView(
            searchControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )

        val selectionControls = composeOverlay {
            ManualLocationSelectionPanel(
                state = uiState,
                onConfirm = { confirm() },
                onCancel = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                },
            )
        }
        root.addView(
            selectionControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        setContentView(root)
    }

    private fun composeOverlay(content: @Composable () -> Unit): ComposeView =
        ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TrackWriteTheme(AppSettingsStore(this@ManualLocationActivity).current().appearance) {
                    content()
                }
            }
        }

    private fun createMapWebView(key: String, securityJsCode: String): WebView =
        WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webViewClient = WebViewClient()
            addJavascriptInterface(MapBridge(), "TrackWrite")
            doOnLayout {
                loadDataWithBaseURL(
                    "https://webapi.amap.com/",
                    mapHtml(key, securityJsCode, mapText()),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
            webView = this
        }

    private fun themeBackgroundColor(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        return typedValue.data
    }

    override fun onDestroy() {
        webView?.removeJavascriptInterface("TrackWrite")
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private fun search() {
        if (!uiState.hasAmapKey) {
            uiState = uiState.copy(message = getString(R.string.amap_key_missing))
            return
        }
        if (uiState.query.isBlank()) {
            uiState = uiState.copy(message = getString(R.string.search_query_required))
            return
        }
        val target = webView
        if (target == null) {
            uiState = uiState.copy(message = getString(R.string.amap_loading))
            return
        }
        val query = JSONObject.quote(uiState.query)
        uiState = uiState.copy(
            message = if (uiState.mapReady) getString(R.string.amap_searching) else getString(R.string.amap_loading),
        )
        target.evaluateJavascript(
            "if (window.trackwriteSearch) { window.trackwriteSearch($query); 'started'; } else { 'not_ready'; }",
        ) { result ->
            if (result.contains("not_ready")) {
                uiState = uiState.copy(message = getString(R.string.amap_loading))
            }
        }
    }

    private fun confirm() {
        val lat = selectedLatitude
        val lon = selectedLongitude
        if (lat == null || lon == null) {
            uiState = uiState.copy(message = getString(R.string.select_location_first))
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
        uiState = uiState.copy(
            hasSelection = true,
            message = "$label\n$latitude, $longitude",
        )
    }

    private fun amapWebKey(): String {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString("com.trackwrite.amap.web_key").orEmpty()
    }

    private fun amapSecurityJsCode(): String {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString("com.trackwrite.amap.security_js_code").orEmpty()
    }

    private fun mapText(): ManualLocationMapText =
        ManualLocationMapText(
            searchQueryRequired = getString(R.string.search_query_required),
            noResultsTemplate = getString(R.string.amap_no_results_template),
            foundResultsTemplate = getString(R.string.amap_search_results_found_template),
        )

    private inner class MapBridge {
        @JavascriptInterface
        fun ready() {
            runOnUiThread {
                uiState = uiState.copy(
                    mapReady = true,
                    message = if (uiState.message.isBlank() || uiState.message == getString(R.string.amap_loading)) {
                        getString(R.string.amap_ready)
                    } else {
                        uiState.message
                    },
                )
            }
        }

        @JavascriptInterface
        fun status(message: String?) {
            runOnUiThread {
                if (!message.isNullOrBlank()) {
                    uiState = uiState.copy(message = message)
                }
            }
        }

        @JavascriptInterface
        fun select(latitude: Double, longitude: Double, label: String?) {
            val wgs84 = AmapCoordinateConverter.gcj02ToWgs84(latitude, longitude)
            runOnUiThread {
                this@ManualLocationActivity.selectWgs84(
                    wgs84.latitude,
                    wgs84.longitude,
                    label ?: getString(R.string.amap_selection),
                )
            }
        }

        @JavascriptInterface
        fun error(message: String?) {
            runOnUiThread {
                uiState = uiState.copy(message = message ?: getString(R.string.amap_error))
            }
        }
    }

    companion object {
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_LABEL = "label"
    }
}

private data class ManualLocationUiState(
    val hasAmapKey: Boolean = false,
    val mapReady: Boolean = false,
    val hasSelection: Boolean = false,
    val query: String = "",
    val message: String = "",
)

@Composable
private fun ManualLocationSearchPanel(
    state: ManualLocationUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        if (state.hasAmapKey) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    label = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onSearch, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.search_amap))
                }
            }
        } else {
            Text(
                text = stringResource(R.string.amap_key_missing),
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ManualLocationSelectionPanel(
    state: ManualLocationUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = state.message.ifBlank { stringResource(R.string.no_location_selected) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.message.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    enabled = state.hasSelection,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.confirm_selection))
                }
            }
        }
    }
}
