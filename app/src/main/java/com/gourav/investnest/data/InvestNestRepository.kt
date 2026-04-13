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
class InvestNestRepository @Inject constructor(
    private val apiService: MfApiService,
    private val database: InvestNestDatabase,
    private val exploreCacheDao: ExploreCacheDao,
    private val watchlistDao: com.gourav.investnest.data.local.WatchlistDao,
) {
    private val apiFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    // reads from room dao and maps entities to ui summaries. flow means any db change instantly pushes to the ui
    fun observeWatchlists(): Flow<List<WatchlistSummary>> {
        return watchlistDao.observeWatchlistsWithFunds().map { watchlists ->
            watchlists.map { it.toSummary() }
        }
    }

    fun observeWatchlistDetail(watchlistId: Long): Flow<WatchlistDetail?> {
        return watchlistDao.observeWatchlistWithFunds(watchlistId).map { it?.toDetail() }
    }

    fun observeWatchlistIdsForFund(schemeCode: Int): Flow<Set<Long>> {
        return watchlistDao.observeWatchlistIdsForFund(schemeCode).map { it.toSet() }
    }

    // this reads the room cache mapped by category. groupby helps reconstruct the sections for offline explore
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

    suspend fun searchFundSeeds(
        query: String,
        limit: Int,
    ): List<FundSummary> {
        return apiService.searchFunds(query)
            .distinctBy { it.schemeCode }
            .take(limit)
            .map { it.toPlaceholderSummary() }
    }

    suspend fun getExploreSeeds(
        category: ExploreCategory,
        limit: Int = 4,
    ): List<FundSummary> {
        return searchFundSeeds(
            query = category.query,
            limit = limit,
        )
    }

    suspend fun getFundSummary(
        schemeCode: Int,
    ): FundSummary {
        return apiService.getLatestFund(schemeCode).toFundSummary()
    }

    suspend fun getFundDetail(
        schemeCode: Int,
    ): FundDetail {
        return apiService.getFundHistory(schemeCode).toFundDetail()
    }

    suspend fun saveExploreSection(
        category: ExploreCategory,
        funds: List<FundSummary>,
    ) {
        val now = System.currentTimeMillis()
        // converting remote summary into room entity and replacing old category data wholesale
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

    suspend fun updateFundWatchlists(
        detail: FundDetail,
        selectedWatchlistIds: Set<Long>,
        newWatchlistName: String,
    ) {
        // this is critical. we wrap folder creation, upserting the fund, deleting old links, and inserting new links inside a transaction.
        // if anything fails midway, it rolls back cleanly so we dont get corrupted state.
        database.withTransaction {
            // One transaction keeps folder creation and membership updates aligned.
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
            watchlistDao.deleteFundMemberships(detail.schemeCode)
            val crossRefs = finalIds.map { watchlistId ->
                WatchlistFundCrossRef(
                    watchlistId = watchlistId,
                    schemeCode = detail.schemeCode,
                    addedAtEpochMillis = System.currentTimeMillis(),
                )
            }
            if (crossRefs.isNotEmpty()) {
                watchlistDao.insertCrossRefs(crossRefs)
            }
        }
    }

    fun filterNavPoints(
        navPoints: List<NavPoint>,
        maxPoints: Int = 120,
    ): List<NavPoint> {
        // taking all dates and stepping through them to get approx 120 points. keeps the canvas fast without choking the ui thread.
        // Keep chart rendering light by capping the dataset before it reaches UI.
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

    private fun SearchFundDto.toPlaceholderSummary(): FundSummary {
        return FundSummary(
            schemeCode = schemeCode,
            schemeName = schemeName,
            isMetadataLoading = true,
        )
    }

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
