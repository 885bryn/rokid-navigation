package com.rokidnav.glasses.input

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

/** System broadcasts for temple keys / touchpad, from the CXR docs ("Glasses Keys and System Broadcasts"). */
enum class KeyType(val action: String) {
    CLICK("com.android.action.ACTION_SPRITE_BUTTON_CLICK"),
    BUTTON_DOWN("com.android.action.ACTION_SPRITE_BUTTON_DOWN"),
    BUTTON_UP("com.android.action.ACTION_SPRITE_BUTTON_UP"),
    DOUBLE_CLICK("com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"),
    LONG_PRESS("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"),
    AI_START("com.android.action.ACTION_AI_START"),
    TWO_FINGER_SINGLE_TAP("com.android.action.ACTION_TWO_FINGER_SINGLE_TAP"),
    TWO_FINGER_DOUBLE_TAP("com.android.action.ACTION_TWO_FINGER_DOUBLE_TAP"),
    TWO_FINGER_SWIPE_FORWARD("com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD"),
    TWO_FINGER_SWIPE_BACK("com.android.action.ACTION_TWO_FINGER_SWIPE_BACK"),
    SETTINGS_KEY("com.android.action.ACTION_SETTINGS_KEY");

    companion object {
        fun fromAction(action: String?) = entries.find { it.action == action }
    }
}

/** Our normalized input vocabulary. HUD logic only ever sees these. */
enum class GlassesKey {
    TAP, DOUBLE_TAP, LONG_PRESS, SWIPE_FORWARD, SWIPE_BACK, BACK;

    companion object {
        fun fromKeyType(t: KeyType): GlassesKey? = when (t) {
            KeyType.CLICK -> TAP
            KeyType.DOUBLE_CLICK -> DOUBLE_TAP
            KeyType.LONG_PRESS -> LONG_PRESS
            KeyType.TWO_FINGER_SWIPE_FORWARD -> SWIPE_FORWARD
            KeyType.TWO_FINGER_SWIPE_BACK -> SWIPE_BACK
            else -> null // AI_START etc. belong to the system; never consume them
        }

        /** TODO(M3): fill in from on-device logging. DPAD guesses below are placeholders. */
        fun fromKeyCode(code: Int): GlassesKey? = when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> SWIPE_FORWARD
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> SWIPE_BACK
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> TAP
            else -> null
        }
    }
}

/** [onKey] returns whether the HUD used the key; unused keys stay with the system (e.g. double-click = home). */
class KeyReceiver(private val onKey: (GlassesKey) -> Boolean) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val type = KeyType.fromAction(intent?.action) ?: return
        val key = GlassesKey.fromKeyType(type) ?: return
        // Only swallow keys we used, and only for ordered broadcasts (abortBroadcast throws otherwise).
        if (onKey(key) && isOrderedBroadcast) abortBroadcast()
    }
}
