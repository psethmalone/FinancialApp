package com.example.financehome.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class InvestmentProjectorTest {

    @Test
    fun testFutureValue_compoundGrowth() {
        // $100 lump sum at 10% annual return for 10 years = 100 * (1.10)^10 = ~$259.37
        val fv = InvestmentProjector.futureValue(100.0, 0.10, 10)
        assertEquals(259.37, fv, 0.5)
    }

    @Test
    fun testDcaFutureValue() {
        // $100/mo at 7% return for 20 years
        val dca = InvestmentProjector.dcaFutureValue(100.0, 0.07, 20)
        assertTrue("DCA should be over $50,000", dca > 50000.0)
    }

    @Test
    fun testPercentageGain() {
        // Buy price $10, Current price $12 -> +20%
        val gain = InvestmentProjector.percentageGain(10.0, 12.0)
        assertEquals(20.0, gain, 0.001)

        // Buy price $10, Current price $8 -> -20%
        val loss = InvestmentProjector.percentageGain(10.0, 8.0)
        assertEquals(-20.0, loss, 0.001)
    }

    @Test
    fun testPhantomPortfolioValue() {
        // User spent $10 at McDonald's when SPY was $500.
        // Today SPY is $600.
        // Phantom value should be: (10 / 500) * 600 = $12.00
        val phantomVal = InvestmentProjector.phantomPortfolioValue(10.0, 500.0, 600.0)
        assertEquals(12.0, phantomVal, 0.001)
    }
}
