package com.gourav.investnest.data

import androidx.room.withTransaction
import com.gourav.investnest.data.local.ExploreCacheDao
import com.gourav.investnest.data.local.ExploreCacheEntity
import com.gourav.investnest.data.local.InvestNestDatabase
import com.gourav.investnest.data.local.SavedFundEntity
import com.gourav.investnest.data.local.WatchlistEntity
import com.gourav.investnest.data.local.WatchlistFundCrossRef
import com.gourav.investnest.data.local.toDetail
import com.gourav.investnest.data.local.toSummary
import com.gourav.investnest.data.remote.FundResponseDto
import com.gourav.investnest.data.remote.MfApiService
import com.gourav.investnest.data.remote.SearchFundDto
import com.gourav.investnest.model.ExploreCategory
import com.gourav.investnest.model.ExploreSection
import com.gourav.investnest.model.FundDetail
import com.gourav.investnest.model.FundSummary
import com.gourav.investnest.model.NavPoint
import com.gourav.investnest.model.WatchlistDetail
import com.gourav.investnest.model.WatchlistSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
// this repository is the single source for all data in the app, it coordinates between the network api and local room database
class InvestNestRepository @Inject constructor(
    private val apiService: MfApiService,
    private val database: InvestNestDatabase,
    private val exploreCacheDao: ExploreCacheDao,
    private val watchlistDao: com.gourav.investnest.data.local.WatchlistDao,
) {
    private val apiFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    // streams the list of all watchlists from the database, it automatically updates the ui whenever a change happens in room
    fun observeWatchlists(): Flow<List<WatchlistSummary>> {
        return watchlistDao.observeWatchlistsWithFunds().map { watchlists ->
            watchlists.map { it.toSummary() }
        }
    }

    // fetches details for a specific watchlist including all its funds, it returns a flow for real time updates
    fun observeWatchlistDetail(watchlistId: Long): Flow<WatchlistDetail?> {
        return watchlistDao.observeWatchlistWithFunds(watchlistId).map { it?.toDetail() }
    }

    // finds all watchlists that contain a specific fund, this helps in showing which watchlists a fund is already part of
    fun observeWatchlistIdsForFund(schemeCode: Int): Flow<Set<Long>> {
        return watchlistDao.observeWatchlistIdsForFund(schemeCode).map { it.toSet() }
    }

    // reads saved explore data from room and groups them by category, this allows the app to show data even when offline,
    suspend fun getCachedExploreSections(): List<ExploreSection> {
        return exploreCacheDao.getAll()
            .groupBy { it.categoryKey }
            .mapNotNull { (key, entries) ->
                val category = ExploreCategory.entries.firstOrNull { it.key == key } ?: return@mapNotNull null
                ExploreSection(
                    category = category,
                    funds = entries
                        .sortedBy { it.position }
                        .map { it.toFundSummary() },
                )
            }
            .sortedBy { it.category.ordinal }
    }

    // calls the remote api to search for funds by name, it returns a list of basic fund info limited by the count provided
    suspend fun searchFundSeeds(
        query: String,
        limit: Int,
    ): List<FundSummary> {
        return apiService.searchFunds(query)
            .distinctBy { it.schemeCode }
            .take(limit)
            .map { it.toPlaceholderSummary() }
    }

    // a helper function that searches for funds based on specific category keywords
    suspend fun getExploreSeeds(
        category: ExploreCategory,
        limit: Int = 4,
    ): List<FundSummary> {
        return searchFundSeeds(
            query = category.query,
            limit = limit,
        )
    }

    // fetches the latest nav and basic metadata for a single fund from the api
    suspend fun getFundSummary(
        schemeCode: Int,
    ): FundSummary {
        return apiService.getLatestFund(schemeCode).toFundSummary()
    }

    // gets full historical nav data for a fund, it also applies a filter to keep the chart data light
    suspend fun getFundDetail(
        schemeCode: Int,
    ): FundDetail {
        return apiService.getFundHistory(schemeCode).toFundDetail()
    }

    // replaces the local cache for a specific category with fresh data from the network
    suspend fun saveExploreSection(
        category: ExploreCategory,
        funds: List<FundSummary>,
    ) {
        val now = System.currentTimeMillis()
        val cacheEntries = funds.mapIndexed { index, fund ->
            ExploreCacheEntity(
                categoryKey = category.key,
                schemeCode = fund.schemeCode,
                position = index,
                schemeName = fund.schemeName,
                amcName = fund.amcName,
                latestNav = fund.latestNav,
                latestNavDate = fund.latestNavDate,
                schemeCategory = fund.schemeCategory,
                schemeType = fund.schemeType,
                cachedAtEpochMillis = now,
            )
        }
        exploreCacheDao.deleteByCategory(category.key)
        if (cacheEntries.isNotEmpty()) {
            exploreCacheDao.insertAll(cacheEntries)
        }
    }

    // handles complex logic of creating new watchlists and updating fund memberships, it uses a database transaction to ensure data integrity
    // makes sure there is zero risk of the fund being 'orphaned' or lost if the app crashes midway
    suspend fun updateFundWatchlists(
        detail: FundDetail,
        selectedWatchlistIds: Set<Long>,
        newWatchlistName: String,
    ) {
        // this is critical. we wrap folder creation, upserting the fund, deleting old links, and inserting new links inside a transaction
        // if anything fails midway, it rolls back cleanly so we dont get corrupted state
        database.withTransaction {
            // One transaction keeps folder creation and membership updates aligned
            val finalIds = selectedWatchlistIds.toMutableSet()
            val trimmedName = newWatchlistName.trim()
            if (trimmedName.isNotEmpty()) {
                val existingWatchlist = watchlistDao.getWatchlistByName(trimmedName)
                val watchlistId = existingWatchlist?.id ?: watchlistDao.insertWatchlist(
                    WatchlistEntity(
                        name = trimmedName,
                        createdAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                finalIds += watchlistId
            }

            watchlistDao.upsertSavedFund(detail.toSavedFundEntity())
            watchlistDao.deleteFundMemberships(detail.schemeCode) // delete all existing watchlist memberships for this fund
            val crossRefs = finalIds.map { watchlistId ->
                WatchlistFundCrossRef(
                    watchlistId = watchlistId,
                    schemeCode = detail.schemeCode,
                    addedAtEpochMillis = System.currentTimeMillis(),
                )
            }
            if (crossRefs.isNotEmpty()) {
                watchlistDao.insertCrossRefs(crossRefs) //  insert the new set of relationships from the checkboxes the user just clicked
            }
        }
    }

    // reduces the number of data points for the nav chart, this prevents the ui from lagging when rendering large datasets
    fun filterNavPoints(
        navPoints: List<NavPoint>,
        maxPoints: Int = 120,
    ): List<NavPoint> {
        // taking all dates and stepping through them to get approx 120 points. keeps the canvas fast without choking the ui thread
        // keep chart rendering light by capping the dataset before it reaches UI
        if (navPoints.size <= maxPoints) {
            return navPoints
        }
        val step = navPoints.size.toFloat() / maxPoints.toFloat()
        val sampled = buildList {
            var pointer = 0f
            while (pointer < navPoints.size) {
                add(navPoints[pointer.toInt()])
                pointer += step
            }
        }
        return if (sampled.lastOrNull() == navPoints.last()) sampled else sampled + navPoints.last()
    }

    // private helper to convert a search dto into a placeholder fund summary
    private fun SearchFundDto.toPlaceholderSummary(): FundSummary {
        return FundSummary(
            schemeCode = schemeCode,
            schemeName = schemeName,
            isMetadataLoading = true,
        )
    }

    // private helper to convert a room entity back into a ui fund summary model
    private fun ExploreCacheEntity.toFundSummary(): FundSummary {
        return FundSummary(
            schemeCode = schemeCode,
            schemeName = schemeName,
            amcName = amcName,
            latestNav = latestNav,
            latestNavDate = latestNavDate,
            schemeCategory = schemeCategory,
            schemeType = schemeType,
        )
    }

    // private helper to convert the remote api response into a ui fund summary model
    private fun FundResponseDto.toFundSummary(): FundSummary {
        val latestEntry = data.firstOrNull()
        return FundSummary(
            schemeCode = meta.schemeCode,
            schemeName = meta.schemeName,
            amcName = meta.fundHouse,
            latestNav = latestEntry?.nav.orEmpty(),
            latestNavDate = latestEntry?.date.orEmpty(),
            schemeCategory = meta.schemeCategory,
            schemeType = meta.schemeType,
        )
    }

    // private helper to convert the remote api response into a full ui fund detail model
    private fun FundResponseDto.toFundDetail(): FundDetail {
        val latestEntry = data.firstOrNull()
        val sortedPoints = data.mapNotNull { entry ->
            runCatching {
                NavPoint(
                    date = LocalDate.parse(entry.date, apiFormatter),
                    nav = entry.nav.toFloat(),
                )
            }.getOrNull()
        }.sortedBy { it.date }

        return FundDetail(
            schemeCode = meta.schemeCode,
            schemeName = meta.schemeName,
            amcName = meta.fundHouse,
            latestNav = latestEntry?.nav.orEmpty(),
            latestNavDate = latestEntry?.date.orEmpty(),
            schemeCategory = meta.schemeCategory,
            schemeType = meta.schemeType,
            navHistory = filterNavPoints(sortedPoints),
        )
    }

    // private helper to convert a fund detail model into a room entity for saving
    private fun FundDetail.toSavedFundEntity(): SavedFundEntity {
        return SavedFundEntity(
            schemeCode = schemeCode,
            schemeName = schemeName,
            amcName = amcName,
            latestNav = latestNav,
            latestNavDate = latestNavDate,
            schemeCategory = schemeCategory,
            schemeType = schemeType,
        )
    }
}
