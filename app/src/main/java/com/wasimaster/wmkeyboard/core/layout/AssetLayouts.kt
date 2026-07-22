package com.wasimaster.wmkeyboard.core.layout

import android.content.res.AssetManager

/**
 * The long tail of language layouts, shipped as JSON assets rather than Kotlin
 * [BuiltInLayouts].
 *
 * A boot-critical grid — the default-enabled layouts drawn on the first frame —
 * has to be a compiled `val`: it cannot wait for a parse, and a typo in it must
 * fail the build, not ship a blank keyboard. Every other layout can be data.
 * Shipping it as the same [LayoutFile] envelope the export path already writes
 * gets three things at once: it parses off the main thread, a mistake costs one
 * language rather than the build, and each file is user-importable and editable
 * for free.
 *
 * Loaded once by [load]; [all] returns the cache (empty until then) so
 * [resolveLayouts] can splice these in beside the built-ins with no `Context`.
 * Asset layouts are deliberately never in `defaultEnabledIds`, so an empty cache
 * on the first frame only means a not-yet-selected language is briefly absent —
 * never a keyboard with nothing to draw.
 */
object AssetLayouts {

    /** Folder under `assets/` holding the layout files. */
    private const val DIR = "layouts"

    /** Stable ids of the shipped asset layouts, for the [LanguageDef]s that group them. */
    const val PT_QWERTY_ID = "asset_pt_qwerty"
    const val UK_JCUKEN_ID = "asset_uk_jcuken"
    const val IT_QWERTY_ID = "asset_it_qwerty"
    const val NL_QWERTY_ID = "asset_nl_qwerty"
    const val PL_QWERTY_ID = "asset_pl_qwerty"
    const val SV_QWERTY_ID = "asset_sv_qwerty"
    const val SR_JCUKEN_ID = "asset_sr_jcuken"
    const val BG_PHONETIC_ID = "asset_bg_phonetic"
    const val KA_QWERTY_ID = "asset_ka_qwerty"
    const val CS_QWERTZ_ID = "asset_cs_qwertz"
    const val SK_QWERTZ_ID = "asset_sk_qwertz"
    const val RO_QWERTY_ID = "asset_ro_qwerty"
    const val HU_QWERTZ_ID = "asset_hu_qwertz"
    const val FI_QWERTY_ID = "asset_fi_qwerty"
    const val DA_QWERTY_ID = "asset_da_qwerty"
    const val NB_QWERTY_ID = "asset_nb_qwerty"
    const val HR_QWERTZ_ID = "asset_hr_qwertz"
    const val FA_STANDARD_ID = "asset_fa_standard"
    const val BE_JCUKEN_ID = "asset_be_jcuken"
    const val ET_QWERTY_ID = "asset_et_qwerty"
    const val LT_QWERTY_ID = "asset_lt_qwerty"
    const val LV_QWERTY_ID = "asset_lv_qwerty"
    const val SL_QWERTZ_ID = "asset_sl_qwertz"

