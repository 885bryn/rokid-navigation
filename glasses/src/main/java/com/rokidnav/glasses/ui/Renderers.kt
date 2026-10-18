package com.rokidnav.glasses.ui

import android.graphics.Canvas
import android.graphics.RectF
import com.rokidnav.glasses.state.HudMode
import com.rokidnav.glasses.state.HudState
import com.rokidnav.protocol.Alert
import com.rokidnav.protocol.DetourOffer

/*
 * One renderer per HUD region. Each is stateless apart from cached Paints/Paths, draws only inside
 * the rect it's given, and must not allocate per frame. Specs live in docs/HUD_DESIGN.md.
 */

/** Big maneuver arrow (left), distance (large), road name (medium), "then <arrow>" chip when the next step is < 150 m after. */
class ManeuverBannerRenderer {
    fun draw(canvas: Canvas, r: RectF, s: HudState) {
        // TODO(M2): arrow glyphs per Maneuver (ManeuverGlyphs.kt: Path builders, one per enum value),
        //  distance formatting (Format.kt), lane guidance row when lanes non-empty.
        val m = s.maneuver ?: return
        canvas.drawText(
            "${m.current.maneuver} ${m.distanceToStepMeters} m  ${m.current.roadName}",
            r.left + 16, r.centerY(), HudPaints.text(28f, bold = true),
        )
    }
}

/**
 * Heading-up route map, Google-Maps-nav style: user chevron fixed at ~75% height, route ahead drawn
 * as a thick line, traffic overlay as dashes (SLOW) / dense dashes (JAM), next maneuver point marked,
 * optional RoadContext streets as thin dim lines.
 */
class RouteMapRenderer {
    fun draw(canvas: Canvas, r: RectF, s: HudState) {
        // TODO(M2):
        //  - origin = position (interpolated between the last two PositionUpdates for 30+ fps smoothness)
        //  - project route points with Geo.toLocalMeters, rotate by -bearing, scale by zoom level
        //  - clip to r; only draw route from ~50 m behind to the visible distance ahead
        //  - cache the projected Path; rebuild only when position/zoom changes meaningfully
    }
}

/** ETA clock · remaining time · remaining distance · traffic delay badge (e.g. "+6 min"). */
class StatusStripRenderer {
    fun draw(canvas: Canvas, r: RectF, s: HudState) {
        // TODO(M2): Format.eta(progress.etaEpochMs, clock24h), Format.duration, Format.distance(units)
    }
}

/** Inverted (filled) box with icon + "POLICE  0.4 mi". Pulses once on arrival. Overlays the status strip. */
class AlertRenderer {
    fun draw(canvas: Canvas, r: RectF, s: HudState, a: Alert) {
        // TODO(M7): icon per AlertKind (AlertGlyphs.kt), distance counts down from PositionUpdate
    }
}

/** Card over the map: "Faster route · save 6 min · via I-280" + "tap = take · 2×tap = no" + countdown bar. */
class DetourPromptRenderer {
    fun draw(canvas: Canvas, r: RectF, s: HudState, d: DetourOffer) {
        // TODO(M6): countdown = (d.expiresAtMs - s.nowMs); optional mini-preview of d.encodedPolyline
    }
}

/** Full-screen list menu: 3–4 large rows max, highlighted row filled. Pages per MenuPage. */
class MenuRenderer {
    fun draw(canvas: Canvas, r: RectF, s: HudState, menu: HudMode.Menu) {
        // TODO(M8)
    }
}
