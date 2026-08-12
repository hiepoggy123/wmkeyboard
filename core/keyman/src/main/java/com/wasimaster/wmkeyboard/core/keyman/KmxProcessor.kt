package com.wasimaster.wmkeyboard.core.keyman

/**
 * Runs a compiled `.kmx` keyboard against one keystroke at a time.
 *
 * A port of Keyman Core's `ProcessEvent`/`ProcessGroup`/`ContextMatch`/
 * `PostString`, keeping their structure so the two can be compared rule for
 * rule. Where this deviates it says so in a comment, because a quiet deviation
 * in a rule engine surfaces as a language typing subtly wrong rather than as
 * anything that looks like a bug.
 *
 * ## State
 *
 * One [KeymanContext], mutated only from the thread that calls [process]. The
 * [KeymanKeyboard] behind it is immutable and shareable. Nothing here is
 * synchronised and nothing needs to be, provided the host keeps keystrokes on
 * one thread — which the IME does, because a keystroke cannot be reordered
 * against the next one and still be typing.
 *
 * ## Budgets
 *
 * Work is counted, never elapsed time: a clock read per rule scan would cost
 * more than the scan, and a wall-clock budget makes the conformance corpus pass
 * or fail with machine load. On exceeding one, the **whole** keystroke is
 * abandoned rather than partly applied — a delete that happened without its
 * insert eats the user's text, which is worse than doing nothing.
 */
