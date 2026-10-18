package com.rokidnav.glasses.state

import com.rokidnav.glasses.input.GlassesKey
import com.rokidnav.protocol.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** Single writer: messages/keys -> immutable HudState. Renderers only consume snapshots. */
class HudStore(private val clock:()->Long=System::currentTimeMillis,private val requestResync:()->Unit={},private val sendAction:(GlassesAction)->Unit) {
    private val mutable=MutableStateFlow(HudState())
    val state:StateFlow<HudState> = mutable.asStateFlow()
    private var assembler=RouteAssembler()
    private var lastSeq=-1L
    private var routePendingAt=0L
    private var routeId:String?=null
    private val dismissed=HashSet<String>()
    private var menuTouched=0L
    private var arrivedAt=0L
    private var returnMode:HudMode=HudMode.Compact
    fun onConnection(connected:Boolean){mutable.update {it.copy(linkConnected=connected)}}
    fun onMessage(m:NavMessage) {
        if(m !is SessionStart && m.seq<=lastSeq)return
        lastSeq=m.seq
        when(m) {
            is SessionStart->{
                assembler=RouteAssembler();routeId=null;routePendingAt=0;dismissed.clear()
                mutable.update {it.copy(units=m.units,clock24h=m.clock24h,destinationName=m.destinationName,
                    route=emptyList(),routeTotalMeters=0,traffic=emptyList(),contextRoads=emptyList(),
                    position=null,previousPosition=null,maneuver=null,progress=null,alerts=emptyList(),detour=null,navState=NavState.ROUTING)}
            }
            is RouteChunk->{routePendingAt=clock();assembler.accept(m)?.let { line->
                routePendingAt=0
                val changed=routeId!=m.routeId;routeId=m.routeId
                mutable.update {it.copy(route=PolylineCodec.decode(line),routeTotalMeters=m.totalMeters,
                    contextRoads=if(changed)emptyList() else it.contextRoads,traffic=if(changed)emptyList() else it.traffic)}
            }}
            is PositionUpdate->mutable.update {it.copy(previousPosition=it.position,position=m)}
            is ManeuverUpdate->mutable.update {it.copy(maneuver=m)}
            is TripProgress->mutable.update {it.copy(progress=m)}
            is TrafficSegments->if(m.routeId==routeId)mutable.update {it.copy(traffic=m.segments)}
            is RoadContext->if(m.routeId==routeId)mutable.update {it.copy(contextRoads=m.roads.map {road->HudRoad(PolylineCodec.decode(road.encodedPolyline),road.name,road.major)})}
            is Alert->if(m.id !in dismissed)mutable.update {s->s.copy(
                alerts=(s.alerts.filterNot {it.id==m.id}+m).sortedBy {it.distanceAheadMeters},
                mode=if(s.mode is HudMode.Menu && s.alerts.none {it.id==m.id})returnMode else s.mode)}
            is AlertCleared->{dismissed.remove(m.id);mutable.update {s->s.copy(alerts=s.alerts.filterNot {it.id==m.id})}}
            is DetourOffer->mutable.update {it.copy(detour=m)}
            is DetourResolved->mutable.update {if(it.detour?.offerId==m.offerId)it.copy(detour=null) else it}
            is NavStateUpdate->{
                if(m.state==NavState.ARRIVED)arrivedAt=clock()
                if(m.state==NavState.IDLE || m.state==NavState.ENDED)dismissed.clear()
                mutable.update {it.copy(navState=m.state,alerts=if(m.state in listOf(NavState.IDLE,NavState.ENDED,NavState.ARRIVED))emptyList() else it.alerts)}
            }
            is SettingsUpdate->mutable.update {it.copy(voiceMuted=m.voiceMuted,units=m.units,mapZoom=m.mapZoomLevel.coerceIn(0,2),brightnessPercent=m.brightnessPercent.coerceIn(0,100))}
            else->Unit
        }
    }
    fun onKey(key:GlassesKey) {
        menuTouched=clock()
        val s=state.value
        val menu=s.mode as? HudMode.Menu
        if(menu!=null){menuKey(menu,key);return}
        val d=s.detour
        when {
            d!=null && (key==GlassesKey.TAP || key==GlassesKey.DOUBLE_TAP)->{
                sendAction(GlassesAction(0,if(key==GlassesKey.TAP)ActionType.ACCEPT_DETOUR else ActionType.DISMISS_DETOUR,d.offerId))
                mutable.update {it.copy(detour=null)}
            }
            key==GlassesKey.LONG_PRESS->{returnMode=s.mode;mode(HudMode.Menu(MenuPage.ROOT))}
            key==GlassesKey.TAP->mode(if(s.mode==HudMode.Compact)HudMode.Map else HudMode.Compact)
            key==GlassesKey.SWIPE_FORWARD->{mutable.update {it.copy(mapZoom=(it.mapZoom-1).coerceAtLeast(0))};sendAction(GlassesAction(0,ActionType.ZOOM_IN))}
            key==GlassesKey.SWIPE_BACK->{mutable.update {it.copy(mapZoom=(it.mapZoom+1).coerceAtMost(2))};sendAction(GlassesAction(0,ActionType.ZOOM_OUT))}
            key==GlassesKey.DOUBLE_TAP->{s.alerts.firstOrNull()?.let {dismissed.add(it.id)};mutable.update {it.copy(alerts=it.alerts.drop(1),navState=if(it.navState==NavState.ARRIVED)NavState.IDLE else it.navState)}}
        }
    }
    private fun mode(value:HudMode){mutable.update {it.copy(mode=value)}}
    private fun menuKey(menu:HudMode.Menu,key:GlassesKey) {
        if(key==GlassesKey.LONG_PRESS){mode(returnMode);return}
        if(key==GlassesKey.DOUBLE_TAP || key==GlassesKey.BACK){mode(if(menu.page==MenuPage.ROOT)returnMode else HudMode.Menu(MenuPage.ROOT));return}
        val count=when(menu.page){MenuPage.ROOT->5;MenuPage.SETTINGS->4;else->1}
        if(key==GlassesKey.SWIPE_FORWARD || key==GlassesKey.SWIPE_BACK){mode(menu.copy(index=(menu.index+if(key==GlassesKey.SWIPE_FORWARD)1 else count-1)%count));return}
        if(key!=GlassesKey.TAP)return
        when(menu.page) {
            MenuPage.ROOT->when(menu.index) {
                0->mode(HudMode.Menu(MenuPage.TRIP_INFO))
                1->mode(HudMode.Menu(MenuPage.TRAFFIC))
                2->{mutable.update {it.copy(voiceMuted=!it.voiceMuted)};sendAction(GlassesAction(0,ActionType.TOGGLE_VOICE))}
                3->mode(HudMode.Menu(MenuPage.SETTINGS))
                4->mode(HudMode.Menu(MenuPage.CONFIRM_END))
            }
            MenuPage.SETTINGS->when(menu.index) {
                0->{mutable.update {it.copy(units=if(it.units==Units.IMPERIAL)Units.METRIC else Units.IMPERIAL)};sendAction(GlassesAction(0,ActionType.TOGGLE_UNITS))}
                1->{mutable.update {it.copy(mapZoom=(it.mapZoom+1)%3)};sendAction(GlassesAction(0,ActionType.CYCLE_ZOOM))}
                2->{sendAction(GlassesAction(0,ActionType.CYCLE_BRIGHTNESS))}
                else->mode(HudMode.Menu(MenuPage.ROOT))
            }
            MenuPage.TRAFFIC->{sendAction(GlassesAction(0,ActionType.SHOW_ALTERNATIVES));mode(returnMode)}
            MenuPage.CONFIRM_END->{sendAction(GlassesAction(0,ActionType.END_NAVIGATION));mode(returnMode)}
            else->Unit
        }
    }
    fun tick(now:Long=clock()) {
        if(routePendingAt>0 && now-routePendingAt>=5000){routePendingAt=0;requestResync()}
        mutable.update {s->s.copy(nowMs=now,detour=s.detour?.takeIf {it.expiresAtMs>now},
            mode=if(s.mode is HudMode.Menu && now-menuTouched>=10_000)returnMode else s.mode,
            navState=if(s.navState==NavState.ARRIVED && now-arrivedAt>=10_000)NavState.IDLE else s.navState)}
    }
    suspend fun tickLoop(){while(true){tick();delay(1000)}}
}
