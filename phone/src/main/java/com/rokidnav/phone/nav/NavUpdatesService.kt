package com.rokidnav.phone.nav

import android.app.Service
import android.content.Intent
import android.os.*
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager
import com.rokidnav.phone.RokidNavApp

/** Google binds within this app and delivers the TBT feed through Messenger on the main thread. */
class NavUpdatesService:Service() {
    private val messenger=Messenger(Handler(Looper.getMainLooper()){message ->
        if(message.what==TurnByTurnManager.MSG_NAV_INFO) {
            val info=TurnByTurnManager.createInstance().readNavInfoFromBundle(message.data)
            val engine=(application as RokidNavApp).navEngine as? GoogleNavigationEngine
            NavInfoMapper.toManeuverUpdate(info)?.let {engine?.onManeuver(it)}
            engine?.onNavStateFromFeed(NavInfoMapper.toNavState(info.navState))
        }
        true
    })
    override fun onBind(intent:Intent?):IBinder=messenger.binder
}
