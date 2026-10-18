# Milestones for Codex

Work top to bottom. Each milestone ends green on `./gradlew :protocol:test :phone:testDebugUnitTest
:glasses:testDebugUnitTest` and with its acceptance checks done. Mark `[x]` as you finish; add
findings (confirmed API names, measured limits) to `docs/ARCHITECTURE.md`.

Hardware needed from M3 on: Android phone + Rokid Glasses + Hi Rokid / Rokid AI App. M1–M2 and most
of M4–M8 logic can be done without it.

## M0 — Project boots
- [ ] Generate Gradle wrapper (8.9+), sync. Fix any version in `gradle/libs.versions.toml` that fails to resolve.
- [ ] Download CxrlSample.zip (link in Rokid docs → Quick Start), unzip into `vendor/rokid-samples/`.
      From the bundled glasses sample (CXRSWithCXRLSample) read the real `cxr-service-bridge` version,
      `CXRServiceBridge` / `Caps` package names, and `StatusListener` signature. Pin the version.
- [ ] Confirm package names/imports for `CxrSessionManager`, `SessionConfig`, `SessionType`,
      `AiInterceptMode`, `ISessionLifecycleCbk`, `ICustomCmdSessionCallback`, `RokidAppStatus` in the client-l AAR.
- [ ] `local.properties` with `MAPS_API_KEY` (user provides).
- **Accept:** `./gradlew assembleDebug` builds both APKs; protocol tests pass.

## M1 — Protocol solid
- [ ] Round-trip tests for every `NavMessage` subtype (extend `ProtocolCodecTest`).
- [x] `PolylineCodec` test against Google's reference sample (`PolylineCodecTest`, scaffold).
- [ ] Helper: `Geo.metersAlongPolyline(points, position)` (project onto nearest segment) + tests.
- **Accept:** `:protocol:test` green.

## M2 — HUD in demo mode (no phone, no glasses)
- [ ] `tools/` JVM script that turns a Routes API response JSON (checked-in sample) into `glasses/src/main/assets/demo_trip.jsonl`.
- [ ] `DemoPhoneLink` plays it in a loop (see class doc).
- [ ] Renderers: maneuver glyphs for every `Maneuver`, `Format` (distance mi/ft & km/m, ETA, durations),
      banner, heading-up route map with interpolation, traffic overlay, status strip.
- [ ] Measure real view size; send in `GlassesHello`.
- **Accept:** on a 480×398 landscape AVD the demo trip looks like `docs/HUD_DESIGN.md` at ≥ 30 fps;
      screenshots committed to `docs/screens/`.

## M3 — Real link (hardware)
- [ ] Implement `CxrGlassesLink` (env check, authorize + `onActivityResult`, CUSTOM_APP connect,
      lifecycle → `LinkState`, send/receive). `GlassesApkInstaller` copies bundled `assets/glasses.apk`
      to `getExternalFilesDir("rokid")`. Gradle task to build+copy the glasses APK into phone assets.
- [ ] Implement `CxrPhoneLink`; `HudViewModel` picks real vs demo link.
- [ ] Phone Setup screen + Debug screen (protocol log, send test frames).
- [ ] Measure max reliable frame size and round-trip latency; update `MAX_FRAME_BYTES`.
- [ ] Log raw glasses key events (broadcasts + KeyEvents) for every gesture; finalize `GlassesKey` mapping.
- **Accept:** phone sends a `maneuver` test frame → glasses HUD shows it; glasses tap → phone log shows `input`.
      Pausing via the AI button and resuming triggers a resync.

## M4 — Search & route picking
- [ ] Places autocomplete screen with recents.
- [ ] `RoutesApiClient.computeRoutes` + parser tests with a recorded JSON response.
- [ ] Route picker: Google map (Maps Compose or the Nav SDK map) with alternatives, ETA, traffic delay.
- **Accept:** pick destination → see 1–3 routes with traffic delay → Go.

## M5 — Guidance to glasses
- [ ] `GoogleNavigationEngine`: navigator init (terms dialog), `setDestinations` with route token,
      start/stop, road-snapped location → `pos`, remaining time/distance → `progress`.
- [ ] `NavUpdatesService` + `NavInfoMapper` (all maneuvers, lanes) + mapper tests.
- [ ] `NavForegroundService` while guiding.
- [ ] Debug "simulate drive" toggle.
- **Accept:** simulated drive on the phone shows matching turns/distances/ETA on the glasses; screen-off keeps working.

## M6 — Traffic & detours
- [ ] `TrafficMonitor` poll loop; speed intervals → `TrafficSegment`s by distance.
- [ ] Detour offer → glasses prompt → accept/keep → `switchRoute`; `detour_resolved` both ways; 30 s expiry; phone UI shows the offer too.
- **Accept:** with a recorded "faster alternative" response injected, the full accept and dismiss flows work end to end.

## M7 — Alerts
- [ ] Coordinator: incidents → distance-ahead along route → `alert` within 2 km, `alert_clear` once passed.
- [ ] `TomTomIncidentProvider` (key in local.properties), `OsmSpeedCameraProvider`.
- [ ] Alert glyphs per `AlertKind`.
- [ ] **Ask the user** before implementing any police source (see DATA_SOURCES.md).
- **Accept:** mock provider police alert counts down and clears in demo + real drive.

## M8 — Menu & settings
- [ ] Menu pages per HUD_DESIGN.md, auto-close, alert pre-emption.
- [ ] Voice toggle, units, brightness (`setGlassBrightness`), default zoom; persisted on phone (DataStore), pushed via `settings`.
- **Accept:** every menu item works from the touchpad alone.

## M9 — Robustness
- [ ] Reconnect strategy: Paused → wait; Closed(needsReauth) → prompt; Rokid AI App killed → notification hint.
- [ ] Stale-message guard by `seq`; route-chunk timeout → `resync`.
- [ ] Battery: lower position rate when stopped; glasses frame rate cap; dim when stationary.
- [ ] 1-hour real drive soak test; note issues here.
