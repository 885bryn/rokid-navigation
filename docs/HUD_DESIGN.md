# Glasses HUD design

## Display constraints
- Monochrome **green** micro-LED waveguide. **Black = transparent** (you see the road). Every lit
  pixel costs battery and blocks the view, so: thin outlines over filled areas, black backgrounds,
  brightness hierarchy via alpha (100% primary, ~60% secondary, ~30% context).
- ~480×398 px per eye (VERIFY). Lay out with fractions of the measured view size.
- Text: min ~22 px for anything read while driving; the distance number is the biggest thing on screen.
- Glanceable: each region answers one question in < 1 s.

## Map mode (default)
```
┌──────────────────────────────────────────────┐  0%
│  ┏━┓                                          │
│  ┃↱┃   0.3 mi                    then ↰      │  Maneuver banner (0–30%)
│  ┗━┛   Main St                               │  arrow · distance · road · "then" chip
├──────────────────────────────────────────────┤  30%
│                    ·                          │
│              ━━━━━━┓   ← route line (thick)   │
│                    ┃   ┄┄ slow  ▬▬ jam        │  Route map, heading-up (30–85%)
│        thin dim streets (v2)                  │  next maneuver point marked
│                    ▲   ← you, fixed ~75% h    │
├──────────────────────────────────────────────┤  85%
│ 12:41  ·  18 min  ·  9.2 mi          +4 min ▲ │  Status strip (85–100%): ETA · left · dist · traffic delay
└──────────────────────────────────────────────┘ 100%
```

**Minimal mode** (tap to toggle): banner + status strip only, map hidden. Good for highways.

**Overlays**
- **Alert** — replaces the status strip with a filled bar: `[icon] POLICE  0.4 mi`. Shown when within
  ~2 km (cameras ~1 km), counts down, disappears once passed or on double-tap. One at a time, nearest first.
- **Detour prompt** — card over the lower map: `Faster route · save 6 min · via I-280`,
  `tap = take · 2× tap = keep`, shrinking countdown bar (30 s). Expiry = keep current route.
- **Rerouting / GPS lost** — small text in the banner, map keeps last state.
- **Arrived** — big check + destination name for 10 s, then idle screen.

## Input map
Rokid temple touchpad. Broadcast names from the CXR docs; single-finger swipe delivery is unknown
(probably KeyEvents) — log raw events on hardware in M3 and adjust `GlassesKey.fromKeyCode`.

| Gesture | Map / Minimal | Detour prompt showing | Menu |
|---|---|---|---|
| Tap | toggle Map ↔ Minimal | **accept detour** | select |
| Double tap | dismiss current alert | **keep current route** | back / close |
| Long press | open menu | open menu | close |
| Swipe forward | zoom in | — | next item |
| Swipe back | zoom out | — | previous item |
| AI button | system (never consumed) | | |

## Menu (long press)
Root list, max 4 visible rows, highlighted row filled:
1. **Trip info** — arrival time, time left, distance left, next 3 maneuvers with distances.
2. **Traffic & routes** — current delay, worst congestion ahead ("Jam in 2.1 mi, 1.3 mi long"),
   "Find faster route" (sends SHOW_ALTERNATIVES → phone runs a poll and may send a detour_offer).
3. **Voice on/off** — TOGGLE_VOICE.
4. **Display** — brightness (phone calls `setGlassBrightness`), units, default zoom.
5. **End navigation** — confirm page (tap = end, double tap = cancel).

Auto-close after 10 s without input. The menu never hides a new alert: alerts pop the menu closed.

## Zoom levels
0 = ~300 m ahead (city turns), 1 = ~800 m (default), 2 = ~2 km (highway). Auto-zoom (v2): zoom in as
a maneuver approaches (< 300 m), out on highways at speed.
