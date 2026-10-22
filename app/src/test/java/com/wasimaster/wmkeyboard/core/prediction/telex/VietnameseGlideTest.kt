package com.wasimaster.wmkeyboard.core.prediction.telex

import com.wasimaster.wmkeyboard.core.gesture.GlideBeam
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class VietnameseGlideTest {

    @Test
    fun testVietnameseGlideResolution() {
        val sampleTsv = """
            tieng	tiếng,tiêng,tiềng
            nguoi	người,ngươi,nguội
            viet	viết,việt
            quehuong	quê hương
        """.trimIndent()

        val index = VietnameseGlide.loadFromStream(
            ByteArrayInputStream(sampleTsv.toByteArray(Charsets.UTF_8)),
        )
        assertNotNull(index)
        assertTrue(index.alphabet.contains('t'.code))
        assertTrue(index.alphabet.contains('n'.code))

        // Test resolving single syllable
        val candidate = GlideBeam.Candidate("tieng", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolved = index.resolve(listOf(candidate))
        assertTrue(resolved.isNotEmpty())
        assertEquals("tiếng", resolved[0].word)

        // Test resolving compound word
        val compoundCandidate = GlideBeam.Candidate("quehuong", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolvedCompound = index.resolve(listOf(compoundCandidate))
        assertTrue(resolvedCompound.isNotEmpty())
        assertEquals("quê hương", resolvedCompound[0].word)

        // Test lookup direct
        val forms = VietnameseGlide.lookup("nguoi")
        assertEquals(listOf("người", "ngươi", "nguội"), forms)
    }

    @Test
    fun testThreeColumnChineseFormatAndFrequencyRanking() {
        // 3-column Chinese Pinyin format: reading \t word \t freq
        val threeColTsv = """
            may	mày	50
            may	mấy	189
            may	máy	181
            thumay	thứ mấy	200
        """.trimIndent()

        val index = VietnameseGlide.loadFromStream(
            ByteArrayInputStream(threeColTsv.toByteArray(Charsets.UTF_8)),
        )
        assertNotNull(index)

        // Since mấy (189) > máy (181) > mày (50), mấy should rank first!
        val candidate = GlideBeam.Candidate("may", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolved = index.resolve(listOf(candidate))
        assertTrue(resolved.size >= 3)
        assertEquals("mấy", resolved[0].word)
        assertEquals("máy", resolved[1].word)
        assertEquals("mày", resolved[2].word)

        // Test compound word
        val compound = GlideBeam.Candidate("thumay", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolvedCompound = index.resolve(listOf(compound))
        assertTrue(resolvedCompound.isNotEmpty())
        assertEquals("thứ mấy", resolvedCompound[0].word)
    }

    @Test
    fun testUnigramFrequencyIntegration() {
        // 2-column TSV where "mày" is listed first, but unigrams map gives "máy" and "mấy" higher frequency
        val twoColTsv = """
            may	mày,mấy,máy,mây
        """.trimIndent()

        val unigramMap = mapOf(
            "máy" to 220,
            "mấy" to 210,
            "mây" to 140,
            "mày" to 100
        )

        val index = VietnameseGlide.loadFromStream(
            ByteArrayInputStream(twoColTsv.toByteArray(Charsets.UTF_8)),
            unigramMap
        )

        val candidate = GlideBeam.Candidate("may", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolved = index.resolve(listOf(candidate))
        assertEquals("máy", resolved[0].word)
        assertEquals("mấy", resolved[1].word)
        assertEquals("mây", resolved[2].word)
        assertEquals("mày", resolved[3].word)
    }

    @Test
    fun testDynamicUserCompoundSynthesisAndPhonotacticFilter() {
        VietnameseGlide.resetForTesting()

        val sampleTsv = """
            chi	chi
            rua	rửa,rùa,rứa
            em	em
            nguoi	người,ngươi
            ta	ta
        """.trimIndent()

        val unigramMap = mapOf(
            "chi" to 1500,
            "rứa" to 800,
            "rửa" to 1200,
            "em" to 5000,
            "người" to 2000,
            "ta" to 1500,
        )

        val lexicon = UserLexicon(null)
        // Phrases added by hand in Personal Dictionary
        lexicon.addWordByHand("chi rứa")
        lexicon.addWordByHand("chi rứa em")

        // Phrases merely learned via typing n-grams (bigrams/trigrams) - must NOT synthesize stems!
        repeat(5) { lexicon.learnBigram("người", "ta") }
        repeat(5) { lexicon.learnTrigram("người", "ta", "ơi") }
        // Teencode phrase "ko bt" - should be filtered!
        lexicon.addWordByHand("ko bt")

        val index = VietnameseGlide.loadFromStream(
            ByteArrayInputStream(sampleTsv.toByteArray(Charsets.UTF_8)),
            unigramMap,
            emptyMap(),
            lexicon,
        )

        // 1. "chirua" must be synthesized from manual entry
        val chiruaForms = VietnameseGlide.lookupUserCompounds("chirua")
        assertEquals(listOf("chi rứa"), chiruaForms)

        // 2. "chiruaem" must be synthesized from manual entry
        val chiruaemForms = VietnameseGlide.lookupUserCompounds("chiruaem")
        assertEquals(listOf("chi rứa em"), chiruaemForms)

        // 3. Mere n-grams "người ta" must NOT synthesize stems (avoids junk!)
        assertTrue(VietnameseGlide.lookupUserCompounds("nguoita").isEmpty())

        // 4. Teencode "kobt" must NOT be synthesized
        assertTrue(VietnameseGlide.lookupUserCompounds("kobt").isEmpty())

        // 5. Resolving candidate "chirua" must yield "chi rứa"
        val candidate = GlideBeam.Candidate("chirua", 1.0, 0.0, FuzzyBeamSearch.Tier.USER)
        val resolved = index.resolve(listOf(candidate))
        assertTrue(resolved.isNotEmpty())
        assertEquals("chi rứa", resolved[0].word)

        // 6. Test blacklist: when blacklisted, resolving candidate "chirua" filters out "chi rứa"
        VietnameseGlide.currentBlacklist = setOf("chi rứa")
        val resolvedBlacklisted = index.resolve(listOf(candidate))
        assertTrue(resolvedBlacklisted.isEmpty())
        VietnameseGlide.currentBlacklist = emptySet()

        // 7. Test deletion in UserLexicon: when phrase is forgotten, stem must disappear!
        lexicon.forget("chi rứa")
        VietnameseGlide.updateUserCompounds(lexicon)
        assertTrue(VietnameseGlide.lookupUserCompounds("chirua").isEmpty())
    }

    @Test
    fun testLabanKeyStyleTrajectoryGlide() {
        VietnameseGlide.resetForTesting()

        val sampleTsv = """
            nguoi	người,ngươi,nguội
            viet	viết,việt
            vietnam	Việt Nam
            chieu	chiều,chiếu,chiêu
            homnay	hôm nay
        """.trimIndent()

        val unigramMap = mapOf(
            "người" to 2500,
            "ngươi" to 100,
            "việt" to 3000,
            "viết" to 2000,
            "Việt Nam" to 2200,
            "chiều" to 1800,
            "hôm nay" to 2100,
        )

        val index = VietnameseGlide.loadFromStream(
            ByteArrayInputStream(sampleTsv.toByteArray(Charsets.UTF_8)),
            unigramMap,
        )
        assertNotNull(index)
        assertNotNull(index.trajectoryDecoder)

        // Build standard QWERTY key centers
        val keyCenters = com.wasimaster.wmkeyboard.core.gesture.eval.SwipeCorpus.keyCenters()
        val keyWidth = com.wasimaster.wmkeyboard.core.gesture.eval.SwipeCorpus.KEY_WIDTH
        val keys = com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap.of(keyCenters, keyWidth)

        // Simulate gesture for "nguoi" (n -> g -> u -> o -> i)
        val charSeq = "nguoi"
        val points = ArrayList<com.wasimaster.wmkeyboard.core.gesture.GesturePoint>()
        var time = 1000L
        for (i in 0 until charSeq.length - 1) {
            val c1 = charSeq[i]
            val c2 = charSeq[i + 1]
            val idx1 = keys.keyIndex(c1)
            val idx2 = keys.keyIndex(c2)
            val kx1 = keys.keyX[idx1] * keyWidth
            val ky1 = keys.keyY[idx1] * keyWidth
            val kx2 = keys.keyX[idx2] * keyWidth
            val ky2 = keys.keyY[idx2] * keyWidth
            for (step in 0..8) {
                val t = step / 8.0f
                val px = kx1 + (kx2 - kx1) * t
                val py = ky1 + (ky2 - ky1) * t
                points.add(com.wasimaster.wmkeyboard.core.gesture.GesturePoint(px, py, time))
                time += 25L
            }
        }
        val lastIdx = keys.keyIndex(charSeq.last())
        points.add(com.wasimaster.wmkeyboard.core.gesture.GesturePoint(keys.keyX[lastIdx] * keyWidth, keys.keyY[lastIdx] * keyWidth, time))

        // Call trajectoryDecoder directly
        val candidates = index.trajectoryDecoder!!.invoke(points, keys, keyWidth, 4)
        assertTrue(candidates.isNotEmpty())
        assertEquals("nguoi", candidates[0].word)

        // Resolve candidate to Vietnamese accented forms
        val resolved = index.resolve(candidates)
        assertTrue(resolved.isNotEmpty())
        assertEquals("người", resolved[0].word)

        // Simulate gesture for short compound word "homnay" (h -> o -> m -> n -> a -> y)
        // Stems with length <= 8 previously suffered from erroneous phonotactic filtering
        val compSeq = "homnay"
        val compPoints = ArrayList<com.wasimaster.wmkeyboard.core.gesture.GesturePoint>()
        var compTime = 2000L
        for (i in 0 until compSeq.length - 1) {
            val c1 = compSeq[i]
            val c2 = compSeq[i + 1]
            val idx1 = keys.keyIndex(c1)
            val idx2 = keys.keyIndex(c2)
            val kx1 = keys.keyX[idx1] * keyWidth
            val ky1 = keys.keyY[idx1] * keyWidth
            val kx2 = keys.keyX[idx2] * keyWidth
            val ky2 = keys.keyY[idx2] * keyWidth
            for (step in 0..8) {
                val t = step / 8.0f
                val px = kx1 + (kx2 - kx1) * t
                val py = ky1 + (ky2 - ky1) * t
                compPoints.add(com.wasimaster.wmkeyboard.core.gesture.GesturePoint(px, py, compTime))
                compTime += 25L
            }
        }
        val compLastIdx = keys.keyIndex(compSeq.last())
        compPoints.add(com.wasimaster.wmkeyboard.core.gesture.GesturePoint(keys.keyX[compLastIdx] * keyWidth, keys.keyY[compLastIdx] * keyWidth, compTime))

        val compCandidates = index.trajectoryDecoder!!.invoke(compPoints, keys, keyWidth, 4)
        assertTrue(compCandidates.isNotEmpty())
        assertEquals("homnay", compCandidates[0].word)

        val resolvedComp = index.resolve(compCandidates)
        assertTrue(resolvedComp.isNotEmpty())
        assertEquals("hôm nay", resolvedComp[0].word)
    }

    @Test
    fun testBuildFromUnigramsDirectly() {
        val unigrams = mapOf(
            "tiếng" to 200,
            "người" to 250,
            "việt" to 220,
        )
        val index = VietnameseGlide.buildFromUnigrams(unigrams)
        assertNotNull(index)
        val candidate = GlideBeam.Candidate("nguoi", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolved = index.resolve(listOf(candidate))
        assertTrue(resolved.isNotEmpty())
        assertEquals("người", resolved[0].word)
    }

    @Test
    fun testCornerCuttingGlideTrajectory() {
        VietnameseGlide.resetForTesting()
        val sampleTsv = """
            viet	việt,viết
            nguoi	người
        """.trimIndent()
        val unigramMap = mapOf("việt" to 3000, "viết" to 2000, "người" to 2500)
        val index = VietnameseGlide.loadFromStream(
            java.io.ByteArrayInputStream(sampleTsv.toByteArray(Charsets.UTF_8)),
            unigramMap,
        )
        org.junit.Assert.assertNotNull(index)
        org.junit.Assert.assertNotNull(index.trajectoryDecoder)

        val keyCenters = com.wasimaster.wmkeyboard.core.gesture.eval.SwipeCorpus.keyCenters()
        val keyWidth = com.wasimaster.wmkeyboard.core.gesture.eval.SwipeCorpus.KEY_WIDTH
        val keys = com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap.of(keyCenters, keyWidth)

        // Simulate swipe for "viet": v -> i -> e -> t with fast corner cutting
        val charSeq = "viet"
        val points = ArrayList<com.wasimaster.wmkeyboard.core.gesture.GesturePoint>()
        var time = 5000L
        for (i in 0 until charSeq.length - 1) {
            val c1 = charSeq[i]
            val c2 = charSeq[i + 1]
            val idx1 = keys.keyIndex(c1)
            val idx2 = keys.keyIndex(c2)
            val kx1 = keys.keyX[idx1] * keyWidth
            val ky1 = keys.keyY[idx1] * keyWidth
            val kx2 = keys.keyX[idx2] * keyWidth
            val ky2 = keys.keyY[idx2] * keyWidth
            for (step in 0..6) {
                val t = step / 6.0f
                val px = kx1 + (kx2 - kx1) * t
                val py = ky1 + (ky2 - ky1) * t
                points.add(com.wasimaster.wmkeyboard.core.gesture.GesturePoint(px, py, time))
                time += 20L
            }
        }
        val lastIdx = keys.keyIndex(charSeq.last())
        points.add(com.wasimaster.wmkeyboard.core.gesture.GesturePoint(keys.keyX[lastIdx] * keyWidth, keys.keyY[lastIdx] * keyWidth, time))

        val candidates = index.trajectoryDecoder!!.invoke(points, keys, keyWidth, 4)
        org.junit.Assert.assertTrue("Corner-cutting glide should resolve candidate", candidates.isNotEmpty())
        org.junit.Assert.assertEquals("viet", candidates[0].word)
        val resolved = index.resolve(candidates)
        org.junit.Assert.assertTrue(resolved.isNotEmpty())
        org.junit.Assert.assertEquals("việt", resolved[0].word)
    }

    @Test
    fun testLabanKeyBundledStemsAndCompounds() {
        VietnameseGlide.resetForTesting()
        val assetFile = java.io.File("src/main/assets/telex/vietnamese_stems.tsv")
        val altFile = java.io.File("app/src/main/assets/telex/vietnamese_stems.tsv")
        val target = if (assetFile.exists()) assetFile else altFile
        if (!target.exists()) return // Skip if running in isolated runner without asset root

        val index = java.io.FileInputStream(target).use { stream ->
            VietnameseGlide.loadFromStream(stream)
        }
        org.junit.Assert.assertNotNull(index)
        org.junit.Assert.assertNotNull(index.trajectoryDecoder)

        // Verify common stems exist
        val lookupViet = VietnameseGlide.lookup("viet")
        org.junit.Assert.assertTrue("Lookup viet should contain việt", lookupViet.contains("việt"))

        val lookupQuehuong = VietnameseGlide.lookup("quehuong")
        org.junit.Assert.assertTrue("Lookup quehuong should contain quê hương", lookupQuehuong.contains("quê hương"))

        val lookupCamon = VietnameseGlide.lookup("camon")
        org.junit.Assert.assertTrue("Lookup camon should contain cảm ơn", lookupCamon.contains("cảm ơn"))

        // Verify resolution of compound candidate
        val compCandidate = GlideBeam.Candidate("quehuong", 2.5, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolved = index.resolve(listOf(compCandidate))
        org.junit.Assert.assertTrue(resolved.isNotEmpty())
        org.junit.Assert.assertEquals("quê hương", resolved[0].word)
    }
}
