# Rokid Navigation

A navigation HUD for Rokid Glasses in the style of Google Maps. You pick a destination and route on your
Android phone. The phone runs Google's turn-by-turn guidance, and the glasses show their own HUD:
a heading-up route map, the next turn, ETA, congestion, alerts, and detour offers. A touchpad menu on
the glasses gives you trip info and settings.

Current state: version 0.2.0 builds both apps with a bottom-corner HUD and tap-to-expand map.
Search, Google route alternatives/map, route-token guidance, turn-by-turn mapping, background service,
traffic/detours, WzSabre providers, persistent settings and reconnect snapshots are implemented.
The full HUD and tap input have worked on real glasses. **Google API keys are still absent**:
live Google/WzSabre integration and the new corner layout's waveguide fit remain to be verified.
See [Google setup](docs/GOOGLE_SETUP.md) for the next manual step and `docs/TASKS.md` for evidence.
Police reports use the WzSabre Android plugin; speed cameras use its cached ExCam database.
See `docs/DATA_SOURCES.md` for setup and outstanding device validation.

## Start here
- `AGENTS.md`: rules and orientation for coding agents
- `docs/ARCHITECTURE.md`: how the parts fit together, plus facts about the Rokid SDK
- `docs/PROTOCOL.md`: the phone ↔ glasses messages
- `docs/HUD_DESIGN.md`: the glasses screens and gestures
- `docs/DATA_SOURCES.md`: which Google APIs are used, and the situation with police alerts
- `docs/TASKS.md`: milestones M0–M9

## You'll need
- Android phone with **Hi Rokid** (Rokid AI App) installed and the glasses connected in it
- Google Cloud API key with Navigation SDK, Places API (New) and Routes API enabled → `local.properties`
- Android Studio (JDK 17, SDK 34)

## Build and test

```powershell
./gradlew.bat :protocol:test :phone:testDebugUnitTest :glasses:testDebugUnitTest assembleDebug
```

Use `./gradlew` on macOS/Linux. `local.properties` supplies `sdk.dir`, `MAPS_API_KEY` and optional separate `ROUTES_API_KEY`; an empty key
allows compilation but cannot authorize Google guidance. Install the police-report plugin from
[WzSabre](https://wzsabre.rocks/) on the phone before testing live reports.
