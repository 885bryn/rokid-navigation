package com.rokidnav.phone.settings

import android.content.Context
import com.rokidnav.protocol.Units
import kotlinx.coroutines.flow.*

/**
 * @property alertVoice speak each new police/hazard report once, when it comes within [alertVoiceMeters].
 * @property alertVoiceCameras also speak speed cameras (fixed cameras are frequent, so this is separate).
 * @property alertVoiceMeters how far ahead along the route a report is first announced.
 * @property alertToleranceMeters how far from the route line a report may be placed and still count:
 *   user reports are often pinned off the road or on the wrong carriageway.
 */
data class UserSettings(val units:Units=Units.METRIC,val voiceMuted:Boolean=false,
    val zoom:Int=1,val brightness:Int=35,val police:Boolean=true,val cameras:Boolean=true,
    val alertVoice:Boolean=true,val alertVoiceCameras:Boolean=true,val alertVoiceMeters:Int=1000,
    val alertToleranceMeters:Int=150) {
    companion object {
        val VOICE_DISTANCES=listOf(500,1000,2000)
        val TOLERANCES=listOf(60,150,300)
        fun toleranceLabel(meters:Int)=when(meters){60->"Tight";300->"Loose";else->"Normal"}
    }
}

/** App-private local preferences, written only when a setting changes. */
class SettingsRepository(context:Context) {
    private val prefs=context.getSharedPreferences("navigation_settings",Context.MODE_PRIVATE).also {prefs->
        // One-time switch to metric (user request, 2026-10-18); choosing imperial later in Settings sticks.
        if(!prefs.getBoolean("metricMigrated",false))prefs.edit().putString("units",Units.METRIC.name).putBoolean("metricMigrated",true).apply()
    }
    private val mutable=MutableStateFlow(normalize(UserSettings(
        runCatching {Units.valueOf(prefs.getString("units","METRIC")!!)}.getOrDefault(Units.METRIC),
        prefs.getBoolean("muted",false),prefs.getInt("zoom",1),prefs.getInt("brightness",35),
        prefs.getBoolean("police",true),prefs.getBoolean("cameras",true),
        prefs.getBoolean("alertVoice",true),prefs.getBoolean("alertVoiceCameras",true),
        prefs.getInt("alertVoiceMeters",1000),prefs.getInt("alertToleranceMeters",150))))
    val state=mutable.asStateFlow()
    fun update(transform:(UserSettings)->UserSettings) {
        val next=normalize(transform(mutable.value))
        if(next==mutable.value)return
        prefs.edit().putString("units",next.units.name).putBoolean("muted",next.voiceMuted)
            .putInt("zoom",next.zoom).putInt("brightness",next.brightness)
            .putBoolean("police",next.police).putBoolean("cameras",next.cameras)
            .putBoolean("alertVoice",next.alertVoice).putBoolean("alertVoiceCameras",next.alertVoiceCameras)
            .putInt("alertVoiceMeters",next.alertVoiceMeters).putInt("alertToleranceMeters",next.alertToleranceMeters).apply()
        mutable.value=next
    }
    private fun normalize(s:UserSettings)=s.copy(zoom=s.zoom.coerceIn(0,2),brightness=s.brightness.coerceIn(5,100),
        alertVoiceMeters=s.alertVoiceMeters.takeIf {it in UserSettings.VOICE_DISTANCES} ?: 1000,
        alertToleranceMeters=s.alertToleranceMeters.takeIf {it in UserSettings.TOLERANCES} ?: 150)
}
