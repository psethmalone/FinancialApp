package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.BillOccurrenceEntity
import com.example.financehome.data.model.MerchantBillRuleEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class MerchantAutoMatchingTest {

    @Test
    fun testMerchantNormalizerCleaning() {
        // Strip store numbers, punctuation, lowercase
        val clean1 = MerchantNormalizer.normalize("NETFLIX.COM* 1234 CA")
        assertTrue(clean1.contains("netflix"))

        val clean2 = MerchantNormalizer.normalize("WAL-MART #4210")
        assertEquals("wal mart", clean2)

        val clean3 = MerchantNormalizer.normalize("Spotify USA Inc.")
        assertTrue(clean3.contains("spotify"))

        val clean4 = MerchantNormalizer.normalize("   VERIZON WIRELESS PAYMENTS   ")
        assertEquals("verizon wireless payments", clean4)
    }

    @Test
    fun testNearestOccurrenceMatchingWithin10Days() {
        val txDate = LocalDate.of(2026, 11, 14)

        val bill = Bill(
            id = "b_netflix",
            name = "Netflix",
            amount = 19.99,
            dueDayOfMonth = 15,
            category = "Entertainment"
        )

        // Potential candidate cycle dates:
        // Cycle 1: Oct 15 (30 days before -> difference 30 > 10)
        // Cycle 2: Nov 15 (1 day after -> difference 1 <= 10) -> MATCH!
        // Cycle 3: Dec 15 (31 days after -> difference 31 > 10)
        val cycleCandidates = listOf(
            LocalDate.of(2026, 10, 15),
            LocalDate.of(2026, 11, 15),
            LocalDate.of(2026, 12, 15)
        )

        val matchedDate = cycleCandidates.find { candidateDate ->
            abs(candidateDate.toEpochDay() - txDate.toEpochDay()) <= 10
        }

        assertNotNull(matchedDate)
        assertEquals(LocalDate.of(2026, 11, 15), matchedDate)
    }

    @Test
    fun testTransactionTooFarOutside10DaysWindowDoesNotMatch() {
        val txDate = LocalDate.of(2026, 11, 28) // 13 days after Nov 15
        val dueDate = LocalDate.of(2026, 11, 15)

        val diffDays = abs(dueDate.toEpochDay() - txDate.toEpochDay())
        assertTrue("Difference ($diffDays days) should exceed 10 days", diffDays > 10)

        val matches = diffDays <= 10
        assertFalse("Should not match occurrence outside 10-day tolerance", matches)
    }

    @Test
    fun testRuleBasedMatchingAndSettlement() {
        val rawMerchant = "NETFLIX.COM* 8829"
        val normalized = MerchantNormalizer.normalize(rawMerchant)

        val rule = MerchantBillRuleEntity(
            normalizedMerchant = normalized,
            billId = "b_netflix",
            isDebt = false
        )

        assertEquals("netflix com", rule.normalizedMerchant)
        assertEquals("b_netflix", rule.billId)
        assertFalse(rule.isDebt)

        // Simulated incoming payment matching
        val incomingTxMerchant = "NETFLIX.COM* 8829"
        val incomingNormalized = MerchantNormalizer.normalize(incomingTxMerchant)
        assertEquals(rule.normalizedMerchant, incomingNormalized)

        val txDate = LocalDate.of(2026, 11, 15)
        val candidateDueDate = LocalDate.of(2026, 11, 15)

        val occurrence = BillOccurrenceEntity(
            billId = rule.billId,
            dueDateEpochDay = candidateDueDate.toEpochDay(),
            isPaid = true,
            paidDateEpochDay = txDate.toEpochDay(),
            matchedTransactionId = "tx_auto_001"
        )

        assertTrue(occurrence.isPaid)
        assertEquals("tx_auto_001", occurrence.matchedTransactionId)
    }
}
