package nl.jovmit.countries.coroutinespractice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Goal:
 * Convert a callback-based API into a suspending function.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallbackToCoroutineTest {

    /**
     * Exercise 1:
     * Implement this function using suspendCancellableCoroutine.
     *
     * Requirements:
     * - onSuccess resumes with the value
     * - onError resumes with exception
     * - coroutine cancellation calls HttpCall.cancel()
     */
    private suspend fun <T> HttpCall<T>.await(): T = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback<T> {
            override fun onSuccess(value: T) {
                continuation.resume(value)
            }

            override fun onError(error: Throwable) {
                continuation.resumeWithException(error)
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }

    @Test
    fun `await returns successful response`() = runTest {
        val api = FakeUserApi(scope = this)

        val deferred = async {
            api.getUser("123").await()
        }

        advanceTimeBy(1_000)

        val user = deferred.await()

        assertEquals("123", user.id)
        assertEquals("John Doe", user.name)
    }

    @Test
    fun `await throws when call fails`() = runTest {
        val api = FakeUserApi(scope = this, failUser = true)

        val deferred = async {
            runCatching {
                api.getUser("123").await()
            }
        }

        advanceTimeBy(1_000)

        val error = deferred.await().exceptionOrNull()

        assertTrue(error is RuntimeException)
        assertEquals("Network error", error?.message)
    }

    @Test
    fun `await cancels underlying call when coroutine is cancelled`() = runTest {
        val api = FakeUserApi(scope = this)

        val job = async {
            api.getUser("123").await()
        }

        runCurrent()
        job.cancelAndJoin()

        val call = api.lastUserCall

        assertTrue(call?.wasCancelled == true)
    }
}
