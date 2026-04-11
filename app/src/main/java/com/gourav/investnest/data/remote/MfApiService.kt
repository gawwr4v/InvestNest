package com.gourav.investnest.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class SearchFundDto(
    @SerializedName("schemeCode")
    val schemeCode: Int,
    @SerializedName("schemeName")
    val schemeName: String,
)

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

data class NavEntryDto(
    @SerializedName("date")
    val date: String,
    @SerializedName("nav")
    val nav: String,
)

data class FundResponseDto(
    @SerializedName("meta")
    val meta: FundMetaDto,
    @SerializedName("data")
    val data: List<NavEntryDto>,
    @SerializedName("status")
    val status: String,
)

interface MfApiService {
    @GET("mf/search")
    suspend fun searchFunds(
        @Query("q") query: String,
    ): List<SearchFundDto>

    @GET("mf/{schemeCode}/latest")
    suspend fun getLatestFund(
        @Path("schemeCode") schemeCode: Int,
    ): FundResponseDto

    @GET("mf/{schemeCode}")
    suspend fun getFundHistory(
        @Path("schemeCode") schemeCode: Int,
    ): FundResponseDto
}
