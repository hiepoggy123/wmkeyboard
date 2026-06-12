package com.wasimaster.wmkeyboard.core.prediction

/**
 * Physical adjacency on the QWERTY layout, used to weight typo
 * corrections: substituting a key with one of its neighbours is a likely
 * fat-finger slip, substituting a distant key is probably a different
 * word entirely.
 */
object KeyProximity {

    private val neighbors: Map<Char, String> = mapOf(
        'q' to "was",
        'w' to "qeasd",
        'e' to "wrsdf",
        'r' to "etdfg",
        't' to "ryfgh",
        'y' to "tughj",
        'u' to "yihjk",
        'i' to "uojkl",
        'o' to "ipkl",
        'p' to "ol",
        'a' to "qwszx",
        's' to "qweadzxc",
        'd' to "wersfxcv",
        'f' to "ertdgcvb",
        'g' to "rtyfhvbn",
        'h' to "tyugjbnm",
        'j' to "yuihknm",
        'k' to "uiojlm",
        'l' to "iopk",
        'z' to "asx",
        'x' to "asdzc",
        'c' to "sdfxv",
        'v' to "dfgcb",
        'b' to "fghvn",
        'n' to "ghbjm",
        'm' to "hjnk",
    )

    fun areAdjacent(a: Char, b: Char): Boolean = neighbors[a]?.contains(b) == true
}
