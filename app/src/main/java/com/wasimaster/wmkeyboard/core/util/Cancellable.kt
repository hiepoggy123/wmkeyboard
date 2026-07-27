package com.wasimaster.wmkeyboard.core.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching], but coroutine cancellation still propagates.
 *
 * `runCatching` catches [Throwable], and inside a coroutine that includes the
 * [CancellationException] the machinery throws to unwind a cancelled job. The
 * result is a job that reports a *failure* instead of stopping: the caller sees
 * `Result.failure`, carries on down the error path, and the coroutine that was
 * supposed to die keeps running. In the keyboard that shows up as a cancelled
 * model download that still writes its file, or a dismissed panel whose
 * recogniser goes on consuming audio.
 *
 * Use this instead of `runCatching` in any `suspend` function or coroutine
 * body. detekt's `SuspendFunSwallowedCancellation` rule enforces it.
 */
inline fun <T> runCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
