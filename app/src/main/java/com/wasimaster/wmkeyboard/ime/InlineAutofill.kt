package com.wasimaster.wmkeyboard.ime

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi

/**
 * Inline autofill: the chips a password manager offers ("wasi@example.com",
 * "Saved password") drawn inside the suggestion strip instead of a separate
 * dropdown.
 *
 * The keyboard does not build these views and never sees their contents.
 * It publishes a *presentation spec* — how large a chip may be and which
 * colours to use — the autofill service renders each suggestion remotely,
 * and what comes back is an opaque view that only the manager can populate.
 * That is the whole security model of the API: credentials are never handed
 * to the IME, which is exactly what you want from a keyboard.
 *
 * Android 11 (API 30) and up. Below that, the platform never calls any of
 * this and the strip behaves as it always did.
 */
object InlineAutofill {

    /** The API exists from Android 11 on. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Chips are sized to the strip, so they line up with word suggestions. */
    private const val MIN_CHIP_WIDTH_PX = 100
    private const val MAX_CHIPS = 6

    /**
     * The request handed to the autofill service, describing how many chips
     * the strip will take and how big each may be.
     *
     * [stripHeightPx] is the strip's real height so a chip cannot push the
     * keyboard around.
     *
     * Deliberately no colour styling. The keyboard's theme is resolved in
     * Compose and is not reachable from the service, so any colours set here
     * would be a guess — and a wrong guess renders someone's credentials
     * unreadable. The manager's own chip styling is always legible, which
     * matters more than matching the keys.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun request(
        uiExtras: Bundle,
        stripHeightPx: Int,
        maxWidthPx: Int,
    ): InlineSuggestionsRequest {
        val style = UiVersions.newStylesBuilder()
            .addStyle(InlineSuggestionUi.newStyleBuilder().build())
            .build()

        val spec = InlinePresentationSpec
            .Builder(
                Size(MIN_CHIP_WIDTH_PX, stripHeightPx),
                Size(maxWidthPx, stripHeightPx),
            )
            .setStyle(style)
            .build()

        return InlineSuggestionsRequest.Builder(List(MAX_CHIPS) { spec })
            .setMaxSuggestionCount(MAX_CHIPS)
            .setExtras(uiExtras)
            .build()
    }

    /**
     * Inflates [suggestions] into views, calling [onReady] once with those
     * that succeeded.
     *
     * Inflation is remote and asynchronous — each view is built by the
     * autofill service's process — so the results are gathered and handed
     * over in one go rather than making the strip flicker in chip by chip.
     * A suggestion that fails to inflate is dropped: a manager that cannot
     * draw a chip should cost the user a missing chip, not a crash.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun inflateAll(
        context: Context,
        suggestions: List<InlineSuggestion>,
        stripHeightPx: Int,
        maxWidthPx: Int,
        onReady: (List<View>) -> Unit,
    ) {
        if (suggestions.isEmpty()) {
            onReady(emptyList())
            return
        }
        val wanted = suggestions.take(MAX_CHIPS)
        val views = arrayOfNulls<View>(wanted.size)
        var outstanding = wanted.size
        val executor = context.mainExecutor

        wanted.forEachIndexed { index, suggestion ->
            runCatching {
                suggestion.inflate(
                    context,
                    Size(maxWidthPx, stripHeightPx),
                    executor,
                ) { view ->
                    views[index] = view
                    // Ordering matters: chips arrive out of order, and the
                    // manager ranks them, so slot them back by index rather
                    // than appending as they land.
                    if (--outstanding == 0) onReady(views.filterNotNull())
                }
            }.onFailure {
                if (--outstanding == 0) onReady(views.filterNotNull())
            }
        }
    }
}
