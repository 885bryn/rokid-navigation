package com.rokidnav.glasses.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.rokidnav.glasses.state.HudState

/**
 * Fits the reference design without stretching; no per-frame Rect/Paint/Path/string allocation.
 *
 * Battery: the HUD redraws only when state changes, plus a capped frame loop while the map moves
 * between the phone's ~1 Hz fixes (15 fps expanded, 10 fps for the small corner map). Exactly one
 * frame is ever pending: re-posting on every draw used to stack loops and ran at ~48 fps.
 */
class HudView(context: Context) : View(context) {
    private var state = HudState()
    private val renderer=HudRenderer(context)
    private val nextFrame=Runnable { invalidate() }
    init { setBackgroundColor(Color.BLACK) }
    fun render(newState:HudState){state=newState;renderer.prepare(newState);invalidate()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        // The native glasses Activity is portrait. Independent X/Y scales made text 61% taller.
        val compact=state.mode==com.rokidnav.glasses.state.HudMode.Compact || state.mode==com.rokidnav.glasses.state.HudMode.Minimal
        val designHeight=if(compact)640f else 398f
        val scale=minOf(width/480f,height/designHeight)*.92f
        canvas.save()
        canvas.translate((width-480f*scale)/2f,(height-designHeight*scale)/2f)
        canvas.scale(scale,scale)
        if(compact)renderer.drawCompact(canvas,System.nanoTime()) else renderer.draw(canvas,System.nanoTime())
        canvas.restore()
        removeCallbacks(nextFrame)
        val mapShown=state.mode!=com.rokidnav.glasses.state.HudMode.Minimal
        if(isAttachedToWindow && mapShown && (renderer.zoomSettling || renderer.moving))postDelayed(nextFrame,if(compact)COMPACT_FRAME_MS else FRAME_MS)
    }
    override fun onDetachedFromWindow(){removeCallbacks(nextFrame);super.onDetachedFromWindow()}
    private companion object {
        const val FRAME_MS=66L
        const val COMPACT_FRAME_MS=100L
    }
}
