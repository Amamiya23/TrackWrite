package com.trackwrite.app.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnLayout
import com.trackwrite.app.R
import com.trackwrite.app.settings.AppSettingsStore
import com.trackwrite.app.ui.TrackAlpha
import com.trackwrite.app.ui.TrackShape
import com.trackwrite.app.ui.TrackSpacing
import com.trackwrite.app.ui.TrackWriteTheme
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ManualLocationActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var uiState by mutableStateOf(ManualLocationUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

        root.addView(
            composeOverlay {
                ManualLocationSearchOverlay(
                    state = uiState,
                    onBack = ::cancel,
                    onQueryChanged = ::updateQuery,
                    onSearch = ::search,
                    onResultSelected = ::selectSearchResult,
                )
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )

        root.addView(
            composeOverlay {
                ManualLocationSelectionOverlay(
                    selection = uiState.selection,
                    onConfirm = ::confirm,
                )
            },
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun createMapWebView(key: String, securityJsCode: String): WebView =
        WebView(this).apply {
            setBackgroundColor(themeBackgroundColor())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    blockDisallowedNavigation(view, request.url)
            }
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

    private fun updateQuery(query: String) {
        uiState = uiState.copy(
            query = query,
            searchState = ManualLocationSearchState.Idle,
        )
    }

    private fun search() {
        if (uiState.searchState is ManualLocationSearchState.Searching) return
        if (!uiState.hasAmapKey) {
            showSearchError(getString(R.string.amap_key_missing))
            return
        }
        val query = uiState.query.trim()
        if (query.isBlank()) {
            showSearchError(getString(R.string.search_query_required))
            return
        }
        val target = webView
        if (!uiState.mapReady || target == null) {
            showSearchError(getString(R.string.amap_loading))
            return
        }
        uiState = uiState.copy(
            query = query,
            searchState = ManualLocationSearchState.Searching,
        )
        target.evaluateJavascript(
            "if (window.trackwriteSearch) { window.trackwriteSearch(${JSONObject.quote(query)}); 'started'; } else { 'not_ready'; }",
        ) { result ->
            if (result.contains("not_ready")) {
                showSearchError(getString(R.string.amap_loading))
            }
        }
    }

    private fun selectSearchResult(result: AmapSearchResult) {
        val target = webView
        if (!uiState.mapReady || target == null) {
            showSearchError(getString(R.string.amap_not_ready))
            return
        }
        uiState = uiState.copy(searchState = ManualLocationSearchState.Idle)
        target.evaluateJavascript(
            "if (window.trackwriteSelectResult) { window.trackwriteSelectResult(${result.longitude}, ${result.latitude}, ${JSONObject.quote(result.name)}); } else { 'not_ready'; }",
        ) { evaluation ->
            if (evaluation.contains("not_ready")) {
                showSearchError(getString(R.string.amap_not_ready))
            }
        }
    }

    private fun showSearchError(message: String) {
        uiState = uiState.copy(searchState = ManualLocationSearchState.Error(message))
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun confirm() {
        val selection = uiState.selection ?: return
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_LATITUDE, selection.latitude)
                .putExtra(EXTRA_LONGITUDE, selection.longitude)
                .putExtra(EXTRA_LABEL, selection.label),
        )
        finish()
    }

    private fun selectWgs84(latitude: Double, longitude: Double, label: String) {
        uiState = uiState.copy(
            selection = ManualLocationSelection(
                latitude = latitude,
                longitude = longitude,
                label = label,
            ),
            searchState = ManualLocationSearchState.Idle,
        )
    }

    private fun blockDisallowedNavigation(view: WebView, uri: Uri?): Boolean {
        if (isAllowedAmapNavigation(uri?.scheme, uri?.host)) return false
        view.removeJavascriptInterface("TrackWrite")
        return true
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
            failedToLoad = getString(R.string.amap_script_load_failed),
            mapErrorPrefix = getString(R.string.amap_error_prefix),
            notInitialized = getString(R.string.amap_not_initialized),
            mapSelection = getString(R.string.amap_map_selection),
            mapTap = getString(R.string.amap_map_tap),
            notReady = getString(R.string.amap_not_ready),
            searchFailedPrefix = getString(R.string.amap_search_failed_prefix),
        )

    private inner class MapBridge {
        @JavascriptInterface
        fun ready() {
            runOnUiThread {
                uiState = uiState.copy(mapReady = true)
            }
        }

        @JavascriptInterface
        fun searchResults(payload: String?) {
            val results = parseAmapSearchResults(payload)
            runOnUiThread {
                if (uiState.searchState !is ManualLocationSearchState.Searching) return@runOnUiThread
                uiState = uiState.copy(
                    searchState = when {
                        results == null -> ManualLocationSearchState.Error(
                            getString(R.string.amap_search_results_invalid),
                        )
                        results.isEmpty() -> ManualLocationSearchState.Empty
                        else -> ManualLocationSearchState.Results(results)
                    },
                )
            }
        }

        @JavascriptInterface
        fun select(latitude: Double, longitude: Double, label: String?) {
            if (!isValidAmapBridgeCoordinate(latitude, longitude)) {
                error(getString(R.string.manual_location_invalid))
                return
            }
            val wgs84 = AmapCoordinateConverter.gcj02ToWgs84(latitude, longitude)
            runOnUiThread {
                this@ManualLocationActivity.selectWgs84(
                    latitude = wgs84.latitude,
                    longitude = wgs84.longitude,
                    label = label?.takeIf(String::isNotBlank) ?: getString(R.string.amap_selection),
                )
            }
        }

        @JavascriptInterface
        fun error(message: String?) {
            runOnUiThread {
                showSearchError(message ?: getString(R.string.amap_error))
            }
        }
    }

    companion object {
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_LABEL = "label"
    }
}

internal fun isAllowedAmapNavigation(scheme: String?, host: String?): Boolean =
    scheme.equals("https", ignoreCase = true) &&
        host.equals("webapi.amap.com", ignoreCase = true)

internal fun isValidAmapBridgeCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

internal data class AmapSearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

internal fun parseAmapSearchResults(payload: String?): List<AmapSearchResult>? {
    if (payload.isNullOrBlank()) return null
    return runCatching {
        val json = JSONArray(payload)
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                if (name.isBlank() || !isValidAmapBridgeCoordinate(latitude, longitude)) continue
                add(
                    AmapSearchResult(
                        name = name,
                        address = item.optString("address").trim(),
                        latitude = latitude,
                        longitude = longitude,
                    ),
                )
                if (size == MAX_AMAP_SEARCH_RESULTS) break
            }
        }
    }.getOrNull()
}