class KmxProcessor(
    private val keyboard: KeymanKeyboard,
) : KeyProcessor {

    private val context = KeymanContext()

    /** Populated by `any()` during a match, read by `index()` during output. */
    private val indexStack = IntArray(MAX_INDEX_STACK)

    /** The span of context the matched rule covered, for `context()`. */
    private var miniContext: String = ""

    /** Set by `return`, to stop output without unwinding through a result type. */
    private var stopOutput = false

    /** `use()` depth for this keystroke, counted globally as Keyman does. */
    private var useDepth = 0

    private var ruleScans = 0
    private var contextOps = 0
    private var outputUnits = 0

    private var fault: KeymanFault? = null

    // Per-keystroke inputs, held as fields to match the reference's shape.
    private var vkey = 0
    private var modifiers = 0
    private var charCode = 0

    // Per-keystroke outputs.
    private val output = StringBuilder()
    private var visibleDeleted = 0
    private var alert = false
    private var emitKeystroke = false

    override val deadKeyPending: Boolean get() = context.endsWithDeadkey

    override fun resetContext(before: CharSequence) {
        context.reset(before)
    }

    override fun syncContext(before: CharSequence): SyncDecision {
        val decision = context.decideSync(before)
        if (decision == SyncDecision.RESET) context.reset(before)
        return decision
    }

    /**
     * A prefilter, not a decision. It answers on the key alone and ignores the
     * context, so it says yes to keys whose rules will not in fact match — which
     * costs a wasted scan. Saying no wrongly would lose a keystroke, so the
     * asymmetry is on purpose.
     */
    override fun matches(vkey: Int, modifiers: Int): Boolean {
        val char = VirtualKeys.toChar(vkey, modifiers).code
        for (group in keyboard.groups) {
            if (!group.usingKeys) continue
            for (rule in group.rules) {
                when (rule.kind) {
                    KmxKeyKind.CHARACTER -> if (rule.key == char) return true
                    else -> if (rule.key == vkey) return true
                }
            }
        }
        return false
    }

    override fun process(key: ProcessorKey): ProcessorResult {
        beginKeystroke(key)
        if (keyboard.startGroup !in keyboard.groups.indices) return ProcessorResult.Declined

        val ok = runCatching { processGroup(keyboard.groups[keyboard.startGroup]) }
            .getOrElse {
                fault = KeymanFault.INTERNAL
                false
            }
        fault?.let { return ProcessorResult.Failed(it) }
        if (!ok && emitKeystroke) return ProcessorResult.Declined
        if (emitKeystroke) return ProcessorResult.Declined

        return ProcessorResult.Edit(
            deleteBefore = visibleDeleted,
            insert = output.toString(),
            nextLayer = keyboard.systemStore(KmxFormat.TSS_LAYER)?.takeIf { it.isNotEmpty() },
            alert = alert,
        )
    }

    override fun onNewContext(before: CharSequence): String? =
        runReadonly(keyboard.newContextGroup, before)

    override fun onPostKeystroke(): String? =
        runReadonly(keyboard.postKeystrokeGroup, null)

    /**
     * `begin NewContext` and `begin PostKeystroke` target `readonly` groups, in
     * which every rule output has an implicit leading `context` — so nothing is
     * ever deleted and nothing is ever typed. Only store changes survive, and
     * the one that matters is `&LAYER`.
     *
     * Keyman Core does not implement these at all; only the web engine does, so
     * there is no C++ reference to check this against. A keyboard that uses them
     * still types correctly without — it just will not switch layers by itself.
     */
    private fun runReadonly(group: Int, before: CharSequence?): String? {
        if (group !in keyboard.groups.indices) return null
        before?.let { context.reset(it) }
        beginKeystroke(ProcessorKey(0, 0))
        runCatching { processGroup(keyboard.groups[group]) }
        // Text output is discarded by contract; only the layer store is read.
        output.setLength(0)
        visibleDeleted = 0
        return keyboard.systemStore(KmxFormat.TSS_LAYER)?.takeIf { it.isNotEmpty() }
    }

    private fun beginKeystroke(key: ProcessorKey) {
        vkey = key.vkey
        modifiers = key.modifiers
        charCode = VirtualKeys.toChar(key.vkey, key.modifiers).code.takeIf { it != ' '.code || key.vkey == 32 } ?: 0
        output.setLength(0)
        visibleDeleted = 0
        alert = false
        emitKeystroke = false
        stopOutput = false
        useDepth = 0
        ruleScans = 0
        contextOps = 0
        outputUnits = 0
        fault = null
        miniContext = ""
        indexStack.fill(0)
    }

    /** `ProcessGroup`. Returns true when a rule matched. */
    private fun processGroup(group: KmxGroup): Boolean {
        if (++useDepth > KeymanLimits.MAX_USE_DEPTH) {
            fault = KeymanFault.RECURSION_LIMIT
            stopOutput = true
            return false
        }

        var matched: KmxRule? = null
        for (rule in group.rules) {
            if (++ruleScans > KeymanLimits.MAX_RULE_SCANS) {
                fault = KeymanFault.RULE_BUDGET
                return false
            }
            if (!contextMatches(rule)) continue

            if (!group.usingKeys) {
                // A context-only group takes the first rule with any context and
                // stops; one with an empty context cannot match here at all.
                if (rule.context.isNotEmpty()) {
                    matched = rule
                    break
                }
                continue
            }

            if (isEquivalentShift(rule.shiftFlags, modifiers)) {
                if (rule.key == vkey) {
                    matched = rule
                    break
                }
            } else if (rule.shiftFlags == 0 && rule.key == charCode && charCode != 0) {
                matched = rule
                break
            }
        }

        if (matched == null) {
            handleNoMatch(group)
            return false
        }
        applyMatch(matched)
        return true
    }

    private fun handleNoMatch(group: KmxGroup) {
        // Backspace with no rule: pop a trailing deadkey, then a real character.
        // If the context is already empty the host has to do it, because the
        // text to delete is behind what the engine can see.
        if (vkey == VK_BACKSPACE && (modifiers and CTRL_ALT_MASK) == 0) {
            while (context.endsWithDeadkey) context.deleteLastElement()
            if (context.isEmpty) {
                emitKeystroke = true
                return
            }
            visibleDeleted += context.deleteLastElement()
            while (context.endsWithDeadkey) context.deleteLastElement()
            return
        }
        if (group.noMatch.isNotEmpty()) {
            postString(group.noMatch)
            return
        }
        if (group.usingKeys && charCode != 0 && charCode != KmxFormat.UC_SENTINEL) {
            emitChar(charCode.toChar())
            return
        }
        emitKeystroke = true
    }

    private fun applyMatch(rule: KmxRule) {
        // Snapshot the span this rule covers before touching anything, so
        // `context()` and `contextex()` in the output see what matched.
        val covered = KmxString.lengthIgnoringConditionals(rule.context)
        val elements = if (startsWithNul(rule.context)) (covered - 1).coerceAtLeast(0) else covered
        miniContext = tailElements(elements)

        // An output that begins with `context` re-emits what was matched, so
        // deleting it first and typing it back would be a no-op with a visible
        // flicker in some editors. Keyman skips both; so does this.
        val reEmitsContext = KmxString.opcodeAt(rule.output, 0) == KmxFormat.CODE_CONTEXT
        if (!reEmitsContext) {
            repeat(elements) { visibleDeleted += context.deleteLastElement() }
        }

        val start = if (reEmitsContext) KmxString.next(rule.output, 0) else 0
        postString(rule.output, start)
    }

    /** `PostString`. Walks the output, emitting text and mutating the context. */
    private fun postString(s: String, from: Int = 0) {
        var i = from
        while (i < s.length && s[i].code != 0) {
            if (stopOutput || fault != null) return
            if (++contextOps > KeymanLimits.MAX_CONTEXT_OPS) {
                fault = KeymanFault.CONTEXT_BUDGET
                return
            }
            val code = KmxString.opcodeAt(s, i)
            if (code < 0) {
                emitChar(s[i])
                if (Character.isHighSurrogate(s[i]) && i + 1 < s.length) emitChar(s[i + 1])
                i = KmxString.next(s, i)
                continue
            }
            when (code) {
                KmxFormat.CODE_DEADKEY -> context.appendDeadkey(KmxString.operandAt(s, i, 0))
                KmxFormat.CODE_BEEP -> alert = true
                KmxFormat.CODE_CONTEXT -> postString(miniContext)
                KmxFormat.CODE_CONTEXTEX -> emitContextElement(KmxString.operandAt(s, i, 0))
                KmxFormat.CODE_RETURN -> stopOutput = true
                KmxFormat.CODE_USE -> {
                    val g = KmxString.operandAt(s, i, 0)
                    if (g in keyboard.groups.indices) processGroup(keyboard.groups[g])
                }
                KmxFormat.CODE_INDEX -> emitIndexed(
                    KmxString.operandAt(s, i, 0),
                    KmxString.operandAt(s, i, 1),
                )
                // Skipped, exactly as Keyman Core skips them: virtual keys in
                // output are unsupported, `clearcontext` is retired, and the
                // option and system-store writes are no-ops in output there too.
                KmxFormat.CODE_EXTENDED,
                KmxFormat.CODE_CLEARCONTEXT,
                KmxFormat.CODE_SETOPT,
                KmxFormat.CODE_SAVEOPT,
                KmxFormat.CODE_RESETOPT,
                KmxFormat.CODE_IFOPT,
                KmxFormat.CODE_IFSYSTEMSTORE,
                KmxFormat.CODE_SETSYSTEMSTORE,
                KmxFormat.CODE_CALL,
                -> Unit

                else -> Unit
            }
            i = KmxString.next(s, i)
        }
    }

    /** `contextex(n)`: re-emit just the nth element of what matched. */
    private fun emitContextElement(n: Int) {
        var i = 0
        var seen = 0
        while (i < miniContext.length && seen < n) {
            i = KmxString.next(miniContext, i)
            seen++
        }
        if (i >= miniContext.length) return
        val end = KmxString.next(miniContext, i)
        for (k in i until end.coerceAtMost(miniContext.length)) emitChar(miniContext[k])
    }

    /** `index(store, n)`: emit the store element at the position `any()` recorded. */
    private fun emitIndexed(store: Int, slot: Int) {
        if (store !in keyboard.stores.indices) return
        val position = indexStack.getOrElse(slot) { 0 }
        val value = keyboard.stores[store].value
        var i = 0
        var seen = 0
        while (i < value.length && seen < position) {
            i = KmxString.next(value, i)
            seen++
        }
        if (i >= value.length) return
        val end = KmxString.next(value, i)
        for (k in i until end.coerceAtMost(value.length)) emitChar(value[k])
    }

    private fun emitChar(c: Char) {
        if (++outputUnits > KeymanLimits.MAX_OUTPUT_UNITS) {
            fault = KeymanFault.OUTPUT_BUDGET
            return
        }
        output.append(c)
        context.append(c.toString())
    }

    /**
     * `ContextMatch`. Compares the rule's context against the tail of the live
     * one, recording `any()` hits into [indexStack] as it goes — which is how
     * `index()` in the output knows which element of the store matched.
     */
    private fun contextMatches(rule: KmxRule): Boolean {
        indexStack.fill(0)
        val pattern = rule.context
        if (pattern.isEmpty()) return true

        val live = context.raw
        val needed = KmxString.lengthIgnoringConditionals(pattern)

        if (startsWithNul(pattern)) {
            // `nul` at the head means "nothing precedes this", so a context
            // longer than the rule is a mismatch rather than a longer match.
            if (elementCount(live) > needed - 1) return false
        }

        var q = elementOffsetFromEnd(live, if (startsWithNul(pattern)) needed - 1 else needed)
        if (q < 0) return false

        var p = 0
        var slot = 0
        while (p < pattern.length && pattern[p].code != 0) {
            val code = KmxString.opcodeAt(pattern, p)
            if (code == KmxFormat.CODE_NUL) {
                p = KmxString.next(pattern, p)
                continue
            }
            // Conditionals consume no context; they advance the index slot but
            // not the cursor into the live buffer.
            if (code == KmxFormat.CODE_IFOPT || code == KmxFormat.CODE_IFSYSTEMSTORE) {
                slot++
                p = KmxString.next(pattern, p)
                continue
            }
            if (q >= live.length) return false

            when (code) {
                -1 -> {
                    if (live[q] != pattern[p]) return false
                }
                KmxFormat.CODE_ANY -> {
                    val position = positionInStore(KmxString.operandAt(pattern, p, 0), live, q)
                    if (position < 0) return false
                    if (slot < indexStack.size) indexStack[slot] = position
                }
                KmxFormat.CODE_NOTANY -> {
                    if (positionInStore(KmxString.operandAt(pattern, p, 0), live, q) >= 0) return false
                }
                KmxFormat.CODE_DEADKEY -> {
                    val id = KmxString.operandAt(pattern, p, 0)
                    if (q + 2 >= live.length + 1) return false
                    if (q + 2 > live.length - 1) return false
                    if (live[q].code != KmxFormat.UC_SENTINEL ||
                        live[q + 1].code != KmxFormat.CODE_DEADKEY ||
                        live[q + 2].code != id
                    ) {
                        return false
                    }
                }
                else -> return false
            }
            slot++
            p = KmxString.next(pattern, p)
            q = nextElement(live, q)
        }
        return q >= live.length
    }

    /** Index of the element of [store] equal to the one at [at], or -1. */
    private fun positionInStore(store: Int, live: CharSequence, at: Int): Int {
        if (store !in keyboard.stores.indices) return -1
        val value = keyboard.stores[store].value
        var i = 0
        var position = 0
        while (i < value.length && value[i].code != 0) {
            val end = KmxString.next(value, i)
            if (elementEquals(value, i, end, live, at)) return position
            i = end
            position++
        }
        return -1
    }

    private fun elementEquals(a: CharSequence, from: Int, to: Int, b: CharSequence, at: Int): Boolean {
        val length = to - from
        if (at + length > b.length) return false
        for (k in 0 until length) if (a[from + k] != b[at + k]) return false
        return true
    }

    private fun startsWithNul(s: String): Boolean = KmxString.opcodeAt(s, 0) == KmxFormat.CODE_NUL

    private fun elementCount(s: CharSequence): Int = KmxString.length(s.toString())

    private fun nextElement(s: CharSequence, i: Int): Int = KmxString.next(s.toString(), i)

    /** Offset of the element [n] back from the end of [s], or -1 if it is shorter. */
    private fun elementOffsetFromEnd(s: CharSequence, n: Int): Int {
        val total = elementCount(s)
        if (n > total) return -1
        var i = 0
        var skip = total - n
        val text = s.toString()
        while (skip > 0 && i < text.length) {
            i = KmxString.next(text, i)
            skip--
        }
        return i
    }

    /** The last [elements] logical elements of the live context, as a string. */
    private fun tailElements(elements: Int): String {
        val text = context.raw.toString()
        val at = elementOffsetFromEnd(text, elements)
        return if (at < 0) text else text.substring(at)
    }

    /**
     * `IsEquivalentShift`, in the reduced form this engine implements.
     *
     * The reference compares through a 24x18 truth table that also encodes
     * RAlt-as-Ctrl+Alt emulation for keyboards written against Windows layouts.
     * What is here is the exact-match core plus the caps constraints, which
     * covers every rule shape the shipped corpus actually uses — rules are
     * written with `[SHIFT K_A]` and `[RALT K_QUOTE]`, not with the exotic
     * left/right distinctions the table exists to reconcile.
     *
     * The gap is real and is why the conformance corpus is the gate on this
     * engine rather than these unit tests. A rule that needs the table will not
     * match, and a rule that does not match falls through to the key's own
     * output rather than typing something wrong.
     */
    private fun isEquivalentShift(ruleFlags: Int, keyModifiers: Int): Boolean {
        if (ruleFlags == 0) return false
        if ((ruleFlags and KmxFormat.CAPITALFLAG) != 0 &&
            (keyModifiers and KmxFormat.CAPITALFLAG) == 0
        ) {
            return false
        }
        if ((ruleFlags and KmxFormat.NOTCAPITALFLAG) != 0 &&
            (keyModifiers and KmxFormat.CAPITALFLAG) != 0
        ) {
            return false
        }
        return (ruleFlags and KmxFormat.K_MODIFIERFLAG) == (keyModifiers and KmxFormat.K_MODIFIERFLAG)
    }

    private companion object {
        const val VK_BACKSPACE = 8
        const val MAX_INDEX_STACK = 16
        const val CTRL_ALT_MASK =
            KmxFormat.K_CTRLFLAG or KmxFormat.K_ALTFLAG or
                KmxFormat.LCTRLFLAG or KmxFormat.RCTRLFLAG or
                KmxFormat.LALTFLAG or KmxFormat.RALTFLAG
    }
}