    // --- Multi-language expansion: long-tail asset layouts. ---
    const val UR_PHONETIC_ID = "asset_ur_phonetic"
    const val PS_PASHTO_ID = "asset_ps_pashto"
    const val SD_SINDHI_ID = "asset_sd_sindhi"
    const val UG_UYGHUR_ID = "asset_ug_uyghur"
    const val CKB_SORANI_ID = "asset_ckb_sorani"
    const val HY_PHONETIC_ID = "asset_hy_phonetic"
    const val HY_EASTERN_ID = "asset_hy_eastern"
    const val MK_CYRILLIC_ID = "asset_mk_macedonian"
    const val KK_CYRILLIC_ID = "asset_kk_kazakh"
    const val KY_CYRILLIC_ID = "asset_ky_kyrgyz"
    const val TG_CYRILLIC_ID = "asset_tg_tajik"
    const val MN_CYRILLIC_ID = "asset_mn_mongolian"
    const val TT_CYRILLIC_ID = "asset_tt_tatar"
    const val BA_CYRILLIC_ID = "asset_ba_bashkir"
    const val CV_CYRILLIC_ID = "asset_cv_chuvash"
    const val CE_CYRILLIC_ID = "asset_ce_chechen"
    const val OS_CYRILLIC_ID = "asset_os_ossetian"
    const val SAH_CYRILLIC_ID = "asset_sah_yakut"
    const val MR_INSCRIPT_ID = "asset_mr_inscript"
    const val NE_INSCRIPT_ID = "asset_ne_inscript"
    const val SA_INSCRIPT_ID = "asset_sa_inscript"
    const val TA_TAMIL99_ID = "asset_ta_tamil99"
    const val SI_WIJESEKARA_ID = "asset_si_wijesekara"
    const val TE_INSCRIPT_ID = "asset_te_inscript"
    const val KN_INSCRIPT_ID = "asset_kn_inscript"
    const val ML_INSCRIPT_ID = "asset_ml_inscript"
    const val GU_INSCRIPT_ID = "asset_gu_inscript"
    const val PA_INSCRIPT_ID = "asset_pa_gurmukhi_inscript"
    const val OR_INSCRIPT_ID = "asset_or_odia_inscript"
    const val CA_CATALAN_ID = "asset_ca_catalan"
    const val GL_GALICIAN_ID = "asset_gl_galician"
    const val EU_BASQUE_ID = "asset_eu_basque"
    const val OC_OCCITAN_ID = "asset_oc_occitan"
    const val BR_BRETON_ID = "asset_br_breton"
    const val CO_CORSICAN_ID = "asset_co_corsican"
    const val LA_LATIN_ID = "asset_la_latin"
    const val LB_LUXEMBOURGISH_ID = "asset_lb_luxembourgish"
    const val FY_FRISIAN_ID = "asset_fy_frisian"
    const val FO_FAROESE_ID = "asset_fo_faroese"
    const val CY_WELSH_ID = "asset_cy_welsh"
    const val GA_IRISH_ID = "asset_ga_irish"
    const val GD_GAELIC_ID = "asset_gd_gaelic"
    const val IS_ICELANDIC_ID = "asset_is_icelandic"
    const val SQ_ALBANIAN_ID = "asset_sq_albanian"
    const val MT_MALTESE_ID = "asset_mt_maltese"
    const val EO_ESPERANTO_ID = "asset_eo_esperanto"
    const val AF_AFRIKAANS_ID = "asset_af_afrikaans"
    const val TR_TURKISH_Q_ID = "asset_tr_turkishq"
    const val AZ_LATIN_ID = "asset_az_azlatin"
    const val UZ_LATIN_ID = "asset_uz_uzlatin"
    const val TK_LATIN_ID = "asset_tk_tklatin"
    const val KU_KURMANJI_ID = "asset_ku_kurmanji"
    const val ID_QWERTY_ID = "asset_id_indonesian"
    const val MS_QWERTY_ID = "asset_ms_malay"
    const val TL_QWERTY_ID = "asset_tl_filipino"
    const val CEB_QWERTY_ID = "asset_ceb_cebuano"
    const val JV_QWERTY_ID = "asset_jv_javanese"
    const val SU_QWERTY_ID = "asset_su_sundanese"
    const val MI_QWERTY_ID = "asset_mi_maori"
    const val HAW_QWERTY_ID = "asset_haw_hawaiian"
    const val MG_QWERTY_ID = "asset_mg_malagasy"
    const val SM_QWERTY_ID = "asset_sm_samoan"
    const val FJ_QWERTY_ID = "asset_fj_fijian"
    const val TO_QWERTY_ID = "asset_to_tongan"
    const val SW_QWERTY_ID = "asset_sw_sw_qwerty"
    const val ZU_QWERTY_ID = "asset_zu_zu_qwerty"
    const val XH_QWERTY_ID = "asset_xh_xh_qwerty"
    const val YO_QWERTY_ID = "asset_yo_yo_qwerty"
    const val IG_QWERTY_ID = "asset_ig_ig_qwerty"
    const val HA_QWERTY_ID = "asset_ha_ha_qwerty"
    const val SO_QWERTY_ID = "asset_so_so_qwerty"
    const val RW_QWERTY_ID = "asset_rw_rw_qwerty"
    const val LN_QWERTY_ID = "asset_ln_ln_qwerty"
    const val NY_QWERTY_ID = "asset_ny_ny_qwerty"
    const val SN_QWERTY_ID = "asset_sn_sn_qwerty"
    const val ST_QWERTY_ID = "asset_st_st_qwerty"
    const val TN_QWERTY_ID = "asset_tn_tn_qwerty"
    const val WO_QWERTY_ID = "asset_wo_wo_qwerty"
    const val VI_QWERTY_ID = "asset_vi_vietnamese"
    const val HT_QWERTY_ID = "asset_ht_haitian"
    const val QU_QWERTY_ID = "asset_qu_quechua"
    const val GN_QWERTY_ID = "asset_gn_guarani"
    const val TH_KEDMANEE_ID = "asset_th_kedmanee"
    const val LO_LAO_ID = "asset_lo_lao"
    const val KM_NIDA_ID = "asset_km_nida"
    const val MY_MYANMAR3_ID = "asset_my_myanmar3"
    const val RU_PHONETIC_ID = "asset_ru_phonetic"
    const val FR_BEPO_ID = "asset_fr_bepo"
    const val ES_LATAM_ID = "asset_es_latam"
    const val DE_SWISS_ID = "asset_de_swiss"
    const val UK_PHONETIC_ID = "asset_uk_phonetic"

