package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.ui.I18n
import com.sandevsystems.omarchyremote.ui.I18n.Choice
import com.sandevsystems.omarchyremote.ui.I18n.Lang
import com.sandevsystems.omarchyremote.ui.tr
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class I18nTest {
    @After
    fun reset() = I18n.set(Choice.SYSTEM, Locale.forLanguageTag("pt-BR"))

    @Test
    fun theSystemLanguageIsTheDefault() {
        assertEquals(Lang.PT, I18n.resolve(Choice.SYSTEM, Locale.forLanguageTag("pt-BR")))
        assertEquals(Lang.PT, I18n.resolve(Choice.SYSTEM, Locale.forLanguageTag("pt-PT")))
        assertEquals(Lang.EN, I18n.resolve(Choice.SYSTEM, Locale.forLanguageTag("en-US")))
        assertEquals(Lang.EN, I18n.resolve(Choice.SYSTEM, Locale.forLanguageTag("de-DE")))  // anything else: English
    }

    @Test
    fun aChosenLanguageWinsOverTheSystem() {
        assertEquals(Lang.EN, I18n.resolve(Choice.EN, Locale.forLanguageTag("pt-BR")))
        assertEquals(Lang.PT, I18n.resolve(Choice.PT, Locale.forLanguageTag("en-US")))
    }

    @Test
    fun textFollowsTheLanguageInEffect() {
        I18n.set(Choice.EN, Locale.forLanguageTag("pt-BR"))
        assertEquals("Connected", tr("Conectado", "Connected"))
        I18n.set(Choice.SYSTEM, Locale.forLanguageTag("pt-BR"))
        assertEquals("Conectado", tr("Conectado", "Connected"))
    }

    @Test
    fun theChoiceIsSavedByName() {
        assertEquals(Choice.EN, Choice.from("EN"))
        assertEquals(Choice.SYSTEM, Choice.from(null))
        assertEquals(Choice.SYSTEM, Choice.from("klingon"))
    }
}
