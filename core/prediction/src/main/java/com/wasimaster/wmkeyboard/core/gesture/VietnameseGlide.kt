package com.wasimaster.wmkeyboard.core.gesture

import com.wasimaster.wmkeyboard.core.prediction.TrieCompleter
import com.wasimaster.wmkeyboard.core.prediction.WordSource

/**
 * Glide typing support for Vietnamese.
 *
 * Provides base unaccented spellings for Vietnamese syllables and words so that
 * [GlideBeam] can decode swipe trajectories on standard Latin QWERTY layouts.
 */
object VietnameseGlide {

    const val GLIDE_KEYS = "abcdefghijklmnopqrstuvwxyz"

    private const val SEED_FREQUENCY = 500

    /**
     * Extracts unaccented swipe spellings from [words] and merges them with
     * the core Vietnamese syllable inventory.
     */
    fun spellings(words: WordSource?): List<Pair<String, Int>> {
        val best = HashMap<String, Int>(COMMON_SYLLABLES.size + 1024)

        fun keep(spelling: String, freq: Int) {
            if (spelling.length < 2 || !spelling.all { it in 'a'..'z' }) return
            val cur = best[spelling] ?: -1
            if (freq > cur) best[spelling] = freq
        }

        // 1. Seed with common syllables so swipe typing is immediately available
        for (syllable in COMMON_SYLLABLES) {
            keep(syllable, SEED_FREQUENCY)
        }

        // 2. Index words from downloaded/active dictionaries
        words?.walkers()?.forEach { walker ->
            val top = TrieCompleter.top(walker, 30_000)
            for (s in top) {
                val bare = VietnameseDiacriticsModel.stripDiacritics(s.word).lowercase()
                keep(bare, s.frequency.coerceAtLeast(1))
            }
        }

        return best.entries.map { it.key to it.value }
    }

