package com.example.financehome.data.remote

import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------------------
// Top-level response wrapper
// ---------------------------------------------------------------------------

/**
 * Root object returned by the Yahoo Finance chart endpoint.
 *
 * JSON shape:
 * ```json
 * { "chart": { "result": [...], "error": null } }
 * ```
 */
data class YahooFinanceResponse(
    @SerializedName("chart")
    val chart: YahooChart,
)

// ---------------------------------------------------------------------------
// chart
// ---------------------------------------------------------------------------

/**
 * Container for the chart payload.
 *
 * [result] is `null` when the ticker is unrecognised or the request fails
 * at the Yahoo side, in which case [error] will be non-null.
 */
data class YahooChart(
    /** One entry per requested symbol. Typically a single-element list. */
    @SerializedName("result")
    val result: List<YahooResult>?,

    /** Non-null when Yahoo Finance returns an application-level error. */
    @SerializedName("error")
    val error: YahooError?,
)

// ---------------------------------------------------------------------------
// chart.result[n]
// ---------------------------------------------------------------------------

/**
 * Per-symbol result object.
 *
 * [timestamp] is a parallel array to the quote arrays inside [indicators].
 * Index `i` in [timestamp] corresponds to index `i` in every quote array.
 */
data class YahooResult(
    /**
     * Unix epoch seconds for each candle's open time.
     * May be `null` if Yahoo returns no data for the requested range.
     */
    @SerializedName("timestamp")
    val timestamp: List<Long>?,

    @SerializedName("indicators")
    val indicators: YahooIndicators?,
)

// ---------------------------------------------------------------------------
// chart.result[n].indicators
// ---------------------------------------------------------------------------

/**
 * Wrapper that holds the OHLCV quote arrays.
 *
 * Yahoo Finance also returns an `adjclose` list inside this object, but it is
 * not modelled here because we only need closing prices for P&L calculations.
 */
data class YahooIndicators(
    /** Typically a single-element list containing OHLCV arrays. */
    @SerializedName("quote")
    val quote: List<YahooQuote>?,
)

// ---------------------------------------------------------------------------
// chart.result[n].indicators.quote[n]
// ---------------------------------------------------------------------------

/**
 * Per-candle OHLCV data.
 *
 * Individual list elements can be `null` for sessions where trading was
 * halted or data is otherwise unavailable (e.g. a holiday in the middle of
 * a weekly range). Callers should guard against `null` entries.
 */
data class YahooQuote(
    /** Closing price for each candle, parallel to [YahooResult.timestamp]. */
    @SerializedName("close")
    val close: List<Double?>?,

    /** Opening price for each candle. */
    @SerializedName("open")
    val open: List<Double?>?,

    /** Intraday high for each candle. */
    @SerializedName("high")
    val high: List<Double?>?,

    /** Intraday low for each candle. */
    @SerializedName("low")
    val low: List<Double?>?,

    /** Volume for each candle. */
    @SerializedName("volume")
    val volume: List<Long?>?,
)

// ---------------------------------------------------------------------------
// Error object (returned when Yahoo reports an application-level problem)
// ---------------------------------------------------------------------------

/**
 * Application-level error returned by Yahoo Finance.
 *
 * Example:
 * ```json
 * { "code": "Not Found", "description": "No fundamentals data found for any of the summaryTypes=..." }
 * ```
 */
data class YahooError(
    @SerializedName("code")
    val code: String?,

    @SerializedName("description")
    val description: String?,
)
