package com.trackwrite.app.map

data class ManualLocationMapText(
    val searchQueryRequired: String,
    val noResultsTemplate: String,
    val foundResultsTemplate: String,
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
      top: 8px;
      left: 8px;
      right: 8px;
      max-height: 44%;
      overflow: auto;
      background: white;
      border: 1px solid #d8ddd8;
      font-family: sans-serif;
      font-size: 13px;
      z-index: 2;
    }
    .item { padding: 8px; border-bottom: 1px solid #eef1ee; }
    .hint { padding: 8px; color: #4b5a53; }
  </style>
  <script>
    $securityConfig
    function panelMessage(message) {
      var panel = document.getElementById('panel');
      if (panel) {
        panel.innerHTML = '<div class="hint">' + message + '</div>';
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
      bridgeError('AMap error: ' + message);
      return false;
    };
  </script>
  <script
    src="https://webapi.amap.com/maps?v=2.0&key=$amapKey&plugin=AMap.PlaceSearch"
    onerror="bridgeError('AMap JS failed to load. Check network, Web key, security code, and allowed domain settings.')">
  </script>
</head>
<body>
  <div id="map"></div>
  <div id="panel"><div class="hint">Loading AMap...</div></div>
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
      TrackWrite.select(lat, lng, label || 'Map selection');
    }

    function initMap() {
      if (typeof AMap === 'undefined') {
        bridgeError('AMap did not initialize. Check Web key and TRACKWRITE_AMAP_SECURITY_JS_CODE.');
        return;
      }
      map = new AMap.Map('map', {
        viewMode: '2D',
        zoom: 13,
        center: [116.397428, 39.90923]
      });
      map.on('complete', function() {
        panelMessage('Tap the map or search for a place.');
        if (window.TrackWrite && TrackWrite.ready) {
          TrackWrite.ready();
        }
      });
      map.on('click', function(event) {
        select(event.lnglat.lng, event.lnglat.lat, 'Map tap');
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
        bridgeError('AMap is not ready. Check map loading errors first.');
        return;
      }
      panelMessage('Searching...');
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
            bridgeError('Search failed: ' + status + (info ? ' (' + info + ')' : ''));
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
