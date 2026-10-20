package com.wasimaster.wmkeyboard.core.input.composer

fun interface V7CandidateProvider {
    fun predict(reading: String): List<String>
}

object V7Dictionaries {
    @Volatile
    var provider: V7CandidateProvider? = null
}
