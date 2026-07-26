package com.wasimaster.wmkeyboard.core.dictionaries

/**
 * One downloadable frequency wordlist from the wmkeyboard-data repo
 * (https://github.com/wasi-master/wmkeyboard-data). Each is a gzipped
 * `word<space>count` list sorted by descending frequency, which is what lets
 * the downloader stop reading after the user's chosen word cap instead of
 * transferring the whole file (Thai is 41 MB compressed; the first 150k words
 * are a fraction of that).
 */
data class DictionaryEntry(
    /** Stable key for status maps and UI (== [languageId] except `pt_br`). */
    val id: String,
    /** [com.wasimaster.wmkeyboard.core.script.LanguageRegistry] id it serves. */
    val languageId: String,
    /** Directory/file stem in the data repo (`de` -> `data/de/de_full.txt.gz`). */
    val repoCode: String,
    /** Total lines in the full repo list (the cap trims this). */
    val totalWordCount: Int,
    /** Compressed size of the full file — progress denominator upper bound. */
    val approxGzBytes: Long,
    /** Distinguishes two entries for one language ("Europe"/"Brazil"). */
    val variant: String? = null,
) {
    val url: String
        get() = "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/" +
            "HEAD/data/$repoCode/${repoCode}_full.txt.gz"
}

/**
 * The downloadable wordlist for every language the registry knows and the data
 * repo covers (183 of them; identity mapping except Wikipedia-style codes:
 * `roa_rup`->`rup`, `mhr`->`chm`, `bxr`->`bua`, `nrm`->`nrf`, and `pt`/`pt_br`
 * both feeding `pt`). Regenerate the table against a fresh repo checkout with
 * a directory listing — sizes and counts are display/progress hints, not
 * checksums, so drift is harmless.
 */
object DictionaryCatalog {

    /** User-selectable download size: how many of the most frequent words to keep. */
    enum class DictionarySize(val label: String, val wordCap: Int) {
        SMALL("Small", 50_000),
        MEDIUM("Medium", 150_000),
        LARGE("Large", 300_000),
    }

    private fun entry(
        id: String,
        languageId: String,
        repoCode: String,
        totalWordCount: Int,
        approxGzBytes: Long,
        variant: String? = null,
    ) = DictionaryEntry(id, languageId, repoCode, totalWordCount, approxGzBytes, variant)

