package com.wasimaster.wmkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftCaseTest {

    @Test fun offLeavesTheSuggestionAsTheEngineProducedIt() {
        assertEquals("hello", displayCaseForShift("hello", ShiftState.OFF))
        assertEquals("Hello", displayCaseForShift("Hello", ShiftState.OFF))
    }

    @Test fun onTitleCasesAndCapsLockUppercases() {
        assertEquals("Hello", displayCaseForShift("hello", ShiftState.ON))
        assertEquals("HELLO", displayCaseForShift("hello", ShiftState.CAPS_LOCK))
    }

    @Test fun emailsAndEmptyStringsAreLeftAlone() {
        assertEquals("john.doe@gmail.com", displayCaseForShift("john.doe@gmail.com", ShiftState.CAPS_LOCK))
        assertEquals("", displayCaseForShift("", ShiftState.ON))
    }
}
