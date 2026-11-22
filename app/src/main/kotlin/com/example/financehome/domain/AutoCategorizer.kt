package com.example.financehome.domain

import com.example.financehome.data.model.CategoryRule
import java.util.Locale

data class CategorizationResult(
    val category: String,
    val isDiscretionary: Boolean,
    val isRecurringRent: Boolean = false,
    val matchedKeyword: String? = null
)

object AutoCategorizer {

    // Default category definitions: keyword to (category, isDiscretionary, isRecurringRent)
    private val DEFAULT_GROCERY_KEYWORDS = listOf(
        "walmart", "kroger", "aldi", "trader joe", "safeway", "h-e-b", "heb",
        "publix", "whole foods", "costco", "sam's club", "sams club", "wegmans",
        "winco", "meijer", "food lion", "giant food", "stop & shop", "supermarket",
        "grocery", "fresh market", "sprouts"
    )

    private val DEFAULT_DINING_KEYWORDS = listOf(
        "mcdonald", "starbucks", "chipotle", "chick-fil-a", "chick fil a",
        "taco bell", "wendy", "subway", "burger king", "doordash", "uber eats",
        "grubhub", "postmates", "dunkin", "panera", "domino", "pizza hut",
        "papa john", "shake shack", "five guys", "panda express", "kfc",
        "popeyes", "sonic drive", "chili's", "applebee", "olive garden",
        "texas roadhouse", "buffalo wild wings", "cheesecake factory",
        "boba", "bakery", "coffee", "roasters", "diner", "cafe", "bar & grill",
        "brewery", "restaurant", "tacos", "bbq", "sushi", "ramen"
    )

    private val DEFAULT_GAS_KEYWORDS = listOf(
        "shell", "chevron", "bp ", "exxon", "mobil", "7-eleven", "7 eleven",
        "wawa", "circle k", "speedway", "quiktrip", "marathon", "sunoco",
        "valero", "sheetz", "casey's", "gas station", "fuel", "petro"
    )

    private val DEFAULT_ENTERTAINMENT_KEYWORDS = listOf(
        "netflix", "spotify", "hulu", "disney", "hbo", "max.com", "apple.com/bill",
        "steam", "playstation", "nintendo", "xbox", "amc", "regal", "cinemark",
        "ticketmaster", "live nation", "youtube premium", "paramount", "crunchyroll",
        "cinema", "theater", "movie"
    )

    private val DEFAULT_SHOPPING_KEYWORDS = listOf(
        "amazon", "target", "ebay", "best buy", "shein", "zara", "nike",
        "adidas", "tj maxx", "marshalls", "ross store", "gap", "old navy",
        "lululemon", "sephora", "ulta", "etsy", "home depot", "lowe's",
        "ikea", "nordstrom", "macy"
    )

    private val DEFAULT_RENT_KEYWORDS = listOf(
        "rent", "leasing", "property management", "apartments", "realty",
        "landlord", "mortgage", "hoa fee", "housing"
    )

    private val DEFAULT_UTILITIES_KEYWORDS = listOf(
        "electric", "water utility", "sewer", "internet", "at&t", "verizon",
        "t-mobile", "comcast", "xfinity", "spectrum", "cox comm", "pge",
        "edison", "duke energy", "national grid", "con edison", "utility",
        "trash", "waste management"
    )

    private val DEFAULT_MEDICAL_KEYWORDS = listOf(
        "cvs", "walgreens", "rite aid", "pharmacy", "urgent care", "clinic",
        "hospital", "dental", "doctor", "health", "optometry"
    )

    /**
     * Categorizes a transaction based on user-defined rules first,
     * then falls back to built-in dictionary rules.
     */
    fun categorize(
        merchant: String,
        userRules: List<CategoryRule> = emptyList()
    ): CategorizationResult {
        val cleanMerchant = merchant.lowercase(Locale.US).trim()
        if (cleanMerchant.isEmpty()) {
            return CategorizationResult(
                category = "Uncategorized",
                isDiscretionary = true,
                isRecurringRent = false
            )
        }

        // 1. Check user custom rules first
        for (rule in userRules) {
            val key = rule.keyword.lowercase(Locale.US).trim()
            if (key.isNotEmpty() && cleanMerchant.contains(key)) {
                return CategorizationResult(
                    category = rule.category,
                    isDiscretionary = rule.isDiscretionary,
                    isRecurringRent = rule.isRecurringRent,
                    matchedKeyword = rule.keyword
                )
            }
        }

        // 2. Check Rent/Housing (Need, Recurring)
        for (kw in DEFAULT_RENT_KEYWORDS) {
            if (cleanMerchant.contains(kw)) {
                return CategorizationResult(
                    category = "Rent/Mortgage",
                    isDiscretionary = false,
                    isRecurringRent = true,
                    matchedKeyword = kw
                )
            }
        }

        // 3. Check Groceries (Need)
        for (kw in DEFAULT_GROCERY_KEYWORDS) {
            if (cleanMerchant.contains(kw)) {
                return CategorizationResult(
                    category = "Groceries",
                    isDiscretionary = false,
                    isRecurringRent = false,
                    matchedKeyword = kw
                )
            }
        }

        // 4. Check Food & Dining (Want)
        for (kw in DEFAULT_DINING_KEYWORDS) {
            if (cleanMerchant.contains(kw)) {
                return CategorizationResult(
                    category = "Food & Dining",
                    isDiscretionary = true,
                    isRecurringRent = false,
                    matchedKeyword = kw
                )
            }
        }

        // 5. Check Gas & Fuel (Need)
        for (kw in DEFAULT_GAS_KEYWORDS) {
            if (cleanMerchant.contains(kw)) {
                return CategorizationResult(
                    category = "Gas & Fuel",
                    isDiscretionary = false,
                    isRecurringRent = false,
                    matchedKeyword = kw
                )
            }
        }

        // 6. Check Utilities & Bills (Need)
        for (kw in DEFAULT_UTILITIES_KEYWORDS) {
            if (cleanMerchant.contains(kw)) {
                return CategorizationResult(
                    category = "Bills & Utilities",
                    isDiscretionary = false,
                    isRecurringRent = false,
                    matchedKeyword = kw
                )
            }
        }

        // 7. Check Entertainment (Want)
        for (kw in DEFAULT_ENTERTAINMENT_KEYWORDS) {
            if (cleanMerchant.contains(kw)) {
                return CategorizationResult(
                    category = "Entertainment",
                    isDiscretionary = true,
                    isRecurringRent = false,
                    matchedKeyword = kw
                )
            }
        }

        // 8. Check Shopping (Want)
        for (kw in DEFAULT_SHOPPING_KEYWORDS) {
            if (cleanMerchant.contains(kw)) {
                return CategorizationResult(
                    category = "Shopping",
                    isDiscretionary = true,
                    isRecurringRent = false,
                    matchedKeyword = kw
                )
            }
        }

        // 9. Check Health & Medical (Need)
        for (kw in DEFAULT_MEDICAL_KEYWORDS) {
            if (cleanMerchant.contains(kw)) {
                return CategorizationResult(
                    category = "Bills & Utilities",
                    isDiscretionary = false,
                    isRecurringRent = false,
                    matchedKeyword = kw
                )
            }
        }

        // Fallback: Uncategorized
        return CategorizationResult(
            category = "Uncategorized",
            isDiscretionary = true,
            isRecurringRent = false,
            matchedKeyword = null
        )
    }
}
