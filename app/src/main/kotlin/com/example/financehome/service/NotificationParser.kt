package com.example.financehome.service

import com.example.financehome.data.model.TransactionType

// ---------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------

data class ParsedTransaction(
    val amount: Double,
    val merchant: String,
    val type: TransactionType,   // DEBIT or CREDIT
    val rawText: String
)

// ---------------------------------------------------------------------------
// Parser
// ---------------------------------------------------------------------------

/**
 * Pure Kotlin (no Android dependencies) parser that inspects notification text
 * from known financial apps and returns a [ParsedTransaction], or null when the
 * notification is not a financial transaction (e.g. balance alerts, marketing).
 */
class NotificationParser {

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Attempt to parse a financial transaction from a notification.
     *
     * @param packageName  Source app package name (used to select parser strategy).
     * @param title        Notification title string (may be null).
     * @param text         Notification body text (may be null).
     * @return             A [ParsedTransaction] if a supported pattern matched, null otherwise.
     */
    fun parse(packageName: String, title: String?, text: String?): ParsedTransaction? {
        // Combine title + text into a single string for pattern matching.
        // Some apps put critical info in the title, some in the body.
        val combined = buildCombined(title, text) ?: return null

        return when (packageName) {
            PACKAGE_CASH_APP  -> parseCashApp(combined)
            PACKAGE_ALLY_BANK -> parseAllyBank(combined)
            else              -> parseGeneric(combined)
        }
    }

    // -----------------------------------------------------------------------
    // Package constants
    // -----------------------------------------------------------------------

    companion object {
        const val PACKAGE_CASH_APP  = "com.squareup.cash"
        const val PACKAGE_ALLY_BANK = "com.ally.mobile"

        // Reusable dollar-amount group: matches $1,200.00 or $12.50 or $5
        // Group name: "amount"
        private const val AMOUNT_PATTERN = """\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)"""
    }

    // -----------------------------------------------------------------------
    // Cash App patterns
    // -----------------------------------------------------------------------

    /**
     * "You paid $12.50 to John"
     * -> DEBIT, merchant = "John"
     */
    private val cashAppYouPaid = Regex(
        """You paid \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) to (?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "You sent $12.50"  (no recipient)
     * -> DEBIT, merchant = "Cash App Transfer"
     */
    private val cashAppYouSent = Regex(
        """You sent \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "$12.50 from Sarah"
     * -> CREDIT, merchant = "Sarah"
     */
    private val cashAppFromSender = Regex(
        """\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) from (?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "Sarah paid you $12.50"
     * -> CREDIT, merchant = "Sarah"
     */
    private val cashAppSenderPaidYou = Regex(
        """(?<merchant>.+?) paid you \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "Cash App: Payment of $12.50 to Merchant"
     * -> DEBIT, merchant = "Merchant"
     */
    private val cashAppPaymentTo = Regex(
        """Cash App:\s+Payment of \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) to (?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "Cash Card purchase: $12.50 at STARBUCKS"
     * -> DEBIT, merchant = "STARBUCKS"
     */
    private val cashAppCardPurchase = Regex(
        """Cash Card purchase:\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) at (?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    private fun parseCashApp(text: String): ParsedTransaction? {
        // --- DEBIT patterns ---

        cashAppYouPaid.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        cashAppYouSent.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = "Cash App Transfer",
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        cashAppPaymentTo.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        cashAppCardPurchase.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        // --- CREDIT patterns ---

        cashAppSenderPaidYou.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.CREDIT,
                rawText  = text
            )
        }

        cashAppFromSender.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.CREDIT,
                rawText  = text
            )
        }

        return null
    }

    // -----------------------------------------------------------------------
    // Ally Bank patterns
    // -----------------------------------------------------------------------

    /**
     * Ignore balance-alert notifications — they don't describe a transaction.
     * "Your balance is now $X" / "Your Ally Bank balance is $X"
     */
    private val allyBalanceAlert = Regex(
        """your\s+(?:ally\s+bank\s+)?balance\s+is""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "A $45.00 transaction was made using your Ally Bank Debit Card at WALMART"
     * -> DEBIT, merchant = "WALMART"
     */
    private val allyDebitCardTransaction = Regex(
        """A \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) transaction was made using your Ally Bank Debit Card at (?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "Ally Bank: $45.00 charge at WALMART"
     * -> DEBIT, merchant = "WALMART"
     */
    private val allyChargeAt = Regex(
        """Ally Bank:\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) charge at (?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "Ally Bank: A deposit of $1,200.00 has been made to your account"
     * -> CREDIT, merchant = "Deposit"
     */
    private val allyDeposit = Regex(
        """Ally Bank:\s+A deposit of \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) has been made""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "A direct deposit of $1,200.00 has posted"
     * -> CREDIT, merchant = "Direct Deposit"
     */
    private val allyDirectDeposit = Regex(
        """A direct deposit of \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) has posted""",
        RegexOption.IGNORE_CASE
    )

    private fun parseAllyBank(text: String): ParsedTransaction? {
        // Explicitly ignore balance alerts
        if (allyBalanceAlert.containsMatchIn(text)) return null

        // --- DEBIT patterns ---

        allyDebitCardTransaction.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        allyChargeAt.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        // --- CREDIT patterns ---

        allyDirectDeposit.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = "Direct Deposit",
                type     = TransactionType.CREDIT,
                rawText  = text
            )
        }

        allyDeposit.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = "Deposit",
                type     = TransactionType.CREDIT,
                rawText  = text
            )
        }

        return null
    }

    // -----------------------------------------------------------------------
    // Generic / fallback patterns (any app)
    // -----------------------------------------------------------------------

    /**
     * "charged $X at MERCHANT"
     * -> DEBIT
     */
    private val genericChargedAt = Regex(
        """charged\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) at (?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "spent $X at MERCHANT"
     * -> DEBIT
     */
    private val genericSpentAt = Regex(
        """spent\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) at (?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "$X debit"
     * -> DEBIT, merchant = "Unknown"
     */
    private val genericDebit = Regex(
        """\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+debit\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "$X credit"
     * -> CREDIT, merchant = "Unknown"
     */
    private val genericCredit = Regex(
        """\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+credit\b""",
        RegexOption.IGNORE_CASE
    )

    private fun parseGeneric(text: String): ParsedTransaction? {
        genericChargedAt.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        genericSpentAt.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = m.groups["merchant"]!!.value.trim(),
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        genericDebit.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = "Unknown",
                type     = TransactionType.DEBIT,
                rawText  = text
            )
        }

        genericCredit.find(text)?.let { m ->
            return ParsedTransaction(
                amount   = parseAmount(m.groups["amount"]!!.value),
                merchant = "Unknown",
                type     = TransactionType.CREDIT,
                rawText  = text
            )
        }

        return null
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Combine title and text into a single string for unified pattern matching.
     * Returns null only if both inputs are null/blank.
     */
    private fun buildCombined(title: String?, text: String?): String? {
        val parts = listOfNotNull(
            title?.takeIf { it.isNotBlank() },
            text?.takeIf  { it.isNotBlank() }
        )
        return if (parts.isEmpty()) null else parts.joinToString(separator = " ")
    }

    /**
     * Strip commas from amount strings like "1,200.00" then parse to Double.
     * Returns 0.0 on any parse failure (should not happen when regex already matched).
     */
    private fun parseAmount(raw: String): Double =
        raw.replace(",", "").toDoubleOrNull() ?: 0.0
}
