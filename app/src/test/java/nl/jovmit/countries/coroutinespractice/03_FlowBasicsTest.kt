package nl.jovmit.countries.coroutinespractice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowBasicsTest {

    class UserRepository(
        private val dao: UserDao
    ) {
        /**
         * Exercise 5:
         * Expose the user stream from the DAO.
         */
        fun observeUser(userId: String): Flow<User> {
            return dao.observeUser(userId)
        }

        /**
         * Exercise 6:
         * Map User into UserUiModel.
         */
        fun observeUserUiModel(userId: String): Flow<UserUiModel> {
            return observeUser(userId).map { user ->
                UserUiModel(title = user.name, subtitle = user.email)
            }
        }
    }

    @Test
    fun `observe user emits first value`() = runTest {
        val repository = UserRepository(FakeUserDao())

        val user = repository.observeUser("123").first()

        assertEquals("123", user.id)
        assertEquals("Loading name...", user.name)
    }

    @Test
    fun `observe user ui model maps user into ui model`() = runTest {
        val repository = UserRepository(FakeUserDao())

        val uiModel = repository.observeUserUiModel("123").first()

        assertEquals("Loading name...", uiModel.title)
        assertEquals("loading@example.com", uiModel.subtitle)
    }

    @Test
    fun `observe user can collect multiple emissions`() = runTest {
        val repository = UserRepository(FakeUserDao())

        val users = repository.observeUser("123")
            .take(3)
            .toList()

        assertEquals(3, users.size)
        assertEquals("Loading name...", users[0].name)
        assertEquals("John Doe", users[1].name)
        assertEquals("John Updated", users[2].name)
    }
}
