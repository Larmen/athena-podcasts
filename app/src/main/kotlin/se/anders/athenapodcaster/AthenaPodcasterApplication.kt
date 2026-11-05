package se.anders.athenapodcaster

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.anders.athenapodcaster.core.AppContainer
import se.anders.athenapodcaster.data.refresh.RefreshScheduler

class AthenaPodcasterApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // FR-030: keep the periodic refresh in line with the setting, including after an update or reboot.
        scope.launch {
            container.settings.settings.map { it.refreshIntervalHours }.distinctUntilChanged()
                .collect { RefreshScheduler.apply(this@AthenaPodcasterApplication, it) }
        }
    }
}
