package com.rokidnav.glasses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rokidnav.glasses.input.GlassesKey
import com.rokidnav.glasses.link.CxrPhoneLink
import com.rokidnav.glasses.link.DemoPhoneLink
import com.rokidnav.glasses.link.PhoneLink
import com.rokidnav.glasses.state.HudStore
import com.rokidnav.protocol.GlassesHello
import com.rokidnav.protocol.InputEvent
import com.rokidnav.protocol.SeqCounter
import kotlinx.coroutines.launch

class HudViewModel : ViewModel() {
    private val seq = SeqCounter()

    // TODO(M2): choose DemoPhoneLink when not launched by CXR (e.g. intent extra / no bridge connect
    //  within 3 s / BuildConfig flag). Hard-coded to demo until M3 so HUD work can start immediately.
    private val link: PhoneLink = DemoPhoneLink(viewModelScope).also { it.start() }
    @Suppress("unused") private fun realLink(): PhoneLink = CxrPhoneLink()

    val store = HudStore(sendAction = { link.send(it.copy(seq = seq.next())) })

    init {
        viewModelScope.launch { link.incoming.collect(store::onMessage) }
        viewModelScope.launch { store.tickLoop() }
    }

    fun onDisplayReady(width: Int, height: Int) {
        link.send(GlassesHello(seq.next(), BuildConfig.VERSION_NAME, width, height))
    }

    fun onKey(key: GlassesKey) {
        link.send(InputEvent(seq.next(), key.name))
        store.onKey(key)
    }

    override fun onCleared() {
        link.close()
    }
}
