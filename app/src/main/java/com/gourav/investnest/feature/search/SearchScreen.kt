package com.gourav.investnest.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gourav.investnest.data.InvestNestRepository
import com.gourav.investnest.model.FundSummary
import com.gourav.investnest.ui.components.FundListItem
import com.gourav.investnest.ui.components.InvestNestSearchField
import com.gourav.investnest.ui.theme.InvestNestTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class SearchUiState(
    val query: String = "",
    val results: List<FundSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
@OptIn(FlowPreview::class)
class SearchViewModel @Inject constructor(
    private val repository: InvestNestRepository,
) : ViewModel() {
    private val queryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // search pipeline: whitespace trimming, 300ms debounce to prevent excessive API requests,
            // and collectLatest to cancel stale searches when the query changes.
            queryFlow
                .map { it.trim() }
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.value = SearchUiState(query = "")
                        return@collectLatest
                    }
                    _uiState.value = SearchUiState(
                        query = query,
                        isLoading = true,
                    )
                    val seeds = runCatching {
                        // NOTE: MFAPI.in free tier limits search results to 15 items.
                        repository.searchFundSeeds(query, 20)
                    }.getOrElse {
                        _uiState.value = SearchUiState(
                            query = query,
                            errorMessage = "Unable to search funds right now.",
                        )
                        return@collectLatest
                    }
                    _uiState.update { state ->
                        state.copy(
                            query = query,
                            results = seeds,
                            isLoading = false,
                        )
                    }
                    val currentFunds = seeds.associateBy { it.schemeCode }.toMutableMap()
                    // same enrichment as explore, we backfill nav data in parallel
                    supervisorScope {
                        seeds.forEach { seed ->
                            launch {
                                val summary = runCatching {
                                    repository.getFundSummary(seed.schemeCode)
                                }.getOrNull() ?: return@launch
                                synchronized(currentFunds) {
                                    currentFunds[summary.schemeCode] = summary
                                }
                                val snapshot = synchronized(currentFunds) {
                                    seeds.map { currentFunds[it.schemeCode] ?: it }
                                }
                                _uiState.update { state ->
                                    state.copy(results = snapshot)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        queryFlow.value = query
        _uiState.update { it.copy(query = query) }
    }
}

@Composable
fun SearchScreenRoute(
    onBackClick: () -> Unit,
    onFundClick: (Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onFundClick = onFundClick,
        onQueryChange = viewModel::onQueryChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onBackClick: () -> Unit,
    onFundClick: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Search Funds") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InvestNestSearchField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search by fund name",
                )
            }
            when {
                uiState.isLoading -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                uiState.errorMessage != null -> {
                    item {
                        Text(
                            text = uiState.errorMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                uiState.query.isBlank() -> {
                    item {
                        Text(
                            text = "Start typing to search across mutual funds.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                uiState.results.isEmpty() -> {
                    item {
                        Text(
                            text = "No funds matched your search.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    // keys are important here so compose knows exactly which item changed
                    items(uiState.results, key = { it.schemeCode }) { fund ->
                        FundListItem(
                            fund = fund,
                            onClick = { onFundClick(fund.schemeCode) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    InvestNestTheme {
        SearchScreen(
            uiState = SearchUiState(
                query = "hdfc",
                results = listOf(
                    FundSummary(1, "HDFC Index Fund", latestNav = "126.50"),
                    FundSummary(2, "HDFC Top 100 Fund", latestNav = "95.60"),
                ),
            ),
            onBackClick = {},
            onFundClick = {},
            onQueryChange = {},
        )
    }
}
