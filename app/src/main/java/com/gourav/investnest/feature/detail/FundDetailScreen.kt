package com.gourav.investnest.feature.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gourav.investnest.data.InvestNestRepository
import com.gourav.investnest.model.FundDetail
import com.gourav.investnest.model.NavPoint
import com.gourav.investnest.model.NavRange
import com.gourav.investnest.model.WatchlistSummary
import com.gourav.investnest.ui.theme.InvestNestTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FundDetailUiState(
    val detail: FundDetail? = null,
    val chartPoints: List<NavPoint> = emptyList(),
    val selectedRange: NavRange = NavRange.ONE_YEAR,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isInWatchlist: Boolean = false,
    val availableWatchlists: List<WatchlistSummary> = emptyList(),
    val isSheetVisible: Boolean = false,
    val selectedWatchlistIds: Set<Long> = emptySet(),
    val newWatchlistName: String = "",
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null,
)

@HiltViewModel
class FundDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: InvestNestRepository,
) : ViewModel() {
    private val schemeCode = checkNotNull(savedStateHandle.get<String>("schemeCode")).toInt()
    private val _uiState = MutableStateFlow(FundDetailUiState())
    val uiState = _uiState.asStateFlow()
    private var currentMembershipIds: Set<Long> = emptySet()

    init {
        observeWatchlists()
        observeMembership()
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val detail = runCatching {
                repository.getFundDetail(schemeCode)
            }.getOrElse {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "Unable to load fund details.",
                    )
                }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    detail = detail,
                    chartPoints = detail.pointsFor(state.selectedRange),
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    fun selectRange(range: NavRange) {
        _uiState.update { state ->
            state.copy(
                selectedRange = range,
                chartPoints = state.detail?.pointsFor(range).orEmpty(),
            )
        }
    }

    fun openWatchlistSheet() {
        _uiState.update { state ->
            state.copy(
                isSheetVisible = true,
                selectedWatchlistIds = currentMembershipIds,
                newWatchlistName = "",
                saveErrorMessage = null,
            )
        }
    }

    fun dismissWatchlistSheet() {
        _uiState.update { state ->
            state.copy(
                isSheetVisible = false,
                isSaving = false,
                saveErrorMessage = null,
            )
        }
    }

    fun toggleWatchlist(watchlistId: Long) {
        _uiState.update { state ->
            val updatedIds = state.selectedWatchlistIds.toMutableSet().apply {
                if (contains(watchlistId)) remove(watchlistId) else add(watchlistId)
            }
            state.copy(selectedWatchlistIds = updatedIds)
        }
    }

    fun updateNewWatchlistName(value: String) {
        _uiState.update { it.copy(newWatchlistName = value) }
    }

    fun saveWatchlistSelection() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveErrorMessage = null) }
            runCatching {
                repository.updateFundWatchlists(
                    detail = detail,
                    selectedWatchlistIds = _uiState.value.selectedWatchlistIds,
                    newWatchlistName = _uiState.value.newWatchlistName,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isSheetVisible = false,
                        isSaving = false,
                        newWatchlistName = "",
                        saveErrorMessage = null,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        saveErrorMessage = "Unable to save watchlist changes.",
                    )
                }
            }
        }
    }

    private fun observeWatchlists() {
        viewModelScope.launch {
            repository.observeWatchlists().collect { watchlists ->
                _uiState.update { state -> state.copy(availableWatchlists = watchlists) }
            }
        }
    }

    private fun observeMembership() {
        viewModelScope.launch {
            repository.observeWatchlistIdsForFund(schemeCode).collect { watchlistIds ->
                currentMembershipIds = watchlistIds
                _uiState.update { state ->
                    state.copy(
                        isInWatchlist = watchlistIds.isNotEmpty(),
                        selectedWatchlistIds = if (state.isSheetVisible) {
                            state.selectedWatchlistIds
                        } else {
                            watchlistIds
                        },
                    )
                }
            }
        }
    }

    private fun FundDetail.pointsFor(range: NavRange): List<NavPoint> {
        val latestDate = navHistory.lastOrNull()?.date ?: return emptyList()
        // simple date subtraction using minusMonths/minusYears to filter the historical nav
        val filtered = when (range) {
            NavRange.SIX_MONTHS -> navHistory.filter { it.date >= latestDate.minusMonths(6) }
            NavRange.ONE_YEAR -> navHistory.filter { it.date >= latestDate.minusYears(1) }
            NavRange.ALL -> navHistory
        }
        // Reuse sampled data so chart range changes stay cheap on slower devices.
        return repository.filterNavPoints(filtered)
    }
}

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

            detail != null -> {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
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
                                modifier = Modifier.weight(1f),
                            )
                            DetailInfoCard(
                                title = "AMC",
                                value = detail.amcName,
                                modifier = Modifier.weight(1f),
                            )
                            DetailInfoCard(
                                title = "Updated",
                                value = detail.latestNavDate,
                                modifier = Modifier.weight(1f),
                            )
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

@Composable
private fun NavChart(
    points: List<NavPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                text = "Not enough NAV history yet.",
                modifier = Modifier.padding(20.dp),
            )
        }
        return
    }

    val minValue = points.minOf { it.nav }
    val maxValue = points.maxOf { it.nav }
    val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
    val chartBackground = MaterialTheme.colorScheme.surfaceVariant
    val chartLineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    // native canvas is way faster than pulling in a massive chart library just to draw a single line path
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                color = chartBackground,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
    ) {
        val rowCount = 4
        repeat(rowCount) { index ->
            val y = size.height / rowCount * index
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f,
            )
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = if (points.size == 1) {
                size.width / 2f
            } else {
                size.width * index / (points.size - 1).toFloat()
            }
            val normalized = (point.nav - minValue) / range
            val y = size.height - (normalized * size.height)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = chartLineColor,
            style = Stroke(
                width = 8f,
                cap = StrokeCap.Round,
            ),
        )
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
