package com.medbot.app.presentation.persona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PersonaUiEvent {
    data class SaveConfig(val config: PersonaConfig) : PersonaUiEvent
}

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    val personaConfig: StateFlow<PersonaConfig> = userPreferencesRepository.personaConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonaConfig())

    fun onEvent(event: PersonaUiEvent) {
        when (event) {
            is PersonaUiEvent.SaveConfig -> updateConfig(event.config)
        }
    }

    fun updateConfig(config: PersonaConfig) {
        viewModelScope.launch { userPreferencesRepository.updatePersonaConfig(config) }
    }
}
