package com.rokidnav.phone.nav

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Bound service that receives the Navigation SDK "turn-by-turn data feed" (docs:
 * developers.google.com/maps/documentation/navigation/android-sdk/tbt-feed).
 *
 * TODO(M5):
 *  - Hold a Messenger(Handler(Looper.getMainLooper()) { msg -> ... }) and return messenger.binder.
 *  - if (msg.what == TurnByTurnManager.MSG_NAV_INFO):
 *        val navInfo = TurnByTurnManager.createInstance().readNavInfoFromBundle(msg.data)
 *        engine.onManeuver(NavInfoMapper.toManeuverUpdate(navInfo, seq))
 *        engine.onNavStateFromFeed(NavInfoMapper.toNavState(navInfo.navState))
 *  - Engine instance: (application as RokidNavApp).navEngine as GoogleNavigationEngine
 */
class NavUpdatesService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null // TODO(M5): messenger.binder
}
