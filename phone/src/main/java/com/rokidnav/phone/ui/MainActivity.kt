package com.rokidnav.phone.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rokidnav.phone.RokidNavApp

/**
 * Single activity. Screen flow (docs/ARCHITECTURE.md, "Phone UX"):
 *   Setup (Rokid AI App check -> authorize -> connect glasses)
 *   -> Search (Places autocomplete, recents)
 *   -> RoutePicker (Routes API alternatives on a Google map, traffic delay per route)
 *   -> ActiveNav (Navigation SDK NavigationView + glasses link status + End button)
 *   Debug screen: live protocol log, link state, "send test message", simulate drive.
 */
class MainActivity : ComponentActivity() {
    private val app get() = RokidNavApp.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { PlaceholderScreen(app) }
            }
        }
    }

    @Deprecated("CXR-L still returns authorization results via onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // TODO(M3): forward to CxrSessionManager.parseAuthorizationResult(resultCode, data) via CxrGlassesLink
    }
}

@Composable
private fun PlaceholderScreen(app: RokidNavApp) {
    val linkState by app.glassesLink.state.collectAsState()
    Column(Modifier.padding(24.dp)) {
        Text("Rokid Nav — scaffold", style = MaterialTheme.typography.headlineSmall)
        Text("Glasses link: $linkState")
        Text("Replace with ui/setup, ui/search, ui/route, ui/nav, ui/debug screens (see docs/TASKS.md).")
    }
}
