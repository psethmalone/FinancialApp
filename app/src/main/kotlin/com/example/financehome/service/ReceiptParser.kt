package com.example.financehome.service

import com.example.financehome.data.model.ReceiptItemCategory
import java.util.Locale

data class ParsedReceiptItem(
    val name: String,
    val price: Double,
    val quantity: Double = 1.0,
    val unitPrice: Double? = null,
    val category: ReceiptItemCategory = ReceiptItemCategory.GROCERY,
    val rawLine: String? = null
)

data class ParsedFuelInfo(
    val fuelGrade: String,
    val gallons: Double,
    val pricePerGallon: Double,
    val totalAmount: Double
)

data class ParsedReceipt(
    val merchant: String?,
    val totalAmount: Double?,
    val taxAmount: Double?,
    val items: List<ParsedReceiptItem>,
    val fuelInfo: ParsedFuelInfo? = null,
    val rawText: String
)

class ReceiptParser {

    companion object {
        private val PRICE_REGEX = Regex("""(?:\$|\b)(?<amount>\d{1,4}\.\d{2})\b""")
        private val GALLONS_REGEX = Regex("""(?i)(?<gal>\d{1,3}\.\d{2,3})\s*(?:gal(?:lons?)?)""")
        private val PRICE_PER_GAL_REGEX = Regex(
            """(?i)(?:price\s*(?:/|per)\s*(?:gal(?:lon)?|g)\s*:?\s*\$?(?<price1>\d\.\d{2,3})|\$?(?<price2>\d\.\d{2,3})\s*(?:/|per)\s*(?:gal(?:lon)?|g)|@\s*\$?(?<price3>\d\.\d{2,3}))"""
        )

        private val KNOWN_MERCHANTS = listOf(
            "WALMART", "KROGER", "COSTCO", "TARGET", "HEB", "TRADER JOE", "ALDI", "SAFEWAY",
            "PUBLIX", "WHOLE FOODS", "SAM'S CLUB", "MEIJER", "SHELL", "CHEVRON", "EXXON", "MOBIL",
            "BP", "TEXACO", "CIRCLE K", "7-ELEVEN", "WAWA", "BUC-EE'S", "SPEEDWAY", "RACETRAC",
            "CVS", "WALGREENS", "HOME DEPOT", "LOWE'S", "BEST BUY"
        )

        private val FUEL_KEYWORDS = listOf(
            "unleaded", "regular", "diesel", "premium", "plus", "gallons", "pump", "fuel", "gasoline", "super"
        )

        private val GROCERY_KEYWORDS = listOf(
            "milk", "egg", "bread", "banana", "apple", "orange", "meat", "chicken", "beef", "pork",
            "cheese", "yogurt", "butter", "cereal", "water", "soda", "juice", "coffee", "tea",
            "produce", "fruit", "veg", "onion", "potato", "tomato", "lettuce", "rice", "pasta",
            "sauce", "oil", "sugar", "flour", "snack", "chips", "cookie", "frozen", "deli",
            "bakery", "organic", "bacon", "berry", "grape", "avocado", "spinach", "salad"
        )

        private val HOUSEHOLD_KEYWORDS = listOf(
            "paper", "towel", "toilet", "tissue", "detergent", "soap", "clean", "bleach", "trash",
            "bag", "sponge", "shampoo", "conditioner", "tooth", "paste", "foil", "wrap", "wipe",
            "dish", "battery", "napkin", "spray", "cleaner"
        )
    }

