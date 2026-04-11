package com.gourav.investnest.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.gourav.investnest.model.FundSummary
import com.gourav.investnest.model.WatchlistDetail
import com.gourav.investnest.model.WatchlistSummary
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watchlists")
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "saved_funds")
data class SavedFundEntity(
    @androidx.room.PrimaryKey
    val schemeCode: Int,
    val schemeName: String,
    val amcName: String,
    val latestNav: String,
    val latestNavDate: String,
    val schemeCategory: String,
    val schemeType: String,
)

@Entity(
    tableName = "watchlist_fund_cross_refs",
    primaryKeys = ["watchlistId", "schemeCode"],
    indices = [Index("schemeCode")],
)
data class WatchlistFundCrossRef(
    val watchlistId: Long,
    val schemeCode: Int,
    val addedAtEpochMillis: Long,
)

@Entity(
    tableName = "explore_cache",
    primaryKeys = ["categoryKey", "schemeCode"],
)
data class ExploreCacheEntity(
    val categoryKey: String,
    val schemeCode: Int,
    val position: Int,
    val schemeName: String,
    val amcName: String,
    val latestNav: String,
    val latestNavDate: String,
    val schemeCategory: String,
    val schemeType: String,
    val cachedAtEpochMillis: Long,
)

data class WatchlistWithFunds(
    @Embedded
    val watchlist: WatchlistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "schemeCode",
        associateBy = Junction(
            value = WatchlistFundCrossRef::class,
            parentColumn = "watchlistId",
            entityColumn = "schemeCode",
        ),
    )
    val funds: List<SavedFundEntity>,
)

@Dao
interface WatchlistDao {
    @Transaction
    @Query("SELECT * FROM watchlists ORDER BY createdAtEpochMillis ASC")
    fun observeWatchlistsWithFunds(): Flow<List<WatchlistWithFunds>>

    @Transaction
    @Query("SELECT * FROM watchlists WHERE id = :watchlistId")
    fun observeWatchlistWithFunds(watchlistId: Long): Flow<WatchlistWithFunds?>

    @Query("SELECT watchlistId FROM watchlist_fund_cross_refs WHERE schemeCode = :schemeCode")
    fun observeWatchlistIdsForFund(schemeCode: Int): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWatchlist(entity: WatchlistEntity): Long

    @Query("SELECT * FROM watchlists WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getWatchlistByName(name: String): WatchlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedFund(entity: SavedFundEntity)

    @Query("DELETE FROM watchlist_fund_cross_refs WHERE schemeCode = :schemeCode")
    suspend fun deleteFundMemberships(schemeCode: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(entities: List<WatchlistFundCrossRef>)
}

@Dao
interface ExploreCacheDao {
    @Query("SELECT * FROM explore_cache ORDER BY categoryKey ASC, position ASC")
    suspend fun getAll(): List<ExploreCacheEntity>

    @Query("DELETE FROM explore_cache WHERE categoryKey = :categoryKey")
    suspend fun deleteByCategory(categoryKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ExploreCacheEntity>)
}

@Database(
    entities = [
        WatchlistEntity::class,
        SavedFundEntity::class,
        WatchlistFundCrossRef::class,
        ExploreCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class InvestNestDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun exploreCacheDao(): ExploreCacheDao
}

fun WatchlistWithFunds.toSummary(): WatchlistSummary {
    return WatchlistSummary(
        id = watchlist.id,
        name = watchlist.name,
        fundCount = funds.size,
    )
}

fun WatchlistWithFunds.toDetail(): WatchlistDetail {
    return WatchlistDetail(
        id = watchlist.id,
        name = watchlist.name,
        funds = funds.map { it.toFundSummary() },
    )
}

fun SavedFundEntity.toFundSummary(): FundSummary {
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
