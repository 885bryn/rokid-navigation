package com.rokidnav.phone

import android.app.Application
import com.rokidnav.phone.alerts.CompositeIncidentProvider
import com.rokidnav.phone.alerts.MockIncidentProvider
import com.rokidnav.phone.glasses.CxrGlassesLink
import com.rokidnav.phone.glasses.GlassesLink
import com.rokidnav.phone.nav.GoogleNavigationEngine
import com.rokidnav.phone.nav.NavigationEngine
import com.rokidnav.phone.session.NavSessionCoordinator
import com.rokidnav.phone.traffic.RoutesApiClient
import com.rokidnav.phone.traffic.TrafficMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Manual DI. Swap implementations here (e.g. a fake GlassesLink for UI work without hardware). */
class RokidNavApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var glassesLink: GlassesLink
        private set
    lateinit var navEngine: NavigationEngine
        private set
    lateinit var coordinator: NavSessionCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        glassesLink = CxrGlassesLink(this)
        navEngine = GoogleNavigationEngine(this)
        val routesApi = RoutesApiClient(BuildConfig.MAPS_API_KEY)
        coordinator = NavSessionCoordinator(
            scope = appScope,
            link = glassesLink,
            engine = navEngine,
            traffic = TrafficMonitor(routesApi, navEngine),
            incidents = CompositeIncidentProvider(
                listOf(
                    // TODO(M7): replace with real providers; mock is debug-only.
                    MockIncidentProvider(enabled = BuildConfig.DEBUG),
                ),
            ),
        )
        coordinator.start()
    }

    companion object {
        lateinit var instance: RokidNavApp
            private set
    }
}