    fun parse(rawText: String): ParsedReceipt {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val merchant = extractMerchant(lines)
        val fuelInfo = extractFuelInfo(lines)
        val taxAmount = extractTax(lines)
        val totalAmount = extractTotal(lines)
        val items = mutableListOf<ParsedReceiptItem>()

        if (fuelInfo != null) {
            items.add(
                ParsedReceiptItem(
                    name = fuelInfo.fuelGrade,
                    price = fuelInfo.totalAmount,
                    quantity = fuelInfo.gallons,
                    unitPrice = fuelInfo.pricePerGallon,
                    category = ReceiptItemCategory.FUEL,
                    rawLine = "Fuel: ${fuelInfo.gallons} GAL @ $${fuelInfo.pricePerGallon}/gal"
                )
            )
        }

        // Parse line items
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Skip lines that represent merchant, headers, subtotal, tax, total, or payment
            if (isNonItemLine(line)) {
                i++
                continue
            }

            // Check if line itself contains an item name and price
            val priceMatch = PRICE_REGEX.findAll(line).lastOrNull()
            if (priceMatch != null) {
                val priceVal = priceMatch.groups["amount"]?.value?.toDoubleOrNull()
                val namePart = line.substring(0, priceMatch.range.first).trim()
                    .replace(Regex("""^\d+\s*x\s*""", RegexOption.IGNORE_CASE), "")
                    .trimEnd('-', ':', '.', '$', ' ')

                if (priceVal != null && namePart.length >= 2 && !isNonItemLine(namePart)) {
                    val cat = categorizeItem(namePart)
                    // Don't duplicate fuel line if already captured
                    if (cat != ReceiptItemCategory.FUEL || fuelInfo == null) {
                        items.add(
                            ParsedReceiptItem(
                                name = cleanItemName(namePart),
                                price = priceVal,
                                category = cat,
                                rawLine = line
                            )
                        )
                    }
                }
            } else if (i + 1 < lines.size) {
                // Name on line i, standalone price on line i+1
                val nextLine = lines[i + 1]
                val nextPriceMatch = PRICE_REGEX.matchEntire(nextLine.trim('$', ' '))
                if (nextPriceMatch != null && line.length >= 2 && !isNonItemLine(line)) {
                    val priceVal = nextPriceMatch.groups["amount"]?.value?.toDoubleOrNull()
                    if (priceVal != null) {
                        val cat = categorizeItem(line)
                        if (cat != ReceiptItemCategory.FUEL || fuelInfo == null) {
                            items.add(
                                ParsedReceiptItem(
                                    name = cleanItemName(line),
                                    price = priceVal,
                                    category = cat,
                                    rawLine = "$line $nextLine"
                                )
                            )
                        }
                        i++ // Skip nextLine as it was consumed as the price
                    }
                }
            }

            i++
        }

        // Add Tax item if recognized and not already present
        if (taxAmount != null && taxAmount > 0.0 && items.none { it.category == ReceiptItemCategory.TAX }) {
            items.add(
                ParsedReceiptItem(
                    name = "Sales Tax",
                    price = taxAmount,
                    category = ReceiptItemCategory.TAX,
                    rawLine = "Tax $taxAmount"
                )
            )
        }

