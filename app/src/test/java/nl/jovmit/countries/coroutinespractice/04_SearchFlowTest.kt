package nl.jovmit.countries.coroutinespractice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchFlowTest {

    class SearchRepository(
        private val api: SearchApi
    ) {
        /**
         * Exercise 7:
         * Build a search pipeline.
         *
         * Rules:
         * - debounce 300ms
         * - ignore query shorter than 3 characters
         * - ignore duplicate queries
         * - cancel old searches when new query arrives
         */
        fun searchResults(queryFlow: Flow<String>): Flow<List<SearchResult>> {
            return queryFlow
                .debounce(300)
                .filter { it.length >= 3 }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    flow { emit(api.search(query)) }
                }
        }

        /**
         * Exercise 8:
         * Convert search results into SearchUiState.
         *
         * Rules:
         * - query shorter than 3 chars => Idle
         * - valid query => Loading, then Success
         * - failure => Error
         */
        fun searchUiState(queryFlow: Flow<String>): Flow<SearchUiState> {
            return queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.length < 3) {
                        flowOf(SearchUiState.Idle)
                    } else {
                        flow {
                            emit(SearchUiState.Loading)
                            val results = api.search(query)
                            emit(SearchUiState.Success(results))
                        }.catch { error ->
                            emit(SearchUiState.Error(error.message ?: "Unknown error"))
                        }
                    }
                }
        }
    }

    @Test
    fun `search ignores short queries`() = runTest {
        val api = FakeSearchApi()
        val repository = SearchRepository(api)

        val queryFlow = flowOf("a", "ab")
        val results = repository.searchResults(queryFlow).toList()

        assertEquals(emptyList<List<SearchResult>>(), results)
        assertEquals(emptyList<String>(), api.queries)
    }

    @Test
    fun `search returns results for valid query`() = runTest {
        val api = FakeSearchApi(delayMillis = 1_000)
        val repository = SearchRepository(api)

        val queryFlow = flowOf("android")
        val results = repository.searchResults(queryFlow).toList()

        assertEquals(1, results.size)
        assertEquals(2, results.first().size)
        assertEquals(listOf("android"), api.queries)
    }

    @Test
    fun `search ui state emits loading and success`() = runTest {
        val api = FakeSearchApi(delayMillis = 1_000)
        val repository = SearchRepository(api)

        val states = repository.searchUiState(flowOf("android")).toList()

        assertEquals(SearchUiState.Loading, states[0])
        assertEquals(true, states[1] is SearchUiState.Success)
    }

    @Test
    fun `search ui state emits idle for short query`() = runTest {
        val api = FakeSearchApi()
        val repository = SearchRepository(api)

        val states = repository.searchUiState(flowOf("ab")).toList()

        assertEquals(listOf(SearchUiState.Idle), states)
    }

    @Test
    fun `search ui state emits error on failure`() = runTest {
        val api = FakeSearchApi(shouldFail = true)
        val repository = SearchRepository(api)

        val states = repository.searchUiState(flowOf("android")).toList()

        assertEquals(SearchUiState.Loading, states[0])
        assertEquals(true, states[1] is SearchUiState.Error)
    }
}