    const val UDM_CYRILLIC_ID = "asset_udm_cyrillic"
    const val KV_CYRILLIC_ID = "asset_kv_cyrillic"
    const val XAL_CYRILLIC_ID = "asset_xal_cyrillic"
    const val TYV_CYRILLIC_ID = "asset_tyv_cyrillic"
    const val BUA_CYRILLIC_ID = "asset_bua_cyrillic"
    const val MYV_CYRILLIC_ID = "asset_myv_cyrillic"
    const val CHM_CYRILLIC_ID = "asset_chm_cyrillic"
    const val ADY_CYRILLIC_ID = "asset_ady_cyrillic"
    const val KBD_CYRILLIC_ID = "asset_kbd_cyrillic"
    const val AB_CYRILLIC_ID = "asset_ab_cyrillic"
    const val AV_CYRILLIC_ID = "asset_av_cyrillic"
    const val LEZ_CYRILLIC_ID = "asset_lez_cyrillic"
    const val KOK_INSCRIPT_ID = "asset_kok_inscript"
    const val MAI_INSCRIPT_ID = "asset_mai_inscript"
    const val BRX_INSCRIPT_ID = "asset_brx_inscript"
    const val DOI_INSCRIPT_ID = "asset_doi_inscript"
    const val BHO_INSCRIPT_ID = "asset_bho_inscript"
    const val AM_ETHIOPIC_ID = "asset_am_ethiopic"
    const val TI_ETHIOPIC_ID = "asset_ti_ethiopic"
    const val RM_QWERTY_ID = "asset_rm_qwerty"
    const val WA_QWERTY_ID = "asset_wa_qwerty"
    const val SCN_QWERTY_ID = "asset_scn_qwerty"
    const val VEC_QWERTY_ID = "asset_vec_qwerty"
    const val LIJ_QWERTY_ID = "asset_lij_qwerty"
    const val NAP_QWERTY_ID = "asset_nap_qwerty"
    const val FUR_QWERTY_ID = "asset_fur_qwerty"
    const val CSB_QWERTY_ID = "asset_csb_qwerty"
    const val HSB_QWERTZ_ID = "asset_hsb_qwertz"
    const val DSB_QWERTZ_ID = "asset_dsb_qwertz"
    const val GV_QWERTY_ID = "asset_gv_qwerty"
    const val KW_QWERTY_ID = "asset_kw_qwerty"
    const val RUP_QWERTY_ID = "asset_rup_qwerty"
    const val CRH_QWERTY_ID = "asset_crh_qwerty"
    const val OM_QWERTY_ID = "asset_om_qwerty"
    const val NSO_QWERTY_ID = "asset_nso_qwerty"
    const val TS_QWERTY_ID = "asset_ts_qwerty"
    const val VE_QWERTY_ID = "asset_ve_qwerty"
    const val SS_QWERTY_ID = "asset_ss_qwerty"
    const val ND_QWERTY_ID = "asset_nd_qwerty"
    const val LG_QWERTY_ID = "asset_lg_qwerty"
    const val KI_QWERTY_ID = "asset_ki_qwerty"
    const val AK_QWERTY_ID = "asset_ak_qwerty"
    const val EE_QWERTY_ID = "asset_ee_qwerty"
    const val BM_QWERTY_ID = "asset_bm_qwerty"
    const val AY_QWERTY_ID = "asset_ay_qwerty"
    const val TY_QWERTY_ID = "asset_ty_qwerty"
    const val BI_QWERTY_ID = "asset_bi_qwerty"
    const val TET_QWERTY_ID = "asset_tet_qwerty"
    const val IA_QWERTY_ID = "asset_ia_qwerty"
    const val DV_THAANA_ID = "asset_dv_thaana"
    const val SE_QWERTY_ID = "asset_se_qwerty"
    const val SMN_QWERTY_ID = "asset_smn_qwerty"
    const val SMS_QWERTY_ID = "asset_sms_qwerty"
    const val SC_QWERTY_ID = "asset_sc_qwerty"
    const val PMS_QWERTY_ID = "asset_pms_qwerty"
    const val LLD_QWERTY_ID = "asset_lld_qwerty"
    const val NRF_QWERTY_ID = "asset_nrf_qwerty"

