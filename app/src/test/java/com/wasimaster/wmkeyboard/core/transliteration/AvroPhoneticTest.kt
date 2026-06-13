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

    @Test fun rephViaDoubleR() {
        // "rr" is reph: single র conjuncting with the next consonant.
        assertEquals("বর্ন", t("borrno"))
        assertEquals("মর্ম", t("morrmo"))
        assertEquals("ধর্ম", t("dhorrmo"))
        assertEquals("আদর্শ", t("adorrsho"))
        assertEquals("নির্ভর", t("nirrvor"))
        assertEquals("কার্য", t("karryo"))
        // Plain single-r conjuncts still produce the same words.
        assertEquals("ধর্ম", t("dhormo"))
        assertEquals("নির্ভর", t("nirvor"))
        // "rri" keeps ঋ, and "rr" with no consonant after stays literal.
        assertEquals("ঋণ", t("rriN"))
        assertEquals("কৃপা", t("krripa"))
        assertEquals("বর্র", t("borr"))
    }

    @Test fun aAfterKarGlidesWithAntastyaYo() {
        // "a" right after a kar glides with য় instead of independent আ.
        assertEquals("কিয়ামত", t("kiamot"))
        assertEquals("পিয়ানো", t("piano"))
        assertEquals("দেয়া", t("dea"))
        assertEquals("মায়া", t("maa"))
        // After an inherent (silent) vowel the independent আ survives.
        assertEquals("কুরআন", t("kuroan"))
        // Capital "A" stays an explicit আ, and word-initial "a" is untouched.
        assertEquals("কিআমত", t("kiAmot"))
        assertEquals("আমি", t("ami"))
    }

    @Test fun wordFinalOAfterConjunctStaysSilent() {
        assertEquals("স্বপ্ন", t("sbopno"))
        assertEquals("কষ্ট", t("koShTo"))
        assertEquals("সত্য", t("sotyo"))
        // ...but after a plain consonant the final "o" still takes o-kar.
        assertEquals("ভালো", t("valo"))
        assertEquals("কালো", t("kalo"))
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
        assertEquals("বড়", t("boR"))
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

    @Test fun jofolaAndAntastyaYo() {
        // "y" after a consonant is jofola; elsewhere it is য়.
        assertEquals("শ্যাম", t("shyam"))
        assertEquals("ত্যাগ", t("tyag"))
        assertEquals("ব্যাপার", t("byapar"))
        assertEquals("মেয়ে", t("meye"))
        assertEquals("নিয়ে", t("niye"))
        assertEquals("হয়", t("hoy"))
        assertEquals("য়", t("y"))
        assertEquals("য়া", t("ya"))
        // "Y" never joins the cluster, so য় is reachable after a consonant.
        assertEquals("কয়া", t("kYa"))
    }

    @Test fun mixedTextPassesThrough() {
        assertEquals("ওকে!", t("OkE!"))
    }
}
