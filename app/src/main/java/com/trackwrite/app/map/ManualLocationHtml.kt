package com.trackwrite.app.map

data class ManualLocationMapText(
    val failedToLoad: String,
    val mapErrorPrefix: String,
    val notInitialized: String,
    val mapSelection: String,
    val mapTap: String,
    val notReady: String,
    val searchFailedPrefix: String,
)

fun mapHtml(amapKey: String, securityJsCode: String, text: ManualLocationMapText): String {
    val securityConfig = if (securityJsCode.isBlank()) {
        ""
    } else {
        "window._AMapSecurityConfig = { securityJsCode: '${jsString(securityJsCode)}' };"
    }
    val failedToLoad = jsString(text.failedToLoad)
    val mapErrorPrefix = jsString(text.mapErrorPrefix)
    val notInitialized = jsString(text.notInitialized)
    val mapSelection = jsString(text.mapSelection)
    val mapTap = jsString(text.mapTap)
    val notReady = jsString(text.notReady)
    val searchFailedPrefix = jsString(text.searchFailedPrefix)

    return """
<!doctype html>
<html>
<head>
  <meta name="viewport" content="initial-scale=1,maximum-scale=1,user-scalable=no,width=device-width">
  <style>
    html, body, #map { width: 100%; height: 100%; margin: 0; }
    #map { position: absolute; inset: 0; background: transparent; }
  </style>
  <script>
    $securityConfig
    function bridgeError(message) {
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
      if (window.TrackWrite && TrackWrite.select) {
        TrackWrite.select(lat, lng, label || '$mapSelection');
      }
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

    window.trackwriteSelectResult = function(lng, lat, label) {
      if (!map || typeof AMap === 'undefined') {
        bridgeError('$notReady');
        return 'not_ready';
      }
      select(lng, lat, label);
      return 'selected';
    };

    window.trackwriteSearch = function(query) {
      if (!query) {
        if (window.TrackWrite && TrackWrite.searchResults) {
          TrackWrite.searchResults('[]');
        }
        return;
      }
      if (!map || typeof AMap === 'undefined') {
        bridgeError('$notReady');
        return;
      }
      AMap.plugin(['AMap.PlaceSearch'], function() {
        var placeSearch = new AMap.PlaceSearch({
          city: '北京',
          citylimit: false,
          pageSize: 8,
          pageIndex: 1,
          extensions: 'base'
        });
        placeSearch.search(query, function(status, result) {
          if (status === 'no_data') {
            if (window.TrackWrite && TrackWrite.searchResults) {
              TrackWrite.searchResults('[]');
            }
            return;
          }
          if (status !== 'complete') {
            var info = resultInfo(result);
            bridgeError('$searchFailedPrefix' + status + (info ? ' (' + info + ')' : ''));
            return;
          }
          var pois = result && result.poiList && result.poiList.pois
            ? result.poiList.pois
            : [];
          var payload = pois
            .filter(function(poi) { return poi && poi.location; })
            .slice(0, 8)
            .map(function(poi) {
              return {
                name: String(poi.name || ''),
                address: String(poi.address || ''),
                latitude: Number(poi.location.lat),
                longitude: Number(poi.location.lng)
              };
            });
          if (window.TrackWrite && TrackWrite.searchResults) {
            TrackWrite.searchResults(JSON.stringify(payload));
          }
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
