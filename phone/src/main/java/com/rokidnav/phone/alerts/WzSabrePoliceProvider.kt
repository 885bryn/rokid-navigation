package com.rokidnav.phone.alerts

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.rokidnav.protocol.LatLng
import com.rokidnav.protocol.PositionUpdate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

interface SabreClient {
    suspend fun fetch(center: LatLng): List<Incident>
    fun close()
}

/** One random response action per exchange; requests only target the user-selected WzSabre app. */
class AndroidSabreClient(context: Context) : SabreClient {
    private val context = context.applicationContext
    private var handshake: SabreProtocol.Handshake? = null

    override suspend fun fetch(center: LatLng): List<Incident> {
        val info = handshake ?: exchange("${SabreProtocol.PACKAGE}.HANDSHAKE",
            { SabreProtocol.handshakeRequest(it) }) { messages ->
            SabreProtocol.handshakeResponse(messages.receive())
        }.also { handshake = it }
        val requestId = UUID.randomUUID().toString()
        return exchange(info.requestAction,
            { SabreProtocol.fetchRequest(requestId, center, it) }) { messages ->
            val batches = SabreBatches(requestId)
            var result: List<Incident>?
            while (true) {
                result = batches.accept(SabreProtocol.batch(messages.receive()))
                if (result != null) break
            }
            result!!
        }
    }

    private suspend fun <T> exchange(
        action: String,
        payload: (String) -> String,
        receive: suspend (Channel<String>) -> T,
    ): T = withTimeout(20_000) {
        val responseAction = "${context.packageName}.SABRE_${UUID.randomUUID()}"
        val messages = Channel<String>(64)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != responseAction) return
                val data = intent.getStringExtra("data") ?: return
                if (data.length <= 512_000) messages.trySend(data)
            }
        }
        // Exported because replies originate in the separate WzSabre application.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(responseAction), ContextCompat.RECEIVER_EXPORTED)
        try {
            context.sendBroadcast(Intent(action).apply {
                component = ComponentName(SabreProtocol.PACKAGE, "${SabreProtocol.PACKAGE}.MainBroadcastReceiver")
                putExtra("data", payload(responseAction))
            })
            receive(messages)
        } finally {
            context.unregisterReceiver(receiver)
            messages.close()
        }
    }

    override fun close() {
        val action = handshake?.shutdownAction
        handshake = null
        if (action != null) context.sendBroadcast(Intent(action).apply {
            component = ComponentName(SabreProtocol.PACKAGE, "${SabreProtocol.PACKAGE}.MainBroadcastReceiver")
        })
    }
}

/** Poll only while the coordinator collects an active trip, centred on the latest road-snapped fix. */
class WzSabrePoliceProvider(
    private val client: SabreClient,
    private val positions: Flow<PositionUpdate>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : IncidentProvider {
    private val mutableStatus=kotlinx.coroutines.flow.MutableStateFlow("Waiting for navigation")
    val status:kotlinx.coroutines.flow.StateFlow<String> = mutableStatus
    override val name = "WzSabre reports"
    override fun incidentsAlong(routePolyline: String): Flow<List<Incident>> = flow {
        var previous = emptyList<Incident>()
        try {
            positions.conflate().collect { position ->
                try {
                    previous = client.fetch(LatLng(position.lat, position.lng))
                    mutableStatus.value="WzSabre connected · ${previous.size} nearby reports"
                } catch (cancelled: CancellationException) {
                    // Timeouts are handled below; a trip cancellation must release the receiver immediately.
                    if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                    mutableStatus.value="WzSabre timed out. Enable plugin integration and keep WzSabre running."
                } catch (_: Exception) {
                    // A plugin error must not stop navigation. Retain only unexpired reports.
                    mutableStatus.value="WzSabre reports unavailable. Check the plugin connection."
                }
                val now = nowMs()
                previous = previous.filter { it.expiresAtMs?.let { expiry -> expiry > now } != false }
                emit(previous)
                delay(30_000)
            }
        } finally {
            client.close()
            mutableStatus.value="Waiting for navigation"
        }
    }
}
