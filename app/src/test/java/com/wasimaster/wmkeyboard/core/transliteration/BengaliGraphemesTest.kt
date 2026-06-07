package com.wasimaster.wmkeyboard.core.transliteration

import org.junit.Assert.assertEquals
import org.junit.Test

class BengaliGraphemesTest {

    private fun len(text: String) = BengaliGraphemes.clusterDeleteLength(text)

    @Test
    fun `empty text deletes nothing`() = assertEquals(0, len(""))

    @Test
    fun `plain latin deletes one`() = assertEquals(1, len("hello"))

    @Test
    fun `surrogate pair deletes two`() = assertEquals(2, len("hi😀"))

    @Test
    fun `single bengali letter deletes one`() = assertEquals(1, len("আমি ক"))

    @Test
    fun `consonant with vowel sign deletes both`() {
        // কি = ক + ি
        assertEquals(2, len("আমার কি"))
    }

    @Test
    fun `simple conjunct deletes as unit`() {
        // ক্ষ = ক ্ ষ
        assertEquals(3, len("ক্ষ"))
    }

    @Test
    fun `conjunct with vowel sign deletes as unit`() {
        // জ্ঞা = জ ্ ঞ া
        assertEquals(4, len("জ্ঞা"))
    }

    @Test
    fun `triple conjunct deletes as unit`() {
        // স্ত্রী = স ্ ত ্ র ী
        assertEquals(6, len("স্ত্রী"))
    }

    @Test
    fun `trailing hasant deletes only itself`() {
        // ক্ = ক ্ → removing the explicit hasant undoes just it.
        assertEquals(1, len("ক্"))
    }

    @Test
    fun `anusvara clings to its cluster`() {
        // বাং = ব া ং → cluster is ব with া and ং
        assertEquals(3, len("বাং"))
    }

    @Test
    fun `independent vowel deletes alone`() = assertEquals(1, len("কথা আ"))

    @Test
    fun `ya-phala cluster deletes as unit`() {
        // ব্য = ব ্ য
        assertEquals(3, len("ব্য"))
    }

    @Test
    fun `preceding text is untouched`() {
        // Only the final cluster counts, not the whole word.
        // আছি = আ ছ ি → final cluster ছি = 2
        assertEquals(2, len("আছি"))
    }

    @Test fun vowelKeyForms() {
        val kar = BengaliGraphemes.VowelKeyForm.KAR
        val glide = BengaliGraphemes.VowelKeyForm.GLIDE
        val indep = BengaliGraphemes.VowelKeyForm.INDEPENDENT

        // Form selection from the preceding character.
        org.junit.Assert.assertEquals(indep, BengaliGraphemes.vowelFormAfter(null))
        org.junit.Assert.assertEquals(indep, BengaliGraphemes.vowelFormAfter(' '))
        org.junit.Assert.assertEquals(kar, BengaliGraphemes.vowelFormAfter('ক'))
        org.junit.Assert.assertEquals(kar, BengaliGraphemes.vowelFormAfter('\u09DF')) // য়
        org.junit.Assert.assertEquals(glide, BengaliGraphemes.vowelFormAfter('া'))
        org.junit.Assert.assertEquals(glide, BengaliGraphemes.vowelFormAfter('আ'))
        org.junit.Assert.assertEquals(glide, BengaliGraphemes.vowelFormAfter('ে'))
        org.junit.Assert.assertEquals(glide, BengaliGraphemes.vowelFormAfter('ঁ'))

        // Output per form: কা + আ-key must give the glide য়া (কায়া, never কাআ).
        org.junit.Assert.assertEquals("া", BengaliGraphemes.vowelKeyText('া', kar))
        org.junit.Assert.assertEquals("\u09DFা", BengaliGraphemes.vowelKeyText('া', glide))
        org.junit.Assert.assertEquals("আ", BengaliGraphemes.vowelKeyText('া', indep))
        org.junit.Assert.assertEquals("\u09DFে", BengaliGraphemes.vowelKeyText('ে', glide))
        // Vowels without a glide form stay independent after a vowel (খাই, বউ).
        org.junit.Assert.assertEquals("ই", BengaliGraphemes.vowelKeyText('ি', glide))
        org.junit.Assert.assertEquals("উ", BengaliGraphemes.vowelKeyText('ু', glide))
        // Non-vowel keys are untouched.
        org.junit.Assert.assertNull(BengaliGraphemes.vowelKeyText('ক', glide))
    }

    @Test fun karHelpers() {
        // Kar → independent vowel used by the fixed layouts' contextual keys.
        org.junit.Assert.assertEquals("আ", BengaliGraphemes.KAR_TO_VOWEL['া'])
        org.junit.Assert.assertEquals("ই", BengaliGraphemes.KAR_TO_VOWEL['ি'])
        org.junit.Assert.assertEquals("ও", BengaliGraphemes.KAR_TO_VOWEL['ো'])
        // Kar attaches to consonants, nukta letters, hasant — not vowels/space.
        org.junit.Assert.assertTrue(BengaliGraphemes.karAttachesTo('ক'))
        org.junit.Assert.assertTrue(BengaliGraphemes.karAttachesTo('\u09DF'))
        org.junit.Assert.assertTrue(BengaliGraphemes.karAttachesTo('্'))
        org.junit.Assert.assertFalse(BengaliGraphemes.karAttachesTo('আ'))
        org.junit.Assert.assertFalse(BengaliGraphemes.karAttachesTo(' '))
        org.junit.Assert.assertFalse(BengaliGraphemes.karAttachesTo('া'))
    }
}
