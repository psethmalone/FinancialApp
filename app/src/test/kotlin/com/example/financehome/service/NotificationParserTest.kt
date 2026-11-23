package com.example.financehome.service

import com.example.financehome.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    private val parser = NotificationParser()

    @Test
    fun testCashApp_youPaidToMerchant() {
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
    fun testCashApp_youPaidMerchantDirect() {
        val result = parser.parse(
            packageName = "com.squareup.cash",
            title = "Cash App",
            text = "You paid McDonald's $12.50"
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
    fun testCashApp_approvedAt() {
        val result = parser.parse(
            packageName = "com.squareup.cash",
            title = "Cash App",
            text = "Approved: $8.50 at Chipotle"
        )

        assertNotNull(result)
        assertEquals(8.50, result!!.amount, 0.001)
        assertEquals("Chipotle", result.merchant)
        assertEquals(TransactionType.DEBIT, result.type)
    }

    @Test
    fun testCashApp_spentWithCashCard() {
        val result = parser.parse(
            packageName = "com.squareup.cash",
            title = "Cash Card",
            text = "You spent $32.00 with your Cash Card at Kroger"
        )

        assertNotNull(result)
        assertEquals(32.00, result!!.amount, 0.001)
        assertEquals("Kroger", result.merchant)
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
    fun testCashApp_senderSentYou() {
        val result = parser.parse(
            packageName = "com.squareup.cash",
            title = "Cash App",
            text = "Alex sent you $25.00"
        )

        assertNotNull(result)
        assertEquals(25.00, result!!.amount, 0.001)
        assertEquals("Alex", result.merchant)
        assertEquals(TransactionType.CREDIT, result.type)
    }

    @Test
    fun testCashApp_bigTextExtraction() {
        val result = parser.parse(
            packageName = "com.squareup.cash",
            title = "Cash App",
            text = null,
            bigText = "You paid $14.20 to Wendy's"
        )

        assertNotNull(result)
        assertEquals(14.20, result!!.amount, 0.001)
        assertEquals("Wendy's", result.merchant)
        assertEquals(TransactionType.DEBIT, result.type)
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
    fun testAlly_debitPurchaseAt() {
        val result = parser.parse(
            packageName = "com.ally.mobile",
            title = "Card Alert",
            text = "Debit card purchase of $55.00 at Costco"
        )

        assertNotNull(result)
        assertEquals(55.00, result!!.amount, 0.001)
        assertEquals("Costco", result.merchant)
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
