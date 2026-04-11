package com.gourav.investnest.feature.watchlistdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gourav.investnest.data.InvestNestRepository
import com.gourav.investnest.model.FundSummary
import com.gourav.investnest.model.WatchlistDetail
import com.gourav.investnest.ui.components.EmptyPortfolioState
import com.gourav.investnest.ui.components.FundListItem
import com.gourav.investnest.ui.theme.InvestNestTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class WatchlistDetailUiState(
    val watchlist: WatchlistDetail? = null,
)

@HiltViewModel
class WatchlistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: InvestNestRepository,
) : ViewModel() {
    private val watchlistId = checkNotNull(savedStateHandle.get<String>("watchlistId")).toLong()

    val uiState: StateFlow<WatchlistDetailUiState> = repository.observeWatchlistDetail(watchlistId)
        .map { WatchlistDetailUiState(watchlist = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WatchlistDetailUiState(),
        )
}

@Composable
fun WatchlistDetailRoute(
    onBackClick: () -> Unit,
    onFundClick: (Int) -> Unit,
    onExploreClick: () -> Unit,
    viewModel: WatchlistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WatchlistDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onFundClick = onFundClick,
        onExploreClick = onExploreClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistDetailScreen(
    uiState: WatchlistDetailUiState,
    onBackClick: () -> Unit,
    onFundClick: (Int) -> Unit,
    onExploreClick: () -> Unit,
) {
    val watchlist = uiState.watchlist
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(watchlist?.name ?: "Watchlist") },
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
            watchlist == null -> {
                Text(
                    text = "This watchlist no longer exists.",
                    modifier = Modifier.padding(innerPadding),
                )
            }

            watchlist.funds.isEmpty() -> {
                EmptyPortfolioState(
                    title = "No funds added yet",
                    message = "Explore the market to save funds into this watchlist.",
                    actionLabel = "Explore Funds",
                    onActionClick = onExploreClick,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "${watchlist.funds.size} funds saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(watchlist.funds, key = { it.schemeCode }) { fund ->
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
private fun WatchlistDetailScreenPreview() {
    InvestNestTheme {
        WatchlistDetailScreen(
            uiState = WatchlistDetailUiState(
                watchlist = WatchlistDetail(
                    id = 1,
                    name = "Retirement",
                    funds = listOf(
                        FundSummary(1, "UTI Nifty 50 Index Fund", latestNav = "210.15"),
                    ),
                ),
            ),
            onBackClick = {},
            onFundClick = {},
            onExploreClick = {},
        )
    }
}
