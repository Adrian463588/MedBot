package com.medbot.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.medbot.app.R
import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.DetailDepth
import com.medbot.app.domain.model.DoctorAgent
import com.medbot.app.domain.model.PersonaTone

/**
 * Maps domain enum values to localized UI labels without leaking Android resources
 * into the domain layer.
 */
@Composable
fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.INDONESIAN -> stringResource(R.string.language_indonesian)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
}

/** Returns the localized label for a persona tone. */
@Composable
fun personaToneLabel(tone: PersonaTone): String = when (tone) {
    PersonaTone.EMPATHETIC -> stringResource(R.string.persona_tone_empathetic)
    PersonaTone.CLINICAL -> stringResource(R.string.persona_tone_clinical)
    PersonaTone.CONCISE -> stringResource(R.string.persona_tone_concise)
    PersonaTone.EDUCATIONAL -> stringResource(R.string.persona_tone_educational)
}

/** Returns the localized label for response depth. */
@Composable
fun detailDepthLabel(depth: DetailDepth): String = when (depth) {
    DetailDepth.SIMPLE -> stringResource(R.string.persona_depth_simple)
    DetailDepth.STANDARD -> stringResource(R.string.persona_depth_standard)
    DetailDepth.DEEP -> stringResource(R.string.persona_depth_deep)
}

/** Returns the agent name stored for the selected response language. */
fun DoctorAgent.displayName(language: AppLanguage): String = when (language) {
    AppLanguage.INDONESIAN -> displayNameId
    AppLanguage.ENGLISH -> displayNameEn
}

/** Returns the agent specialty stored for the selected response language. */
fun DoctorAgent.specialty(language: AppLanguage): String = when (language) {
    AppLanguage.INDONESIAN -> specialtyId
    AppLanguage.ENGLISH -> specialtyEn
}
