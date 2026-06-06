package com.wasimaster.wmkeyboard.core.transliteration

import org.junit.Assert.assertEquals
import org.junit.Test

class AvroPhoneticTest {

    private fun t(input: String) = AvroPhonetic.transliterate(input)

    @Test fun simpleWords() {
        assertEquals("আমি", t("ami"))
        assertEquals("তুমি", t("tumi"))
        assertEquals("ভালো", t("valo"))
        assertEquals("ভালো", t("bhalo"))
        assertEquals("আছি", t("achi"))
        assertEquals("আসি", t("asi"))
    }

    @Test fun sentence() {
        assertEquals("আমি ভালো আছি", t("ami valo achi"))
    }

    @Test fun inherentVowel() {
        // Medial "o" is silent, word-final "o" takes o-kar, initial "o" is অ.
        assertEquals("করি", t("kori"))
        assertEquals("মন", t("mon"))
        assertEquals("কেমন", t("kemon"))
        assertEquals("অনেক", t("onek"))
    }

    @Test fun aspiratedConsonants() {
        assertEquals("খাই", t("khai"))
        assertEquals("ঘর", t("ghor"))
        assertEquals("ছবি", t("chobi"))
        assertEquals("ধন", t("dhon"))
    }

    @Test fun conjuncts() {
        // Consecutive consonants form conjuncts with hasant.
        assertEquals("ক্ক", t("kk"))
        assertEquals("স্কুল", t("skul"))
        assertEquals("বন্ধু", t("bondhu"))
    }

    @Test fun fixedConjunctKkh() {
        // "kkh" is the conventional Avro key for ক্ষ.
        assertEquals("ক্ষ", t("kkh"))
        assertEquals("ক্ষমা", t("kkhoma"))
        assertEquals("লক্ষ", t("lokkh"))
    }

    @Test fun clusterBreaksOnlyWithInherentVowel() {
        // Documented invariant: an explicit medial "o" breaks the cluster.
        assertEquals("কলকাতা", t("kolokata"))
    }

    @Test fun vowelSigns() {
        assertEquals("বই", t("boi"))
        assertEquals("নদী", t("nodI"))
        assertEquals("দুই", t("dui"))
    }

    @Test fun retroflex() {
        assertEquals("টাকা", t("Taka"))
        assertEquals("ডাল", t("Dal"))
        assertEquals("বড়", t("boR"))
    }

    @Test fun digitsAndPunctuation() {
        assertEquals("১২৩", t("123"))
        assertEquals("।", t("."))
        assertEquals(".", t(".."))
        assertEquals("৳", t("$"))
    }

    @Test fun anusvaraAndChandrabindu() {
        assertEquals("রং", t("rong"))
        assertEquals("চাঁদ", t("ca^d"))
        assertEquals("দুঃখ", t("du:kh"))
    }

    @Test fun mixedTextPassesThrough() {
        assertEquals("ওকে!", t("OkE!"))
    }
}
