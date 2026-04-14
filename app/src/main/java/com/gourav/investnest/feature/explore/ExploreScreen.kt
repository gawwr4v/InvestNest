package com.gourav.investnest.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gourav.investnest.data.InvestNestRepository
import com.gourav.investnest.model.ExploreCategory
import com.gourav.investnest.model.ExploreSection
import com.gourav.investnest.model.FundSummary
import com.gourav.investnest.ui.components.FundCard
import com.gourav.investnest.ui.components.InvestNestSearchField
import com.gourav.investnest.ui.components.SectionHeader
import com.gourav.investnest.ui.theme.InvestNestTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// this state class represents everything shown on the explore screen
data class ExploreUiState(
    val sections: List<ExploreSection> = emptyList(), // the list of categories and their funds
    val isLoading: Boolean = true, // used for the very first load when the app opens
    val isRefreshing: Boolean = false, // used when we are updating data in the background
    val errorMessage: String? = null, // holds the error text if things go south
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: InvestNestRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // we read from the local room database first so data is visible instantly without waiting for the network
            val cachedSections = repository.getCachedExploreSections()
            _uiState.value = if (cachedSections.isNotEmpty()) {
                ExploreUiState(
                    sections = cachedSections,
                    isLoading = false,
                    isRefreshing = true,
                )
            } else {
                ExploreUiState(isLoading = true)
            }

            var sawFreshData = false
            // we loop through each category to fetch fresh data from the network
            ExploreCategory.entries.forEach { category ->
                val seeds = runCatching { // first we get the basic fund info which we call seeds (names/codes)
                    repository.getExploreSeeds(category)
                }.getOrElse {
                    return@forEach
                }
                sawFreshData = true
                _uiState.update { state -> // immediately update the UI state with these seeds
                    state.withSection(
                        ExploreSection(
                            category = category,
                            funds = seeds,
                        ),
                    ).copy(
                        isLoading = false,
                        isRefreshing = true,
                        errorMessage = null,
                    )
                }

                val currentFunds = seeds.associateBy { it.schemeCode }.toMutableMap()
                // supervisor scope is important because it prevents one failed network call from stopping the whole process
                supervisorScope {
                    seeds.forEach { seed ->
                        launch {
                            // fetch the full details (nav, etc) for each fund in parallel
                            val summary = runCatching {
                                repository.getFundSummary(seed.schemeCode)
                            }.getOrNull() ?: return@launch
                            
                            // we use synchronized to safely update our local map across different coroutines
                            synchronized(currentFunds) {
                                currentFunds[summary.schemeCode] = summary
                            }
                            val snapshot = synchronized(currentFunds) {
                                seeds.map { currentFunds[it.schemeCode] ?: it }
                            }
                            
                            // we update the ui state with the new fund details as they arrive
                            _uiState.update { state ->
                                state.withSection(
                                    ExploreSection(
                                        category = category,
                                        funds = snapshot,
                                    ),
                                ).copy(
                                    isLoading = false,
                                    isRefreshing = true,
                                    errorMessage = null,
                                )
                            }
                        }
                    }
                }

                // we save the completed section to room so it is available offline next time
                val finalSection = synchronized(currentFunds) {
                    seeds.map { currentFunds[it.schemeCode] ?: it }
                        .filterNot { it.isMetadataLoading }
                }
                repository.saveExploreSection(category, finalSection)
            }

            // final update to turn off the refreshing indicators
            _uiState.update { state ->
                when {
                    state.sections.isNotEmpty() -> state.copy(isRefreshing = false, isLoading = false)
                    sawFreshData -> state.copy(isRefreshing = false, isLoading = false)
                    else -> state.copy(
                        isRefreshing = false,
                        isLoading = false,
                        errorMessage = "Unable to load funds right now.",
                    )
                }
            }
        }
    }

    // helper function to update a specific category section without affecting others
    private fun ExploreUiState.withSection(section: ExploreSection): ExploreUiState {
        val updatedSections = sections
            .filterNot { it.category == section.category }
            .plus(section)
            .sortedBy { it.category.ordinal }
        return copy(sections = updatedSections)
    }
}

// this is the main entry point for the explore screen that connects navigation to the viewmodel
@Composable
fun ExploreScreenRoute(
    onSearchClick: () -> Unit,
    onViewAllClick: (ExploreCategory) -> Unit,
    onFundClick: (Int) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExploreScreen(
        uiState = uiState,
        onRetry = viewModel::refresh,
        onSearchClick = onSearchClick,
        onViewAllClick = onViewAllClick,
        onFundClick = onFundClick,
    )
}

@Composable
fun ExploreScreen(
    uiState: ExploreUiState,
    onRetry: () -> Unit,
    onSearchClick: () -> Unit,
    onViewAllClick: (ExploreCategory) -> Unit,
    onFundClick: (Int) -> Unit,
) {
    // using lazy column as its great for performance as it only renders what is currently visible
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Text(
                text = "InvestNest",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            InvestNestSearchField(
                value = "",
                onValueChange = {},
                enabled = false,
                placeholder = "Search funds",
                onDisabledClick = onSearchClick,
            )
        }
        
        // showing a big loading spinner if we have no data to show yet
        if (uiState.isLoading && uiState.sections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        
        // showing the error state if something went wrong and the screen is empty
        if (uiState.errorMessage != null && uiState.sections.isEmpty()) {
            item {
                ErrorState(
                    message = uiState.errorMessage,
                    actionLabel = "Try Again",
                    onActionClick = onRetry,
                )
            }
        }
        
        // rendering each category section on the screen
        items(uiState.sections, key = { it.category.key }) { section ->
            ExploreSectionBlock(
                section = section,
                onViewAllClick = { onViewAllClick(section.category) },
                onFundClick = onFundClick,
            )
        }
        
        // a small indicator to show we are updating the latest prices in the background
        if (uiState.isRefreshing && uiState.sections.isNotEmpty()) {
            item {
                Text(
                    text = "Refreshing latest NAV data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// this component handles the layout for a single category of funds
@Composable
private fun ExploreSectionBlock(
    section: ExploreSection,
    onViewAllClick: () -> Unit,
    onFundClick: (Int) -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = section.category.title,
            actionLabel = "View All",
            onActionClick = onViewAllClick,
        )
        // we group the funds in pairs of two to create a simple grid layout
        section.funds.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { fund ->
                    FundCard(
                        fund = fund,
                        onClick = { onFundClick(fund.schemeCode) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // we add a spacer if there is only one item in the row to keep the layout consistent
                if (rowItems.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// a reusable error view for when network requests fail
@Composable
private fun ErrorState(
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        androidx.compose.material3.TextButton(onClick = onActionClick) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExploreScreenPreview() {
    InvestNestTheme {
        ExploreScreen(
            uiState = ExploreUiState(
                sections = listOf(
                    ExploreSection(
                        category = ExploreCategory.INDEX_FUNDS,
                        funds = listOf(
                            FundSummary(1, "UTI Nifty 50 Index Fund", latestNav = "210.15"),
                            FundSummary(2, "ICICI Sensex Index Fund", latestNav = "195.30"),
                            FundSummary(3, "HDFC Index S&P BSE", latestNav = "87.50"),
                            FundSummary(4, "SBI Nifty 50 Index Fund", latestNav = "210.00"),
                        ),
                    ),
                ),
                isLoading = false,
            ),
            onRetry = {},
            onSearchClick = {},
            onViewAllClick = {},
            onFundClick = {},
        )
    }
}
