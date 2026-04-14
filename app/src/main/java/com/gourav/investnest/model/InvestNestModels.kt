package com.gourav.investnest.model

import java.time.LocalDate

// defines the fund categories shown on the explore screen with their search keywords
enum class ExploreCategory(
    val key: String,
    val title: String,
    val query: String,
) {
    INDEX_FUNDS(
        key = "index",
        title = "Index Funds",
        query = "index",
    ),
    BLUECHIP_FUNDS(
        key = "bluechip",
        title = "Bluechip Funds",
        query = "bluechip",
    ),
    ELSS_FUNDS(
        key = "elss",
        title = "Tax Saver (ELSS)",
        query = "tax saver",
    ),
    LARGE_CAP_FUNDS(
        key = "large_cap",
        title = "Large Cap Funds",
        query = "large cap",
    ),
    ;

    companion object {
        // helper to find a category by its string key or default to index funds
        fun fromKey(key: String): ExploreCategory {
            return entries.firstOrNull { it.key == key } ?: INDEX_FUNDS
        }
    }
}

// represents a basic summary of a mutual fund enough for list items and cards
data class FundSummary(
    val schemeCode: Int,
    val schemeName: String,
    val amcName: String = "",
    val latestNav: String = "",
    val latestNavDate: String = "",
    val schemeCategory: String = "",
    val schemeType: String = "",
    val isMetadataLoading: Boolean = false,
)

// groups a category with its list of funds for display in the explore screen sections
data class ExploreSection(
    val category: ExploreCategory,
    val funds: List<FundSummary>,
)

// represents a single nav price point on a specific date for drawing charts
data class NavPoint(
    val date: LocalDate,
    val nav: Float,
)

// holds complete information and price history for a specific mutual fund
data class FundDetail(
    val schemeCode: Int,
    val schemeName: String,
    val amcName: String,
    val latestNav: String,
    val latestNavDate: String,
    val schemeCategory: String,
    val schemeType: String,
    val navHistory: List<NavPoint>,
)

// defines the available time periods for filtering the performance chart
enum class NavRange(
    val label: String,
) {
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y"),
    ALL("ALL"),
}

// provides a brief overview of a watchlist including the number of funds inside
data class WatchlistSummary(
    val id: Long,
    val name: String,
    val fundCount: Int,
)

// contains the full content of a watchlist including the list of all saved funds
data class WatchlistDetail(
    val id: Long,
    val name: String,
    val funds: List<FundSummary>,
)
