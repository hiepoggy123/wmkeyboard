package com.wasimaster.wmkeyboard.core.emoji

/**
 * One labelled block of text art. The label is a section header inside the
 * panel's grid, so a long catalog stays skimmable instead of reading as one
 * undifferentiated wall of faces.
 */
data class TextArtGroup(val name: String, val items: List<String>)

/**
 * Kaomoji (Japanese-style emoticons, drawn with CJK and box-drawing glyphs)
 * and Western ASCII emoticons, for the two optional emoji-panel tabs.
 *
 * Plain data, compiled in rather than loaded from assets: the whole thing is
 * a few kilobytes of strings, so there is nothing to gain from the async
 * asset load the emoji catalog needs — and no parsing step to get wrong on
 * entries that are themselves made of tabs-adjacent punctuation.
 *
 * Entries are deliberately drawn from glyphs Android's bundled fonts cover
 * (Noto Sans CJK / Symbols); anything needing an exotic font would show as
 * tofu on most devices.
 */
object TextArt {

    /** Japanese-style kaomoji, grouped by mood. */
    val kaomoji: List<TextArtGroup> = listOf(
        TextArtGroup(
            "Happy",
            listOf(
                "(◕‿◕)", "(^_^)", "(＾▽＾)", "(≧▽≦)", "(*^▽^*)", "(✿◠‿◠)",
                "ヽ(・∀・)ﾉ", "(｡◕‿◕｡)", "٩(◕‿◕)۶", "(☆▽☆)", "\\(^ヮ^)/",
                "(´｡• ᵕ •｡`)", "(＾ｖ＾)", "(*≧ω≦*)", "(￣▽￣)ノ", "( ˆ▽ˆ )",
                "ヽ(＾Д＾)ﾉ", "(๑˃ᴗ˂)ﻭ",
            ),
        ),
        TextArtGroup(
            "Love",
            listOf(
                "(♡°▽°♡)", "(´,,•ω•,,)♡", "(づ￣ ³￣)づ", "(｡♥‿♥｡)", "(*♡∀♡)",
                "♡(ᐢ ᴥ ᐢ)♡", "(ෆ˙ᵕ˙ෆ)♡", "(♥ω♥*)", "(´ε｀ )♡", "(っ˘з˘)っ",
                "(*˘︶˘*).｡.:*♡", "♡＾▽＾♡", "(⁄ ⁄•⁄ω⁄•⁄ ⁄)", "( ˘⌣˘)♡(˘⌣˘ )",
                "ヽ(♡‿♡)ノ", "(*¯ ³¯*)♡",
            ),
        ),
        TextArtGroup(
            "Sad",
            listOf(
                "(╥﹏╥)", "(T_T)", "(ಥ﹏ಥ)", "(´;ω;`)", "｡ﾟ(ﾟ´ω`ﾟ)ﾟ｡",
                "(っ- ‸ - ς)", "(∩︵∩)", "( ˘︹˘ )", "(._.)", "(个_个)",
                "(ノ_<。)", "( ｉ _ ｉ )", "(っ˘̩╭╮˘̩)っ", "｡ﾟ･ (>﹏<) ･ﾟ｡",
                "(⋟﹏⋞)", "(*︶︶*)",
            ),
        ),
        TextArtGroup(
            "Angry",
            listOf(
                "(＃`Д´)", "ლ(ಠ益ಠ)ლ", "(ﾉಥ益ಥ)ﾉ", "ヽ(`Д´)ﾉ", "(҂ `з´ )",
                "(︶︹︺)", "(¬､¬)", "ᕙ(⇀‸↼‵‵)ᕗ", "(＃＞＜)", "(♯｀∧´)",
                "凸(￣ヘ￣)", "٩(╬ʘ益ʘ╬)۶", "(ↀДↀ)⁼³₌₃", "(҂◡_◡)",
            ),
        ),
        TextArtGroup(
            "Table flip",
            listOf(
                "(╯°□°）╯︵ ┻━┻", "(ノಠ益ಠ)ノ彡┻━┻", "(ノ ゜Д゜)ノ ︵ ┻━┻",
                "┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻", "┻━┻ミヽ(ಠ益ಠ)ﾉ彡┻━┻",
                "┬─┬ ノ( ゜-゜ノ)", "┬──┬ ¯\\_(ツ)", "(ノ^_^)ノ ┻━┻",
            ),
        ),
        TextArtGroup(
            "Confused",
            listOf(
                "¯\\_(ツ)_/¯", "(・_・?)", "(⊙_⊙)?", "┐(´д`)┌", "╮(￣ω￣;)╭",
                "(´･_･`)", "(・・？)", "(◎_◎;)", "ヽ(ー_ー )ノ", "(・_・;)",
                "(°ロ°) !", "๑(•̀_•́)๑", "(⊙▂⊙)", "(・∧‐)ゞ",
            ),
        ),
        TextArtGroup(
            "Surprised",
            listOf(
                "(⊙_⊙)", "(°ロ°)", "Σ(°ロ°)", "(ﾟдﾟ；)", "Σ(ﾟДﾟ)", "(☉_☉)",
                "w(°ｏ°)w", "(๑°ㅁ°๑)", "( ﾟoﾟ)", "Σ(っ °Д °;)っ", "(＃￣0￣)",
                "(°□°）", "( ゜Д゜)", "(*ﾟﾛﾟ)",
            ),
        ),
        TextArtGroup(
            "Cool",
            listOf(
                "(⌐■_■)", "( •_•)>⌐■-■", "( ͡° ͜ʖ ͡°)", "ಠ_ಠ", "(¬‿¬)",
                "( ͡~ ͜ʖ ͡°)", "ᕦ(ò_óˇ)ᕤ", "(҂⌣̀_⌣́)", "(￣ω￣;)", "(￣ー￣)b",
                "( ˙▿˙ )", "(ㆆ_ㆆ)", "(⌐▨_▨)", "(•̀ᴗ•́)و",
            ),
        ),
        TextArtGroup(
            "Cute",
            listOf(
                "(´• ω •`)", "(｡•́‿•̀｡)", "(๑˘︶˘๑)", "( ´ ▽ ` )", "(◍•ᴗ•◍)",
                "(*ᴗ͈ˬᴗ͈)ꕤ", "(っ˘ω˘ς )", "(´｡• ω •｡`)", "(*・ω・)", "(⁀ᗢ⁀)",
                "( ˘ᵕ˘ )", "(๑>ᴗ<๑)", "(≧ᴗ≦)", "(っ´ω`c)",
            ),
        ),
        TextArtGroup(
            "Animals",
            listOf(
                "(=^･ω･^=)", "ฅ^•ﻌ•^ฅ", "ʕ•ᴥ•ʔ", "(・(ｴ)・)", "ʕっ•ᴥ•ʔっ",
                "/ᐠ｡ꞈ｡ᐟ\\", "ᓚᘏᗢ", "(^･ω･^)", "ʕ￫ᴥ￩ʔ", "(=｀ω´=)",
                "(＾• ω •＾)", "੯•́ᴥ•̀੭", "(´･ω･`)", "( ᴼ̈ )", "＼(=^‥^)/",
            ),
        ),
        TextArtGroup(
            "Actions",
            listOf(
                "(づ｡◕‿‿◕｡)づ", "ᕕ( ᐛ )ᕗ", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "٩(˘◡˘)۶",
                "(~‾▿‾)~", "ε=ε=ε=┌(;*´Д`)ﾉ", "(ง'̀-'́)ง", "┏(＾0＾)┛",
                "( ･ω･)つ⊂(･ω･ )", "(ﾉ*･ω･)ﾉ", "＼(٥⁀▽⁀ )／", "(っ▀¯▀)つ",
                "ヽ(๑◕ϖ◕๑)ﾉ", "( •̀ ω •́ )✧",
            ),
        ),
        TextArtGroup(
            "Sleepy",
            listOf(
                "(－_－) zzZ", "(=_=)", "(¬_¬)", "(´-ω-`)", "( ˘ω˘ )ｽﾔｧ",
                "(。-ω-)zzz", "(￣o￣) zzZZ", "(∪｡∪)｡｡｡zzz", "(-, – )…zzzZZ",
                "(´〜｀*) zzz", "(－ω－) zzZ",
            ),
        ),
        TextArtGroup(
            "Greetings",
            listOf(
                "(^_^)/", "ヾ(＾∇＾)", "(・∀・)ノ", "ヾ(・ω・*)ﾉ", "( ´ ▽ ` )ﾉ",
                "(=^-ω-^=)", "ノ( º _ ºノ)", "(*ﾟｰﾟ)ﾉ", "ヾ(*'▽'*)", "(¬_¬)ﾉ",
                "( ' ')ノ", "ヽ(•‿•)ノ",
            ),
        ),
        TextArtGroup(
            "Party",
            listOf(
                "♪(´▽｀)", "ヽ(´▽`)/", "♪~ ᕕ(ᐛ)ᕗ", "ヾ(⌐■_■)ノ♪",
                "ヽ(o´∀`)ﾉ♪♬", "⌒*(ﾟ∀ﾟ)*⌒", "(ノ^o^)ノ", "＼(^o^)／",
                "☆*:.｡.o(≧▽≦)o.｡.:*☆", "(ノ ˙∀˙)ノ ⌒ ♪", "٩(♡ε♡)۶",
            ),
        ),
        TextArtGroup(
            "Apologies",
            listOf(
                "m(_ _)m", "(シ_ _)シ", "orz", "OTL", "(-\"-;)", "(_ _|||)",
                "(*_ _)人", "٩(๑´0`๑)۶", "(ﾟдﾟ)ゞ", "(；一_一)",
            ),
        ),
    )

