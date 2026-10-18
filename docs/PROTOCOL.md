# Phone ↔ Glasses protocol (v1)

Source of truth: `protocol/src/main/kotlin/com/rokidnav/protocol/NavMessage.kt`. Keep this doc in sync.

## Transport
| Direction | Phone (CXR-L) | Glasses (CXR-S) | Channel key |
|---|---|---|---|
| phone → glasses | `session.sendCustomCmd(key, caps, ByteArray(0))` | `bridge.subscribe(key, cb)` | `rknav_down` |
| glasses → phone | `onCustomCmdResult(key, bytes)` | `bridge.sendMessage(key, caps)` | `rknav_up` |

Every frame is a Caps of exactly two strings: `["rknav/1", <json>]`. JSON uses a `"t"` discriminator
(e.g. `{"t":"maneuver","seq":12,...}`). Unknown fields are ignored, unknown `t` values are dropped.
Frames should stay under 8 KB (`MAX_FRAME_BYTES`, to be measured on hardware in M3).

Units: meters, seconds, epoch-ms, degrees (0 = north, clockwise). Formatting happens on glasses.

## Phone → glasses
| `t` | Rate | Purpose |
|---|---|---|
| `session_start` | once per trip + resync | units, 24h clock, destination name |
| `route_chunk` | per route / reroute / resync | encoded polyline split into chunks; glasses reassemble with `RouteAssembler` |
| `road_context` | optional, per route | nearby street polylines for map context (v2) |
| `pos` | ~1 Hz | lat/lng, bearing, speed, metersAlongRoute |
| `maneuver` | on change + ~1 Hz distance updates | current step, distance/time to it, next step |
| `progress` | ~every 5 s | ETA, remaining m/s, traffic delay s |
| `traffic` | every traffic poll | congestion segments by distance along route |
| `alert` / `alert_clear` | on change | police/camera/crash/etc. with distance ahead |
| `detour_offer` / `detour_resolved` | rare | faster route offer; expires at `expiresAtMs` |
| `nav_state` | on change | IDLE/ROUTING/ACTIVE/REROUTING/GPS_LOST/ARRIVED/ENDED |
| `settings` | on change | voice muted, units, zoom |

## Glasses → phone
| `t` | When |
|---|---|
| `hello` | glasses app start; carries display size. Phone replies with a full resync. |
| `resync` | glasses missed something (e.g. incomplete route chunks after 3 s) |
| `action` | ACCEPT_DETOUR / DISMISS_DETOUR (with offerId), END_NAVIGATION, TOGGLE_VOICE, ZOOM_IN/OUT, SHOW_ALTERNATIVES, TOGGLE_UNITS |
| `input` | raw key name, for logs/calibration only |

## Resync rule
Whenever the link becomes Ready (connect, resume after Paused, `hello`, `resync`), the phone sends:
route chunks → then the last value of every stateful message (`session_start`, `nav_state`, `maneuver`,
`progress`, `traffic`, active alerts, pending detour). See `NavSessionCoordinator.resyncAll`.

## Ordering
`seq` is per-sender and monotonic. Receivers may drop a stateful message older than the last one of
the same type they applied (TODO M9 if reordering is ever observed).
