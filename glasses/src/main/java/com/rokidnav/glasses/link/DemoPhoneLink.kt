package com.rokidnav.glasses.link

import com.rokidnav.protocol.NavMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Plays a scripted trip so the HUD can be built and reviewed on an emulator (480x398 AVD) with no
 * phone, no glasses, no API key. This is the main development loop for all HUD work.
 *
 * TODO(M2): load assets/demo_trip.jsonl (one protocol JSON message per line, with a "delayMs"
 *  wrapper or a fixed cadence), and emit: session_start, route_chunk(s), then pos/maneuver/progress
 *  every second along the route, a traffic update, a police alert at ~40%, a detour_offer at ~60%,
 *  nav_state ARRIVED at the end. Loop forever. Actions the HUD sends are logged; ACCEPT_DETOUR may
 *  swap to a second scripted route.
 *  Generate the .jsonl once with a small JVM script in /tools from a real Routes API polyline.
 */
class DemoPhoneLink(private val scope: CoroutineScope) : PhoneLink {
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)
    private val _incoming = MutableSharedFlow<NavMessage>(extraBufferCapacity = 128)
    override val incoming = _incoming.asSharedFlow()

    fun start() {
        // TODO(M2)
    }

    override fun send(message: NavMessage) {
        android.util.Log.d("DemoPhoneLink", "HUD -> phone: $message")
    }

    override fun close() = Unit
}
