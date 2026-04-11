package com.gourav.investnest.feature.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.supervisorScope

data class CategoryUiState(
    val title: String = "",
    val funds: List<FundSummary> = emptyList(),
    val visibleCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: InvestNestRepository,
) : ViewModel() {
    private val category = ExploreCategory.fromKey(checkNotNull(savedStateHandle["categoryKey"]))

    private val _uiState = MutableStateFlow(CategoryUiState(title = category.title))
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        loadCategory()
    }

    fun loadCategory() {
        viewModelScope.launch {
            _uiState.value = CategoryUiState(title = category.title, isLoading = true)
            val seeds = runCatching {
                repository.searchFundSeeds(category.query, 28)
            }.getOrElse {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "Unable to load ${category.title.lowercase()}.",
                    )
                }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    funds = seeds,
                    visibleCount = minOf(10, seeds.size),
                    isLoading = false,
                    errorMessage = null,
                )
            }

            val currentFunds = seeds.associateBy { it.schemeCode }.toMutableMap()
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

    fun loadMore() {
        _uiState.update { state ->
            // view all uses a local reveal strategy. we bump visibleCount by 10 to simulate pagination since the api doesnt support it natively.
            if (state.visibleCount >= state.funds.size) state else state.copy(
                visibleCount = minOf(state.visibleCount + 10, state.funds.size),
            )
        }
    }
}

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

    // snapshotflow watches the lazy list state. when we scroll within 2 items of the visible end, we fire loadmore
    LaunchedEffect(visibleFunds.size, uiState.funds.size, listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }.collect { lastVisibleIndex: Int ->
            if (visibleFunds.isNotEmpty() && lastVisibleIndex >= visibleFunds.lastIndex - 2) {
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
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.padding(innerPadding))
            }

            uiState.errorMessage != null -> {
                androidx.compose.material3.TextButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    Text("${uiState.errorMessage} Try Again")
                }
            }

            visibleFunds.isEmpty() -> {
                Text(
                    text = "No funds found in this category.",
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(visibleFunds, key = { _, fund -> fund.schemeCode }) { index, fund ->
                        FundListItem(
                            fund = fund,
                            onClick = { onFundClick(fund.schemeCode) },
                        )
                        if (index == visibleFunds.lastIndex && visibleFunds.size < uiState.funds.size) {
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