    /** Western ASCII emoticons, grouped by mood. */
    val emoticons: List<TextArtGroup> = listOf(
        TextArtGroup(
            "Happy",
            listOf(
                ":)", ":-)", ":D", ":-D", "=)", "=D", ":]", ":-]", "(:", "^_^",
                "^^", "c:", "n_n", ":3", "=]", "(^^)", ":')", "*\\o/*",
            ),
        ),
        TextArtGroup(
            "Sad",
            listOf(
                ":(", ":-(", ":'(", "D:", "D-:", ";(", "=(", ":<", ":c", ":[",
                "T_T", ";_;", "Q_Q", "v_v", ":-<", "=/",
            ),
        ),
        TextArtGroup(
            "Playful",
            listOf(
                ";)", ";-)", ":P", ":-P", ":p", ";P", "xP", ":b", ":-b", "xD",
                "XD", "x3", ">:D", ":->", "^_-", ";D",
            ),
        ),
        TextArtGroup(
            "Love",
            listOf(
                "<3", "</3", ":*", ":-*", "(L)", ":x", ":-x", "3<", "@}->--",
                "(>^_^)>", "<(^_^<)",
            ),
        ),
        TextArtGroup(
            "Surprised",
            listOf(
                ":O", ":-O", "=O", ":0", "o_O", "O_o", "O_O", "o.O", "8O",
                ":-o", ">_<", "@_@",
            ),
        ),
        TextArtGroup(
            "Neutral",
            listOf(
                ":|", ":-|", "-_-", "-.-", "¬_¬", ":/", ":-/", ":\\", "=|",
                "._.", "•_•", "u_u",
            ),
        ),
        TextArtGroup(
            "Cool",
            listOf(
                "B)", "B-)", "8)", "8-)", "O:)", "O:-)", ">:)", ">:-)", ">:(",
                ":X", ":-X", ":\$", "%)", "%-)",
            ),
        ),
        TextArtGroup(
            "Figures",
            listOf(
                "\\o/", "o/", "\\o", "/o\\", "\\m/", "<(\")", "=^.^=", "(y)",
                "(n)", "d-_-b", "(-_-)zzz", "[-o<",
            ),
        ),
    )
}
