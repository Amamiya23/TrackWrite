# Research: Android map/search providers for manual photo location picker MVP

- **Query**: Research map and place-search provider options for an Android manual photo location picker MVP. Compare Google Maps/Places, MapLibre/OpenStreetMap/Nominatim, and pragmatic Android alternatives. Focus on licensing/API keys/offline implications, implementation effort, China/global availability caveats, and MVP recommendation.
- **Scope**: external
- **Date**: 2026-05-26

## Findings

### Files Found

| File Path | Description |
|---|---|
| N/A | External provider research only; no project code searched for this topic. |

### Provider comparison

| Option | Map display | Place/search | API keys / billing | Licensing / attribution | Offline implications | Global / China caveats | Android MVP implementation effort |
|---|---|---|---|---|---|---|---|
| Google Maps SDK + Places SDK for Android | Native Google map, markers, gestures, Maps Compose/KTX options | Places Autocomplete, Text Search, Place Details, geocoding | Google Cloud project, billing account, enabled APIs, API key required. Maps SDK SKU has unlimited free usage in current global price table; Places/Geocoding SKUs have free caps then pay-as-you-go. | Google Maps Platform Terms; Places requires displaying returned attributions when showing place info. | Good online-first SDK. Google map data/services should be treated as network/provider dependent; not an offline-map solution for downloaded areas. | Google coverage table lists China map tiles/geocoding/driving/walking as available, traffic approximate; practical access can still depend on local network/device/service availability, especially on non-GMS Android devices. | Lowest effort for a polished MVP: official Android SDKs, built-in widgets/APIs, strong search quality, straightforward marker/camera interactions. |
| MapLibre Native Android + OSM tiles + Nominatim | Open-source native renderer; app supplies a style/tile source, e.g. OSM-derived raster/vector tiles | Nominatim search/reverse APIs, or third-party/self-hosted geocoder | MapLibre itself has no platform API key requirement; tile/geocoder providers may require keys/contracts. Public OSM tile and Nominatim endpoints do not use API keys but have strict acceptable-use policies. | MapLibre Native is BSD 2-Clause. OSM data is ODbL and requires attribution. Public OSM tiles require visible OSM attribution. | Public `tile.openstreetmap.org` explicitly forbids offline/download-for-later and bulk prefetch. Offline requires self-hosted/packaged tiles or a provider that explicitly allows offline. | OSM coverage varies by region/community. Public OSM/Nominatim services are globally reachable on a best-effort basis with no SLA; China availability may depend on network access and data quality. | Medium/high effort: integrate MapLibre lifecycle/style, choose and configure tile provider, add attribution/cache/User-Agent, build search UI, rate-limit/cache Nominatim or use a paid geocoder. |
| Android framework Geocoder | No map display by itself | Forward/reverse geocoding via device backend where present | No app-specific map/search API key; depends on platform backend/service availability. | Android API, but returned data source/quality depends on device implementation. | Not an offline guarantee; Android docs historically expose `isPresent()` because backend service may be unavailable. | Highly device/OEM dependent; unreliable as the only global place search provider, especially on AOSP/no-GMS devices. | Low code but insufficient for rich place search/autocomplete/map picker by itself. Useful only as fallback for reverse address labels. |
| Geo URI / Google Maps intent handoff | External installed map app, not embedded | External map app search/picker behavior | No embedded SDK key for handoff; depends on installed apps. | User leaves/uses another app; app receives only what intent flow supports. | External app may have its own offline/cache behavior, outside TrackWrite control. | Depends on installed apps and regional app ecosystem. | Very low effort, but poor in-app UX and weak data return/control; pragmatic fallback only. |
| Mapbox Maps/Search SDK | Native map and search SDKs | Mapbox Search/geocoding/search box | Mapbox account/API key required; paid service terms/pricing apply. | Proprietary service terms; third-party data/content notices. | Mapbox has commercial offline/self-hosted products in some tiers, but terms/product fit must be checked for the exact plan. | Global commercial provider; coverage and China constraints should be verified for target launch regions. | Similar to Google for maps; extra commercial/legal review. More useful if Mapbox-specific styling/offline/commercial terms are desired. |
| Huawei Map Kit / Site Kit | Native Huawei map on HMS devices | Site Kit search/place services | Huawei developer setup/app credentials required. | Huawei service terms and attribution requirements. | Provider-dependent; offline not assumed for MVP. | Pragmatic China/Huawei ecosystem alternative; less universal on Google Play/GMS-first devices. | Medium effort; best as regional/provider abstraction or future China/HMS build variant, not simplest global MVP baseline. |
| AMap/Gaode, Baidu Maps, Tencent Maps | Native China-focused maps | China-focused POI/search/geocoding | Provider keys, Chinese developer accounts, compliance/terms required. | Proprietary Chinese provider terms and required attribution/logos. | Provider-dependent; offline availability varies by SDK/product. | Strongest practical China mainland options; weaker/global availability and international developer onboarding may be limiting. | Medium/high effort and region-specific; likely separate provider abstraction if China launch is first-class. |

