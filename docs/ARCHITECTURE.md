# Architecture

## Big picture

```
┌──────────────────────── Android phone ────────────────────────┐        ┌──────── Rokid Glasses ────────┐
│                                                                │        │                               │
│  Compose UI ── Search (Places) ── RoutePicker (Routes API)     │        │  glasses app (CXR-S)          │
│        │                                                       │        │                               │
│        ▼                                                       │        │  CxrPhoneLink                 │
│  GoogleNavigationEngine (Navigation SDK)                       │        │      │ NavMessage             │
│     ├─ TBT feed → NavUpdatesService → NavInfoMapper            │        │      ▼                        │
│     ├─ road-snapped location                                   │        │  HudStore ──► HudState        │
│     └─ ETA / remaining                                         │        │      ▲            │           │
│  TrafficMonitor (Routes API poll) → TrafficSegments, Detours   │        │  keys (temple     ▼           │
│  IncidentProvider(s) → Alerts                                  │        │  touchpad)     HudView        │
│        │                                                       │        │               (Canvas)        │
│        ▼                                                       │        │                               │
│  NavSessionCoordinator ──NavMessage──► CxrGlassesLink (CXR-L) ─┼─► Rokid AI App ─► BT ─► CXRServiceBridge│
│        ▲                                     │                 │        │                               │
│        └──────── GlassesAction ◄─────────────┘                 │        │                               │
└────────────────────────────────────────────────────────────────┘        └───────────────────────────────┘
```

**Key decision: the glasses draw the map themselves from vector data** (route polyline + position +
optional nearby streets). We do *not* stream Google map images. Reasons:
- Bandwidth: the CXR link is Bluetooth via the Rokid AI App; bitmaps at a useful frame rate are unrealistic.
- The display is monochrome green; Google's colored map styling would turn into mush. A purpose-made
  line rendering is clearer and lights fewer pixels (battery, see-through).
- Google Maps ToS restricts re-rendering/extracting map imagery.
- Smooth 30+ fps motion: glasses interpolate position locally between 1 Hz updates.

The phone still shows the full Google Navigation UI — handy as ground truth while debugging.

## Session type: CUSTOM_APP (not CUSTOM_VIEW)
CXR-L offers CUSTOM_VIEW (phone pushes a JSON layout of TextViews/ImageViews, no glasses APK) and
CUSTOM_APP (our own APK on the glasses, binary messages both ways). A live map, a menu and touchpad
input need CUSTOM_APP. The phone bundles the glasses APK and CXR-L installs/launches it on `connect()`.

*Fallback idea:* CUSTOM_VIEW could do a text-only "next turn + ETA" mode with almost no glasses work.
Not planned, but `GlassesLink` is the seam if it's ever wanted.

## Rokid SDK facts (from CXR-L 1.1.2 docs, read 2026-10-17)
- Phone SDK: `com.rokid.cxr:client-l:1.1.2`, Rokid Maven `https://maven.rokid.com/repository/maven-public/`.
  minSdk 28, targetSdk 34, Kotlin 1.9+, AGP 8.2+, Java 17.
- Chain: **our app → CXR-L → Rokid AI App (Hi Rokid overseas) → glasses.** We never touch Bluetooth.
  Rokid AI App must be installed (`checkRokidAppCompatibility`, threshold 1.11.0), glasses connected
  inside it, and it must stay alive in the background.
- Flow: `CxrSessionManager.getInstance(ctx)` → `requestAuthorization(activity, perms) { token }`
  (result also via `onActivityResult` → `parseAuthorizationResult`) → `create(SessionConfig)` →
  `addLifecycleCallback` + `addCustomCmdCallback` → `connect(token)` → `onSessionStarted()`.
- States: Idle, Starting, Started, Paused, Terminating. Paused reasons: `AI_ASSIST`, `BT_DISCONNECTED`
  (token stays valid). Closed reasons: `CHAIN_TORN`, `LINK_LOST` → re-authorize.
- CUSTOM_APP config: `glassesPackageName` (required, = glasses applicationId), `glassesActivityName`,
  `glassesApkPath` (phone-readable path, e.g. `getExternalFilesDir("rokid")`; SDK auto-installs).
  `aiInterceptMode`: `ALLOW_WITH_PAUSE` (default) or `BLOCK_AI`.
- Messaging: phone `sendCustomCmd(cmd, Caps, ByteArray)`; glasses `CXRServiceBridge.subscribe(cmd, MsgCallback)`;
  glasses → phone `bridge.sendMessage(key, Caps)`; phone `onCustomCmdResult(key, ByteArray?)` → `Caps.fromBytes`.
  We use only `Caps.write(String)` (it's on the SDK's keep list). Payload size limit is undocumented.
- Glasses SDK: `com.rokid.cxr:cxr-service-bridge:<version>` — **version unknown, see M0.**
- Glasses keys arrive as system broadcasts (`com.android.action.ACTION_SPRITE_BUTTON_CLICK`, `..._DOUBLE_CLICK`,
  `..._LONG_PRESS`, two-finger tap/swipe, `ACTION_AI_START`, ...) plus normal `onKeyDown` KeyEvents.
- Brightness/volume: `session.setGlassBrightness(0..100)`, `setGlassVolume`.

## Glasses hardware (from reviews, unverified)
Dual monochrome green micro-LED waveguides, ~480×398 per eye (one source says 640×480), 23° FOV,
1500 nits, Qualcomm AR1, 2 GB RAM, BT 5.3, Wi-Fi 6. Measure the real view size in M2 and log it in
`GlassesHello`.

## Decisions & open questions
| Topic | Decision | Status |
|---|---|---|
| AI assistant during nav | `ALLOW_WITH_PAUSE`: AI wake pauses our session; on resume, full resync. Try `BLOCK_AI` if pauses are annoying. | ASSUMPTION |
| Voice guidance | Navigation SDK speaks on the phone; audio routes to whatever BT audio is active (glasses speakers if Rokid AI App routes it). | VERIFY M5 |
| Position rate | 1 Hz from phone, interpolated on glasses | ASSUMPTION |
| Map context streets | v1: route line only. v2: `RoadContext` from OSM (see DATA_SOURCES.md) | planned |
| Police alerts | No Google API. Pluggable `IncidentProvider`; see DATA_SOURCES.md | open |

## Phone UX
1. **Setup** (first run / when link not Ready): Rokid AI App check → "Authorize glasses" → "Connect".
2. **Search**: Places Autocomplete, recents, home/work shortcuts.
3. **Route picker**: Google map with 1–3 alternatives from Routes API; each card shows ETA, distance,
   traffic delay ("+8 min traffic"). Tap Go.
4. **Active nav**: Navigation SDK `NavigationView` full screen; small chip with glasses link state;
   End button. Detour offers appear here too (accept from phone or glasses).
5. **Debug** (hidden, long-press title): link state, live protocol log, send test frames, simulate drive.
