package com.wasimaster.wmkeyboard.app.llm

import android.app.Application
import android.content.Context

/**
 * The [installLlmDelivery] of every channel that is not Play. Like
 * NoPlayUpdater beside it, the no-op is the correct answer, not a fallback:
 * these builds compile the LiteRT-LM runtime straight into the base APK
 * (`:core:intelligence`'s src/llmbridge), so the default always-installed
 * gate in LlmModule is already telling the truth and there is nothing to
 * deliver or to SplitCompat.
 */
@Suppress("UNUSED_PARAMETER")
fun installLlmDelivery(app: Application) = Unit

@Suppress("UNUSED_PARAMETER")
fun llmSplitCompat(context: Context) = Unit
