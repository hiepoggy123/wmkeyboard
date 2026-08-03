package com.wasimaster.wmkeyboard.app.updates

/** Which of Play's two update flows to run. */
internal enum class UpdateFlow {
    /** Downloads in the background; the app keeps working until the user installs it. */
    FLEXIBLE,

    /** Play takes the screen and restarts the app itself. For releases that must land. */
    IMMEDIATE,
}

/**
 * What to do about an available update: which flow, and whether to open it
 * without being asked.
 *
 * [flow] is null when neither flow is allowed for this release, which is Play's
 * way of saying "do not offer this one".
 */
internal data class UpdatePlan(val flow: UpdateFlow?, val automatic: Boolean)

/**
 * When to prompt, and how hard.
 *
 * Play hands back two numbers with every available update: the priority the
 * release was published with (0-5, set through the Publishing API — it cannot
 * be changed after the release goes out) and how many days the device has been
 * behind. This object turns those into one decision, away from any Play type,
 * so the rules are readable in one screen and testable without a device.
 *
 * The shape of the rules is set by what this app is. A keyboard is not a thing
 * people open; it is a thing they type with, and the only time this code runs
 * is the rare visit to the settings app. So the default is deliberately quiet —
 * a card the user can ignore — and the loud paths are reserved for releases the
 * maintainer explicitly marked urgent, or installs that have gone stale enough
 * that the quiet card plainly is not working.
 */
internal object UpdatePolicy {

    /**
     * Priority at or above which a release takes the blocking flow. Google's
     * own guidance uses 4 for "high priority"; publishing at 5 means the same
     * thing here.
     */
    const val IMMEDIATE_PRIORITY = 4

    /** Priority at or above which the quiet card becomes a Play dialog on the next visit. */
    const val PROMPT_PRIORITY = 2

    /** Days behind after which an ordinary release still earns a prompt. */
    const val PROMPT_STALENESS_DAYS = 7

    /**
     * Days behind after which any release takes the blocking flow. An install
     * three months behind has ignored every gentler signal, and by then the
     * gap is likely to be the reason something is broken.
     */
    const val IMMEDIATE_STALENESS_DAYS = 90

    /** How long "Not now" keeps one version quiet. */
    const val SNOOZE_DAYS = 7

    /** [SNOOZE_DAYS] in milliseconds, for comparing against a stored timestamp. */
    const val SNOOZE_MILLIS = SNOOZE_DAYS * 24L * 60L * 60L * 1000L

    /**
     * Decides what to do about one available update.
     *
     * @param priority the release's `updatePriority`, 0-5.
     * @param stalenessDays `clientVersionStalenessDays`, or -1 when Play does
     *   not report it (which it does not for a release published minutes ago).
     * @param immediateAllowed `isUpdateTypeAllowed(IMMEDIATE)`.
     * @param flexibleAllowed `isUpdateTypeAllowed(FLEXIBLE)`.
     * @param snoozed whether the user already said "Not now" to this version.
     * @param autoPrompt the user's own setting; false means never open a Play
     *   dialog on our own initiative, only draw the card.
     */
    @Suppress("ReturnCount")
    fun plan(
        priority: Int,
        stalenessDays: Int,
        immediateAllowed: Boolean,
        flexibleAllowed: Boolean,
        snoozed: Boolean,
        autoPrompt: Boolean,
    ): UpdatePlan {
        val urgent = priority >= IMMEDIATE_PRIORITY || stalenessDays >= IMMEDIATE_STALENESS_DAYS

        // Which flow, given what Play permits. An urgent release wants the
        // blocking one; anything else prefers the background download, because
        // it costs the user nothing to say yes to.
        val flow = when {
            urgent && immediateAllowed -> UpdateFlow.IMMEDIATE
            flexibleAllowed -> UpdateFlow.FLEXIBLE
            immediateAllowed -> UpdateFlow.IMMEDIATE
            else -> null
        } ?: return UpdatePlan(flow = null, automatic = false)

        // An urgent release that got the blocking flow ignores both the snooze
        // and the setting: those exist to stop nagging about ordinary
        // releases, and this one is not one. It is also the only case where
        // opening the flow unasked is fair — Play's full-screen page is a
        // prompt in its own right, with its own way out.
        if (urgent && flow == UpdateFlow.IMMEDIATE) return UpdatePlan(flow, automatic = true)

        if (snoozed || !autoPrompt) return UpdatePlan(flow, automatic = false)

        // Everything else: a Play dialog once the release is either flagged as
        // worth pushing or old enough that the quiet card has clearly been
        // ignored. Saying no to that dialog snoozes the version, so the user
        // sees it at most once a week.
        val worthPrompting = priority >= PROMPT_PRIORITY || stalenessDays >= PROMPT_STALENESS_DAYS
        return UpdatePlan(flow, automatic = worthPrompting)
    }
}
