package com.rokidnav.phone.session

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service (type=location) started when guidance starts, stopped when it ends.
 * Keeps the process alive with the screen off so the glasses keep getting updates.
 *
 * TODO(M9): notification channel + ongoing notification ("Navigating to X · ETA 12:41", End action),
 *  startForeground(id, notification, FOREGROUND_SERVICE_TYPE_LOCATION). Note the Rokid AI App must
 *  also stay alive in the background; surface a hint if the link drops while we're backgrounded.
 */
class NavForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
