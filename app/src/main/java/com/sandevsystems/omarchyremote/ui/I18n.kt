package com.sandevsystems.omarchyremote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The app's language: Portuguese or English, following the phone unless the person picks one
 * (Ajustes → Idioma). Text is written as pairs next to where it is used — `tr("Conectado", "Connected")` —
 * so a translation is never far from its original. The language is Compose state: switching it
 * redraws every screen at once, no restart.
 */
object I18n {
    enum class Lang { PT, EN }

    enum class Choice(val label: String) {
        SYSTEM("Sistema"), PT("Português"), EN("English");

        companion object {
            fun from(name: String?): Choice = entries.firstOrNull { it.name == name } ?: SYSTEM
        }
    }

    var choice by mutableStateOf(Choice.SYSTEM)
        private set
    var lang by mutableStateOf(resolve(Choice.SYSTEM, Locale.getDefault()))
        private set

    fun set(choice: Choice, system: Locale = Locale.getDefault()) {
        this.choice = choice
        lang = resolve(choice, system)
    }

    /** Portuguese for a phone in any Portuguese; English for everything else. */
    fun resolve(choice: Choice, system: Locale): Lang = when (choice) {
        Choice.PT -> Lang.PT
        Choice.EN -> Lang.EN
        Choice.SYSTEM -> if (system.language == "pt") Lang.PT else Lang.EN
    }
}

/** The text in the language in effect. */
fun tr(pt: String, en: String): String = if (I18n.lang == I18n.Lang.EN) en else pt
