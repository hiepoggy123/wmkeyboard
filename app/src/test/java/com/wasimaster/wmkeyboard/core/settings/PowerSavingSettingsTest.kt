package com.wasimaster.wmkeyboard.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSavingSettingsTest {

    private val full = DevicePowerState(batteryPercent = 90, charging = false, systemSaver = false)
    private val low = DevicePowerState(batteryPercent = 12, charging = false, systemSaver = false)

    @Test
    fun `manual wins over everything`() {
        val config = PowerSavingSettings(manual = true, trigger = PowerSavingTrigger.OFF)
        assertTrue(config.appliesTo(full))
        // Even on the charger: someone who flipped it by hand meant it.
        assertTrue(config.appliesTo(full.copy(charging = true)))
    }

    @Test
    fun `the default follows the system battery saver only`() {
        val config = PowerSavingSettings()
        assertEquals(PowerSavingTrigger.SYSTEM_SAVER, config.trigger)
        assertFalse(config.appliesTo(full))
        assertFalse("a low battery alone must not trigger the default", config.appliesTo(low))
        assertTrue(config.appliesTo(full.copy(systemSaver = true)))
    }

    @Test
    fun `the low-battery trigger uses the user's percentage`() {
        val config = PowerSavingSettings(trigger = PowerSavingTrigger.LOW_BATTERY, batteryPercent = 20)
        assertTrue(config.appliesTo(low))
        assertTrue("the threshold itself counts as low", config.appliesTo(low.copy(batteryPercent = 20)))
        assertFalse(config.appliesTo(low.copy(batteryPercent = 21)))
        assertFalse("the system saver is a different trigger", config.appliesTo(full.copy(systemSaver = true)))
    }

    @Test
    fun `an unread battery level is never low`() {
        val config = PowerSavingSettings(trigger = PowerSavingTrigger.EITHER)
        assertFalse(config.appliesTo(DevicePowerState()))
    }

    @Test
    fun `charging stands the automatic triggers down`() {
        val config = PowerSavingSettings(trigger = PowerSavingTrigger.EITHER)
        assertTrue(config.appliesTo(low))
        assertFalse(config.appliesTo(low.copy(charging = true)))
        assertTrue(
            "opting out of the charging exception keeps it on",
            config.copy(offWhileCharging = false).appliesTo(low.copy(charging = true)),
        )
    }

    @Test
    fun `the defaults drop something`() {
        assertTrue(PowerSavingSettings().dropsAnything)
        assertFalse(
            PowerSavingSettings(
                dropHaptics = false, dropKeySound = false, dropAnimations = false,
                dropGlideTrail = false, dropKeyPopup = false, dropGestureTyping = false,
                dropEmojiPrediction = false, dropSmartChips = false,
                dropBackgroundNetwork = false, dropScreenshotWatch = false,
                dropOnDeviceModels = false,
            ).dropsAnything,
        )
    }

    @Test
    fun `the view switches off exactly what is asked for`() {
        val settings = KeyboardSettings(powerSaving = PowerSavingSettings())
        val saving = settings.underPowerSaving()
        assertFalse(saving.hapticFeedback)
        assertTrue(saving.reduceMotion)
        assertEquals(0f, saving.gesture.trailOpacity, 0f)
        assertFalse(saving.emojiPrediction)
        assertFalse(saving.smartSuggestions)
        assertFalse(saving.clipboard.linkPreviews)
        assertFalse(saving.clipboard.userScreenshots)
        assertEquals("system", saving.whisper.engine)
        // Off by default, so these survive.
        assertTrue(saving.gestureTyping)
        assertTrue(saving.popup.enabled)
    }

    @Test
    fun `a drop that is switched off leaves its feature alone`() {
        val settings = KeyboardSettings(
            powerSaving = PowerSavingSettings(dropHaptics = false, dropAnimations = false),
        )
        val saving = settings.underPowerSaving()
        assertTrue(saving.hapticFeedback)
        assertFalse(saving.reduceMotion)
    }

    @Test
    fun `typing statistics stop only when the opt-in drop asks`() {
        // Off by default: saving power must not silently stop the counters.
        assertTrue(KeyboardSettings().underPowerSaving().typingStatsEnabled)
        val opted = KeyboardSettings(powerSaving = PowerSavingSettings(dropTypingStats = true))
        assertFalse(opted.underPowerSaving().typingStatsEnabled)
        assertTrue("the stored setting is untouched", opted.typingStatsEnabled)
    }

    @Test
    fun `reduced motion the user chose survives power saving ending`() {
        // The view ORs into reduceMotion rather than setting it, so an
        // accessibility choice is never quietly turned off by the transform.
        val settings = KeyboardSettings(reduceMotion = true, powerSaving = PowerSavingSettings())
        assertTrue(settings.underPowerSaving().reduceMotion)
        assertTrue(settings.reduceMotion)
    }

    @Test
    fun `handwriting falls back only when it is the swipe action`() {
        val writing = KeyboardSettings(letterSwipeAction = LetterSwipeAction.HANDWRITE)
        assertEquals(LetterSwipeAction.TYPE_WORDS, writing.underPowerSaving().letterSwipeAction)
        val typing = KeyboardSettings(letterSwipeAction = LetterSwipeAction.TYPE_WORDS)
        assertEquals(LetterSwipeAction.TYPE_WORDS, typing.underPowerSaving().letterSwipeAction)
    }

    @Test
    fun `the transform never touches the stored configuration`() {
        val settings = KeyboardSettings()
        val saving = settings.underPowerSaving()
        assertEquals(settings.powerSaving, saving.powerSaving)
        assertTrue("the original is untouched", settings.hapticFeedback)
    }
}
