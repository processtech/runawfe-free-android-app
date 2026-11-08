package ru.runa.wfe.data

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.runa.wfe.data.PreferencesManager.Companion.POLLING_INTERVAL
import ru.runa.wfe.data.PreferencesManager.Companion.SHOW_URL
import ru.runa.wfe.data.PreferencesManager.Companion.WEBVIEW_URL
import ru.runa.wfe.ui.notification.DurationPreference

class PreferencesViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val wfUrl: StateFlow<String?> = preferencesManager.getValueFlow(
        WEBVIEW_URL,
        ""
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val showUrl: StateFlow<Boolean?> = preferencesManager.getValueFlow(
        SHOW_URL,
        false
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val pollingInterval: StateFlow<Long?> = preferencesManager.getValueFlow(
        POLLING_INTERVAL,
        DurationPreference.DEFAULT
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    fun <T> updatePreference(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            preferencesManager.setKey(key, value)
        }
    }

    class Factory(private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PreferencesViewModel::class.java)) {
                return PreferencesViewModel(preferencesManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

}