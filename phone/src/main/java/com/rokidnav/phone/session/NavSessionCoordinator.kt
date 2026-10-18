package com.rokidnav.phone.session

import com.rokidnav.phone.alerts.*
import com.rokidnav.phone.glasses.*
import com.rokidnav.phone.nav.*
import com.rokidnav.phone.traffic.*
import com.rokidnav.phone.settings.*
import com.rokidnav.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Serial main-thread coordinator. Cached messages get fresh sequence numbers on reconnect. */
class NavSessionCoordinator(private val scope:CoroutineScope,private val link:GlassesLink,
    private val engine:NavigationEngine,private val traffic:TrafficMonitor,private val incidents:IncidentProvider,
    private val roads:com.rokidnav.phone.map.RoadContextProvider?=null,
    private val settings:SettingsRepository?=null) {
    private val seq=SeqCounter()
    private val lastSent=linkedMapOf<String,NavMessage>()
    private val activeAlerts=linkedMapOf<String,Alert>()
    private var pending:DetourEvaluator.Decision?=null
    private var offerJob:Job?=null
    private var accepting=false
    private val offer=MutableStateFlow<DetourOffer?>(null)
    val detour=offer.asStateFlow()
    private var preferences=UserSettings()
    fun sendTestSnapshot(build:(()->Long)->List<NavMessage>) {
        if(link.state.value==LinkState.Ready)build(seq::next).forEach {link.send(it)}
    }
    fun start() {
        scope.launch {engine.destination.filterNotNull().collect {
            sendStateful("session",SessionStart(seq.next(),preferences.units,false,it.name))
        }}
        if(settings!=null)scope.launch {settings.state.collect(::applySettings)}
        scope.launch {engine.maneuver.collect {sendStateful("maneuver",it.copy(seq=seq.next()))}}
        scope.launch {engine.progress.collect {sendStateful("progress",it.copy(seq=seq.next()))}}
        scope.launch {engine.position.collect {sendStateful("position",it.copy(seq=seq.next()))}}
        scope.launch {engine.navState.collect {
            if(it==NavState.ENDED || it==NavState.ARRIVED)resolve(false)
            sendStateful("nav_state",NavStateUpdate(seq.next(),it))
        }}
        scope.launch {engine.activeRoute.filterNotNull().collect {
            lastSent.remove("road_context");lastSent.remove("traffic");sendRoute()
        }}
        scope.launch {traffic.segments.collect {
            val id=engine.activeRoute.value?.routeId ?: return@collect
            sendStateful("traffic",TrafficSegments(seq.next(),id,it))
        }}
        scope.launch {traffic.detours.collect(::offerDetour)}
        scope.launch {
            activeGuidance().collectLatest {route ->
                val provider=roads ?: return@collectLatest
                if(route==null)return@collectLatest
                var center:LatLng?=null
                engine.position.collect {fix ->
                    val point=LatLng(fix.lat,fix.lng)
                    if(center==null || Geo.distanceMeters(center!!,point)>=600) {
                        val nearby=provider.roadsNear(point)
                        sendStateful("road_context",RoadContext(seq.next(),route.routeId,nearby))
                        center=point.takeIf {nearby.isNotEmpty()};delay(30_000)
                    }
                }
            }
        }
        scope.launch {
            activeGuidance().collectLatest {route ->
                activeAlerts.keys.toList().forEach {sendAlert(AlertCleared(seq.next(),it))}
                if(route==null)return@collectLatest
                val tracker=AlertTracker(PolylineCodec.decode(route.encodedPolyline))
                coroutineScope {
                    var snapshot=emptyList<Incident>();var position:PositionUpdate?=null
                    fun update(){position?.let {tracker.update(snapshot,it,System.currentTimeMillis(),seq::next).forEach(::sendAlert)}}
                    launch {incidents.incidentsAlong(route.encodedPolyline).collect {snapshot=it;update()}}
                    launch {engine.position.collect {position=it;update()}}
                    launch {while(isActive){delay(1000);update()}}
                }
            }
        }
        scope.launch {link.incoming.collect {when(it){is GlassesHello,is ResyncRequest->resync();is GlassesAction->action(it);else->Unit}}}
        scope.launch {link.state.collect {if(it==LinkState.Ready){applySettings(settings?.state?.value ?: preferences);resync()}}}
        traffic.start(scope)
    }
    private fun activeGuidance()=combine(engine.activeRoute,engine.navState){route,state->
        route.takeIf {state in listOf(NavState.ACTIVE,NavState.REROUTING,NavState.GPS_LOST)}}.distinctUntilChanged()
    private fun sendStateful(key:String,message:NavMessage){lastSent[key]=message;link.send(message)}
    private fun sendAlert(m:NavMessage) {
        val id=when(m){is Alert->{activeAlerts[m.id]=m;m.id};is AlertCleared->{activeAlerts.remove(m.id);m.id};else->return}
        sendStateful("alert:$id",m)
    }
    private fun sendRoute(){engine.activeRoute.value?.let {route->
        RouteChunker.split(route.routeId,route.encodedPolyline,route.totalMeters,seq::next).forEach {link.send(it)}
    }}
    private fun resync() {
        lastSent["session"]?.let {link.send(fresh(it))};sendRoute()
        lastSent.filterKeys {it!="session"}.values.forEach {link.send(fresh(it))}
        offer.value?.takeIf {it.expiresAtMs>System.currentTimeMillis()}?.let {link.send(it.copy(seq=seq.next()))}
    }
    private fun fresh(m:NavMessage):NavMessage=when(m) {
        is SessionStart->m.copy(seq=seq.next());is ManeuverUpdate->m.copy(seq=seq.next())
        is TripProgress->m.copy(seq=seq.next());is PositionUpdate->m.copy(seq=seq.next())
        is RoadContext->m.copy(seq=seq.next());is SettingsUpdate->m.copy(seq=seq.next())
        is TrafficSegments->m.copy(seq=seq.next());is NavStateUpdate->m.copy(seq=seq.next())
        is Alert->m.copy(seq=seq.next());is AlertCleared->m.copy(seq=seq.next());else->m
    }
    internal fun offerDetour(decision:DetourEvaluator.Decision) {
        if(pending!=null)return
        pending=decision
        val message=DetourOffer(seq.next(),"d${seq.next()}",decision.savesSeconds,decision.extraMeters,
            decision.option.description,expiresAtMs=System.currentTimeMillis()+30_000,encodedPolyline=decision.option.encodedPolyline)
        offer.value=message;link.send(message)
        offerJob=scope.launch {delay(30_000);resolve(false)}
    }
    private fun resolve(accepted:Boolean) {
        val message=offer.value ?: return
        if(!accepted)pending?.let {traffic.dismiss(it.option,System.currentTimeMillis())}
        offerJob?.cancel();offerJob=null;pending=null;offer.value=null;accepting=false
        link.send(DetourResolved(seq.next(),message.offerId,accepted))
    }
    fun respondToDetour(accept:Boolean){offer.value?.let {action(GlassesAction(0,if(accept)ActionType.ACCEPT_DETOUR else ActionType.DISMISS_DETOUR,it.offerId))}}
    private fun applySettings(value:UserSettings) {
        preferences=value;engine.setVoiceMuted(value.voiceMuted)
        if(link.state.value==LinkState.Ready)link.setBrightness(value.brightness)
        sendStateful("settings",SettingsUpdate(seq.next(),value.voiceMuted,value.units,value.zoom,value.brightness))
    }
    private fun changeSettings(transform:(UserSettings)->UserSettings) {
        if(settings!=null)settings.update(transform) else applySettings(transform(preferences))
    }
    private fun action(a:GlassesAction) {
        when(a.action) {
            ActionType.ACCEPT_DETOUR->{
                val o=offer.value?.takeIf {it.offerId==a.targetId} ?: return
                val decision=pending ?: return
                if(accepting)return
                if(o.expiresAtMs<=System.currentTimeMillis()){resolve(false);return}
                accepting=true;offerJob?.cancel()
                scope.launch {val success=engine.switchRoute(decision.option);if(success)traffic.seed(decision.option);if(offer.value?.offerId==a.targetId)resolve(success)}
            }
            ActionType.DISMISS_DETOUR->if(offer.value?.offerId==a.targetId && !accepting)resolve(false)
            ActionType.END_NAVIGATION->engine.stop()
            ActionType.TOGGLE_VOICE->changeSettings {it.copy(voiceMuted=!it.voiceMuted)}
            ActionType.TOGGLE_UNITS->changeSettings {it.copy(units=if(it.units==Units.IMPERIAL)Units.METRIC else Units.IMPERIAL)}
            ActionType.ZOOM_IN->changeSettings {it.copy(zoom=(it.zoom-1).coerceAtLeast(0))}
            ActionType.ZOOM_OUT->changeSettings {it.copy(zoom=(it.zoom+1).coerceAtMost(2))}
            ActionType.CYCLE_ZOOM->changeSettings {it.copy(zoom=(it.zoom+1)%3)}
            ActionType.CYCLE_BRIGHTNESS->changeSettings {it.copy(brightness=when {it.brightness<35->35;it.brightness<60->60;it.brightness<85->85;else->15})}
            ActionType.SHOW_ALTERNATIVES->traffic.forceRefresh()
        }
    }
}
