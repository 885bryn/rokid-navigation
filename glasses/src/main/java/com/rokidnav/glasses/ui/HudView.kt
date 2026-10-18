package com.rokidnav.glasses.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.rokidnav.glasses.state.HudState
import com.rokidnav.protocol.NavState

/** Fits the reference design without stretching; no per-frame Rect/Paint/Path/string allocation. */
class HudView(context: Context) : View(context) {
    private var state = HudState()
    private val renderer=HudRenderer(context)
    init { setBackgroundColor(Color.BLACK) }
    fun render(newState:HudState){state=newState;renderer.prepare(newState);invalidate()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        // The native glasses Activity is portrait. Independent X/Y scales made text 61% taller.
        val compact=state.mode==com.rokidnav.glasses.state.HudMode.Compact
        val designHeight=if(compact)640f else 398f
        val scale=minOf(width/480f,height/designHeight)*.92f
        canvas.save()
        canvas.translate((width-480f*scale)/2f,(height-designHeight*scale)/2f)
        canvas.scale(scale,scale)
        if(compact)renderer.drawCompact(canvas,System.nanoTime()) else renderer.draw(canvas,System.nanoTime())
        canvas.restore()
        if(isAttachedToWindow && state.navState==NavState.ACTIVE && (state.position?.speedMps ?: 0f)>=.5f)postInvalidateDelayed(33)
    }
}
