package com.rokidnav.glasses

import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.rokidnav.glasses.input.GlassesKey
import com.rokidnav.glasses.input.KeyReceiver
import com.rokidnav.glasses.input.KeyType
import com.rokidnav.glasses.ui.HudView
import kotlinx.coroutines.launch

/**
 * Launched remotely by the phone's CXR-L CUSTOM_APP session. Also runnable standalone (launcher
 * icon / emulator) where it falls back to demo mode — see [HudViewModel].
 */
class MainActivity : AppCompatActivity() {
    private lateinit var vm: HudViewModel
    private lateinit var hud: HudView
    private val keyReceiver = KeyReceiver { vm.onKey(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vm = ViewModelProvider(this)[HudViewModel::class.java]
        hud = HudView(this)
        setContentView(hud)

        registerReceiver(keyReceiver, IntentFilter().apply {
            KeyType.entries.forEach { addAction(it.action) }
        })
        onBackPressedDispatcher.addCallback(this) { vm.onKey(GlassesKey.BACK) }

        hud.post { vm.onDisplayReady(hud.width, hud.height) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.store.state.collect { hud.render(it) }
            }
        }
    }

    /**
     * Single-finger touchpad swipes most likely arrive as KeyEvents, not broadcasts (unconfirmed).
     * TODO(M2/M3): log every keyCode on hardware, then map them in GlassesKey.fromKeyCode.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val key = GlassesKey.fromKeyCode(keyCode) ?: return super.onKeyDown(keyCode, event)
        vm.onKey(key)
        return true
    }

    override fun onDestroy() {
        unregisterReceiver(keyReceiver)
        super.onDestroy()
    }
}