    val entries: List<DictionaryEntry> = listOf(
        entry("ab", "ab", "ab", 81_433, 380_406L),
        entry("ady", "ady", "ady", 20_195, 94_516L),
        entry("af", "af", "af", 288_488, 1_390_628L),
        entry("ak", "ak", "ak", 3_893, 15_910L),
        entry("am", "am", "am", 91_579, 412_108L),
        entry("ar", "ar", "ar", 2_487_447, 9_706_359L),
        entry("av", "av", "av", 53_036, 241_190L),
        entry("ay", "ay", "ay", 34_729, 132_886L),
        entry("az", "az", "az", 814_123, 3_616_285L),
        entry("ba", "ba", "ba", 159_186, 790_316L),
        entry("be", "be", "be", 349_962, 1_737_920L),
        entry("bg", "bg", "bg", 1_055_844, 4_924_294L),
        entry("bho", "bho", "bho", 7_304, 49_129L),
        entry("bi", "bi", "bi", 6_558, 25_592L),
        entry("bm", "bm", "bm", 13_745, 56_151L),
        entry("bn", "bn", "bn", 451_348, 1_675_761L),
        entry("bo", "bo", "bo", 19_157, 309_528L),
        entry("br", "br", "br", 128_028, 558_000L),
        entry("brx", "brx", "brx", 10_900, 74_842L),
        entry("bua", "bua", "bxr", 60_442, 263_262L),
        entry("ca", "ca", "ca", 184_216, 669_028L),
        entry("ce", "ce", "ce", 73_434, 332_281L),
        entry("ceb", "ceb", "ceb", 383_771, 1_344_763L),
        entry("chr", "chr", "chr", 6_500, 28_870L),
        entry("ckb", "ckb", "ckb", 201_528, 863_815L),
        entry("co", "co", "co", 30_522, 119_761L),
        entry("crh", "crh", "crh", 1_486, 7_819L),
        entry("cs", "cs", "cs", 1_719_446, 6_998_615L),
        entry("csb", "csb", "csb", 63_525, 255_227L),
        entry("cv", "cv", "cv", 35_945, 172_438L),
        entry("cy", "cy", "cy", 123_099, 545_010L),
        entry("da", "da", "da", 685_713, 3_082_306L),
        entry("de", "de", "de", 1_153_001, 5_474_930L),
        entry("doi", "doi", "doi", 2_539, 17_410L),
        entry("dsb", "dsb", "dsb", 46_802, 187_995L),
        entry("dv", "dv", "dv", 100_368, 493_127L),
        entry("ee", "ee", "ee", 14_882, 48_645L),
        entry("el", "el", "el", 1_168_400, 5_726_781L),
        entry("en", "en", "en", 1_636_178, 6_753_423L),
        entry("eo", "eo", "eo", 386_958, 1_630_975L),
        entry("es", "es", "es", 1_190_537, 4_833_505L),
        entry("et", "et", "et", 1_000_180, 4_084_505L),
        entry("eu", "eu", "eu", 389_587, 1_584_930L),
        entry("fa", "fa", "fa", 442_333, 1_756_622L),
        entry("fi", "fi", "fi", 2_488_573, 10_584_625L),
        entry("fj", "fj", "fj", 10_583, 44_024L),
        entry("fo", "fo", "fo", 231_427, 1_049_407L),
        entry("fr", "fr", "fr", 825_643, 3_464_732L),
        entry("fur", "fur", "fur", 53_308, 200_165L),
        entry("fy", "fy", "fy", 138_753, 633_511L),
        entry("ga", "ga", "ga", 188_674, 810_882L),
        entry("gd", "gd", "gd", 71_344, 291_138L),
        entry("gl", "gl", "gl", 268_205, 1_164_344L),
        entry("gn", "gn", "gn", 30_178, 127_355L),
        entry("gu", "gu", "gu", 172_335, 834_724L),
        entry("gv", "gv", "gv", 20_491, 87_175L),
        entry("ha", "ha", "ha", 38_571, 200_967L),
        entry("haw", "haw", "haw", 19_109, 79_128L),
        entry("he", "he", "he", 1_053_149, 4_291_295L),
        entry("hi", "hi", "hi", 242_911, 1_313_058L),
        entry("hr", "hr", "hr", 1_497_358, 5_787_914L),
        entry("hsb", "hsb", "hsb", 110_141, 451_823L),
        entry("ht", "ht", "ht", 36_690, 159_068L),
        entry("hu", "hu", "hu", 3_138_382, 13_176_302L),
        entry("hy", "hy", "hy", 394_817, 1_782_177L),
        entry("ia", "ia", "ia", 50_502, 208_771L),
        entry("id", "id", "id", 354_661, 1_444_041L),
        entry("ig", "ig", "ig", 13_929, 78_042L),
        entry("is", "is", "is", 256_264, 1_094_639L),
        entry("it", "it", "it", 791_470, 3_240_903L),
        entry("iu", "iu", "iu", 9_978, 53_532L),
        entry("ja", "ja", "ja", 213_393, 922_434L),
        entry("jbo", "jbo", "jbo", 2_412, 6_319L),
        entry("jv", "jv", "jv", 144_058, 591_696L),
        entry("ka", "ka", "ka", 335_324, 1_639_061L),
        entry("kbd", "kbd", "kbd", 43_188, 193_415L),
        entry("ki", "ki", "ki", 86_175, 329_664L),
        entry("kk", "kk", "kk", 361_083, 1_788_094L),
        entry("km", "km", "km", 9_427, 155_670L),
        entry("kn", "kn", "kn", 549_589, 2_884_310L),
        entry("ko", "ko", "ko", 675_331, 2_838_560L),
        entry("kok", "kok", "kok", 58_449, 220_459L),
        entry("ku", "ku", "ku", 75_880, 340_422L),
        entry("kv", "kv", "kv", 49_462, 228_954L),
        entry("kw", "kw", "kw", 49_355, 194_052L),
        entry("ky", "ky", "ky", 336_397, 1_691_959L),
        entry("la", "la", "la", 184_648, 716_296L),
        entry("lb", "lb", "lb", 152_991, 734_198L),
        entry("lez", "lez", "lez", 59_601, 267_993L),
        entry("lg", "lg", "lg", 187_969, 735_891L),
        entry("lij", "lij", "lij", 147_785, 567_240L),
        entry("lld", "lld", "lld", 163_062, 632_585L),
        entry("ln", "ln", "ln", 4_865, 17_803L),
        entry("lo", "lo", "lo", 33_334, 333_101L),
        entry("lt", "lt", "lt", 321_660, 1_204_167L),
        entry("lv", "lv", "lv", 330_323, 1_403_969L),
        entry("mai", "mai", "mai", 49_795, 354_711L),
        entry("mg", "mg", "mg", 184_155, 728_748L),
        entry("chm", "chm", "mhr", 62_439, 285_836L),
        entry("mi", "mi", "mi", 16_654, 67_366L),
        entry("mk", "mk", "mk", 293_121, 1_298_185L),
        entry("ml", "ml", "ml", 250_542, 1_275_671L),
        entry("mn", "mn", "mn", 137_799, 641_531L),
        entry("mr", "mr", "mr", 331_817, 1_721_372L),
        entry("ms", "ms", "ms", 242_279, 1_118_799L),
        entry("mt", "mt", "mt", 79_257, 341_653L),
        entry("my", "my", "my", 49_585, 340_725L),
        entry("myv", "myv", "myv", 71_985, 320_352L),
        entry("nb", "nb", "nb", 387_768, 1_734_759L),
        entry("nd", "nd", "nd", 36_656, 126_306L),
        entry("ne", "ne", "ne", 163_948, 818_119L),
        entry("nl", "nl", "nl", 1_099_506, 5_009_575L),
        entry("nqo", "nqo", "nqo", 51_467, 230_261L),
        entry("nrf", "nrf", "nrm", 33_765, 128_026L),
        entry("nso", "nso", "nso", 26_865, 103_682L),
        entry("ny", "ny", "ny", 4_020, 16_541L),
        entry("oc", "oc", "oc", 154_753, 623_673L),
        entry("om", "om", "om", 11_941, 46_241L),
        entry("or", "or", "or", 72_978, 401_802L),
        entry("os", "os", "os", 12_863, 62_079L),
        entry("pa", "pa", "pa", 226_706, 1_148_074L),
        entry("pl", "pl", "pl", 1_481_466, 6_171_710L),
        entry("pms", "pms", "pms", 47_916, 200_183L),
        entry("ps", "ps", "ps", 146_694, 626_376L),
        entry("pt", "pt", "pt", 763_183, 3_138_081L, variant = "Europe"),
        entry("pt_br", "pt", "pt_br", 847_162, 3_384_245L, variant = "Brazil"),
        entry("qu", "qu", "qu", 20_435, 83_225L),
        entry("rm", "rm", "rm", 49_238, 204_904L),
        entry("ro", "ro", "ro", 1_218_883, 5_040_931L),
        entry("rup", "rup", "roa_rup", 25_337, 100_417L),
        entry("ru", "ru", "ru", 1_236_328, 5_940_540L),
        entry("rw", "rw", "rw", 143_158, 552_327L),
        entry("sa", "sa", "sa", 219_660, 1_356_999L),
        entry("sah", "sah", "sah", 197_782, 912_422L),
        entry("sc", "sc", "sc", 38_576, 151_283L),
        entry("scn", "scn", "scn", 142_482, 539_216L),
        entry("sd", "sd", "sd", 92_822, 369_930L),
        entry("se", "se", "se", 27_671, 126_224L),
        entry("si", "si", "si", 163_154, 811_027L),
        entry("sk", "sk", "sk", 746_834, 2_988_084L),
        entry("sl", "sl", "sl", 842_678, 3_236_603L),
        entry("sm", "sm", "sm", 12_149, 48_536L),
        entry("sn", "sn", "sn", 81_621, 318_116L),
        entry("so", "so", "so", 34_258, 133_508L),
        entry("sq", "sq", "sq", 240_759, 901_232L),
        entry("sr", "sr", "sr", 225_229, 950_408L),
        entry("ss", "ss", "ss", 43_023, 172_299L),
        entry("st", "st", "st", 16_704, 53_082L),
        entry("su", "su", "su", 173_735, 689_687L),
        entry("sv", "sv", "sv", 900_975, 4_079_684L),
        entry("sw", "sw", "sw", 128_718, 527_814L),
        entry("ta", "ta", "ta", 1_874_722, 7_756_983L),
        entry("te", "te", "te", 393_342, 2_061_715L),
        entry("tet", "tet", "tet", 22_227, 87_799L),
        entry("tg", "tg", "tg", 144_177, 692_345L),
        entry("th", "th", "th", 4_024_497, 41_176_795L),
        entry("ti", "ti", "ti", 5_411, 23_047L),
        entry("tk", "tk", "tk", 79_436, 320_667L),
        entry("tl", "tl", "tl", 144_149, 631_911L),
        entry("tlh", "tlh", "tlh", 2_428, 6_843L),
        entry("tn", "tn", "tn", 33_977, 108_600L),
        entry("to", "to", "to", 10_181, 41_437L),
        entry("tr", "tr", "tr", 2_027_282, 8_051_955L),
        entry("ts", "ts", "ts", 16_508, 54_430L),
        entry("tt", "tt", "tt", 142_022, 702_178L),
        entry("ty", "ty", "ty", 5_164, 21_116L),
        entry("tyv", "tyv", "tyv", 98_879, 451_744L),
        entry("udm", "udm", "udm", 43_500, 202_058L),
        entry("ug", "ug", "ug", 83_651, 374_285L),
        entry("uk", "uk", "uk", 409_369, 1_835_322L),
        entry("ur", "ur", "ur", 154_781, 541_600L),
        entry("uz", "uz", "uz", 176_272, 741_660L),
        entry("ve", "ve", "ve", 13_743, 45_724L),
        entry("vec", "vec", "vec", 205_090, 766_850L),
        entry("vi", "vi", "vi", 605_499, 3_056_250L),
        entry("wa", "wa", "wa", 43_783, 178_143L),
        entry("wo", "wo", "wo", 20_916, 68_267L),
        entry("xal", "xal", "xal", 10_333, 46_484L),
        entry("xh", "xh", "xh", 87_838, 325_602L),
        entry("yi", "yi", "yi", 46_500, 212_440L),
        entry("yo", "yo", "yo", 27_710, 124_495L),
        entry("zgh", "zgh", "zgh", 58_474, 243_558L),
        entry("zu", "zu", "zu", 367_932, 1_490_853L),
    )

    init {
        check(entries.map { it.id }.toSet().size == entries.size) { "entry ids must be unique" }
    }

    fun byId(id: String): DictionaryEntry? = entries.firstOrNull { it.id == id }

    fun forLanguage(langId: String): List<DictionaryEntry> =
        entries.filter { it.languageId == langId }
}
