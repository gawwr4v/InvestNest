package com.gourav.investnest.feature.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gourav.investnest.data.InvestNestRepository
import com.gourav.investnest.model.ExploreCategory
import com.gourav.investnest.model.FundSummary
import com.gourav.investnest.ui.components.FundListItem
import com.gourav.investnest.ui.theme.InvestNestTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope

// this data class holds the entire state for the category screen
data class CategoryUiState(
    val title: String = "",
    val funds: List<FundSummary> = emptyList(),
    val visibleCount: Int = 0,
    val isLoading: Boolean = true,
    val isMoreLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: InvestNestRepository,
) : ViewModel() {
    // we get the category key from navigation to know which type of funds to display
    private val category = ExploreCategory.fromKey(checkNotNull(savedStateHandle["categoryKey"]))

    // internal state that handles updates within the viewmodel
    private val _uiState = MutableStateFlow(CategoryUiState(title = category.title))
    // external state that the ui observes for changes
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        loadCategory()
    }

    // fetches the funds for the selected category and starts background data enrichment
    fun loadCategory() {
        viewModelScope.launch {
            _uiState.value = CategoryUiState(title = category.title, isLoading = true)
            val seeds = runCatching {
                // we limit the initial fetch size to optimize network performance and manage memory overhead during subsequent detail enrichment.
                repository.searchFundSeeds(category.query, 60)
            }.getOrElse {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "Unable to load ${category.title.lowercase()}.",
                    )
                }
                return@launch
            }
            
            // showing the first few results quickly while details load in background
            _uiState.update { state ->
                state.copy(
                    funds = seeds,
                    visibleCount = minOf(10, seeds.size),
                    isLoading = false,
                    errorMessage = null,
                )
            }

            val currentFunds = seeds.associateBy { it.schemeCode }.toMutableMap()
            // fetching extra details like nav prices for all funds in parallel
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
                        _uiState.update { state -> state.copy(funds = snapshot) }
                    }
                }
            }
        }
    }

    // simple pagination logic to show more funds as the user scrolls down
    fun loadMore() {
        // NOTE: Due to the 15-result limit of the free MFAPI, the 'loadMore' logic will only trigger once for most queries before hitting the end of the results.
        if (_uiState.value.isMoreLoading || _uiState.value.visibleCount >= _uiState.value.funds.size) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isMoreLoading = true) }
            delay(400) // simulated delay so the user can actually see the "Loading more..." state
            _uiState.update { state ->
                state.copy(
                    isMoreLoading = false,
                    visibleCount = minOf(state.visibleCount + 10, state.funds.size),
                )
            }
        }
    }
}

// this route connects the navigation system to our viewmodel and screen
@Composable
fun CategoryScreenRoute(
    onBackClick: () -> Unit,
    onFundClick: (Int) -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onFundClick = onFundClick,
        onRetry = viewModel::loadCategory,
        onLoadMore = viewModel::loadMore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    uiState: CategoryUiState,
    onBackClick: () -> Unit,
    onFundClick: (Int) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val visibleFunds = uiState.funds.take(uiState.visibleCount)

    // snapshotFlow watches our scroll position... when we get near the end
    // of the currently visible list we trigger loadMore to show the next 10
    LaunchedEffect(visibleFunds.size, uiState.funds.size, listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }.collect { lastVisibleIndex: Int ->
            // if we are near the end of the visible list we call loadMore
            if (lastVisibleIndex >= visibleFunds.lastIndex - 3 && visibleFunds.size < uiState.funds.size) {
                onLoadMore()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.title) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // showing a centered loading spinner for the initial load
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }

                // displaying error message with a retry button if the fetch fails
                uiState.errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        androidx.compose.material3.TextButton(onClick = onRetry) {
                            Text(
                                text = "Try Again",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // simple text display when no funds match the selected category
                visibleFunds.isEmpty() -> {
                    Text(
                        text = "No funds found in this category.",
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    // rendering the list of funds efficiently with lazycolumn
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(20.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(visibleFunds, key = { _, fund -> fund.schemeCode }) { _, fund ->
                            FundListItem(
                                fund = fund,
                                onClick = { onFundClick(fund.schemeCode) },
                            )
                        }

                        // showing a loading indicator at the bottom when fetching more items
                        if (uiState.isMoreLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Loading more...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryScreenPreview() {
    InvestNestTheme {
        CategoryScreen(
            uiState = CategoryUiState(
                title = "Index Funds",
                funds = List(5) { index ->
                    FundSummary(
                        schemeCode = index,
                        schemeName = "Fund $index",
                        latestNav = "145.$index",
                    )
                },
                visibleCount = 5,
                isLoading = false,
            ),
            onBackClick = {},
            onFundClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}
