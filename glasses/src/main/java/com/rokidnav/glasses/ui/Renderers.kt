package com.rokidnav.glasses.ui

import android.content.Context
import android.graphics.*
import com.rokidnav.glasses.state.*
import com.rokidnav.protocol.*
import kotlin.math.*

/** Draws in Claude's 480x398 design coordinates; HudView scales to the actual measured display.
 * Geometry, labels and glyph paths are cached on input changes. Drawing creates no application objects.
 */
class HudRenderer(context: Context) {
    private val regular = Typeface.createFromAsset(context.assets, "fonts/BarlowSemiCondensed-Medium.ttf")
    private val bold = Typeface.createFromAsset(context.assets, "fonts/BarlowSemiCondensed-SemiBold.ttf")
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; fontFeatureSettings = "tnum" }
    private val glyphs = ManeuverGlyphs()
    private val routePath = Path()
    private val minorRoads = Path()
    private val majorRoads = Path()
    private var hasRoads = false
    private var roadNames = emptyArray<String>()
    private var roadMajor = BooleanArray(0)
    private var roadEast = FloatArray(0)
    private var roadNorth = FloatArray(0)
    /** Road indices by label priority: turn street, the "after" street, major roads, the rest. */
    private var labelOrder = IntArray(0)
    private var turnRoad = -1
    private var cueRoad = -1
    private val placedLabelX = FloatArray(MAX_LABELS)
    private val placedLabelY = FloatArray(MAX_LABELS)
    private val placedLabelW = FloatArray(MAX_LABELS)
    private var maneuverX = 0f
    private var maneuverY = 0f
    private var hasManeuverPoint = false
    private var cue = ""
    private var trafficPaths = emptyArray<Path>()
    private var trafficLevels = emptyArray<TrafficLevel>()
    private val chevron = Path().apply { moveTo(0f,-24f); lineTo(12f,6f); lineTo(0f,0f); lineTo(-12f,6f); close() }
    private val shield = Path().apply { moveTo(12f,2.5f); lineTo(20f,5.5f); lineTo(20f,11f); cubicTo(20f,16f,16.5f,19.5f,12f,21.5f); cubicTo(7.5f,19.5f,4f,16f,4f,11f); lineTo(4f,5.5f); close() }
    private val alternativeDash = DashPathEffect(floatArrayOf(8f,7f),0f)
    private var s = HudState()
    private var east = FloatArray(0)
    private var north = FloatArray(0)
    private var distances = DoubleArray(0)
    private var originLat = 0.0
    private var originLng = 0.0
    private var previousX = 0f
    private var previousY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var previousBearing = 0f
    private var currentBearing = 0f
    private var receivedNanos = 0L
    private var transitionMs = 1000L
    private var number = ""
    private var unit = ""
    private var road = ""
    private var compactRoad = ""
    private var compactVia = ""
    private var eta = ""
    private var remainingTime = ""
    private var remainingDistance = ""
    private var statusTime = ""
    private var statusDistance = ""
    private var delayLabel = ""
    private var alertDistance = ""
    private var alertAge = ""
    private var alertKind = ""
    private var detourSaving = ""
    private var detourVia = ""
    private var menuDestination = ""
    private var destination = ""
    private var exit = ""
    private var jamLabel = ""
    private val menuLabels = arrayOf("Trip info", "Traffic & routes", "Voice guidance", "Display", "End navigation")
    private val displayLabels = arrayOf("Units", "Zoom", "Brightness", "Back")
    private var unitsLabel = ""
    private var zoomLabel = ""
    private var brightnessLabel = ""

    fun prepare(next: HudState) {
        val old = s
        s = next
        val geometryChanged = old.route !== next.route
        if (geometryChanged) {
            val points = next.route
            originLat = points.firstOrNull()?.lat ?: 0.0
            originLng = points.firstOrNull()?.lng ?: 0.0
            east = FloatArray(points.size); north = FloatArray(points.size); distances = DoubleArray(points.size)
            routePath.reset()
            for (i in points.indices) {
                east[i] = x(points[i].lng); north[i] = y(points[i].lat)
                if (i == 0) routePath.moveTo(east[i],north[i]) else {
                    routePath.lineTo(east[i],north[i]); distances[i] = distances[i-1] + Geo.distanceMeters(points[i-1],points[i])
                }
            }
        }
        if (geometryChanged || old.contextRoads !== next.contextRoads) {
            val usedNames=HashSet<String>()
            minorRoads.reset();majorRoads.reset()
            for(road in next.contextRoads) {
                val path=if(road.major)majorRoads else minorRoads
                road.points.forEachIndexed { i,p -> if(i==0) path.moveTo(x(p.lng),y(p.lat)) else path.lineTo(x(p.lng),y(p.lat)) }
            }
            hasRoads=next.contextRoads.isNotEmpty()
            roadNames=next.contextRoads.map { road ->
                if(road.name.isNotBlank() && usedNames.add(road.name))ellipsize(road.name,15f,130f) else ""
            }.toTypedArray()
            roadMajor=BooleanArray(next.contextRoads.size) {next.contextRoads[it].major}
            roadEast=FloatArray(next.contextRoads.size)
            roadNorth=FloatArray(next.contextRoads.size)
        }
        if (geometryChanged || old.traffic !== next.traffic) {
            trafficPaths = next.traffic.map { segment -> range(segment.startMeters.toDouble(),segment.endMeters.toDouble()) }.toTypedArray()
            trafficLevels = next.traffic.map { it.level }.toTypedArray()
        }
        if (geometryChanged || old.position !== next.position) {
            next.position?.let { current ->
                val previous = next.previousPosition ?: current
                previousX=x(previous.lng); previousY=y(previous.lat); currentX=x(current.lng); currentY=y(current.lat)
                previousBearing=previous.bearingDeg; currentBearing=current.bearingDeg
                transitionMs=(current.timestampMs-previous.timestampMs).coerceIn(250,1500)
                receivedNanos=System.nanoTime()
            }
        }
        if(geometryChanged || old.position !== next.position || old.contextRoads !== next.contextRoads || old.maneuver !== next.maneuver) {
            val along=next.position?.metersAlongRoute ?: -1
            val toStep=next.maneuver?.distanceToStepMeters
            hasManeuverPoint=false
            if(along>=0 && toStep!=null) {
                val target=(along+toStep).toDouble()
                var index=1
                while(index<distances.size && distances[index]<target)index++
                if(index<distances.size) {
                    val f=((target-distances[index-1])/(distances[index]-distances[index-1]).coerceAtLeast(.01)).toFloat()
                    maneuverX=east[index-1]+(east[index]-east[index-1])*f;maneuverY=north[index-1]+(north[index]-north[index-1])*f
                    hasManeuverPoint=true
                }
            }
            cue=next.maneuver?.let { m -> TurnCue.compute(next.route,along,m.distanceToStepMeters,m.current.maneuver,next.contextRoads) }.orEmpty()
            val turnName=normalizeName(next.maneuver?.current?.roadName.orEmpty())
            val cueName=normalizeName(cue.substringAfter(" · after ","").takeIf {cue.contains(" · after ")}.orEmpty())
            // Anchor names near the look-ahead window, not at a long street's distant midpoint. The turn
            // street is labelled on its piece nearest the turn, the "after" street halfway to the turn.
            val angle=Math.toRadians(currentBearing.toDouble())
            val aheadX=currentX+sin(angle).toFloat()*350f
            val aheadY=currentY-cos(angle).toFloat()*350f
            val turnX=if(hasManeuverPoint)maneuverX else aheadX;val turnY=if(hasManeuverPoint)maneuverY else aheadY
            val cueX=(currentX+turnX)/2;val cueY=(currentY+turnY)/2
            turnRoad=-1;cueRoad=-1
            var turnBest=Float.MAX_VALUE;var cueBest=Float.MAX_VALUE
            for(index in next.contextRoads.indices) {
                val name=normalizeName(next.contextRoads[index].name)
                val (tx,ty)=when {
                    name.isNotEmpty() && name==turnName -> turnX to turnY
                    name.isNotEmpty() && name==cueName -> cueX to cueY
                    else -> aheadX to aheadY
                }
                val best=anchor(next.contextRoads[index].points,index,tx,ty)
                if(name.isNotEmpty() && name==turnName && best<turnBest){turnBest=best;turnRoad=index}
                if(name.isNotEmpty() && name==cueName && best<cueBest){cueBest=best;cueRoad=index}
            }
            // The chosen piece carries the label even if an earlier piece of the same street took the name.
            if(turnRoad>=0)roadNames[turnRoad]=ellipsize(next.contextRoads[turnRoad].name,17f,150f)
            if(cueRoad>=0)roadNames[cueRoad]=ellipsize(next.contextRoads[cueRoad].name,15f,130f)
            labelOrder=next.contextRoads.indices.filter { roadNames[it].isNotEmpty() }
                .sortedBy { when { it==turnRoad->0; it==cueRoad->1; roadMajor[it]->2; else->3 } }.toIntArray()
        }
        val formatted = Format.distance(next.maneuver?.distanceToStepMeters ?: 0,next.units).split(' ',limit=2)
        number=formatted[0]; unit=formatted.getOrElse(1){""}
        road=ellipsize(next.maneuver?.current?.roadName.orEmpty(),26f,if(next.maneuver?.next != null) 238f else 340f)
        compactRoad=ellipsize(next.maneuver?.current?.roadName.orEmpty(),22f,210f)
        compactVia=ellipsize(next.detour?.via.orEmpty(),18f,210f)
        exit=next.maneuver?.current?.exitNumber?.takeIf { it.isNotBlank() }?.let { "EXIT $it" }.orEmpty()
        next.progress?.let { p ->
            eta=Format.eta(p.etaEpochMs,next.clock24h); remainingTime=Format.duration(p.remainingSeconds)
            remainingDistance=Format.distance(p.remainingMeters,next.units)
            statusTime=ellipsize(remainingTime,22f,102f)
            statusDistance=ellipsize(remainingDistance,22f,100f)
            delayLabel=if(p.trafficDelaySeconds>0) "+${Format.duration(p.trafficDelaySeconds)}" else "No delays"
        }
        next.alerts.firstOrNull()?.let { a ->
            alertDistance=Format.distance(a.distanceAheadMeters,next.units)
            alertKind=when(a.kind) { AlertKind.POLICE->"POLICE"; AlertKind.SPEED_CAMERA->"CAMERA"; AlertKind.ROAD_CLOSED->"CLOSED"; AlertKind.TRAFFIC_JAM->"TRAFFIC"; else->a.kind.name.replace('_',' ') }
            alertAge=if(a.reportedAtMs>0) "Reported ${((next.nowMs-a.reportedAtMs).coerceAtLeast(0)/60_000)} min ago" else a.source
        }
        next.detour?.let { d -> detourSaving="Save ${Format.duration(d.savesSeconds)}"; detourVia=ellipsize("${d.via} · +${Format.distance(d.extraMeters,next.units)}",20f,284f) }
        destination=ellipsize(next.destinationName,26f,420f)
        menuDestination=ellipsize("${next.destinationName} · $eta",18f,280f)
        unitsLabel=if(next.units==Units.IMPERIAL) "mi / ft" else "km / m"
        zoomLabel=when(next.mapZoom) {0->"Close"; 2->"Far"; else->"Default"}
        brightnessLabel="${next.brightnessPercent}%"
        val along=next.position?.metersAlongRoute ?: 0
        val jam=next.traffic.firstOrNull { it.level==TrafficLevel.JAM && it.endMeters>along }
        jamLabel=if(jam==null) "" else "in ${Format.distance((jam.startMeters-along).coerceAtLeast(0),next.units)} · ${Format.distance(jam.endMeters-jam.startMeters,next.units)} long"
    }
    /** Sets the road's label anchor to its point nearest (tx,ty); returns that squared distance. */
    private fun anchor(points:List<LatLng>,index:Int,tx:Float,ty:Float):Float {
        var best=Float.MAX_VALUE
        for(j in 1 until points.size) {
            val ax=x(points[j-1].lng);val ay=y(points[j-1].lat)
            val dx=x(points[j].lng)-ax;val dy=y(points[j].lat)-ay
            val fraction=if(dx*dx+dy*dy==0f)0f else (((tx-ax)*dx+(ty-ay)*dy)/(dx*dx+dy*dy)).coerceIn(0f,1f)
            val lx=ax+fraction*dx;val ly=ay+fraction*dy
            val squared=(lx-tx)*(lx-tx)+(ly-ty)*(ly-ty)
            if(squared<best){best=squared;roadEast[index]=lx;roadNorth[index]=ly}
        }
        return best
    }
    /** Google says "Bay St", OSM says "Bay Street": compare on a common short form. */
    private fun normalizeName(name:String):String {
        var n=name.lowercase().trim().replace(SPACES," ")
        for((long,short) in NAME_ABBREVIATIONS) n=n.replace(long,short)
        return n.replace(".","")
    }
    private fun x(lng: Double) = (Math.toRadians((lng-originLng+540)%360-180)*6_371_000*cos(Math.toRadians(originLat))).toFloat()
    private fun y(lat: Double) = (-Math.toRadians(lat-originLat)*6_371_000).toFloat()
    private fun range(start:Double,end:Double):Path = Path().apply {
        var started=false
        for(i in 1 until distances.size) {
            val lo=maxOf(start,distances[i-1]); val hi=minOf(end,distances[i]); val len=distances[i]-distances[i-1]
            if(lo>hi || len<=0) continue
            val a=((lo-distances[i-1])/len).toFloat(); val b=((hi-distances[i-1])/len).toFloat()
            val ax=east[i-1]+(east[i]-east[i-1])*a; val ay=north[i-1]+(north[i]-north[i-1])*a
            if(!started) {moveTo(ax,ay);started=true}
            lineTo(east[i-1]+(east[i]-east[i-1])*b,north[i-1]+(north[i]-north[i-1])*b)
        }
    }
    private fun paint(size:Float=0f,alpha:Int=255,b:Boolean=false,black:Boolean=false,stroke:Float=0f):Paint {
        ink.color=if(black) Color.BLACK else GREEN; ink.alpha=alpha; ink.style=if(stroke>0) Paint.Style.STROKE else Paint.Style.FILL
        ink.strokeWidth=stroke; ink.textSize=size; ink.typeface=if(b) bold else regular; ink.pathEffect=null
        return ink
    }
    private fun text(c:Canvas,value:String,x:Float,y:Float,size:Float,alpha:Int=255,b:Boolean=false,black:Boolean=false) { c.drawText(value,x,y,paint(size,alpha,b,black)) }
    private fun right(c:Canvas,value:String,x:Float,y:Float,size:Float,alpha:Int=255,b:Boolean=false,black:Boolean=false) {
        val p=paint(size,alpha,b,black); c.drawText(value,x-p.measureText(value),y,p)
    }
    private fun center(c:Canvas,value:String,x:Float,y:Float,size:Float,alpha:Int=255,b:Boolean=false) {
        val p=paint(size,alpha,b); c.drawText(value,x-p.measureText(value)/2,y,p)
    }
    private fun box(c:Canvas,l:Float,t:Float,r:Float,b:Float,radius:Float,fill:Boolean=false,alpha:Int=255,black:Boolean=false) {
        c.drawRoundRect(l,t,r,b,radius,radius,paint(alpha=alpha,black=black,stroke=if(fill)0f else 2f))
    }
    private fun ellipsize(value:String,size:Float,width:Float):String {
        val p=paint(size); if(p.measureText(value)<=width) return value
        val count=p.breakText(value,true,width-p.measureText("…"),null)
        return value.take(count)+"…"
    }

    fun draw(c:Canvas,nanos:Long) {
        if(!s.linkConnected && s.navState!=NavState.IDLE && s.navState!=NavState.ENDED) {
            text(c,"Phone disconnected",18f,340f,24f)
            text(c,"Reconnect in Hi Rokid",18f,376f,20f,153)
            return
        }
        when {
            s.navState==NavState.ARRIVED -> arrived(c)
            s.mode is HudMode.Menu -> menu(c,s.mode as HudMode.Menu)
            s.navState==NavState.IDLE || s.navState==NavState.ENDED -> {
                center(c,"Rokid Nav",240f,184f,44f,b=true); center(c,"Choose a destination on your phone",240f,226f,24f,180)
            }
            else -> {
                banner(c)
                if(s.mode==HudMode.Map) map(c,nanos)
                lanes(c)
                if(s.alerts.isNotEmpty()) alert(c,s.alerts[0]) else status(c)
                if(s.detour!=null) detour(c,s.detour!!)
            }
        }
    }
    /** Corner layout: upper 62% stays transparent; map labels are available in expanded mode. */
    fun drawCompact(c:Canvas,nanos:Long) {
        if(s.navState==NavState.IDLE || s.navState==NavState.ENDED) {
            text(c,"Rokid Nav",18f,574f,24f)
            text(c,"Choose a route on your phone",18f,605f,18f,153)
            return
        }
        if(s.navState==NavState.ARRIVED) {
            text(c,"You've arrived",18f,560f,30f,b=true)
            text(c,destination,18f,600f,22f,217)
            return
        }
        if(!s.linkConnected) {
            text(c,"Phone disconnected",18f,560f,22f)
            text(c,"Reconnect in Hi Rokid",18f,600f,18f,153)
            return
        }
        map(c,nanos,compact=true)
        val d=s.detour
        if(d!=null) {
            text(c,detourSaving,18f,458f,28f,b=true)
            text(c,compactVia,18f,488f,18f,217)
            text(c,"Tap: take · double tap: keep",18f,522f,16f,191)
        } else {
            val m=s.maneuver
            if(m!=null) {
                glyphs.draw(c,m.current.maneuver,18f,430f,44f,paint(stroke=6.5f))
                text(c,number,76f,464f,36f,b=true)
                text(c,unit,82f+paint(36f,b=true).measureText(number),464f,20f,191)
                text(c,compactRoad,18f,503f,22f,217)
            }
            val lanesShown=m!=null && m.distanceToStepMeters<150 && m.current.lanes.isNotEmpty() && s.navState==NavState.ACTIVE
            if(!lanesShown && cue.isNotEmpty() && s.navState==NavState.ACTIVE)text(c,ellipsize(cue,15f,220f),18f,528f,15f,166)
            if(m!=null && m.distanceToStepMeters<150 && s.navState==NavState.ACTIVE) {
                var laneIndex=0
                while(laneIndex<m.current.lanes.size && laneIndex<7) {
                    val lane=m.current.lanes[laneIndex];var direction=0
                    while(direction<lane.directions.size) {
                        glyphs.draw(c,lane.directions[direction],18f+laneIndex*28f,516f,24f,
                            paint(alpha=if(lane.recommended)255 else 77,stroke=7f));direction++
                    }
                    laneIndex++
                }
            }
            if(s.navState==NavState.GPS_LOST || s.navState==NavState.REROUTING)
                text(c,if(s.navState==NavState.GPS_LOST)"GPS lost" else "Rerouting",18f,534f,20f)
        }
        val a=s.alerts.firstOrNull()
        if(a!=null) {
            icon(c,a.kind,18f,550f,24f)
            text(c,alertKind,52f,570f,20f,b=true)
            text(c,alertDistance,18f,602f,26f,b=true)
        } else if(s.progress!=null) {
            text(c,eta,18f,570f,24f,b=true)
            text(c,"ETA",24f+paint(24f,b=true).measureText(eta),570f,15f,153)
            text(c,statusTime,18f,602f,20f,217)
            right(c,statusDistance,228f,602f,20f,217)
        }
    }
    private fun banner(c:Canvas) {
        val m=s.maneuver ?: return
        glyphs.draw(c,m.current.maneuver,18f,16f,84f,paint(stroke=6.5f))
        // With a turn cue the three lines move up 9 px so the cue fits above the progress bar.
        val lift=if(cue.isNotEmpty())9f else 0f
        text(c,number,118f,65f-lift,52f,b=true)
        val numberWidth=paint(52f,b=true).measureText(number)
        text(c,unit,124f+numberWidth,65f-lift,22f,191)
        if(exit.isNotEmpty()) { box(c,118f,79f-lift,200f,105f-lift,6f); text(c,exit,124f,99f-lift,18f,b=true); text(c,road,210f,101f-lift,24f) }
        else text(c,road,118f,99f-lift,24f)
        if(cue.isNotEmpty())text(c,cue,118f,108f,16f,179)
        val nextStep=m.next
        if(nextStep!=null && m.distanceToStepMeters>=150) {
            box(c,380f,22f,462f,54f,10f,alpha=179)
            text(c,"then",390f,44f,16f,179)
            glyphs.draw(c,nextStep.maneuver,430f,27f,22f,paint(alpha=179,stroke=9f))
        }
        if(m.distanceToStepMeters<150) {
            val barY=if(cue.isNotEmpty())115f else 110f
            c.drawLine(18f,barY,462f,barY,paint(alpha=89,stroke=4f))
            c.drawLine(18f,barY,18f+444f*(m.distanceToStepMeters/150f).coerceIn(0f,1f),barY,paint(stroke=4f))
        }
        if(s.navState==NavState.GPS_LOST || s.navState==NavState.REROUTING) {
            box(c,290f,78f,466f,110f,6f,fill=true,black=true)
            right(c,if(s.navState==NavState.GPS_LOST) "GPS lost" else "Rerouting",460f,101f,22f)
        }
    }
    private fun lanes(c:Canvas) {
        val lanes=s.maneuver?.current?.lanes ?: return
        if(lanes.isEmpty() || (s.mode!=HudMode.Minimal && (s.maneuver?.distanceToStepMeters ?: 9999)>=150)) return
        val count=minOf(lanes.size,8); val width=count*34f+20f; val left=240f-width/2
        if(s.mode!=HudMode.Minimal) { box(c,left,128f,left+width,178f,10f,fill=true,black=true);box(c,left,128f,left+width,178f,10f) }
        var i=0
        while(i<count) {
            val lane=lanes[i]; var j=0
            while(j<lane.directions.size) { glyphs.draw(c,lane.directions[j],left+10+i*34,133f,34f,paint(alpha=if(lane.recommended)255 else 77,stroke=8f));j++ }
            if(i>0)c.drawLine(left+8+i*34,136f,left+8+i*34,170f,paint(alpha=77,stroke=2f))
            i++
        }
    }
    private fun map(c:Canvas,nanos:Long,compact:Boolean=false) {
        val position=s.position ?: return
        val zoom=if((s.maneuver?.distanceToStepMeters ?: 9999)<150)0 else s.mapZoom
        val visible=when(zoom){0->300f;2->2000f;else->800f}
        val ppm=(if(compact)148f else 188f)/visible
        val t=((nanos-receivedNanos)/1_000_000.0/transitionMs).toFloat().coerceIn(0f,1f)
        val px=previousX+(currentX-previousX)*t; val py=previousY+(currentY-previousY)*t
        val heading=previousBearing+((currentBearing-previousBearing+540)%360-180)*t
        val cx=if(compact)360f else if(s.detour==null)240f else 380f
        val anchor=if(compact)580f else 306f
        c.save()
        if(compact)c.clipRect(254f,418f,466f,604f) else c.clipRect(0f,118f,450f,338f)
        c.translate(cx,anchor);c.rotate(-heading);c.scale(ppm,ppm);c.translate(-px,-py)
        // Road hierarchy (docs/HUD_DESIGN.md "Rich map"): residential thin at 42%, major roads thick at 70%.
        c.drawPath(minorRoads,paint(alpha=107,stroke=(if(compact)1.8f else if(zoom==0)3.5f else 2f)/ppm))
        c.drawPath(majorRoads,paint(alpha=179,stroke=(if(compact)3.5f else if(zoom==0)6f else 4.5f)/ppm))
        var i=0
        c.drawPath(routePath,paint(stroke=(if(compact)if(zoom==0)8f else 6f else if(zoom==0)14f else 10f)/ppm))
        c.drawPath(routePath,paint(black=true,stroke=(if(compact)if(zoom==0)4f else 3.5f else if(zoom==0)8f else 6f)/ppm))
        i=0
        while(i<trafficPaths.size) {
            if(trafficLevels[i]!=TrafficLevel.NORMAL) {
                val p=paint(stroke=(if(compact)if(zoom==0)4f else 3.5f else if(zoom==0)8f else 6f)/ppm)
                // Dash is applied in screen space below, using the same cached path.
                if(trafficLevels[i]==TrafficLevel.SLOW) {
                    p.pathEffect=metricSlowDash(zoom)
                }
                c.drawPath(trafficPaths[i],p)
            }
            i++
        }
        if(zoom==0 && hasManeuverPoint) {
            c.drawCircle(maneuverX,maneuverY,9f/ppm,paint(black=true));c.drawCircle(maneuverX,maneuverY,9f/ppm,paint(stroke=3f/ppm))
        }
        if(s.alerts.isNotEmpty()) {
            val a=s.alerts[0];val ax=x(a.lng);val ay=y(a.lat)
            c.drawCircle(ax,ay,16/ppm,paint(black=true));c.drawCircle(ax,ay,16/ppm,paint(stroke=3/ppm))
            c.save();c.translate(ax,ay);c.rotate(heading);c.scale(1/ppm,1/ppm);icon(c,a.kind,-12f,-12f,24f);c.restore()
        }
        c.restore()
        if(compact) {
            labels(c,px,py,heading,ppm,cx,anchor,258f,462f,426f,600f,3,0.75f)
            c.save();c.translate(cx,anchor);c.scale(.65f,.65f);c.drawPath(chevron,paint());c.restore()
            return
        }
        val lanesShown=(s.maneuver?.current?.lanes?.isNotEmpty()==true) && (s.maneuver?.distanceToStepMeters ?: 9999)<150
        labels(c,px,py,heading,ppm,cx,306f,18f,446f,if(lanesShown)192f else 136f,326f,MAX_LABELS,1f)
        c.save();c.translate(cx,306f);c.drawPath(chevron,paint());c.restore()
        // Remaining-route ribbon, normalized by meters rather than polyline point index.
        box(c,458f,130f,466f,326f,4f,alpha=204)
        val along=position.metersAlongRoute.coerceAtLeast(0);val remaining=(s.routeTotalMeters-along).coerceAtLeast(1)
        i=0
        while(i<s.traffic.size) {
            val segment=s.traffic[i]
            if(segment.endMeters>along && segment.level!=TrafficLevel.NORMAL) {
                val top=326f-196f*((segment.endMeters-along).toFloat()/remaining).coerceIn(0f,1f)
                val bottom=326f-196f*((segment.startMeters-along).toFloat()/remaining).coerceIn(0f,1f)
                c.drawRect(458f,top,466f,bottom,paint(alpha=if(segment.level==TrafficLevel.SLOW)115 else 255))
            };i++
        }
        c.drawCircle(462f,318f,6f,paint());c.drawCircle(462f,318f,6f,paint(black=true,stroke=2f))
        if(jamLabel.isNotEmpty() && s.detour==null && s.traffic.anyJamInWindow(along,visible.toInt())) {
            c.drawLine(226f,196f,170f,196f,paint(alpha=179,stroke=2f));right(c,"Heavy traffic",162f,190f,20f,b=true)
            right(c,jamLabel,162f,210f,17f,204)
        }
        if(s.detour!=null) { val p=paint(stroke=4f);p.pathEffect=alternativeDash;c.drawLine(430f,282f,440f,128f,p) }
    }
    /**
     * Upright street names in priority order ([labelOrder]) inside the given screen box: the turn street
     * bold and bright, the "after" street and major roads brighter than the rest. Skips labels that would
     * overlap one already placed or the chevron. A black halo cuts the road lines behind each name.
     */
    private fun labels(c:Canvas,px:Float,py:Float,heading:Float,ppm:Float,cx:Float,cy:Float,
                       left:Float,right:Float,top:Float,bottom:Float,max:Int,scale:Float) {
        val angle=Math.toRadians(heading.toDouble())
        val cosine=cos(angle).toFloat();val sine=sin(angle).toFloat()
        var placed=0;var k=0
        while(k<labelOrder.size && placed<max) {
            val i=labelOrder[k];k++
            val turn=i==turnRoad;val after=i==cueRoad
            val size=(if(turn)17f else if(after || roadMajor[i])15f else 14f)*scale
            val alpha=if(turn)255 else if(after)166 else if(roadMajor[i])204 else 153
            val w=paint(size,b=turn).measureText(roadNames[i])
            val dx=roadEast[i]-px;val dy=roadNorth[i]-py
            val lx=cx+(dx*cosine+dy*sine)*ppm+8*scale
            val ly=cy+(-dx*sine+dy*cosine)*ppm-8*scale
            if(lx<left || lx+w>right || ly-size<top || ly>bottom)continue
            if(abs(lx+w/2-cx)<w/2+24*scale && abs(ly-cy)<40*scale)continue
            var overlaps=false;var j=0
            while(j<placed){
                if(lx<placedLabelX[j]+placedLabelW[j]+6 && placedLabelX[j]<lx+w+6 && abs(ly-placedLabelY[j])<size+4)overlaps=true
                j++
            }
            if(overlaps)continue
            val halo=paint(size,b=turn,black=true,stroke=4f*scale);c.drawText(roadNames[i],lx,ly,halo)
            c.drawText(roadNames[i],lx,ly,paint(size,alpha,b=turn))
            placedLabelX[placed]=lx;placedLabelY[placed]=ly;placedLabelW[placed]=w;placed++
        }
    }
    private val metricDashes = arrayOf(300f,800f,2000f).map { visible -> DashPathEffect(floatArrayOf(7f*visible/188f,6f*visible/188f),0f) }.toTypedArray()
    private fun metricSlowDash(zoom:Int)=metricDashes[zoom.coerceIn(0,2)]
    private fun List<TrafficSegment>.anyJamInWindow(along:Int,visible:Int):Boolean {
        var i=0;while(i<size){val t=this[i];if(t.level==TrafficLevel.JAM && t.endMeters>along && t.startMeters<along+visible)return true;i++};return false
    }
    private fun status(c:Canvas) {
        if(s.progress==null)return
        text(c,eta,18f,377f,26f,b=true)
        text(c,"ETA",24f+paint(26f,b=true).measureText(eta),377f,13f,153)
        // Fixed slots reserve room for traffic, including long durations and metric distances.
        text(c,statusTime,130f,377f,22f,217)
        text(c,statusDistance,244f,377f,22f,217)
        val delayed=(s.progress?.trafficDelaySeconds ?: 0)>0
        if(delayed) {
            val filled=(s.progress?.trafficDelaySeconds ?: 0)>=300
            box(c,362f,352f,464f,386f,8f,fill=filled)
            c.drawCircle(378f,369f,7f,paint(black=filled,stroke=2f));c.drawLine(378f,365f,378f,369f,paint(black=filled,stroke=2f));c.drawLine(378f,369f,382f,371f,paint(black=filled,stroke=2f))
            right(c,delayLabel,456f,376f,20f,b=true,black=filled)
        } else right(c,"No delays",464f,376f,18f,153)
    }
    private fun icon(c:Canvas,kind:AlertKind,x:Float,y:Float,size:Float,black:Boolean=false) {
        c.save();c.translate(x,y);c.scale(size/24,size/24)
        val p=paint(black=black,stroke=2.2f)
        when(kind) {
            AlertKind.POLICE -> c.drawPath(shield,p)
            AlertKind.SPEED_CAMERA -> {c.drawRoundRect(3f,6f,21f,20f,2f,2f,p);c.drawCircle(12f,13f,4f,p);c.drawLine(7f,3f,17f,3f,p)}
            AlertKind.TRAFFIC_JAM -> {c.drawRoundRect(4f,8f,20f,19f,2f,2f,p);c.drawLine(6f,8f,8f,4f,p);c.drawLine(8f,4f,16f,4f,p);c.drawLine(16f,4f,18f,8f,p)}
            AlertKind.ROAD_CLOSED -> {c.drawCircle(12f,12f,9f,p);c.drawLine(5f,19f,19f,5f,p)}
            else -> {c.drawLine(12f,3f,2f,21f,p);c.drawLine(2f,21f,22f,21f,p);c.drawLine(22f,21f,12f,3f,p);c.drawLine(12f,9f,12f,14f,p);c.drawPoint(12f,18f,p)}
        };c.restore()
    }
    private fun alert(c:Canvas,a:Alert) {
        box(c,8f,334f,472f,392f,12f)
        icon(c,a.kind,22f,346f,30f)
        text(c,alertKind,68f,359f,24f,b=true);text(c,alertAge,68f,379f,15f,alpha=180)
        right(c,alertDistance,456f,375f,34f,b=true)
    }
    private fun detour(c:Canvas,d:DetourOffer) {
        box(c,14f,124f,334f,312f,14f,fill=true,black=true);box(c,14f,124f,334f,312f,14f)
        text(c,"FASTER ROUTE",30f,151f,15f,179,b=true);text(c,detourSaving,30f,195f,44f,b=true)
        text(c,detourVia,30f,223f,20f,217)
        c.drawLine(30f,242f,316f,242f,paint(alpha=64,stroke=4f))
        c.drawLine(30f,242f,30f+286f*((d.expiresAtMs-s.nowMs)/30_000f).coerceIn(0f,1f),242f,paint(stroke=4f))
        box(c,30f,264f,69f,287f,6f,fill=true);text(c,"TAP",37f,281f,15f,b=true,black=true);text(c,"take it",76f,282f,18f)
        box(c,159f,264f,218f,287f,6f,alpha=191);text(c,"2× TAP",165f,281f,15f,191,b=true);text(c,"keep",227f,282f,18f,191)
    }
    private fun menu(c:Canvas,m:HudMode.Menu) {
        if(m.page==MenuPage.TRIP_INFO){trip(c);return}
        text(c,when(m.page){MenuPage.TRAFFIC->"TRAFFIC & ROUTES";MenuPage.SETTINGS->"DISPLAY";MenuPage.CONFIRM_END->"END NAVIGATION";else->"MENU"},18f,38f,16f,153,b=true)
        right(c,menuDestination,462f,38f,18f,153)
        if(m.page==MenuPage.CONFIRM_END){center(c,"End this trip?",240f,170f,44f,b=true);center(c,"TAP to end · 2× TAP to keep going",240f,230f,22f);return}
        if(m.page==MenuPage.TRAFFIC){text(c,delayLabel,30f,108f,44f,b=true);text(c,jamLabel,30f,152f,22f,217);box(c,18f,194f,462f,248f,12f);text(c,"Find faster route",34f,230f,26f,b=true);return}
        val count=if(m.page==MenuPage.SETTINGS)4 else 5
        var i=0
        while(i<count) {
            val top=52f+i*54f;val selected=m.index==i
            if(selected){box(c,18f,top,462f,top+54,12f);c.drawLine(22f,top+14,22f,top+40,paint(stroke=3f))}
            text(c,if(m.page==MenuPage.SETTINGS)displayLabels[i] else menuLabels[i],34f,top+36,26f,if(selected)255 else 200,selected,false)
            val value=if(m.page==MenuPage.SETTINGS)when(i){0->unitsLabel;1->zoomLabel;2->brightnessLabel;else->""} else when(i){1->delayLabel;2->if(s.voiceMuted)"OFF" else "ON";else->""}
            right(c,value,424f,top+33,20f,if(selected)255 else 163,black=false)
            if(i!=2 && i!=4 && m.page==MenuPage.ROOT){c.drawLine(436f,top+20,444f,top+27,paint(black=false,stroke=2.6f));c.drawLine(444f,top+27,436f,top+34,paint(black=false,stroke=2.6f))}
            i++
        }
        center(c,"SWIPE  move       TAP  select       2× TAP  back",240f,378f,15f,140)
    }
    private fun trip(c:Canvas) {
        text(c,"TRIP INFO",18f,36f,16f,153,b=true);right(c,destination,462f,36f,18f,153)
        text(c,"Arrive",18f,104f,22f,191);text(c,eta,84f,109f,64f,b=true)
        var i=0
        while(i<3){val l=18f+i*151f;box(c,l,129f,l+141f,197f,10f,alpha=230);text(c,when(i){0->remainingTime;1->remainingDistance;else->delayLabel},l+12,163f,30f,b=true);text(c,when(i){0->"left";1->"to go";else->"traffic"},l+12,186f,15f,149);i++}
        text(c,"NEXT TURNS",18f,227f,15f,153,b=true)
        val current=s.maneuver
        if(current!=null){glyphs.draw(c,current.current.maneuver,18f,242f,26f,paint(stroke=8.4f));text(c,number,56f,266f,22f,b=true);text(c,road,132f,266f,22f,217)
            current.next?.let { glyphs.draw(c,it.maneuver,18f,280f,26f,paint(alpha=191,stroke=8.4f));text(c,it.roadName,132f,304f,22f,191) }}
        // Protocol currently carries only current + one next step; never invent a third turn/distance.
    }
    private fun arrived(c:Canvas) {
        c.drawCircle(240f,112f,42f,paint(stroke=6f));c.drawLine(221f,114f,234f,127f,paint(stroke=6f));c.drawLine(234f,127f,260f,99f,paint(stroke=6f))
        center(c,"You've arrived",240f,204f,44f,b=true);center(c,destination,240f,247f,26f,217)
        center(c,eta,240f,286f,18f,140);center(c,"Closes in 10 s · 2× TAP to close now",240f,320f,15f,115)
    }
    companion object {
        const val GREEN = 0xFF3DFF8A.toInt()
        private const val MAX_LABELS = 10
        private val SPACES = Regex("\\s+")
        private val NAME_ABBREVIATIONS = listOf("street" to "st","avenue" to "ave","boulevard" to "blvd","road" to "rd",
            "drive" to "dr","lane" to "ln","place" to "pl","court" to "ct","highway" to "hwy","parkway" to "pkwy",
            "north" to "n","south" to "s","east" to "e","west" to "w").map { (long,short) -> Regex("\\b$long\\b") to short }
    }
}
