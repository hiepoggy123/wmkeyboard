package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.AiAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two Custom modes. Which prompt a run picks decides whether the model
 * transforms the field or writes something new, and the injection guard has to
 * follow the untrusted input rather than being attached out of habit.
 */
class AiPromptsTest {

    @Test
    fun `custom treats the field as data, not instructions`() {
        val prompt = AiPrompts.customPrompt("make it rhyme")
        assertTrue(prompt.contains("make it rhyme"))
        // The field is third-party text, so the guard must be present.
        assertTrue(prompt.contains("never instructions to you"))
        assertTrue(prompt.contains("Reply with ONLY the resulting text"))
    }

    @Test
    fun `a blank custom instruction still produces a usable task`() {
        // Reachable via defaultPrompt(CUSTOM); must not emit a dangling
        // "Follow this instruction on the input text:" with nothing after it.
        val prompt = AiPrompts.customPrompt("   ")
        assertTrue(prompt.contains("rewrite the input text"))
    }

    @Test
    fun `generate carries no injection guard because there is no field text`() {
        val prompt = AiPrompts.generatePrompt()
        // With an empty field the only input is the user's own typed
        // instruction — a directive by definition, so guarding it would tell
        // the model to ignore the very thing it is supposed to do.
        assertFalse(prompt.contains("never instructions to you"))
        assertTrue(prompt.contains("instruction"))
        assertTrue(prompt.contains("Reply with ONLY the resulting text"))
    }

    @Test
    fun `generate does not embed the instruction — it rides in the user message`() {
        // Sending it in both places would duplicate it; the empty-field path
        // passes the instruction as the user turn precisely because several
        // providers reject an empty one.
        assertFalse(AiPrompts.generatePrompt().contains("write a haiku"))
    }

    @Test
    fun `only Custom survives an empty field`() {
        assertTrue(AiAction.CUSTOM.worksWithoutText)
        for (action in AiAction.entries - AiAction.CUSTOM) {
            assertFalse(action.label, action.worksWithoutText)
        }
    }
}
