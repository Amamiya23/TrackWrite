package com.trackwrite.app.map

data class ManualLocationMapText(
    val searchQueryRequired: String,
    val noResultsTemplate: String,
    val foundResultsTemplate: String,
    val failedToLoad: String,
    val mapErrorPrefix: String,
    val notInitialized: String,
    val mapSelection: String,
    val mapTap: String,
    val notReady: String,
    val searching: String,
    val searchFailedPrefix: String,
)

fun mapHtml(amapKey: String, securityJsCode: String, text: ManualLocationMapText): String {
    val securityConfig = if (securityJsCode.isBlank()) {
        ""
    } else {
        "window._AMapSecurityConfig = { securityJsCode: '${jsString(securityJsCode)}' };"
    }
    val searchQueryRequired = jsString(text.searchQueryRequired)
    val noResultsTemplate = jsString(text.noResultsTemplate)
    val foundResultsTemplate = jsString(text.foundResultsTemplate)
    val failedToLoad = jsString(text.failedToLoad)
    val mapErrorPrefix = jsString(text.mapErrorPrefix)
    val notInitialized = jsString(text.notInitialized)
    val mapSelection = jsString(text.mapSelection)
    val mapTap = jsString(text.mapTap)
    val notReady = jsString(text.notReady)
    val searching = jsString(text.searching)
    val searchFailedPrefix = jsString(text.searchFailedPrefix)

    return """
<!doctype html>
<html>
<head>
  <meta name="viewport" content="initial-scale=1,maximum-scale=1,user-scalable=no,width=device-width">
  <style>
    html, body { width: 100%; height: 100%; margin: 0; }
    #map {
      position: absolute;
      inset: 0;
      background: #e8ede8;
      z-index: 1;
    }
    #panel {
      position: absolute;
      top: 112px;
      left: 12px;
      right: 12px;
      display: none;
      max-height: 34%;
      overflow: auto;
      background: white;
      border: 1px solid #d8ddd8;
      border-radius: 12px;
      box-shadow: 0 4px 16px rgba(38, 50, 45, 0.18);
      font-family: sans-serif;
      font-size: 13px;
      z-index: 2;
    }
    .item { padding: 10px 12px; border-bottom: 1px solid #eef1ee; }
    .hint { padding: 10px 12px; color: #4b5a53; }
  </style>
  <script>
    $securityConfig
    function panelMessage(message) {
      var panel = document.getElementById('panel');
      if (panel) {
        panel.style.display = 'block';
        panel.innerHTML = '<div class="hint">' + message + '</div>';
      }
    }
    function hidePanel() {
      var panel = document.getElementById('panel');
      if (panel) {
        panel.innerHTML = '';
        panel.style.display = 'none';
      }
    }
    function bridgeStatus(message) {
      if (window.TrackWrite && TrackWrite.status) {
        TrackWrite.status(message);
      }
    }
    function bridgeError(message) {
      panelMessage(message);
      if (window.TrackWrite && TrackWrite.error) {
        TrackWrite.error(message);
      }
    }
    window.onerror = function(message) {
      bridgeError('$mapErrorPrefix' + message);
      return false;
    };
  </script>
  <script
    src="https://webapi.amap.com/maps?v=2.0&key=$amapKey&plugin=AMap.PlaceSearch"
    onerror="bridgeError('$failedToLoad')">
  </script>
</head>
<body>
  <div id="map"></div>
  <div id="panel"></div>
  <script>
    var map = null;
    var marker = null;

    function resultInfo(result) {
      if (!result) return '';
      return result.info || result.message || result.infocode || '';
    }

    function select(lng, lat, label) {
      if (!marker) {
        marker = new AMap.Marker({ map: map });
      }
      marker.setPosition([lng, lat]);
      map.setCenter([lng, lat]);
      hidePanel();
      TrackWrite.select(lat, lng, label || '$mapSelection');
    }

    function initMap() {
      if (typeof AMap === 'undefined') {
        bridgeError('$notInitialized');
        return;
      }
      map = new AMap.Map('map', {
        viewMode: '2D',
        zoom: 13,
        center: [116.397428, 39.90923]
      });
      map.on('complete', function() {
        if (window.TrackWrite && TrackWrite.ready) {
          TrackWrite.ready();
        }
      });
      map.on('click', function(event) {
        select(event.lnglat.lng, event.lnglat.lat, '$mapTap');
      });
    }

    window.trackwriteSearch = function(query) {
      var panel = document.getElementById('panel');
      if (!query) {
        panelMessage('$searchQueryRequired');
        bridgeStatus('$searchQueryRequired');
        return;
      }
      if (!map || typeof AMap === 'undefined') {
        bridgeError('$notReady');
        return;
      }
      panelMessage('$searching');
      AMap.plugin(['AMap.PlaceSearch'], function() {
        var placeSearch = new AMap.PlaceSearch({
          city: '北京',
          citylimit: false,
          pageSize: 8,
          pageIndex: 1,
          extensions: 'base',
          map: map,
          panel: 'panel',
          autoFitView: true
        });
        placeSearch.search(query, function(status, result) {
          if (status !== 'complete') {
            var info = resultInfo(result);
            bridgeError('$searchFailedPrefix' + status + (info ? ' (' + info + ')' : ''));
            return;
          }
          if (!result.poiList || !result.poiList.pois || !result.poiList.pois.length) {
            var noResultsMessage = '$noResultsTemplate'.replace('{query}', query);
            panelMessage(noResultsMessage);
            bridgeStatus(noResultsMessage);
            return;
          }
          var foundMessage = '$foundResultsTemplate'.replace('{count}', result.poiList.pois.length);
          bridgeStatus(foundMessage);
          panel.innerHTML = '';
          panel.style.display = 'block';
          result.poiList.pois.forEach(function(poi) {
            if (!poi.location) return;
            var item = document.createElement('div');
            item.className = 'item';
            item.textContent = poi.name + ' - ' + (poi.address || '');
            item.onclick = function() {
              select(poi.location.lng, poi.location.lat, poi.name);
            };
            panel.appendChild(item);
          });
        });
      });
    };

    initMap();
  </script>
</body>
</html>
""".trimIndent()
}

private fun jsString(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
