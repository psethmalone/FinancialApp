package com.example.financehome.service

import com.example.financehome.data.model.ReceiptItemCategory
import org.junit.Assert.*
import org.junit.Test

class ReceiptParserTest {

    private val parser = ReceiptParser()

    @Test
    fun testGroceryReceiptParsing() {
        val rawReceipt = """
            WALMART SUPERCENTER
            STORE #1234
            05/14/2026 14:32
            -----------------------------
            ORGANIC MILK 1 GAL     $4.29
            LARGE EGGS DOZEN        $3.49
            FRESH BANANAS           $1.89
            BOUNTY PAPER TOWELS     $8.99
            CHARMIN TOILET PAPER   $12.50
            SUBTOTAL               $31.16
            SALES TAX               $2.49
            TOTAL                  $33.65
            VISA ENDING IN 4321
            AUTH CODE 987654
            THANK YOU FOR SHOPPING!
        """.trimIndent()

        val parsed = parser.parse(rawReceipt)

        assertEquals("Walmart", parsed.merchant)
        assertEquals(33.65, parsed.totalAmount ?: 0.0, 0.01)
        assertEquals(2.49, parsed.taxAmount ?: 0.0, 0.01)

        val groceryItems = parsed.items.filter { it.category == ReceiptItemCategory.GROCERY }
        val householdItems = parsed.items.filter { it.category == ReceiptItemCategory.HOUSEHOLD }
        val taxItems = parsed.items.filter { it.category == ReceiptItemCategory.TAX }

        assertTrue("Should contain groceries", groceryItems.isNotEmpty())
        assertTrue("Should contain household items", householdItems.isNotEmpty())
        assertTrue("Should contain tax item", taxItems.isNotEmpty())

        // Check specific items
        assertTrue("Contains milk", groceryItems.any { it.name.contains("Milk", ignoreCase = true) && it.price == 4.29 })
        assertTrue("Contains eggs", groceryItems.any { it.name.contains("Eggs", ignoreCase = true) && it.price == 3.49 })
        assertTrue("Contains bananas", groceryItems.any { it.name.contains("Bananas", ignoreCase = true) && it.price == 1.89 })
        assertTrue("Contains paper towels", householdItems.any { it.name.contains("Paper", ignoreCase = true) && it.price == 8.99 })

        // Ensure non-item noise lines were filtered
        assertFalse("Subtotal should not be an item", parsed.items.any { it.name.contains("Subtotal", ignoreCase = true) })
        assertFalse("Auth code should not be an item", parsed.items.any { it.name.contains("Auth", ignoreCase = true) })
        assertFalse("Visa should not be an item", parsed.items.any { it.name.contains("Visa", ignoreCase = true) })
    }

    @Test
    fun testFuelReceiptParsing() {
        val rawFuelReceipt = """
            SHELL OIL
            123 MAIN STREET
            DALLAS TX
            PUMP 04
            REGULAR UNLEADED
            14.250 GAL
            PRICE/GAL $3.199
            FUEL TOTAL $45.59
            APPROVED
            THANK YOU
        """.trimIndent()

        val parsed = parser.parse(rawFuelReceipt)

        assertEquals("Shell", parsed.merchant)
        assertNotNull(parsed.fuelInfo)
        val fuel = parsed.fuelInfo!!
        assertEquals("Regular Unleaded", fuel.fuelGrade)
        assertEquals(14.250, fuel.gallons, 0.001)
        assertEquals(3.199, fuel.pricePerGallon, 0.001)
        assertEquals(45.59, fuel.totalAmount, 0.01)

        val fuelItem = parsed.items.firstOrNull { it.category == ReceiptItemCategory.FUEL }
        assertNotNull(fuelItem)
        assertEquals("Regular Unleaded", fuelItem!!.name)
        assertEquals(45.59, fuelItem.price, 0.01)
        assertEquals(14.250, fuelItem.quantity, 0.001)
        assertEquals(3.199, fuelItem.unitPrice ?: 0.0, 0.001)
    }

    @Test
    fun testMultiLineNameAndPriceReceipt() {
        val rawReceipt = """
            KROGER
            FRESH STRAWBERRIES
            $3.99
            CHEDDAR CHEESE BLOCK
            $4.49
            SUBTOTAL $8.48
            TOTAL $8.48
        """.trimIndent()

        val parsed = parser.parse(rawReceipt)

        assertEquals("Kroger", parsed.merchant)
        assertEquals(2, parsed.items.size)
        assertTrue(parsed.items.any { it.name.contains("Strawberries", ignoreCase = true) && it.price == 3.99 })
        assertTrue(parsed.items.any { it.name.contains("Cheese", ignoreCase = true) && it.price == 4.49 })
    }

    @Test
    fun testCategoryClassification() {
        assertEquals(ReceiptItemCategory.FUEL, parser.categorizeItem("Regular Unleaded Pump 2"))
        assertEquals(ReceiptItemCategory.FUEL, parser.categorizeItem("Diesel Fuel"))
        assertEquals(ReceiptItemCategory.GROCERY, parser.categorizeItem("Whole Milk 2%"))
        assertEquals(ReceiptItemCategory.GROCERY, parser.categorizeItem("Honeycrisp Apples"))
        assertEquals(ReceiptItemCategory.GROCERY, parser.categorizeItem("Ground Beef 80/20"))
        assertEquals(ReceiptItemCategory.HOUSEHOLD, parser.categorizeItem("Bounty Paper Towels"))
        assertEquals(ReceiptItemCategory.HOUSEHOLD, parser.categorizeItem("Dawn Dish Detergent"))
        assertEquals(ReceiptItemCategory.CLOTHING, parser.categorizeItem("Cotton T-Shirt"))
        assertEquals(ReceiptItemCategory.ELECTRONICS, parser.categorizeItem("USB-C Fast Charger Cable"))
    }
}
