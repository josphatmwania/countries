package nl.jovmit.countries.coroutinespractice

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SequentialAndParallelCoroutinesTest {

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

    inner class UserRepository(
        private val api: UserApi
    ) {
        suspend fun getUser(userId: String): User = api.getUser(userId).await()

        suspend fun getPosts(userId: String): List<Post> = api.getPosts(userId).await()

        /**
         * Exercise 2:
         * Load user and posts sequentially.
         */
        suspend fun loadProfileSequentially(userId: String): UserProfile {
            val user = getUser(userId)
            val posts = getPosts(userId)
            return UserProfile(user, posts)
        }

        /**
         * Exercise 3:
         * Load user and posts in parallel using async.
         */
        suspend fun loadProfileInParallel(userId: String): UserProfile = coroutineScope {
            val user = async { getUser(userId) }
            val posts = async { getPosts(userId) }
            UserProfile(user.await(), posts.await())
        }

        /**
         * Exercise 4:
         * User is required.
         * Posts are optional.
         *
         * If posts fail, return an empty post list.
         * If user fails, throw.
         */
        suspend fun loadProfileSafely(userId: String): UserProfile = supervisorScope {
            val user = async { getUser(userId) }
            val posts = async {
                runCatching { getPosts(userId) }.getOrDefault(emptyList())
            }
            UserProfile(user.await(), posts.await())
        }
    }

    @Test
    fun `sequential loading takes sum of both calls`() = runTest {
        val api = FakeUserApi(
            scope = this,
            userDelayMillis = 1_000,
            postsDelayMillis = 1_000
        )
        val repository = UserRepository(api)

        val profile = repository.loadProfileSequentially("123")

        assertEquals("123", profile.user.id)
        assertEquals(2, profile.posts.size)
        assertEquals(2_000, currentTime)
    }

    @Test
    fun `parallel loading takes only the slowest call time`() = runTest {
        val api = FakeUserApi(
            scope = this,
            userDelayMillis = 1_000,
            postsDelayMillis = 1_000
        )
        val repository = UserRepository(api)

        val profile = repository.loadProfileInParallel("123")

        assertEquals("123", profile.user.id)
        assertEquals(2, profile.posts.size)
        assertEquals(1_000, currentTime)
    }

    @Test
    fun `safe loading returns empty posts if posts fail`() = runTest {
        val api = FakeUserApi(
            scope = this,
            failPosts = true
        )
        val repository = UserRepository(api)

        val profile = repository.loadProfileSafely("123")

        assertEquals("123", profile.user.id)
        assertTrue(profile.posts.isEmpty())
    }
}
