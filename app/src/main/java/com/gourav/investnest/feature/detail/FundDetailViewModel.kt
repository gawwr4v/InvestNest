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

// this data class tracks everything shown on the fund details screen
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
    // here we extract the scheme code from the navigation arguments
    private val schemeCode = checkNotNull(savedStateHandle.get<String>("schemeCode")).toInt()
    
    // private state flow to handle updates and public state flow for the ui to observe
    private val _uiState = MutableStateFlow(FundDetailUiState())
    val uiState = _uiState.asStateFlow()

    // currentMembershipIds tracks what is actually in room, tracks which watchlists currently contain this fund according to the database
    private var currentMembershipIds: Set<Long> = emptySet()

    init {
        // these keep the bookmark and bottom sheet in sync with the database we start observing data immediately when the viewmodel is created
        observeWatchlists()
        observeMembership()
        loadDetail()
    }

    // fetches all details for the fund including its full history from the repository
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

    // updates the chart based on the selected time range like six months or one year
    fun selectRange(range: NavRange) {
        _uiState.update { state ->
            state.copy(
                selectedRange = range,
                chartPoints = state.detail?.pointsFor(range).orEmpty(),
            )
        }
    }

    // prepares and shows the bottom sheet for managing watchlists
    fun openWatchlistSheet() {
        // we copy the current database state into the bottom sheet to pre fill the checkboxes
        _uiState.update { state ->
            state.copy(
                isSheetVisible = true,
                selectedWatchlistIds = currentMembershipIds,
                newWatchlistName = "",
                saveErrorMessage = null,
            )
        }
    }

    // hides the watchlist bottom sheet and resets any temporary error messages
    fun dismissWatchlistSheet() {
        _uiState.update { state ->
            state.copy(
                isSheetVisible = false,
                isSaving = false,
                saveErrorMessage = null,
            )
        }
    }

    // toggles the selection of a watchlist in the bottom sheet ui
    fun toggleWatchlist(watchlistId: Long) {
        _uiState.update { state ->
            val updatedIds = state.selectedWatchlistIds.toMutableSet().apply {
                if (contains(watchlistId)) remove(watchlistId) else add(watchlistId)
            }
            state.copy(selectedWatchlistIds = updatedIds)
        }
    }

    // updates the name for a potential new watchlist being created
    fun updateNewWatchlistName(value: String) {
        _uiState.update { it.copy(newWatchlistName = value) }
    }

    // saves all watchlist selections and any new watchlist creation to the database
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

    // observes the list of all available watchlists reactively
    private fun observeWatchlists() {
        viewModelScope.launch {
            repository.observeWatchlists().collect { watchlists ->
                _uiState.update { state -> state.copy(availableWatchlists = watchlists) }
            }
        }
    }

    // monitors which watchlists this fund belongs to in real time
    private fun observeMembership() {
        viewModelScope.launch {
            // we use the junction table to stay in sync with the room database
            // bottom sheet state only updated if it's closed to avoid overwriting user edits
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

    // helper function to filter and sample the nav history based on the chosen time frame
    private fun FundDetail.pointsFor(range: NavRange): List<NavPoint> {
        val latestDate = navHistory.lastOrNull()?.date ?: return emptyList()
        val filtered = when (range) {
            NavRange.SIX_MONTHS -> navHistory.filter { it.date >= latestDate.minusMonths(6) }
            NavRange.ONE_YEAR -> navHistory.filter { it.date >= latestDate.minusYears(1) }
            NavRange.ALL -> navHistory
        }
        return repository.filterNavPoints(filtered)
    }
}
