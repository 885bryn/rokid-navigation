package com.rokidnav.phone.settings

import android.content.Context
import com.rokidnav.protocol.Units
import kotlinx.coroutines.flow.*

data class UserSettings(val units:Units=Units.IMPERIAL,val voiceMuted:Boolean=false,
    val zoom:Int=1,val brightness:Int=35,val police:Boolean=true,val cameras:Boolean=true)

/** App-private local preferences, written only when a setting changes. */
class SettingsRepository(context:Context) {
    private val prefs=context.getSharedPreferences("navigation_settings",Context.MODE_PRIVATE)
    private val mutable=MutableStateFlow(UserSettings(
        runCatching {Units.valueOf(prefs.getString("units","IMPERIAL")!!)}.getOrDefault(Units.IMPERIAL),
        prefs.getBoolean("muted",false),prefs.getInt("zoom",1).coerceIn(0,2),prefs.getInt("brightness",35).coerceIn(5,100),
        prefs.getBoolean("police",true),prefs.getBoolean("cameras",true)))
    val state=mutable.asStateFlow()
    fun update(transform:(UserSettings)->UserSettings) {
        val next=transform(mutable.value).let {it.copy(zoom=it.zoom.coerceIn(0,2),brightness=it.brightness.coerceIn(5,100))}
        if(next==mutable.value)return
        prefs.edit().putString("units",next.units.name).putBoolean("muted",next.voiceMuted)
            .putInt("zoom",next.zoom).putInt("brightness",next.brightness)
            .putBoolean("police",next.police).putBoolean("cameras",next.cameras).apply()
        mutable.value=next
    }
}