        return ParsedReceipt(
            merchant = merchant,
            totalAmount = totalAmount ?: items.sumOf { it.price }.takeIf { it > 0.0 },
            taxAmount = taxAmount,
            items = items,
            fuelInfo = fuelInfo,
            rawText = rawText
        )
    }

    private fun extractMerchant(lines: List<String>): String? {
        val topLines = lines.take(6)
        for (line in topLines) {
            val upper = line.uppercase()
            for (known in KNOWN_MERCHANTS) {
                if (upper.contains(known)) {
                    return known.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                }
            }
        }
        // Fallback: pick first clean line from the top that isn't a phone, address, or date
        return topLines.firstOrNull { l ->
            l.length in 3..30 &&
            !l.any { it.isDigit() } &&
            !l.contains("receipt", ignoreCase = true) &&
            !l.contains("welcome", ignoreCase = true)
        }
    }

    private fun extractFuelInfo(lines: List<String>): ParsedFuelInfo? {
        var gallons: Double? = null
        var ppg: Double? = null
        var total: Double? = null
        var grade = "Fuel"

        for (line in lines) {
            val upper = line.uppercase()

            if (gallons == null) {
                GALLONS_REGEX.find(line)?.let { m ->
                    gallons = m.groups["gal"]?.value?.toDoubleOrNull()
                }
            }

            if (ppg == null) {
                PRICE_PER_GAL_REGEX.find(line)?.let { m ->
                    val priceStr = m.groups["price1"]?.value
                        ?: m.groups["price2"]?.value
                        ?: m.groups["price3"]?.value
                    ppg = priceStr?.toDoubleOrNull()
                }
            }

            if (upper.contains("REGULAR") || upper.contains("UNLEADED")) grade = "Regular Unleaded"
            else if (upper.contains("DIESEL")) grade = "Diesel"
            else if (upper.contains("PREMIUM") || upper.contains("SUPER")) grade = "Premium Unleaded"

            if (upper.contains("TOTAL") || upper.contains("FUEL")) {
                PRICE_REGEX.find(line)?.let { m ->
                    total = m.groups["amount"]?.value?.toDoubleOrNull()
                }
            }
        }

        val gal = gallons
        val pricePerGal = ppg
        if (gal != null && pricePerGal != null) {
            val calculatedTotal = total ?: (gal * pricePerGal)
            return ParsedFuelInfo(
                fuelGrade = grade,
                gallons = gal,
                pricePerGallon = pricePerGal,
                totalAmount = calculatedTotal
            )
        }

        return null
    }

    private fun extractTax(lines: List<String>): Double? {
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("tax") && !lower.contains("taxable") && !lower.contains("before tax")) {
                PRICE_REGEX.find(line)?.let { m ->
                    return m.groups["amount"]?.value?.toDoubleOrNull()
                }
            }
        }
        return null
    }

    private fun extractTotal(lines: List<String>): Double? {
        // Search from bottom up for "TOTAL" or "BALANCE DUE"
        for (line in lines.asReversed()) {
            val upper = line.uppercase()
            if ((upper.contains("TOTAL") || upper.contains("BALANCE DUE") || upper.contains("AMOUNT DUE")) &&
                !upper.contains("SUBTOTAL") && !upper.contains("TAX") && !upper.contains("ITEMS")) {
                PRICE_REGEX.find(line)?.let { m ->
                    return m.groups["amount"]?.value?.toDoubleOrNull()
                }
            }
        }
        return null
    }

    fun categorizeItem(itemName: String): ReceiptItemCategory {
        val lower = itemName.lowercase()
        return when {
            FUEL_KEYWORDS.any { lower.contains(it) } -> ReceiptItemCategory.FUEL
            HOUSEHOLD_KEYWORDS.any { lower.contains(it) } -> ReceiptItemCategory.HOUSEHOLD
            GROCERY_KEYWORDS.any { lower.contains(it) } -> ReceiptItemCategory.GROCERY
            lower.contains("tax") -> ReceiptItemCategory.TAX
            lower.contains("shirt") || lower.contains("pants") || lower.contains("shoes") || lower.contains("apparel") -> ReceiptItemCategory.CLOTHING
            lower.contains("cable") || lower.contains("charger") || lower.contains("electronics") -> ReceiptItemCategory.ELECTRONICS
            else -> ReceiptItemCategory.GROCERY
        }
    }

    private fun isNonItemLine(line: String): Boolean {
        val upper = line.uppercase()
        val ignoreKeywords = listOf(
            "SUBTOTAL", "SUB TOTAL", "BALANCE DUE", "CHANGE", "CASH", "VISA", "MASTERCARD",
            "DISCOVER", "AMEX", "APPROVED", "ACCOUNT", "AUTH", "CARD", "CASHIER", "REGISTER",
            "STORE", "PHONE", "TEL", "DATE", "TIME", "THANK YOU", "ITEMS SOLD", "CUSTOMER",
            "SAVINGS", "DISCOUNT", "LOYALTY", "INVOICE", "WELCOME", "ORDER", "STREET", "HIGHWAY"
        )
        return ignoreKeywords.any { upper.startsWith(it) || upper.contains(" $it") } ||
               upper.startsWith("TAX") || upper.startsWith("TOTAL")
    }

    private fun cleanItemName(name: String): String {
        return name
            .replace(Regex("""^\d+\s*"""), "") // strip leading SKU/number
            .trim(' ', '-', '.', '#', ':')
            .lowercase()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString() } }
    }
}
