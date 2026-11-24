package com.example.financehome.service

import com.example.financehome.data.model.TradeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrokerageNotificationParserTest {

    private val parser = NotificationParser()

    @Test
    fun testFidelity_orderToBuySharesAtPrice() {
        val result = parser.parseBrokerageTrade(
            packageName = NotificationParser.PACKAGE_FIDELITY,
            title = "Fidelity Alerts",
            text = "Your order to Buy 5 shares of VOO at $485.20 was executed on 11/24/2026."
        )

        assertNotNull(result)
        assertEquals("VOO", result!!.ticker)
        assertEquals(TradeAction.BUY, result.action)
        assertEquals(5.0, result.shares, 0.001)
        assertEquals(485.20, result.pricePerShare, 0.001)
        assertEquals(2426.0, result.totalAmount, 0.01)
    }

    @Test
    fun testFidelity_orderExecutedBoughtAt() {
        val result = parser.parseBrokerageTrade(
            packageName = NotificationParser.PACKAGE_FIDELITY,
            title = "Fidelity",
            text = "Order executed: Bought 10 AAPL @ $228.50"
        )

        assertNotNull(result)
        assertEquals("AAPL", result!!.ticker)
        assertEquals(TradeAction.BUY, result.action)
        assertEquals(10.0, result.shares, 0.001)
        assertEquals(228.50, result.pricePerShare, 0.001)
        assertEquals(2285.0, result.totalAmount, 0.01)
    }

    @Test
    fun testFidelity_fractionalShares() {
        val result = parser.parseBrokerageTrade(
            packageName = NotificationParser.PACKAGE_FIDELITY,
            title = "Order Fill",
            text = "Your order to Buy 2.500 shares of SPY at $580.00 was executed."
        )

        assertNotNull(result)
        assertEquals("SPY", result!!.ticker)
        assertEquals(TradeAction.BUY, result.action)
        assertEquals(2.5, result.shares, 0.001)
        assertEquals(580.0, result.pricePerShare, 0.001)
        assertEquals(1450.0, result.totalAmount, 0.01)
    }

    @Test
    fun testFidelity_orderToSell() {
        val result = parser.parseBrokerageTrade(
            packageName = NotificationParser.PACKAGE_FIDELITY_SPIRE,
            title = "Order Filled",
            text = "Limit order filled: Sold 4 TSLA at $215.00"
        )

        assertNotNull(result)
        assertEquals("TSLA", result!!.ticker)
        assertEquals(TradeAction.SELL, result.action)
        assertEquals(4.0, result.shares, 0.001)
        assertEquals(215.0, result.pricePerShare, 0.001)
        assertEquals(860.0, result.totalAmount, 0.01)
    }

    @Test
    fun testFidelity_marketOrderSharesForTotal() {
        val result = parser.parseBrokerageTrade(
            packageName = NotificationParser.PACKAGE_FIDELITY,
            title = "Execution Alert",
            text = "Market order filled: Bought 5 shares of QQQ for $2,500.00"
        )

        assertNotNull(result)
        assertEquals("QQQ", result!!.ticker)
        assertEquals(TradeAction.BUY, result.action)
        assertEquals(5.0, result.shares, 0.001)
        assertEquals(500.0, result.pricePerShare, 0.001)
        assertEquals(2500.0, result.totalAmount, 0.01)
    }

    @Test
    fun testFidelity_nonTradeNotification_returnsNull() {
        val result = parser.parseBrokerageTrade(
            packageName = NotificationParser.PACKAGE_FIDELITY,
            title = "Fidelity Account Notice",
            text = "Your monthly account statement is now available to view."
        )

        assertNull(result)
    }

    @Test
    fun testFidelity_differentApp_returnsNull() {
        val result = parser.parseBrokerageTrade(
            packageName = "com.unrelated.app",
            title = "Trade",
            text = "Your order to Buy 5 shares of VOO at $485.20 was executed."
        )

        assertNull(result)
    }
}