### Code Patterns

No internal code patterns searched; this is a provider-selection research note for upcoming Android implementation.

### External References

- [Google Maps SDK for Android overview](https://developers.google.com/maps/documentation/android-sdk/overview) — SDK displays Google Maps in Android apps and supports markers, overlays, gestures; docs state API key, billing, pricing, and terms apply.
- [Google Maps SDK setup](https://developers.google.com/maps/documentation/android-sdk/get-api-key) — setup flow requires account/billing, enabling SDK, and configuring API key.
- [Google Places SDK for Android overview](https://developers.google.com/maps/documentation/places/android-sdk/overview) — current Places SDK (New) provides Autocomplete, Place Details, Nearby Search, Text Search, Photos, Place IDs; requires billing and Places API enabled; attributions must be displayed when showing sourced place data.
- [Google Maps Platform pricing](https://developers.google.com/maps/billing-and-pricing/pricing) — current global table lists Maps SDK with unlimited free usage, and Places/Geocoding SKUs with free usage caps and per-1000-event pricing beyond caps.
- [Google Maps Platform coverage](https://developers.google.com/maps/coverage) — country/region table includes core mapping features; China row shows map tiles/geocoding available and traffic approximate.
- [MapLibre Native Android API](https://maplibre.org/maplibre-native/android/api/) — MapLibre Native Android 13.0.2 API docs; packages include maps, annotations, location, style, and offline APIs.
- [MapLibre Android quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/) — Android integration uses Maven dependency, `MapView`, `MapLibre.getInstance`, lifecycle forwarding, and `map.setStyle(...)`.
- [MapLibre Native license](https://github.com/maplibre/maplibre-native/blob/main/LICENSE.md) — BSD 2-Clause license.
- [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/) — OSM data is free but public tile servers are best-effort/no-SLA; require visible attribution, valid User-Agent, caching, no bulk download; offline use/download areas are not permitted on `tile.openstreetmap.org`.
- [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/) — public Nominatim has maximum 1 request/second, requires identifying Referer/User-Agent and attribution, forbids autocomplete/client-side autocomplete usage, bulk geocoding, systematic queries; apps must be able to switch service without software update and should proxy/cache.
- [Nominatim API overview](https://nominatim.org/release-docs/latest/api/Overview/) — API endpoints include `/search`, `/reverse`, `/lookup`, `/status`, and `/details`.
- [OpenStreetMap ODbL wiki](https://wiki.openstreetmap.org/wiki/Open_Database_License) — OSM data reuse is under Open Database License (ODbL) share-alike license for data; official license resources and attribution guidelines are linked.
- [Mapbox Terms of Service](https://www.mapbox.com/legal/tos) — Mapbox services generally require account/API keys and paid charges under public pricing or marketplace pricing; terms include service/product policies and licensing constraints.
- Huawei Map Kit/Site Kit docs were attempted, but returned minimal “Document” content via fetch; details should be verified directly in Huawei developer portal before choosing HMS/China path.

### MVP recommendation

For a manual photo location picker MVP targeting the broadest Android/GMS audience, **Google Maps SDK for Android + Places SDK for Android (New)** is the pragmatic default: lowest implementation effort, best built-in in-app map/search UX, official Android support, and predictable billing/API-key setup. Use only minimal Places fields/SKUs needed for picking a coordinate and human-readable label.

If avoiding Google dependencies/API keys or requiring open-source map rendering is a hard constraint, **MapLibre + a commercial OSM-derived tile/search provider** is more realistic than direct public OSM tiles/Nominatim for production because public OSM services prohibit offline downloads and impose strict rate/usage limits. Direct public Nominatim is acceptable only for very low-volume deliberate search without autocomplete and with caching/rate limiting.

If mainland China support is a first-class MVP requirement, treat it as a separate provider decision: evaluate Huawei/HMS and China-native providers (AMap/Baidu/Tencent) behind an abstraction rather than assuming Google or public OSM services will provide reliable practical availability on all devices/networks.

### Related Specs

- Not searched for this external provider comparison. Relevant project specs may exist under `.trellis/spec/frontend/` or `.trellis/spec/backend/` once Android architecture work begins.

## Caveats / Not Found

- Pricing and terms change frequently; verify provider pricing/terms again before production release.
- Google coverage table indicates feature availability by region, not real-world reachability on every Android device/network.
- Huawei documentation fetch did not expose usable details in this environment; direct portal verification is needed for HMS specifics.
- No internal TrackWrite Android code was searched or evaluated for provider integration points.
