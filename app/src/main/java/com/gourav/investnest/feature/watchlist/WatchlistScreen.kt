package com.gourav.investnest.feature.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gourav.investnest.data.InvestNestRepository
import com.gourav.investnest.model.WatchlistSummary
import com.gourav.investnest.ui.components.EmptyPortfolioState
import com.gourav.investnest.ui.theme.InvestNestTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope

data class WatchlistUiState(
    val watchlists: List<WatchlistSummary> = emptyList(),
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    repository: InvestNestRepository,
) : ViewModel() {
    val uiState: StateFlow<WatchlistUiState> = repository.observeWatchlists()
        .map { WatchlistUiState(watchlists = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WatchlistUiState(),
        )
}

@Composable
fun WatchlistScreenRoute(
    onWatchlistClick: (Long) -> Unit,
    onExploreClick: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WatchlistScreen(
        uiState = uiState,
        onWatchlistClick = onWatchlistClick,
        onExploreClick = onExploreClick,
    )
}

@Composable
fun WatchlistScreen(
    uiState: WatchlistUiState,
    onWatchlistClick: (Long) -> Unit,
    onExploreClick: () -> Unit,
) {
    if (uiState.watchlists.isEmpty()) {
        EmptyPortfolioState(
            title = "No watchlists yet",
            message = "Create your first watchlist by adding a fund from the Explore screen.",
            actionLabel = "Explore Funds",
            onActionClick = onExploreClick,
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "My Portfolios",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(uiState.watchlists, key = { it.id }) { watchlist ->
            Card(
                onClick = { onWatchlistClick(watchlist.id) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = watchlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${watchlist.fundCount} saved funds",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WatchlistScreenPreview() {
    InvestNestTheme {
        WatchlistScreen(
            uiState = WatchlistUiState(
                watchlists = listOf(
                    WatchlistSummary(1, "Retirement", 4),
                    WatchlistSummary(2, "Tax Savers", 2),
                ),
            ),
            onWatchlistClick = {},
            onExploreClick = {},
        )
    }
}
