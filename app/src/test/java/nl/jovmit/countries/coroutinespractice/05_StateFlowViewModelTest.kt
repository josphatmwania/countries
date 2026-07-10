package nl.jovmit.countries.coroutinespractice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class StateFlowViewModelTest {

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
        suspend fun loadProfile(userId: String): UserProfile = coroutineScope {
            val user = async { api.getUser(userId).await() }
            val posts = async { api.getPosts(userId).await() }
            UserProfile(user.await(), posts.await())
        }
    }

    /**
     * This is a ViewModel-like class without Android dependencies.
     *
     * In a real ViewModel, you would use viewModelScope instead of passing CoroutineScope.
     */
    class ProfilePresenter(
        private val repository: UserRepository,
        private val scope: CoroutineScope
    ) {
        private val _state = MutableStateFlow(ProfileUiState())
        val state: StateFlow<ProfileUiState> = _state.asStateFlow()

        /**
         * Exercise 9:
         * Implement loading state.
         *
         * Rules:
         * - immediately set isLoading = true
         * - on success, put user and posts in state
         * - on failure, set errorMessage
         * - loading should be false at the end
         */
        fun loadProfile(userId: String) {
            scope.launch {
                _state.update { it.copy(isLoading = true) }
                try {
                    val profile = repository.loadProfile(userId)
                    _state.update { it.copy(isLoading = false, user = profile.user, posts = profile.posts) }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            }
        }
    }

    @Test
    fun `load profile updates state on success`() = runTest {
        val api = FakeUserApi(scope = this)
        val presenter = ProfilePresenter(
            repository = UserRepository(api),
            scope = this
        )

        presenter.loadProfile("123")
        advanceUntilIdle()

        val state = presenter.state.value

        assertFalse(state.isLoading)
        assertEquals("123", state.user?.id)
        assertEquals(2, state.posts.size)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `load profile updates state on failure`() = runTest {
        val api = FakeUserApi(scope = this, failUser = true)
        val presenter = ProfilePresenter(
            repository = UserRepository(api),
            scope = this
        )

        presenter.loadProfile("123")
        advanceUntilIdle()

        val state = presenter.state.value

        assertFalse(state.isLoading)
        assertEquals(null, state.user)
        assertTrue(state.errorMessage != null)
    }
}
