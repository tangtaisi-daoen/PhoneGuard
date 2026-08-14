package com.familyguard.kid

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class BindingAttemptTest {

    @Test
    fun `successful authentication and binding returns success`() = runBlocking {
        val result = runBindingAttempt(
            timeoutMillis = 1_000,
            authenticate = { "auth" },
            bind = { auth -> if (auth == "auth") "binding" else null },
        )

        assertEquals(BindingAttemptResult.Success("binding"), result)
    }

    @Test
    fun `authentication failure is reported`() = runBlocking {
        val result = runBindingAttempt<String, String>(
            timeoutMillis = 1_000,
            authenticate = { null },
            bind = { "binding" },
        )

        assertEquals(BindingAttemptResult.AuthenticationFailed, result)
    }

    @Test
    fun `binding failure is reported`() = runBlocking {
        val result = runBindingAttempt(
            timeoutMillis = 1_000,
            authenticate = { "auth" },
            bind = { null },
        )

        assertEquals(BindingAttemptResult.BindingFailed, result)
    }

    @Test
    fun `slow request times out instead of hanging`() = runBlocking {
        val result = runBindingAttempt<String, String>(
            timeoutMillis = 20,
            authenticate = {
                delay(200)
                "auth"
            },
            bind = { "binding" },
        )

        assertEquals(BindingAttemptResult.TimedOut, result)
    }

    @Test
    fun `unexpected failure is converted to a recoverable result`() = runBlocking {
        val result = runBindingAttempt<String, String>(
            timeoutMillis = 1_000,
            authenticate = { error("boom") },
            bind = { "binding" },
        )

        assertTrue(result is BindingAttemptResult.UnexpectedFailure)
    }

    @Test
    fun `caller cancellation is propagated`() = runBlocking {
        try {
            runBindingAttempt<String, String>(
                timeoutMillis = 1_000,
                authenticate = { throw CancellationException("screen closed") },
                bind = { "binding" },
            )
            fail("CancellationException should be propagated")
        } catch (_: CancellationException) {
            // Expected: lifecycle cancellation is not a binding error.
        }
    }
}
