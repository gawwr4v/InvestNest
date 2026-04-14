package com.gourav.investnest.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// these are the data transfer objects for mfapi.in
// i use @SerializedName because the api uses underscores but i want camelCase in kotlin
data class SearchFundDto(
    @SerializedName("schemeCode")
    val schemeCode: Int,
    @SerializedName("schemeName")
    val schemeName: String,
)
// Note:
// Search : Sends data in camelCase (like schemeCode).
// Detail : Sends data in snake_case (like scheme_code).

// this dto holds the general information about the mutual fund like category and fund house
data class FundMetaDto(
    @SerializedName("fund_house")
    val fundHouse: String,
    @SerializedName("scheme_type")
    val schemeType: String,
    @SerializedName("scheme_category")
    val schemeCategory: String,
    @SerializedName("scheme_code")
    val schemeCode: Int,
    @SerializedName("scheme_name")
    val schemeName: String,
)

// represents a single price point for a fund on a specific date
data class NavEntryDto(
    @SerializedName("date")
    val date: String,
    @SerializedName("nav")
    val nav: String,
)

// the main wrapper object returned by the api containing both meta info and price history
data class FundResponseDto(
    @SerializedName("meta")
    val meta: FundMetaDto,
    @SerializedName("data")
    val data: List<NavEntryDto>,
    @SerializedName("status")
    val status: String,
)

// this is the retrofit service that defines all our network calls to the external api
interface MfApiService {
    // searches for funds matching a partial or full name query
    @GET("mf/search")
    suspend fun searchFunds(
        @Query("q") query: String,
    ): List<SearchFundDto>

    // i use the /latest endpoint when i just need the current price and no history
    @GET("mf/{schemeCode}/latest")
    suspend fun getLatestFund(
        @Path("schemeCode") schemeCode: Int,
    ): FundResponseDto

    // pulls the complete historical nav data which we use to draw the performance charts
    @GET("mf/{schemeCode}")
    suspend fun getFundHistory(
        @Path("schemeCode") schemeCode: Int,
    ): FundResponseDto
}
