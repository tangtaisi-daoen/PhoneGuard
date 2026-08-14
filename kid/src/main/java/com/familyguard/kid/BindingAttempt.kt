package com.familyguard.kid

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

internal sealed interface BindingAttemptResult<out T> {
    data class Success<T>(val value: T) : BindingAttemptResult<T>
    data object AuthenticationFailed : BindingAttemptResult<Nothing>
    data object BindingFailed : BindingAttemptResult<Nothing>
    data object TimedOut : BindingAttemptResult<Nothing>
    data class UnexpectedFailure(val cause: Throwable) : BindingAttemptResult<Nothing>
}

/** Runs the two-step binding transaction with a hard upper time limit. */
internal suspend fun <A : Any, B : Any> runBindingAttempt(
    timeoutMillis: Long,
    authenticate: suspend () -> A?,
    bind: suspend (A) -> B?,
): BindingAttemptResult<B> = try {
    withTimeout(timeoutMillis) {
        val auth = authenticate() ?: return@withTimeout BindingAttemptResult.AuthenticationFailed
        val binding = bind(auth) ?: return@withTimeout BindingAttemptResult.BindingFailed
        BindingAttemptResult.Success(binding)
    }
} catch (_: TimeoutCancellationException) {
    BindingAttemptResult.TimedOut
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    BindingAttemptResult.UnexpectedFailure(error)
}
