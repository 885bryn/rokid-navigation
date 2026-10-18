package com.rokidnav.glasses.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.rokidnav.glasses.state.HudMode
import com.rokidnav.glasses.state.HudState

/**
 * One full-screen custom View, drawn with Canvas. No XML layouts, no Compose: the display is a tiny
 * monochrome-green waveguide where black = transparent, and we want total control of every lit pixel.
 *
 * Layout regions are fractions of the real view size (never hard-code 480x398). See
 * docs/HUD_DESIGN.md for the spec and the mock-up.
 */
class HudView(context: Context) : View(context) {
    private var state = HudState()

    private val banner = ManeuverBannerRenderer()
    private val map = RouteMapRenderer()
    private val status = StatusStripRenderer()
    private val alert = AlertRenderer()
    private val detour = DetourPromptRenderer()
    private val menu = MenuRenderer()

    init {
        setBackgroundColor(Color.BLACK)
    }

    fun render(newState: HudState) {
        state = newState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bannerRect = RectF(0f, 0f, w, h * 0.30f)
        val mapRect = RectF(0f, h * 0.30f, w, h * 0.85f)
        val statusRect = RectF(0f, h * 0.85f, w, h)

        when (val mode = state.mode) {
            is HudMode.Menu -> menu.draw(canvas, RectF(0f, 0f, w, h), state, mode)
            HudMode.Minimal -> {
                banner.draw(canvas, bannerRect, state)
                status.draw(canvas, statusRect, state)
            }
            HudMode.Map -> {
                banner.draw(canvas, bannerRect, state)
                map.draw(canvas, mapRect, state)
                status.draw(canvas, statusRect, state)
            }
        }
        if (state.mode !is HudMode.Menu) {
            state.alerts.firstOrNull()?.let { alert.draw(canvas, statusRect, state, it) }
            state.detour?.let { detour.draw(canvas, mapRect, state, it) }
        }
    }
}

/** Shared paint palette: one hue, brightness via alpha. */
object HudPaints {
    const val GREEN = 0xFF00FF66.toInt() // VERIFY: display is green-only; any non-black color renders as green
    fun stroke(widthPx: Float, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GREEN; this.alpha = alpha; style = Paint.Style.STROKE; strokeWidth = widthPx
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    fun fill(alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; this.alpha = alpha }
    fun text(sizePx: Float, alpha: Int = 255, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GREEN; this.alpha = alpha; textSize = sizePx; isFakeBoldText = bold
    }
}
