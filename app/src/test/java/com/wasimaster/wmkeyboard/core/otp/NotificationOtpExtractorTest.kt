package com.wasimaster.wmkeyboard.core.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Code extraction from notification text. The stakes differ from the clipboard
 * tests: a notification chip appears unbidden, so the misses that matter most
 * here are the *impostors* — delivery slots, order numbers, balances — that a
 * looser extractor would happily print above the keys.
 */
class NotificationOtpExtractorTest {

    // ---- anchored shapes ----

    @Test fun codeBeforeItsKeyword() {
        assertEquals("482913", NotificationOtpExtractor.extract("482913 is your Amazon OTP"))
        assertEquals("2931", NotificationOtpExtractor.extract("2931 is the verification code for your account"))
    }

    @Test fun codeAfterItsKeyword() {
        assertEquals("482913", NotificationOtpExtractor.extract("Your OTP is 482913. Do not share it."))
        assertEquals("482913", NotificationOtpExtractor.extract("Verification code: 482913"))
        assertEquals("4829", NotificationOtpExtractor.extract("PIN - 4829"))
    }

    @Test fun codeAfterUseOrEnter() {
        assertEquals("482913", NotificationOtpExtractor.extract("Please use 482913 to sign in to your account"))
        assertEquals("4829", NotificationOtpExtractor.extract("Enter 4829 to verify your number"))
    }

    @Test fun pickTheCodeNotTheAccountNumber() {
        // Two numbers, one code: the "… is <code>" anchor must win over the
        // longer number sitting right beside the keyword.
        assertEquals(
            "4279",
            NotificationOtpExtractor.extract("OTP for account 2310990533 is 4279"),
        )
    }

    @Test fun brandPrefixIsNotPartOfTheCode() {
        assertEquals("482913", NotificationOtpExtractor.extract("G-482913 is your Google verification code"))
    }

    @Test fun formattedCodesLoseTheirSeparators() {
        assertEquals("123456", NotificationOtpExtractor.extract("Your code is 123 456"))
        assertEquals("123456", NotificationOtpExtractor.extract("Your code is 123-456"))
    }

    @Test fun titleAnchorsTheBodysCode() {
        // The listener joins a notification's surfaces with spaces, so a
        // keyword in the title must anchor a code in the body.
        assertEquals("482913", NotificationOtpExtractor.extract("Verification code 482913"))
    }

    // ---- the recall net ----

    @Test fun proximityPassCatchesUnanchoredShapes() {
        assertEquals("A1B2C3", NotificationOtpExtractor.extract("Login code: A1B2C3"))
        assertEquals("482913", NotificationOtpExtractor.extract("482913 — your one-time password"))
    }

    // ---- impostors ----

    @Test fun plainSentencesHaveNoCode() {
        assertNull(NotificationOtpExtractor.extract("Your parcel arrives tomorrow between 9 and 11"))
        assertNull(NotificationOtpExtractor.extract("Anna sent you a photo"))
    }

    @Test fun numbersWithoutACodeWordAreNotCodes() {
        assertNull(NotificationOtpExtractor.extract("Your order 482913 has shipped"))
        assertNull(NotificationOtpExtractor.extract("Flight 4829 departs at 18:40"))
    }

    @Test fun deniedKeywordsStayDenied() {
        assertNull(NotificationOtpExtractor.extract("Deliver to zip code 90210"))
        assertNull(NotificationOtpExtractor.extract("Scan the QR code 483920 at the gate"))
    }

    @Test fun yearsAreNotCodes() {
        assertNull(NotificationOtpExtractor.extract("Your discount code is valid until 2027"))
    }

    @Test fun tooLongIsAReferenceNotACode() {
        assertNull(NotificationOtpExtractor.extract("Use 123456789012 as your case reference code"))
    }

    @Test fun blankIsNull() {
        assertNull(NotificationOtpExtractor.extract("   "))
    }
}
