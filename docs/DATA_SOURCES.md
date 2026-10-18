# Data sources

Requires one Google Cloud project with billing and an API key restricted to the phone app.

| Need | Source | Notes |
|---|---|---|
| Destination search | **Places API (New)** Autocomplete + Place Details (Android Places SDK) | session tokens to keep cost down |
| Route options w/ traffic | **Routes API** `computeRoutes` (`TRAFFIC_AWARE_OPTIMAL`, `computeAlternativeRoutes`, `extraComputations: TRAFFIC_ON_POLYLINE`) | gives `routeToken`, `duration` vs `staticDuration` (= traffic delay), `speedReadingIntervals` (NORMAL/SLOW/TRAFFIC_JAM) |
| Turn-by-turn guidance, rerouting, voice | **Navigation SDK for Android** | follows the picked route via `CustomRoutesOptions(routeToken)`; billed per destination |
| Maneuver data for the HUD | Navigation SDK **turn-by-turn data feed** (`registerServiceForNavUpdates` → `NavInfo`) | built for "devices not designed around a map" — exactly this |
| Congestion numbers | Routes API `speedReadingIntervals` + `duration − staticDuration` | polled every ~2.5 min while driving |
| Detour options | Routes API alternatives from current position (with heading) → `DetourEvaluator` | accepted detour → `navigator.setDestinations(..., routeToken)` |
| Police reports | **WzSabre** Android plugin via SABRE broadcasts | user-selected source; install plugin on phone |
| Speed cameras | **WzSabre** ExCam database | cached on phone; daily update check when a route starts |
| Map context streets | OpenStreetMap via **OpenFreeMap** vector tiles (no key) | z14 tiles around driver + route ahead; decoded roads cached on phone |

## WzSabre police reports and speed cameras

The user selected [WzSabre](https://wzsabre.rocks/) for both on 2026-10-17. This replaces the planned
OSM camera provider and resolves the police-source decision. No Google API is used for these alerts.

Police reports use the separate `app.sabre.wzsabre` Android app. Download it from WzSabre's site,
install it on the **phone**, and open/configure it before the first trip. `AndroidSabreClient` is a
read-only host for the [published SABRE protocol](https://sabre.app/tech): register a response receiver,
send an explicit handshake to `MainBroadcastReceiver`, then use the advertised fetch action.
Each fetch has a unique request ID; responses are assembled across numbered batches. Requests use
the latest road-snapped position and a 5 km radius, at most once per 30 seconds during an active trip.
This sends that position to the WzSabre plugin, which handles the upstream network connection.
This app does not submit, confirm, or discard reports. Receivers are unregistered on timeout or
trip cancellation. Missing/unresponsive plugins leave navigation and static cameras running.

`WzSabrePoliceProvider` maps visible/hidden police and fixed/mobile camera reports to existing
`AlertKind`s. Reports expire 20 minutes after the later of reporting/confirmation. Known headings
must agree with route direction within 60 degrees. These distance, age, and direction thresholds
are app policy and require road testing. The published protocol is implemented and unit tested;
the installed WzSabre plugin's response behavior with our host package still needs device validation.

`WzSabreCameraRepository` reads `https://wzsabre.rocks/cameras` for version metadata, then downloads
`https://wzsabre.rocks/cameras.download` when the cached version differs. The
[ExCam specification](https://highwayradar.com/excam) defines XZ-compressed UTF-8 JSON lines.
On 2026-10-17, the live link and downloaded metadata both reported dataset **2026-07-19**,
`World cameras by @schuster`. Downloads are validated before atomic cache replacement. Failed updates
retain the last valid database; the full dataset stays in app-private storage and is not bundled in Git.
Only speed/average-speed cameras are mapped to `SPEED_CAMERA`; dummy, red-light-only, and plate-reading
cameras are excluded. Unknown JSON fields are ignored. Camera downloads contain no route or position.

The coordinator combines each provider's latest complete snapshot, projects incidents onto the route,
and sends an alert within 2 km ahead and 60 m of the route. Distances update with position; passed,
removed, expired, or opposite-direction reports clear. Clears are retained for reconnection, and
ending/changing a route cancels polling. The nearest active alert is displayed by the HUD.
Nearest-segment projection cannot disambiguate every overlapping road or loop; validate on hardware.
TomTom for other incident categories remains a separate M7 follow-up.

## Map context streets
**Source (since 2026-10-18): [OpenFreeMap](https://openfreemap.org) vector tiles**, OpenMapTiles schema,
free, no key, served from a CDN. Replaced live Overpass queries: on 2026-10-18 `overpass-api.de` timed
out or returned 406, and the kumi.systems / private.coffee / mail.ru mirrors returned 500 or timed out,
so a drive got no streets at all. A single z14 tile downloaded in 0.16 s.

- `RoadContextProvider` reads `https://tiles.openfreemap.org/planet` (TileJSON, refreshed daily) for the
  weekly build's tile URL, then fetches the z14 tiles (~2 km squares) under the driver and the next 2 km
  of route, at most 6. Requests disclose only those tile coordinates, never the destination.
- `VectorTile` is a minimal MVT/protobuf reader that keeps the `transportation_name` layer (named road
  lines with `class`); classes motorway…tertiary are `major`, `minor` and `*_link` are kept, paths and
  tracks dropped. Geometry is simplified to 5 m and cached per tile as JSON (7 days, ≤60 tiles).
- `select` ranks streets by distance to the nearer of the driver and the route ahead (major roads as if
  150 m closer) and fills a 30 KB snapshot; `RoadContextChunker` splits it into ≤6 KB `road_context`
  parts. Refresh after 600 m of movement, at most every 30 s; offline uses the cache.
- Unnamed streets are not in `transportation_name`, so they are not drawn (rare for drivable roads).

Glasses rendering (docs/HUD_DESIGN.md "Visual spec", canvas row "Richer street map"): residential 2 px at
42%, major 4.5 px at 70%, up to 10 upright labels (turn street bold at the turn, the "after" street next,
then majors), 3 labels in the compact corner, and a turn cue such as "2nd right · after Francisco St"
computed from street geometry (`TurnCue`, blank whenever the count is uncertain).

Attribution: shown on the phone's Glasses screen ("Street data © OpenStreetMap contributors · tiles ©
OpenMapTiles, OpenFreeMap", linking to [OpenStreetMap copyright](https://www.openstreetmap.org/copyright)).
Removed from the HUD at the user's request (2026-10-18); OSM's attribution guidelines allow credit
outside the map where the display is too small.

## Cost guardrails
- Traffic poll only while moving and ACTIVE; stop in the final ~2 km.
- One Routes API call per poll (alternatives included in the same call).
- Places: use autocomplete session tokens.
