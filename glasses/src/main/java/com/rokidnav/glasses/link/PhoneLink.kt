package com.rokidnav.glasses.link

import android.util.Log
import com.rokid.cxr.Caps
import com.rokidnav.protocol.Channels
import com.rokidnav.protocol.NavMessage
import com.rokidnav.protocol.ProtocolCodec
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

interface PhoneLink {
    val connected: StateFlow<Boolean>
    val incoming: Flow<NavMessage>
    fun send(message: NavMessage)
    fun close()
}

/**
 * CXR-S bridge, per the CXR docs ("Custom Commands" -> "Glasses side: CXRServiceBridge").
 * TODO(M3): add the CXRServiceBridge import once the cxr-service-bridge version is pinned (M0), then
 * uncomment. StatusListener's signature may differ by CXR-S version; match the real AAR.
 */
class CxrPhoneLink : PhoneLink {
    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected

    private val _incoming = MutableSharedFlow<NavMessage>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incoming = _incoming.asSharedFlow()

    // private val bridge = CXRServiceBridge()

    init {
        // bridge.setStatusListener(object : CXRServiceBridge.StatusListener {
        //     override fun onConnected(p0: String?, p1: String?, p2: Int) { _connected.value = true }
        //     override fun onDisconnected() { _connected.value = false }
        //     override fun onConnecting(p0: String?, p1: String?, p2: Int) {}
        //     override fun onARTCStatus(p0: Float, p1: Boolean) {}
        //     override fun onRokidAccountChanged(p0: String?) {}
        //     override fun onAudioNoise(p0: Float) {}
        // })
        // bridge.subscribe(Channels.PHONE_TO_GLASSES, object : CXRServiceBridge.MsgCallback {
        //     override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) = onFrame(name, args)
        // })
    }

    private fun onFrame(name: String?, args: Caps?) {
        if (name != Channels.PHONE_TO_GLASSES || args == null || args.size() < 2) return
        val msg = ProtocolCodec.decode(args.at(0).asString(), args.at(1).asString())
        if (msg == null) Log.w("CxrPhoneLink", "dropped unparseable frame") else _incoming.tryEmit(msg)
    }

    override fun send(message: NavMessage) {
        val (magic, body) = ProtocolCodec.encode(message)
        val caps = Caps().apply {
            write(magic)
            write(body)
        }
        // bridge.sendMessage(Channels.GLASSES_TO_PHONE, caps)
    }

    override fun close() {
        // TODO(M3): unsubscribe if the bridge exposes it
    }
}
