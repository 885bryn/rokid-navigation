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
| Road alerts (police, etc.) | **No Google API** — see below | `IncidentProvider` interface |
| Map context streets (v2) | OpenStreetMap | see below |

## Police / hazard alerts — the honest situation
Police, speed-trap and most hazard reports in Google Maps come from Waze crowdsourcing. **Google
exposes none of it through public APIs** (not Routes, not Navigation SDK, not Places). Options:

| Option | Police? | Viable for a personal app? |
|---|---|---|
| Waze for Cities (Partner) data feed | yes | No — only for government/partner orgs |
| Scraping Waze live-map endpoints | yes | Works technically, violates Waze ToS, breaks without warning. Not recommended; if the user chooses it anyway, isolate it in its own `IncidentProvider` behind a settings toggle. |
| TomTom Traffic Incidents API | no (accidents, closures, roadworks, jams) | yes, free tier |
| HERE Traffic API v7 incidents | no (same kinds) | yes, free tier |
| Fixed speed / red-light cameras (OSM `highway=speed_camera`, or a camera DB) | cameras only | yes, static data |
| Open the route in Waze on the phone | yes (in Waze) | Doesn't feed our HUD |

Plan: M7 ships `TomTomIncidentProvider` (accidents/closures/construction/jams) + `OsmSpeedCameraProvider`.
Police stays an open decision for the user — the HUD and protocol already support `AlertKind.POLICE`.

## Map context streets (v2)
Google gives no road vector data. To show side streets around the route on the glasses: on route
start, the phone fetches OSM ways in a ~300 m corridor (Overpass API or a Protomaps PMTiles extract),
simplifies them (Douglas–Peucker, ~5 m), and sends `road_context`. Keep it optional and off by default
until the route-only map is solid.

## Cost guardrails
- Traffic poll only while moving and ACTIVE; stop in the final ~2 km.
- One Routes API call per poll (alternatives included in the same call).
- Places: use autocomplete session tokens.
