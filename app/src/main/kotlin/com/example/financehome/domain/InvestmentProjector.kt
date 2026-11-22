package com.example.financehome.domain

import kotlin.math.pow

object InvestmentProjector {

    /**
     * Future value of a lump sum invested today at [annualRate] (e.g. 0.07 for 7%) after [years].
     * FV = P * (1 + r)^t
     */
    fun futureValue(principal: Double, annualRate: Double, years: Int): Double {
        if (principal <= 0.0 || years <= 0) return principal
        return principal * (1.0 + annualRate).pow(years.toDouble())
    }

    /**
     * Future value of regular monthly contributions (Dollar-Cost Averaging).
     * FV = PMT * [((1 + r/12)^(12*t) - 1) / (r/12)]
     */
    fun dcaFutureValue(monthlyContrib: Double, annualRate: Double, years: Int): Double {
        if (monthlyContrib <= 0.0 || years <= 0) return 0.0
        val monthlyRate = annualRate / 12.0
        val totalMonths = years * 12
        if (monthlyRate <= 0.0) return monthlyContrib * totalMonths

        return monthlyContrib * (((1.0 + monthlyRate).pow(totalMonths.toDouble()) - 1.0) / monthlyRate)
    }

    /**
     * Returns a list of (year, futureValue) pairs from year 0 to [maxYears]
     * for plotting compound growth curves.
     */
    fun growthCurve(principal: Double, annualRate: Double, maxYears: Int): List<Pair<Int, Double>> {
        val points = mutableListOf<Pair<Int, Double>>()
        for (year in 0..maxYears) {
            points.add(year to futureValue(principal, annualRate, year))
        }
        return points
    }

    /**
     * Calculates percentage gain/loss between purchase price and current price.
     */
    fun percentageGain(buyPrice: Double, currentPrice: Double): Double {
        if (buyPrice <= 0.0) return 0.0
        return ((currentPrice - buyPrice) / buyPrice) * 100.0
    }

    /**
     * Returns what [amount] dollars invested at [buyPrice] would be worth at [currentPrice].
     * Phantom value = (amount / buyPrice) * currentPrice
     */
    fun phantomPortfolioValue(amount: Double, buyPrice: Double, currentPrice: Double): Double {
        if (buyPrice <= 0.0) return amount
        val shares = amount / buyPrice
        return shares * currentPrice
    }
}
