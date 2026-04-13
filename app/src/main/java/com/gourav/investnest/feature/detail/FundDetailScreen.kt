package com.gourav.investnest.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gourav.investnest.model.FundDetail
import com.gourav.investnest.model.NavPoint
import com.gourav.investnest.model.NavRange
import com.gourav.investnest.model.WatchlistSummary
import com.gourav.investnest.ui.components.NavChart
import com.gourav.investnest.ui.theme.InvestNestTheme
import java.time.LocalDate

@Composable
fun FundDetailRoute(
    onBackClick: () -> Unit,
    viewModel: FundDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FundDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetry = viewModel::loadDetail,
        onRangeSelected = viewModel::selectRange,
        onWatchlistClick = viewModel::openWatchlistSheet,
        onDismissSheet = viewModel::dismissWatchlistSheet,
        onToggleWatchlist = viewModel::toggleWatchlist,
        onNewWatchlistNameChange = viewModel::updateNewWatchlistName,
        onSaveWatchlistSelection = viewModel::saveWatchlistSelection,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundDetailScreen(
    uiState: FundDetailUiState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onRangeSelected: (NavRange) -> Unit,
    onWatchlistClick: () -> Unit,
    onDismissSheet: () -> Unit,
    onToggleWatchlist: (Long) -> Unit,
    onNewWatchlistNameChange: (String) -> Unit,
    onSaveWatchlistSelection: () -> Unit,
) {
    val detail = uiState.detail
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onWatchlistClick,
                        enabled = detail != null,
                    ) {
                        Icon(
                            imageVector = if (uiState.isInWatchlist) {
                                Icons.Filled.Bookmark
                            } else {
                                Icons.Outlined.BookmarkBorder
                            },
                            contentDescription = "Update watchlists",
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
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }

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
                        TextButton(onClick = onRetry) {
                            Text(
                                text = "Try Again",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                detail != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = detail.schemeName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = detail.schemeCategory,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "NAV ₹${detail.latestNav}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    NavChart(points = uiState.chartPoints)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        NavRange.entries.forEach { range ->
                                            FilterChip(
                                                selected = uiState.selectedRange == range,
                                                onClick = { onRangeSelected(range) },
                                                label = { Text(range.label) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                text = buildFundSummaryText(detail),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                DetailInfoCard(
                                    title = "Type",
                                    value = detail.schemeType,
                                    modifier = Modifier.weight(0.9f),
                                )
                                DetailInfoCard(
                                    title = "AMC",
                                    value = detail.amcName,
                                    modifier = Modifier.weight(1f),
                                )
                                DetailInfoCard(
                                    title = "Updated",
                                    value = detail.latestNavDate,
                                    modifier = Modifier.weight(1.1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isSheetVisible) {
        ModalBottomSheet(onDismissRequest = onDismissSheet) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Add to Portfolio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = uiState.newWatchlistName,
                    onValueChange = onNewWatchlistNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("New Portfolio Name") },
                )
                if (uiState.availableWatchlists.isEmpty()) {
                    Text(
                        text = "No existing watchlists yet. Create one above and save it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uiState.availableWatchlists.forEach { watchlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleWatchlist(watchlist.id) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(
                                checked = uiState.selectedWatchlistIds.contains(watchlist.id),
                                onCheckedChange = { onToggleWatchlist(watchlist.id) },
                            )
                            Column {
                                Text(
                                    text = watchlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
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
                if (uiState.saveErrorMessage != null) {
                    Text(
                        text = uiState.saveErrorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onSaveWatchlistSelection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Save Selection")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun buildFundSummaryText(detail: FundDetail): String {
    return "${detail.amcName} manages this ${detail.schemeCategory.lowercase()}. " +
        "Review recent NAV history and save it into one or more watchlists from here."
}

@Preview(showBackground = true)
@Composable
private fun FundDetailScreenPreview() {
    val previewDetail = FundDetail(
        schemeCode = 1,
        schemeName = "SBI Bluechip Fund",
        amcName = "SBI Mutual Fund",
        latestNav = "145.20",
        latestNavDate = "10-04-2026",
        schemeCategory = "Equity, Large Cap",
        schemeType = "Growth",
        navHistory = List(40) { index ->
            NavPoint(
                date = LocalDate.now().minusDays((40 - index).toLong()),
                nav = 100f + index * 1.5f,
            )
        },
    )
    InvestNestTheme {
        FundDetailScreen(
            uiState = FundDetailUiState(
                detail = previewDetail,
                chartPoints = previewDetail.navHistory,
                isLoading = false,
                isInWatchlist = true,
                availableWatchlists = listOf(
                    WatchlistSummary(1, "Retirement", 4),
                    WatchlistSummary(2, "Tax Savers", 2),
                ),
            ),
            onBackClick = {},
            onRetry = {},
            onRangeSelected = {},
            onWatchlistClick = {},
            onDismissSheet = {},
            onToggleWatchlist = {},
            onNewWatchlistNameChange = {},
            onSaveWatchlistSelection = {},
        )
    }
}
