package com.trackwrite.app.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

        setContent {
            TrackWriteTheme(AppSettingsStore(this).current().appearance) {
                ManualLocationScreen(
                    state = uiState,
                    onQueryChanged = { uiState = uiState.copy(query = it) },
                    onLatitudeChanged = { uiState = uiState.copy(latitudeText = it) },
                    onLongitudeChanged = { uiState = uiState.copy(longitudeText = it) },
                    onSearch = { search() },
                    onUseTypedCoordinates = { useTypedCoordinates() },
                    onConfirm = { confirm() },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    webViewFactory = {
                        WebView(this).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            addJavascriptInterface(MapBridge(), "TrackWrite")
                            loadDataWithBaseURL(
                                "https://webapi.amap.com/",
                                mapHtml(key, securityJsCode),
                                "text/html",
                                "UTF-8",
                                null,
                            )
                            webView = this
                        }
                    },
                )
            }
        }
    }

    private fun search() {
        if (!uiState.hasAmapKey) {
            uiState = uiState.copy(message = getString(R.string.amap_key_missing))
            return
        }
        val query = JSONObject.quote(uiState.query)
        webView?.evaluateJavascript("window.trackwriteSearch($query);", null)
    }

    private fun useTypedCoordinates() {
        val lat = uiState.latitudeText.toDoubleOrNull()
        val lon = uiState.longitudeText.toDoubleOrNull()
        if (lat == null || lon == null) {
            uiState = uiState.copy(message = getString(R.string.invalid_coordinates))
            return
        }
        selectWgs84(lat, lon, getString(R.string.typed_wgs84_coordinates))
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
            latitudeText = latitude.toString(),
            longitudeText = longitude.toString(),
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

    private inner class MapBridge {
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
    val query: String = "",
    val latitudeText: String = "",
    val longitudeText: String = "",
    val message: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualLocationScreen(
    state: ManualLocationUiState,
    onQueryChanged: (String) -> Unit,
    onLatitudeChanged: (String) -> Unit,
    onLongitudeChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onUseTypedCoordinates: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    webViewFactory: () -> WebView,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.manual_location_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.manual_location_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.hasAmapKey) {
                PickerCard(title = stringResource(R.string.search_places), icon = Icons.Default.Search) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChanged,
                        label = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSearch, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search_amap))
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.amap_key_missing),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            PickerCard(title = stringResource(R.string.direct_coordinates), icon = Icons.Default.MyLocation) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.latitudeText,
                        onValueChange = onLatitudeChanged,
                        label = { Text(stringResource(R.string.latitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.longitudeText,
                        onValueChange = onLongitudeChanged,
                        label = { Text(stringResource(R.string.longitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onUseTypedCoordinates, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.use_typed_coordinates))
                }
            }

            PickerCard(title = stringResource(R.string.selected_location), icon = Icons.Default.Check) {
                Text(
                    text = state.message.ifBlank { stringResource(R.string.no_location_selected) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.message.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }

            if (state.hasAmapKey) {
                PickerCard(title = stringResource(R.string.map_view), icon = Icons.Default.Map) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    ) {
                        AndroidView(
                            factory = { webViewFactory() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.confirm_selection))
                }
            }
        }
    }
}

@Composable
private fun PickerCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
