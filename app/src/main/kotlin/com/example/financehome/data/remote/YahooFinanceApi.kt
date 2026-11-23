package com.example.financehome.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Yahoo Finance chart endpoint.
 *
 * Base URL: https://query1.finance.yahoo.com/
 *
 * No API key is required. The endpoint returns OHLCV data for a given
 * symbol over the requested time range at the specified interval.
 */
interface YahooFinanceApi {

    /**
     * Fetches historical OHLCV chart data for [symbol].
     *
     * @param symbol      The ticker symbol (e.g. "AAPL", "MSFT"). Case-insensitive on the server,
     *                    but should be normalised to uppercase by the caller.
     * @param interval    Candle interval. Common values: "1m", "5m", "1h", "1d", "1wk", "1mo".
     *                    Defaults to daily ("1d").
     * @param periodStart Unix epoch seconds – start of the requested range (inclusive).
     * @param periodEnd   Unix epoch seconds – end of the requested range (exclusive).
     * @return A [YahooFinanceResponse] wrapping the chart payload.
     */
    @GET("v8/finance/chart/{symbol}")
    suspend fun getHistoricalData(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("period1") periodStart: Long,
        @Query("period2") periodEnd: Long,
    ): YahooFinanceResponse

    /**
     * Fetches recent chart & live price metadata for [symbol] using a range query.
     */
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "5d",
        @Query("interval") interval: String = "1d",
    ): YahooFinanceResponse
}
