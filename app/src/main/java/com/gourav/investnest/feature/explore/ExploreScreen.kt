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

data class ExploreUiState(
    val sections: List<ExploreSection> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
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
            // immediate read from room to show cached cards without network delay
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
            // progressive enrichment starts here. we fire search to get basic seeds instantly.
            ExploreCategory.entries.forEach { category ->
                val seeds = runCatching {
                    repository.getExploreSeeds(category)
                }.getOrElse {
                    return@forEach
                }
                sawFreshData = true
                // Seed the section fast, then patch richer metadata as detail calls return.
                _uiState.update { state ->
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
                // supervisor scope prevents a single failed network call from crashing the other coroutines in parallel
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

                val finalSection = synchronized(currentFunds) {
                    seeds.map { currentFunds[it.schemeCode] ?: it }
                        .filterNot { it.isMetadataLoading }
                }
                repository.saveExploreSection(category, finalSection)
            }

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

    private fun ExploreUiState.withSection(section: ExploreSection): ExploreUiState {
        val updatedSections = sections
            .filterNot { it.category == section.category }
            .plus(section)
            .sortedBy { it.category.ordinal }
        return copy(sections = updatedSections)
    }
}

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
        if (uiState.errorMessage != null && uiState.sections.isEmpty()) {
            item {
                ErrorState(
                    message = uiState.errorMessage,
                    actionLabel = "Try Again",
                    onActionClick = onRetry,
                )
            }
        }
        items(uiState.sections, key = { it.category.key }) { section ->
            ExploreSectionBlock(
                section = section,
                onViewAllClick = { onViewAllClick(section.category) },
                onFundClick = onFundClick,
            )
        }
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
                if (rowItems.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

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
