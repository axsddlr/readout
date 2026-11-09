package dev.readout.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.readout.app.data.CommandResult
import dev.readout.app.data.PrinterConfig
import dev.readout.app.data.PrinterController
import dev.readout.app.data.PrinterStore
import dev.readout.app.data.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReadoutViewModel(application: Application) : AndroidViewModel(application) {

    private val store = PrinterStore(application)
    val controller = PrinterController(application)

    val state = controller.state

    private val _theme = MutableStateFlow(store.themeMode())
    val theme: StateFlow<ThemePreference> = _theme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(store.dynamicColor())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _notifications = MutableStateFlow(store.notificationsEnabled())
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    fun setNotifications(enabled: Boolean) {
        store.setNotificationsEnabled(enabled)
        _notifications.value = enabled
    }

    fun setTheme(preference: ThemePreference) {
        store.setThemeMode(preference)
        _theme.value = preference
    }

    fun setDynamicColor(enabled: Boolean) {
        store.setDynamicColor(enabled)
        _dynamicColor.value = enabled
    }

    suspend fun connect(config: PrinterConfig): CommandResult = controller.connect(config)

    fun onForeground() = controller.start()

    fun onBackground() = controller.stop()

    override fun onCleared() {
        controller.shutdown()
        super.onCleared()
    }
}
