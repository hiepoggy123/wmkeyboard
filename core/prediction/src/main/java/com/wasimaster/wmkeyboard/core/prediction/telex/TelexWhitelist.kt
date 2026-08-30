package com.wasimaster.wmkeyboard.core.prediction.telex

/**
 * Whitelist of common Vietnamese abbreviations, slang, gaming, technology,
 * and English loanwords that should NEVER be autocorrected to other Vietnamese words.
 */
object TelexWhitelist {

    private val WHITELIST = hashSetOf(
        // Vietnamese Abbreviations & Social Slang
        "nyc", "vcl", "clgt", "dm", "dcm", "vcc", "vcd", "vl", "cl", "vc", "đm", "đcm", "đcl",
        "ad", "ae", "sp", "ck", "vk", "fb", "ib", "yt", "gg", "zalo", "pro", "lol", "kpop",
        "vip", "ms", "ko", "k", "dc", "đc", "nt", "cmt", "stt", "snvv", "g9", "tks", "inbox",
        "rep", "check", "unfriend", "block", "tag", "share", "like", "sub", "fl", "follow",
        "stream", "idol", "fandom", "otp", "dảk", "bủh", "bruh", "wibu", "simp", "chằm", "zn",
        "clm", "dmm", "vkl", "đkl", "vlxx", "av", "jav", "cosplay", "fan", "fame", "drama",
        "bóc", "phốt", "nobra", "crush", "match", "tinder", "dating", "call", "mess", "zui",

        // Gaming Terminology
        "afk", "lag", "ping", "bug", "hack", "gank", "buff", "nerf", "meta", "combo", "team",
        "clan", "guild", "room", "slot", "drop", "kill", "farm", "push", "feed", "ks", "solo",
        "dame", "damage", "tank", "heal", "mana", "ulti", "skill", "level", "exp", "quest",
        "event", "server", "item", "top", "bot", "mid", "adc", "sp", "jg", "jung", "rank",
        "auto", "acc", "skin", "hero", "champ", "carry", "cover", "combat", "back", "def",
        "atk", "crit", "stun", "slow", "aoe", "dps", "pvp", "pve", "boss", "mob", "loot",
        "cheat", "mod", "ping", "fps", "hz", "noob", "pro", "mvp", "ggwp", "ez", "glhf",

        // Tech, Web, Dev & Daily English
        "app", "link", "web", "game", "code", "admin", "pass", "test", "demo", "file", "data",
        "wifi", "bluetooth", "sim", "usb", "ram", "cpu", "gpu", "ssd", "hdd", "pc", "laptop",
        "mac", "win", "ios", "android", "apk", "ipa", "root", "jailbreak", "flash", "rom",
        "dev", "log", "run", "fix", "reset", "reboot", "setup", "config", "api", "ui", "ux",
        "ai", "gpt", "bot", "cloud", "mail", "gmail", "drive", "post", "blog", "site",
        "host", "domain", "dns", "ip", "vpn", "proxy", "port", "ssh", "ftp", "html", "css",
        "js", "php", "sql", "git", "hub", "page", "group", "video", "audio", "mp3", "mp4",
        "hd", "fhd", "4k", "full", "free", "sale", "off", "order", "ship", "cod", "bill",
        "card", "atm", "bank", "momo", "zalopay", "vnpay", "shopee", "lazada", "tiki",

        // Conversational English words commonly typed by Vietnamese users
        "hi", "hello", "hey", "bye", "ok", "okay", "okie", "oke", "yes", "no", "yeah", "yep",
        "nope", "thanks", "thank", "plz", "pls", "please", "sorry", "sry", "welcome", "good",
        "nice", "cool", "great", "fine", "happy", "sad", "love", "hate", "omg", "btw", "idk",
        "imo", "tbh", "fyi", "asap", "rip", "diy", "faq", "nsfw", "hot", "trend", "viral",
        "vlog", "review", "unbox", "show", "live", "tour", "trip", "checkin", "selfie",

        // Laban Key specific whitelisted words
        "sex", "goo", "ny"
    )

    /**
     * Returns true if the word is in the whitelist and should NOT be autocorrected.
     */
    fun isWhitelisted(word: String?): Boolean {
        if (word.isNullOrBlank()) return false
        return WHITELIST.contains(word.trim().lowercase())
    }
}
