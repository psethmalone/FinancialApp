package com.example.financehome.domain

import com.example.financehome.data.model.CategoryRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCategorizerTest {

    @Test
    fun testGroceries_walmartAndKroger() {
        val r1 = AutoCategorizer.categorize("Walmart Supercenter #1234")
        assertEquals("Groceries", r1.category)
        assertFalse("Groceries should not be discretionary", r1.isDiscretionary)
        assertFalse(r1.isRecurringRent)

        val r2 = AutoCategorizer.categorize("KROGER STORE #045")
        assertEquals("Groceries", r2.category)
        assertFalse(r2.isDiscretionary)
    }

    @Test
    fun testDining_mcdonaldsAndStarbucks() {
        val r1 = AutoCategorizer.categorize("McDonald's 4920")
        assertEquals("Food & Dining", r1.category)
        assertTrue("Dining out should be discretionary", r1.isDiscretionary)
        assertFalse(r1.isRecurringRent)

        val r2 = AutoCategorizer.categorize("STARBUCKS STORE #1203")
        assertEquals("Food & Dining", r2.category)
        assertTrue(r2.isDiscretionary)
    }

    @Test
    fun testGas_shellAndChevron() {
        val r1 = AutoCategorizer.categorize("Shell Oil 5754")
        assertEquals("Gas & Fuel", r1.category)
        assertFalse(r1.isDiscretionary)

        val r2 = AutoCategorizer.categorize("Chevron 0392")
        assertEquals("Gas & Fuel", r2.category)
        assertFalse(r2.isDiscretionary)
    }

    @Test
    fun testRent_propertyManagementAndLeasing() {
        val r1 = AutoCategorizer.categorize("Highland Park Apartments Rent")
        assertEquals("Rent/Mortgage", r1.category)
        assertFalse(r1.isDiscretionary)
        assertTrue("Should be marked as recurring rent", r1.isRecurringRent)

        val r2 = AutoCategorizer.categorize("Greystar Property Management")
        assertEquals("Rent/Mortgage", r2.category)
        assertFalse(r2.isDiscretionary)
        assertTrue(r2.isRecurringRent)
    }

    @Test
    fun testCustomUserRule_overridesDefault() {
        // Suppose user has a rule that "Target" is Groceries instead of Shopping
        val userRules = listOf(
            CategoryRule(
                id = "1",
                keyword = "target",
                category = "Groceries",
                isDiscretionary = false,
                isRecurringRent = false
            ),
            CategoryRule(
                id = "2",
                keyword = "john landlord",
                category = "Rent/Mortgage",
                isDiscretionary = false,
                isRecurringRent = true
            )
        )

        val r1 = AutoCategorizer.categorize("Target T-0492", userRules)
        assertEquals("Groceries", r1.category)
        assertFalse(r1.isDiscretionary)

        val r2 = AutoCategorizer.categorize("Zelle payment to John Landlord", userRules)
        assertEquals("Rent/Mortgage", r2.category)
        assertTrue("Tagged as recurring rent", r2.isRecurringRent)
    }

    @Test
    fun testUnknownMerchant_returnsUncategorized() {
        val result = AutoCategorizer.categorize("Random Unknown Specialty Boutique")
        assertEquals("Uncategorized", result.category)
        assertTrue(result.isDiscretionary)
        assertFalse(result.isRecurringRent)
    }
}
