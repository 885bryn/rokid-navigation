package com.rokidnav.phone.glasses

import android.app.Activity
import android.content.Context
import android.util.Log
import com.rokid.cxr.Caps
import com.rokidnav.phone.BuildConfig
import com.rokidnav.protocol.Channels
import com.rokidnav.protocol.NavMessage
import com.rokidnav.protocol.ProtocolCodec
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CXR-L implementation. API names come from the CXR-L 1.1.2 docs ("Quick Start", "Glasses Custom App",
 * "Custom Commands"). Imports for SDK types are left for M3 because their packages aren't documented;
 * resolve them against the real AAR / CxrlSample. Lines marked VERIFY are unconfirmed.
 *
 * Flow: checkEnvironment -> authorize(activity) -> token -> connect() -> CUSTOM_APP session ->
 * SDK installs/launches glasses APK -> onSessionStarted -> Ready.
 */
class CxrGlassesLink(private val context: Context) : GlassesLink {

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    override val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<NavMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incoming = _incoming.asSharedFlow()

    // Never log this. Persist only if M9 decides to (EncryptedSharedPreferences).
    private var token: String? = null

    // private val manager = CxrSessionManager.getInstance(context.applicationContext)
    // private var session: CxrSession? = null

    override fun checkEnvironment(): EnvironmentStatus {
        // TODO(M3):
        // when (val s = manager.checkRokidAppCompatibility(context)) {
        //     is RokidAppStatus.NotInstalled -> EnvironmentStatus.RokidAppMissing(s.downloadUrl)
        //     is RokidAppStatus.VersionTooLow -> EnvironmentStatus.RokidAppTooOld(s.downloadUrl)
        //     is RokidAppStatus.Compatible -> EnvironmentStatus.Ok
        // }
        // Do NOT call manager.isGlassesBtConnected() here: it is always false before a session exists.
        return EnvironmentStatus.Ok
    }

    override fun authorize(activity: Activity) {
        _state.value = LinkState.Authorizing
        // TODO(M3): we only need display + custom commands; request the minimum GlassPermission set
        // the SDK accepts (VERIFY whether an empty list is allowed).
        // manager.requestAuthorization(activity, listOf()) { result ->
        //     if (result.isSuccess && result.token != null) {
        //         token = result.token
        //         _state.value = LinkState.Authorized
        //     } else {
        //         _state.value = LinkState.Failed("auth ${result.errorCode}: ${result.message}")
        //     }
        // }
        // MainActivity must also forward onActivityResult -> manager.parseAuthorizationResult(...)
    }

    override fun connect() {
        val t = token ?: run { _state.value = LinkState.Failed("not authorized"); return }
        _state.value = LinkState.Connecting
        // TODO(M3):
        // val apk = GlassesApkInstaller.ensureApkOnDisk(context)   // copies assets/glasses.apk to getExternalFilesDir("rokid")
        // val config = SessionConfig(
        //     sessionType = SessionType.CUSTOM_APP,
        //     glassesPackageName = BuildConfig.GLASSES_PACKAGE,
        //     glassesActivityName = BuildConfig.GLASSES_ACTIVITY,
        //     glassesApkPath = apk?.absolutePath,
        //     aiInterceptMode = AiInterceptMode.ALLOW_WITH_PAUSE,   // decision: see docs/ARCHITECTURE.md
        // )
        // val s = manager.create(config)
        // s.addLifecycleCallback(lifecycleCallback)                  // register BEFORE connect
        // s.addCustomCmdCallback(object : ICustomCmdSessionCallback {
        //     override fun onCustomCmdResult(channelKey: String, data: ByteArray?) = onFrame(channelKey, data)
        // })
        // session = s
        // s.connect(t)
    }

    // Lifecycle mapping (TODO(M3) wire into ISessionLifecycleCbk):
    //   onConnectResult(false, code)      -> Failed("connect $code")
    //   onSessionStarted()                -> Ready
    //   onSessionPaused(AI_ASSIST)        -> Paused("ai")
    //   onSessionPaused(BT_DISCONNECTED)  -> Paused("bluetooth")
    //   onSessionResumed()                -> Ready   (coordinator then sends a full resync)
    //   onSessionTerminating(..)          -> stop sending
    //   onSessionClosed(CHAIN_TORN|LINK_LOST) -> Closed(needsReauth = true), token = null
    //   onSessionClosed(other)            -> Closed(needsReauth = false)

    private fun onFrame(channelKey: String, data: ByteArray?) {
        if (channelKey != Channels.GLASSES_TO_PHONE || data == null) return
        val caps = runCatching { Caps.fromBytes(data) }.getOrNull() ?: return
        if (caps.size() < 2) return
        val msg = ProtocolCodec.decode(caps.at(0).asString(), caps.at(1).asString())
        if (msg == null) {
            Log.w(TAG, "dropped unparseable frame (${data.size} bytes)")
            return
        }
        _incoming.tryEmit(msg)
    }

    override fun send(message: NavMessage): Boolean {
        if (_state.value != LinkState.Ready) return false
        val (magic, body) = ProtocolCodec.encode(message)
        val payload = Caps().apply {
            write(magic)
            write(body)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "-> ${message::class.simpleName} seq=${message.seq} ${body.length}B")
        // TODO(M3):
        // val r = session?.sendCustomCmd(Channels.PHONE_TO_GLASSES, payload, ByteArray(0)) ?: return false
        // return r.isSuccess
        return false
    }

    override fun setBrightness(percent: Int) {
        // TODO(M8): session?.setGlassBrightness(percent.coerceIn(0, 100))
    }

    override fun disconnect() {
        // TODO(M3): session?.close()
        _state.value = LinkState.Idle
    }

    private companion object {
        const val TAG = "CxrGlassesLink"
    }
}
