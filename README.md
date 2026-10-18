# Rokid Navigation

A navigation HUD for Rokid Glasses in the style of Google Maps. You pick a destination and route on your
Android phone. The phone runs Google's turn-by-turn guidance, and the glasses show their own HUD:
a heading-up route map, the next turn, ETA, congestion, alerts, and detour offers. A touchpad menu on
the glasses gives you trip info and settings.

Current state: **this is a framework only.** The protocol module is implemented and has unit tests,
but they haven't been run yet because there's no Gradle wrapper (milestone M0). The other pieces are
interfaces and skeletons with TODO(Mx) markers. `docs/TASKS.md` is the build plan.

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
