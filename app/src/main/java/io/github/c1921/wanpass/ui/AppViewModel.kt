package io.github.c1921.wanpass.ui

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.session.VaultSessionManager
import io.github.c1921.wanpass.session.VaultSessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

data class AppUiState(
    val onboardingComplete: Boolean = false,
    val biometricEnabled: Boolean = true,
    val deviceSecure: Boolean = false,
    val sessionState: VaultSessionState = VaultSessionState.LOCKED,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: VaultSettingsRepository,
    private val sessionManager: VaultSessionManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val deviceSecureFlow = MutableStateFlow(isDeviceSecure(context))

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.settingsFlow,
        sessionManager.sessionState,
        deviceSecureFlow,
    ) { settings, sessionState, deviceSecure ->
        AppUiState(
            onboardingComplete = settings.onboardingComplete,
            biometricEnabled = settings.biometricEnabled,
            deviceSecure = deviceSecure,
            sessionState = sessionState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    fun onAppBackgrounded() {
        viewModelScope.launch { sessionManager.onAppBackgrounded() }
    }

    fun onAppForegrounded() {
        deviceSecureFlow.value = isDeviceSecure(context)
        viewModelScope.launch { sessionManager.onAppForegrounded() }
    }
}
