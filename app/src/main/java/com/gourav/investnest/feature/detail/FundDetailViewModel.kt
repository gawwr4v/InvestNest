package com.gourav.investnest.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gourav.investnest.data.InvestNestRepository
import com.gourav.investnest.model.FundDetail
import com.gourav.investnest.model.NavPoint
import com.gourav.investnest.model.NavRange
import com.gourav.investnest.model.WatchlistSummary
import dagger.hilt.android.lifecycle.HiltViewModel
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
    // file split up because it was getting way too huge for one screen, much cleaner now
    private val schemeCode = checkNotNull(savedStateHandle.get<String>("schemeCode")).toInt()
    private val _uiState = MutableStateFlow(FundDetailUiState())
    val uiState = _uiState.asStateFlow()
    // currentMembershipIds tracks what is actually in room, separate from the sheet's transient state
    private var currentMembershipIds: Set<Long> = emptySet()

    init {
        // parallel reactive observers, these keep the bookmark and sheet in sync with the database
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
        // snapshot what's in room right now into the sheet's selection state
        // this is how we pre-fill the checkboxes when u open the sheet
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
            // we observe the junction table reactively, whenever it changes room re-emits
            // sheet state only updated if it's closed to avoid overwriting user edits
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
        // history is already sampled to ~120 points so filtering by range is super cheap
        val filtered = when (range) {
            NavRange.SIX_MONTHS -> navHistory.filter { it.date >= latestDate.minusMonths(6) }
            NavRange.ONE_YEAR -> navHistory.filter { it.date >= latestDate.minusYears(1) }
            NavRange.ALL -> navHistory
        }
        return repository.filterNavPoints(filtered)
    }
}