    // --- Composer-driven input methods (Vietnamese Telex/VNI, Japanese romaji,
    // Chinese pinyin): plain QWERTY grids whose "composer" field does the work. ---
    /** International Phonetic Alphabet: a phonetic-notation layout, not a language. */
    const val IPA_ID = "asset_ipa"

    const val VI_TELEX_ID = "asset_vi_telex"
    const val VI_VNI_ID = "asset_vi_vni"
    const val JA_ROMAJI_ID = "asset_ja_romaji"
    const val ZH_PINYIN_ID = "asset_zh_pinyin"

    // --- Fancy Text: per-style Unicode "font" layouts (the 𝔣𝔞𝔫𝔠𝔶 𝕦𝕟𝕚𝕔𝕠𝕕𝕖
    // trick). Each is a plain QWERTY grid whose keys carry the styled glyph as
    // their output — no composer, the substitution is the layout itself. ---
    const val FANCY_BOLD_ID = "asset_fancy_bold"
    const val FANCY_ITALIC_ID = "asset_fancy_italic"
    const val FANCY_BOLD_ITALIC_ID = "asset_fancy_bold_italic"
    const val FANCY_SCRIPT_ID = "asset_fancy_script"
    const val FANCY_BOLD_SCRIPT_ID = "asset_fancy_bold_script"
    const val FANCY_FRAKTUR_ID = "asset_fancy_fraktur"
    const val FANCY_BOLD_FRAKTUR_ID = "asset_fancy_bold_fraktur"
    const val FANCY_DOUBLE_STRUCK_ID = "asset_fancy_double_struck"
    const val FANCY_SANS_ID = "asset_fancy_sans"
    const val FANCY_SANS_BOLD_ID = "asset_fancy_sans_bold"
    const val FANCY_SANS_ITALIC_ID = "asset_fancy_sans_italic"
    const val FANCY_SANS_BOLD_ITALIC_ID = "asset_fancy_sans_bold_italic"
    const val FANCY_MONOSPACE_ID = "asset_fancy_monospace"
    const val FANCY_FULLWIDTH_ID = "asset_fancy_fullwidth"
    const val FANCY_CIRCLED_ID = "asset_fancy_circled"
    const val FANCY_CIRCLED_FILLED_ID = "asset_fancy_circled_filled"
    const val FANCY_SQUARED_ID = "asset_fancy_squared"
    const val FANCY_SQUARED_FILLED_ID = "asset_fancy_squared_filled"
    const val FANCY_SMALL_CAPS_ID = "asset_fancy_small_caps"
    const val FANCY_SUPERSCRIPT_ID = "asset_fancy_superscript"
    const val FANCY_STRIKETHROUGH_ID = "asset_fancy_strikethrough"
    const val FANCY_UNDERLINE_ID = "asset_fancy_underline"

    @Volatile private var cached: List<LayoutSpec> = emptyList()
    @Volatile private var loaded = false

    /** The parsed asset layouts, or empty before [load] has run. */
    val all: List<LayoutSpec> get() = cached

    /**
     * Reads and parses every `.wmlayout.json` under `assets/layouts`, caching the
     * result. Idempotent; the I/O runs on the calling thread, so call it off the
     * main thread the way the service loads its dictionaries. A file that fails
     * to parse is skipped, never fatal — one malformed asset cannot cost the
     * others.
     */
    fun load(assets: AssetManager) {
        if (loaded) return
        val names = runCatching { assets.list(DIR)?.asList() }.getOrNull().orEmpty()
        cached = names
            .filter { it.endsWith(SUFFIX) }
            .mapNotNull { name ->
                runCatching {
                    val text = assets.open("$DIR/$name").use { it.readBytes().decodeToString() }
                    LayoutFile.decode(text)?.layout
                }.getOrNull()
            }
        loaded = true
    }

    private val SUFFIX = ".${LayoutFile.FILE_EXTENSION}"
}