private const val MAX_AMAP_SEARCH_RESULTS = 8

private data class ManualLocationUiState(
    val hasAmapKey: Boolean = false,
    val mapReady: Boolean = false,
    val query: String = "",
    val searchState: ManualLocationSearchState = ManualLocationSearchState.Idle,
    val selection: ManualLocationSelection? = null,
)

private sealed interface ManualLocationSearchState {
    data object Idle : ManualLocationSearchState
    data object Searching : ManualLocationSearchState
    data object Empty : ManualLocationSearchState
    data class Results(val items: List<AmapSearchResult>) : ManualLocationSearchState
    data class Error(val message: String) : ManualLocationSearchState
}

private data class ManualLocationSelection(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ManualLocationSearchOverlay(
    state: ManualLocationUiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onResultSelected: (AmapSearchResult) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searching = state.searchState is ManualLocationSearchState.Searching
    val searchEnabled = state.hasAmapKey && state.mapReady && state.query.isNotBlank() && !searching

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(TrackSpacing.x3),
        verticalArrangement = Arrangement.spacedBy(TrackSpacing.x2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrackSpacing.x2),
        ) {
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
                shape = TrackShape.control,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.manual_location_back),
                )
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                enabled = state.hasAmapKey && !searching,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                trailingIcon = {
                    IconButton(
                        onClick = { submitSearch(focusManager, keyboardController, onSearch) },
                        enabled = searchEnabled,
                    ) {
                        if (searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.search_amap),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchEnabled) {
                            submitSearch(focusManager, keyboardController, onSearch)
                        }
                    },
                ),
                shape = TrackShape.control,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        ManualLocationSearchFeedback(
            state = state,
            onResultSelected = onResultSelected,
        )
    }
}

private fun submitSearch(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onSearch: () -> Unit,
) {
    focusManager.clearFocus()
    keyboardController?.hide()
    onSearch()
}

@Composable
private fun ManualLocationSearchFeedback(
    state: ManualLocationUiState,
    onResultSelected: (AmapSearchResult) -> Unit,
) {
    when {
        !state.hasAmapKey -> SearchFeedbackPanel(
            text = stringResource(R.string.amap_key_missing),
            isError = true,
        )
        state.searchState is ManualLocationSearchState.Error -> SearchFeedbackPanel(
            text = state.searchState.message,
            isError = true,
        )
        !state.mapReady -> SearchFeedbackPanel(
            text = stringResource(R.string.amap_loading),
            loading = true,
        )
        state.searchState is ManualLocationSearchState.Searching -> SearchFeedbackPanel(
            text = stringResource(R.string.amap_searching),
            loading = true,
        )
        state.searchState is ManualLocationSearchState.Empty -> SearchFeedbackPanel(
            text = stringResource(R.string.amap_no_results_template, state.query),
        )
        state.searchState is ManualLocationSearchState.Results -> SearchResultsPanel(
            results = state.searchState.items,
            onResultSelected = onResultSelected,
        )
        else -> Unit
    }
}

@Composable
private fun SearchFeedbackPanel(
    text: String,
    isError: Boolean = false,
    loading: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackShape.control,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(TrackSpacing.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrackSpacing.x2),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Search,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchResultsPanel(
    results: List<AmapSearchResult>,
    onResultSelected: (AmapSearchResult) -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.34f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TrackShape.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = maxHeight)) {
            itemsIndexed(
                items = results,
                key = { index, result -> "$index:${result.latitude}:${result.longitude}:${result.name}" },
            ) { index, result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onResultSelected(result) }
                        .padding(horizontal = TrackSpacing.x4, vertical = TrackSpacing.x3),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(TrackSpacing.x1))
                    Text(
                        text = result.address.ifBlank { stringResource(R.string.amap_address_unavailable) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (index < results.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = TrackSpacing.x4),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualLocationSelectionOverlay(
    selection: ManualLocationSelection?,
    onConfirm: () -> Unit,
) {
    val selectionAvailable = selection != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(TrackSpacing.x3),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(TrackSpacing.x3),
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
            shape = TrackShape.control,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = TrackSpacing.x3, vertical = TrackSpacing.x2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrackSpacing.x2),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = if (selectionAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selection?.label ?: stringResource(R.string.manual_location_pick_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (selectionAvailable) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selection != null) {
                        Text(
                            text = formatManualLocationCoordinate(selection),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        FilledIconButton(
            onClick = onConfirm,
            enabled = selectionAvailable,
            modifier = Modifier.size(56.dp),
            shape = TrackShape.control,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TrackAlpha.disabled),
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.confirm_selection),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun formatManualLocationCoordinate(selection: ManualLocationSelection): String =
    String.format(Locale.ROOT, "%.6f, %.6f", selection.latitude, selection.longitude)
