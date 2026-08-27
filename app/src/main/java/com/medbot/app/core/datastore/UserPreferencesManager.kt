package com.medbot.app.core.datastore

import android.content.Context
import android.content.SharedPreferences
import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.DetailDepth
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.model.PersonaTone
import com.medbot.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesManager(context: Context) : UserPreferencesRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("medbot_preferences", Context.MODE_PRIVATE)

    private val _personaConfig = MutableStateFlow(loadPersonaConfig())
    override val personaConfig: Flow<PersonaConfig> = _personaConfig.asStateFlow()

    private val _safModelFolderUri = MutableStateFlow(prefs.getString(KEY_SAF_MODEL_URI, null))
    override val safModelFolderUri: Flow<String?> = _safModelFolderUri.asStateFlow()

    private val _safRagFolderUri = MutableStateFlow(prefs.getString(KEY_SAF_RAG_URI, null))
    override val safRagFolderUri: Flow<String?> = _safRagFolderUri.asStateFlow()

    private val _activeLanguage = MutableStateFlow(
        if (prefs.getString(KEY_LANGUAGE, "id") == "en") AppLanguage.ENGLISH else AppLanguage.INDONESIAN
    )
    override val activeLanguage: Flow<AppLanguage> = _activeLanguage.asStateFlow()

    private val _onlineEvidenceEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_ONLINE_EVIDENCE_ENABLED, false)
    )
    override val onlineEvidenceEnabled: Flow<Boolean> = _onlineEvidenceEnabled.asStateFlow()

    private fun loadPersonaConfig(): PersonaConfig {
        val agentId = prefs.getString(KEY_AGENT_ID, "orchestrator") ?: "orchestrator"
        val toneName = prefs.getString(KEY_TONE, PersonaTone.EMPATHETIC.name) ?: PersonaTone.EMPATHETIC.name
        val depthName = prefs.getString(KEY_DEPTH, DetailDepth.STANDARD.name) ?: DetailDepth.STANDARD.name
        val langCode = prefs.getString(KEY_LANGUAGE, "id") ?: "id"
        val customInstr = prefs.getString(KEY_CUSTOM_INSTR, "") ?: ""
        val patientProfile = prefs.getString(KEY_PATIENT_PROFILE, "") ?: ""

        val tone = try { PersonaTone.valueOf(toneName) } catch (e: Exception) { PersonaTone.EMPATHETIC }
        val depth = try { DetailDepth.valueOf(depthName) } catch (e: Exception) { DetailDepth.STANDARD }
        val lang = if (langCode == "en") AppLanguage.ENGLISH else AppLanguage.INDONESIAN

        return PersonaConfig(
            selectedAgentId = agentId,
            tone = tone,
            depth = depth,
            language = lang,
            customInstructions = customInstr,
            patientProfileSummary = patientProfile
        )
    }

    override suspend fun updatePersonaConfig(config: PersonaConfig) {
        prefs.edit()
            .putString(KEY_AGENT_ID, config.selectedAgentId)
            .putString(KEY_TONE, config.tone.name)
            .putString(KEY_DEPTH, config.depth.name)
            .putString(KEY_LANGUAGE, config.language.code)
            .putString(KEY_CUSTOM_INSTR, config.customInstructions)
            .putString(KEY_PATIENT_PROFILE, config.patientProfileSummary)
            .apply()
        _personaConfig.value = config
    }

    override suspend fun setSafModelFolderUri(uri: String?) {
        prefs.edit().putString(KEY_SAF_MODEL_URI, uri).apply()
        _safModelFolderUri.value = uri
    }

    override suspend fun setSafRagFolderUri(uri: String?) {
        prefs.edit().putString(KEY_SAF_RAG_URI, uri).apply()
        _safRagFolderUri.value = uri
    }

    override suspend fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        _activeLanguage.value = language
        _personaConfig.value = _personaConfig.value.copy(language = language)
    }

    override suspend fun setOnlineEvidenceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_EVIDENCE_ENABLED, enabled).apply()
        _onlineEvidenceEnabled.value = enabled
    }

    companion object {
        private const val KEY_AGENT_ID = "persona_agent_id"
        private const val KEY_TONE = "persona_tone"
        private const val KEY_DEPTH = "persona_depth"
        private const val KEY_LANGUAGE = "persona_language"
        private const val KEY_CUSTOM_INSTR = "persona_custom_instructions"
        private const val KEY_PATIENT_PROFILE = "persona_patient_profile"
        private const val KEY_SAF_MODEL_URI = "saf_model_folder_uri"
        private const val KEY_SAF_RAG_URI = "saf_rag_folder_uri"
        private const val KEY_ONLINE_EVIDENCE_ENABLED = "online_evidence_enabled"
    }
}
