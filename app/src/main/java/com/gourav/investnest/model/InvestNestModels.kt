package com.gourav.investnest.model

import java.time.LocalDate

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
        fun fromKey(key: String): ExploreCategory {
            return entries.firstOrNull { it.key == key } ?: INDEX_FUNDS
        }
    }
}

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

data class ExploreSection(
    val category: ExploreCategory,
    val funds: List<FundSummary>,
)

data class NavPoint(
    val date: LocalDate,
    val nav: Float,
)

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

enum class NavRange(
    val label: String,
) {
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y"),
    ALL("ALL"),
}

data class WatchlistSummary(
    val id: Long,
    val name: String,
    val fundCount: Int,
)

data class WatchlistDetail(
    val id: Long,
    val name: String,
    val funds: List<FundSummary>,
)
