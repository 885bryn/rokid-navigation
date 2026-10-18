package com.rokidnav.glasses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rokidnav.glasses.input.GlassesKey
import com.rokidnav.glasses.link.*
import com.rokidnav.glasses.state.HudStore
import com.rokidnav.protocol.*
import kotlinx.coroutines.launch

class HudViewModel(application:Application) : AndroidViewModel(application) {
    private val seq=SeqCounter()
    private lateinit var link:PhoneLink
    private var width=0
    private var height=0
    val store=HudStore(requestResync={if(::link.isInitialized)link.send(ResyncRequest(seq.next(),"route chunk timeout"))}) { if(::link.isInitialized)link.send(it.copy(seq=seq.next())) }
    init { viewModelScope.launch { store.tickLoop() } }
    fun initialize(demo:Boolean) {
        if(::link.isInitialized)return
        // Confirmed on hardware: CXR remote launch selects this bridge and returns GlassesHello.
        link=if(demo) DemoPhoneLink(viewModelScope) {
            getApplication<Application>().assets.open("demo_trip.jsonl").bufferedReader().use { DemoRecording.parse(it.lineSequence()) }
        } else CxrPhoneLink.get(getApplication<Application>())
        viewModelScope.launch { link.incoming.collect { if(it is HelloRequest){if(width>0)hello()} else store.onMessage(it) } }
        viewModelScope.launch { link.connected.collect { connected -> store.onConnection(connected);if(connected && width>0)hello() } }
        (link as? DemoPhoneLink)?.start()
    }
    private fun hello(){link.send(GlassesHello(seq.next(),BuildConfig.VERSION_NAME,width,height))}
    /** Back in the foreground (e.g. reopened from the Rokid home screen): say hello so the phone resumes. */
    fun onForeground(){if(::link.isInitialized && width>0)hello()}
    fun onDisplayReady(width:Int,height:Int){this.width=width;this.height=height;if(::link.isInitialized)hello()}
    /** Returns whether the HUD used the key (see [HudStore.onKey]). */
    fun onKey(key:GlassesKey):Boolean{if(::link.isInitialized)link.send(InputEvent(seq.next(),key.name));return store.onKey(key)}
    override fun onCleared(){if(::link.isInitialized)link.close()}
}
