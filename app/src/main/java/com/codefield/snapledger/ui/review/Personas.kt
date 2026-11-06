package com.codefield.snapledger.ui.review

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.codefield.snapledger.R
import com.codefield.snapledger.model.Persona

/** 「13 笔 · 3 天有消费」：两个数各自按单复数写（英文是 1 entry / 2 entries）。 */
@Composable
internal fun countAndDays(entries: Int, days: Int): String =
    pluralStringResource(R.plurals.review_entries, entries, entries) + " · " + pluralStringResource(R.plurals.review_days, days, days)

/** 消费性格的表情、名字和一句话说明。 */
internal fun personaEmoji(persona: Persona): String = when (persona) {
    Persona.COFFEE -> "☕"
    Persona.FOODIE -> "🍜"
    Persona.COMMUTER -> "🚌"
    Persona.SHOPPER -> "🛍️"
    Persona.HOME_COOK -> "🛒"
    Persona.FUN -> "🎬"
    Persona.TRAVELER -> "✈️"
    Persona.LEARNER -> "📚"
    Persona.HEALTHY -> "💪"
    Persona.HOMEBODY -> "🏠"
    Persona.GLOBAL -> "🌏"
    Persona.BALANCED -> "⚖️"
}

@StringRes
internal fun personaName(persona: Persona): Int = when (persona) {
    Persona.COFFEE -> R.string.persona_coffee
    Persona.FOODIE -> R.string.persona_foodie
    Persona.COMMUTER -> R.string.persona_commuter
    Persona.SHOPPER -> R.string.persona_shopper
    Persona.HOME_COOK -> R.string.persona_home_cook
    Persona.FUN -> R.string.persona_fun
    Persona.TRAVELER -> R.string.persona_traveler
    Persona.LEARNER -> R.string.persona_learner
    Persona.HEALTHY -> R.string.persona_healthy
    Persona.HOMEBODY -> R.string.persona_homebody
    Persona.GLOBAL -> R.string.persona_global
    Persona.BALANCED -> R.string.persona_balanced
}

@StringRes
internal fun personaText(persona: Persona): Int = when (persona) {
    Persona.COFFEE -> R.string.persona_coffee_text
    Persona.FOODIE -> R.string.persona_foodie_text
    Persona.COMMUTER -> R.string.persona_commuter_text
    Persona.SHOPPER -> R.string.persona_shopper_text
    Persona.HOME_COOK -> R.string.persona_home_cook_text
    Persona.FUN -> R.string.persona_fun_text
    Persona.TRAVELER -> R.string.persona_traveler_text
    Persona.LEARNER -> R.string.persona_learner_text
    Persona.HEALTHY -> R.string.persona_healthy_text
    Persona.HOMEBODY -> R.string.persona_homebody_text
    Persona.GLOBAL -> R.string.persona_global_text
    Persona.BALANCED -> R.string.persona_balanced_text
}
