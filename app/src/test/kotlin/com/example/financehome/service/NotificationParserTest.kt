package com.example.financehome.service

import com.example.financehome.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    private val parser = NotificationParser()

    @Test
    fun testCashApp_youPaidMerchant() {
        val result = parser.parse(
            packageName = "com.squareup.cash",
            title = "Cash App",
            text = "You paid $12.50 to McDonald's"
        )

        assertNotNull(result)
        assertEquals(12.50, result!!.amount, 0.001)
        assertEquals("McDonald's", result.merchant)
        assertEquals(TransactionType.DEBIT, result.type)
    }

    @Test
    fun testCashApp_cardPurchase() {
        val result = parser.parse(
            packageName = "com.squareup.cash",
            title = "Cash Card",
            text = "Cash Card purchase: $24.99 at Target"
        )

        assertNotNull(result)
        assertEquals(24.99, result!!.amount, 0.001)
        assertEquals("Target", result.merchant)
        assertEquals(TransactionType.DEBIT, result.type)
    }

    @Test
    fun testCashApp_receivedMoney() {
        val result = parser.parse(
            packageName = "com.squareup.cash",
            title = "Cash App",
            text = "$50.00 from John Doe"
        )

        assertNotNull(result)
        assertEquals(50.00, result!!.amount, 0.001)
        assertEquals("John Doe", result.merchant)
        assertEquals(TransactionType.CREDIT, result.type)
    }

    @Test
    fun testAlly_debitCardTransaction() {
        val result = parser.parse(
            packageName = "com.ally.mobile",
            title = "Debit Card Alert",
            text = "A $45.00 transaction was made using your Ally Bank Debit Card at WALMART"
        )

        assertNotNull(result)
        assertEquals(45.00, result!!.amount, 0.001)
        assertEquals("WALMART", result.merchant)
        assertEquals(TransactionType.DEBIT, result.type)
    }

    @Test
    fun testAlly_directDeposit() {
        val result = parser.parse(
            packageName = "com.ally.mobile",
            title = "Account Alert",
            text = "A direct deposit of $1,250.00 has posted"
        )

        assertNotNull(result)
        assertEquals(1250.00, result!!.amount, 0.001)
        assertEquals(TransactionType.CREDIT, result.type)
    }

    @Test
    fun testAlly_balanceNotificationIgnored() {
        val result = parser.parse(
            packageName = "com.ally.mobile",
            title = "Balance Update",
            text = "Your balance is now $3,450.00"
        )

        assertNull("Balance updates should be ignored", result)
    }

    @Test
    fun testGenericFallback_chargedAt() {
        val result = parser.parse(
            packageName = "com.someotherbank.app",
            title = "Card Alert",
            text = "You were charged $15.75 at Starbucks"
        )

        assertNotNull(result)
        assertEquals(15.75, result!!.amount, 0.001)
        assertEquals("Starbucks", result.merchant)
        assertEquals(TransactionType.DEBIT, result.type)
    }
}