    private val COMMON_SYLLABLES = arrayOf(
        "a", "ai", "am", "an", "ang", "anh", "ao", "ap", "at", "au", "ay", "ba", "bac", "bach", "bai", "ban",
        "bang", "bao", "bat", "bay", "be", "ben", "beng", "beo", "bet", "bi", "bich", "bien", "biet", "binh",
        "bo", "boc", "bom", "bon", "bong", "bot", "bu", "buc", "bui", "bun", "buoc", "buom", "bung", "buoi",
        "buu", "ca", "cac", "cai", "cam", "can", "cang", "canh", "cao", "cap", "cat", "cau", "cay", "cha",
        "chac", "chai", "cham", "chan", "chang", "chanh", "chao", "chap", "chat", "chau", "chay", "che",
        "chem", "chen", "cheo", "chet", "chi", "chich", "chien", "chiet", "chieu", "chim", "chin", "chinh",
        "cho", "choc", "choi", "chon", "chong", "chop", "chu", "chuc", "chui", "chum", "chun", "chung",
        "chuong", "chuot", "chuyen", "co", "coc", "coi", "com", "con", "cong", "cop", "cot", "cu", "cuc",
        "cui", "cum", "cun", "cung", "cuoi", "cuoc", "cuom", "cuon", "cuop", "cuong", "da", "dac", "dai",
        "dam", "dan", "dang", "dao", "dap", "dat", "dau", "day", "de", "dem", "den", "deo", "det", "di",
        "dich", "dien", "diet", "dieu", "dim", "din", "dinh", "do", "doc", "doi", "dom", "don", "dong",
        "dop", "dot", "du", "duc", "dui", "dum", "dung", "duoi", "duoc", "duong", "ga", "gac", "gai", "gam",
        "gan", "gang", "gao", "gap", "gat", "gau", "gay", "ge", "gen", "ghe", "ghen", "ghep", "ghet", "ghi",
        "ghim", "gio", "giot", "giu", "giup", "giam", "gian", "giang", "giao", "giap", "giat", "giau", "giay",
        "gie", "gien", "giep", "giet", "giong", "giua", "giuc", "giun", "giung", "ha", "hac", "hai", "ham",
        "han", "hang", "hao", "hap", "hat", "hau", "hay", "he", "hem", "hen", "heo", "het", "hi", "hich",
        "hien", "hiet", "hieu", "him", "hin", "hinh", "ho", "hoc", "hoi", "hom", "hon", "hong", "hop", "hot",
        "hu", "huc", "hui", "hum", "hun", "hung", "huong", "huou", "huy", "huyen", "huyet", "ka", "ke", "kem",
        "ken", "keo", "ket", "kha", "khac", "khai", "kham", "khan", "khang", "khao", "khap", "khat", "khau",
        "khay", "khe", "khen", "kheo", "khet", "khi", "khich", "khien", "khiet", "khieu", "khim", "khin",
        "khinh", "kho", "khoc", "khoi", "khom", "khon", "khong", "khop", "khot", "khu", "khuc", "khui",
        "khum", "khung", "khuon", "khuot", "khuya", "khuyen", "khuyet", "khua", "la", "lac", "lai", "lam",
        "lan", "lang", "lao", "lap", "lat", "lau", "lay", "le", "lem", "len", "leo", "let", "li", "lich",
        "lien", "liet", "lieu", "lim", "lin", "linh", "lo", "loc", "loi", "lom", "lon", "long", "lop", "lot",
        "lu", "luc", "lui", "lum", "lun", "lung", "luoi", "luoc", "luon", "luong", "luu", "ma", "mac", "mai",
        "mam", "man", "mang", "mao", "map", "mat", "mau", "may", "me", "mem", "men", "meo", "met", "mi",
        "mich", "mien", "miet", "mieu", "mim", "min", "minh", "mo", "moc", "moi", "mom", "mon", "mong",
        "mop", "mot", "mu", "muc", "mui", "mum", "mun", "mung", "muoi", "muon", "mua", "na", "nac", "nai",
        "nam", "nan", "nang", "nao", "nap", "nat", "nau", "nay", "ne", "nem", "nen", "neo", "net", "ni",
        "nich", "nien", "niet", "nieu", "nim", "nin", "ninh", "no", "noc", "noi", "nom", "non", "nong",
        "nop", "not", "nu", "nuc", "nui", "num", "nun", "nung", "nuoc", "nuoi", "nuong", "nga", "ngac",
        "ngai", "ngam", "ngan", "ngang", "ngao", "ngap", "ngat", "ngau", "ngay", "nge", "ngem", "ngen",
        "ngeo", "nget", "nghi", "nghich", "nghien", "nghiet", "nghieu", "nghim", "nghin", "nghinh", "ngo",
        "ngoc", "ngoi", "ngom", "ngon", "ngong", "ngop", "ngot", "ngu", "nguc", "ngui", "ngum", "ngun",
        "ngung", "nguoi", "nguoc", "nguon", "nguong", "nguu", "nha", "nhac", "nhai", "nham", "nhan",
        "nhang", "nhao", "nhap", "nhat", "nhau", "nhay", "nhe", "nhem", "nhen", "nheo", "nhet", "nhi",
        "nhich", "nhien", "nhiet", "nhieu", "nhim", "nhin", "nhinh", "nho", "nhoc", "nhoi", "nhom", "nhon",
        "nhong", "nhop", "nhot", "nhu", "nhuc", "nhui", "nhum", "nhun", "nhung", "nhuom", "nhuong", "o",
        "oc", "oi", "om", "on", "ong", "op", "ot", "pa", "pac", "pai", "pam", "pan", "pang", "pao", "pap",
        "pat", "pau", "pay", "pe", "pen", "pet", "phi", "phich", "phien", "phiet", "phieu", "phim", "phin",
        "phinh", "pho", "phoc", "phoi", "phom", "phon", "phong", "phot", "phu", "phuc", "phui", "phum",
        "phun", "phung", "phuong", "qua", "quac", "quai", "quam", "quan", "quang", "quao", "quap", "quat",
        "quau", "quay", "que", "quen", "queo", "quet", "qui", "quin", "quinh", "quo", "quoc", "quoi", "quon",
        "quong", "quy", "quyen", "quyet", "quyu", "ra", "rac", "rai", "ram", "ran", "rang", "rao", "rap",
        "rat", "rau", "ray", "re", "rem", "ren", "reo", "ret", "ri", "rich", "rien", "riet", "rieu", "rim",
        "rin", "rinh", "ro", "roc", "roi", "rom", "ron", "rong", "rop", "rot", "ru", "ruc", "rui", "rum",
        "run", "rung", "ruoi", "ruoc", "ruom", "ruon", "ruot", "ruong", "ruu", "sa", "sac", "sai", "sam",
        "san", "sang", "sao", "sap", "sat", "sau", "say", "se", "sem", "sen", "seo", "set", "si", "sich",
        "sien", "siet", "sieu", "sim", "sin", "sinh", "so", "soc", "soi", "som", "son", "song", "sop", "sot",
        "su", "suc", "sui", "sum", "sun", "sung", "suoi", "suoc", "suon", "suong", "ta", "tac", "tai",
        "tam", "tan", "tang", "tao", "tap", "tat", "tau", "tay", "te", "tem", "ten", "teo", "tet", "ti",
        "tich", "tien", "tiet", "tieu", "tim", "tin", "tinh", "to", "toc", "toi", "tom", "ton", "tong",
        "top", "tot", "tu", "tuc", "tui", "tum", "tun", "tung", "tuoi", "tuoc", "tuon", "tuot", "tuong",
        "tuy", "tuyen", "tuyet", "thac", "thai", "tham", "than", "thang", "thao", "thap", "that", "thau",
        "thay", "the", "them", "then", "theo", "thet", "thi", "thich", "thien", "thiet", "thieu", "thim",
        "thin", "thinh", "tho", "thoc", "thoi", "thom", "thon", "thong", "thop", "thot", "thu", "thuc",
        "thui", "thum", "thun", "thung", "thuoc", "thuoi", "thuon", "thuong", "thuy", "thuyen", "thuyet",
        "tra", "trac", "trai", "tram", "tran", "trang", "trao", "trap", "trat", "trau", "tray", "tre",
        "trem", "tren", "treo", "tret", "tri", "trich", "trien", "triet", "trieu", "trim", "trin", "trinh",
        "tro", "troc", "troi", "trom", "tron", "trong", "trop", "trot", "tru", "truc", "trui", "trum", "trun",
        "trung", "truoc", "truoi", "truon", "truot", "truong", "truy", "truyen", "u", "uc", "ui", "um",
        "un", "ung", "uoc", "uom", "uon", "uong", "uy", "uyen", "uyet", "va", "vac", "vai", "vam", "van",
        "vang", "vao", "vap", "vat", "vau", "vay", "ve", "vem", "ven", "veo", "vet", "vi", "vich", "vien",
        "viet", "vieu", "vim", "vin", "vinh", "vo", "voc", "voi", "vom", "von", "vong", "vop", "vot", "vu",
        "vuc", "vui", "vum", "vun", "vung", "vuon", "vuot", "vuong", "xa", "xac", "xai", "xam", "xan",
        "xang", "xao", "xap", "xat", "xau", "xay", "xe", "xem", "xen", "xeo", "xet", "xi", "xich", "xien",
        "xiet", "xieu", "xim", "xin", "xinh", "xo", "xoc", "xoi", "xom", "xon", "xong", "xop", "xot", "xu",
        "xuc", "xui", "xum", "xun", "xung", "xuoi", "xuoc", "xuon", "xuot", "xuong", "y", "yem", "yen",
        "yet", "yeu",
    )
}
