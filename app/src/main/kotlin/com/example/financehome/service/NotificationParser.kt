package com.example.financehome.service

import com.example.financehome.data.model.TransactionType

data class ParsedTransaction(
    val amount: Double,
    val merchant: String,
    val type: TransactionType,   // DEBIT or CREDIT
    val rawText: String
)

/**
 * Pure Kotlin parser that inspects notification text from known financial apps
 * (Cash App, Ally Bank, generic banking apps) and extracts transaction details.
 */
class NotificationParser {

    companion object {
        const val PACKAGE_CASH_APP = "com.squareup.cash"
        const val PACKAGE_ALLY_BANK = "com.ally.mobile"
    }

    /**
     * Attempt to parse a financial transaction from notification fields.
     */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String? = null,
        subText: String? = null
    ): ParsedTransaction? {
        val combined = buildCombined(title, text, bigText, subText) ?: return null

        return when (packageName) {
            PACKAGE_CASH_APP -> parseCashApp(combined) ?: parseGeneric(combined)
            PACKAGE_ALLY_BANK -> parseAllyBank(combined) ?: parseGeneric(combined)
            else -> parseGeneric(combined)
        }
    }

    // -----------------------------------------------------------------------
    // Cash App
    // -----------------------------------------------------------------------

    // "You paid $12.50 to McDonald's"
    private val cashAppYouPaidTo = Regex(
        """You paid \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) to (?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "You paid McDonald's $12.50"
    private val cashAppYouPaidMerchant = Regex(
        """You paid (?<merchant>[^.\n\r$]+?)\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // "You sent $12.50 to Sarah"
    private val cashAppYouSentTo = Regex(
        """You sent \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) to (?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "You sent Sarah $12.50"
    private val cashAppYouSentRecipient = Regex(
        """You sent (?<merchant>[^.\n\r$]+?)\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // "You sent $12.50"
    private val cashAppYouSent = Regex(
        """You sent \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\b""",
        RegexOption.IGNORE_CASE
    )

    // "Cash Card purchase: $12.50 at Target" or "Cash Card: $12.50 at Target"
    private val cashAppCardPurchase = Regex(
        """Cash Card(?:\s+purchase)?:\s*\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "Approved: $12.50 at Target"
    private val cashAppApprovedAt = Regex(
        """Approved:\s*\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "You spent $12.50 (with your Cash Card) at Target"
    private val cashAppSpentAt = Regex(
        """You spent\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)(?:\s+with your Cash Card)?\s+at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "Payment of $12.50 to Target"
    private val cashAppPaymentTo = Regex(
        """(?:Cash App:\s*)?Payment of \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?) to (?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "Sarah paid you $12.50"
    private val cashAppSenderPaidYou = Regex(
        """(?:Cash App:?\s*)?(?<merchant>[^.\n\r$]+?)\s+paid you \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // "Sarah sent you $12.50"
    private val cashAppSenderSentYou = Regex(
        """(?:Cash App:?\s*)?(?<merchant>[^.\n\r$]+?)\s+sent you \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // "$12.50 from Sarah" or "$12.50 received from Sarah"
    private val cashAppFromSender = Regex(
        """\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:received\s+)?from\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    private fun parseCashApp(text: String): ParsedTransaction? {
        cashAppYouPaidTo.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        cashAppYouPaidMerchant.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        cashAppCardPurchase.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        cashAppApprovedAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        cashAppSpentAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        cashAppPaymentTo.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        cashAppYouSentTo.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        cashAppYouSentRecipient.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        cashAppYouSent.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), "Cash App Transfer", TransactionType.DEBIT, text)
        }
        cashAppSenderPaidYou.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.CREDIT, text)
        }
        cashAppSenderSentYou.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.CREDIT, text)
        }
        cashAppFromSender.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.CREDIT, text)
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Ally Bank
    // -----------------------------------------------------------------------

    private val allyBalanceAlert = Regex(
        """your\s+(?:ally\s+bank\s+)?balance\s+is""",
        RegexOption.IGNORE_CASE
    )

    // "A $45.00 transaction was made using your Ally Bank Debit Card at WALMART"
    private val allyDebitCardTransaction = Regex(
        """(?:A\s+)?\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+transaction was made using your Ally Bank Debit Card at (?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "Debit card purchase of $45.00 at WALMART"
    private val allyDebitPurchaseAt = Regex(
        """Debit card purchase of \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "A transaction of $45.00 was made at WALMART"
    private val allyTransactionAt = Regex(
        """(?:A\s+)?transaction of \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+was made at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "Card ending in 1234 was charged $45.00 at WALMART"
    private val allyCardChargedAt = Regex(
        """Card ending in \d+ was charged \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "Ally Bank: $45.00 charge at WALMART"
    private val allyChargeAt = Regex(
        """(?:Ally Bank:\s*)?\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+charge at (?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    // "A direct deposit of $1,200.00 has posted"
    private val allyDirectDeposit = Regex(
        """A direct deposit of \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+has posted""",
        RegexOption.IGNORE_CASE
    )

    // "A deposit of $1,200.00 has been made"
    private val allyDeposit = Regex(
        """(?:A\s+)?deposit of \${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+has been made""",
        RegexOption.IGNORE_CASE
    )

    private fun parseAllyBank(text: String): ParsedTransaction? {
        if (allyBalanceAlert.containsMatchIn(text)) return null

        allyDebitCardTransaction.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        allyDebitPurchaseAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        allyTransactionAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        allyCardChargedAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        allyChargeAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        allyDirectDeposit.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), "Direct Deposit", TransactionType.CREDIT, text)
        }
        allyDeposit.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), "Deposit", TransactionType.CREDIT, text)
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Generic Bank Fallback
    // -----------------------------------------------------------------------

    private val genericChargedAt = Regex(
        """(?:was\s+)?charged\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    private val genericSpentAt = Regex(
        """(?:spent|purchased?)\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    private val genericPurchaseOfAt = Regex(
        """purchase of\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    private val genericPaidTo = Regex(
        """paid\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+to\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    private val genericAmountAt = Regex(
        """\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:charged|spent)?\s*at\s+(?<merchant>[^.\n\r]+)""",
        RegexOption.IGNORE_CASE
    )

    private val genericDepositOf = Regex(
        """(?:deposit|refund|credit)\s+of\s+\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val genericDebit = Regex(
        """\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+debit\b""",
        RegexOption.IGNORE_CASE
    )

    private val genericCredit = Regex(
        """\${'$'}(?<amount>[\d,]+(?:\.\d{1,2})?)\s+credit\b""",
        RegexOption.IGNORE_CASE
    )

    private fun parseGeneric(text: String): ParsedTransaction? {
        genericChargedAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        genericSpentAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        genericPurchaseOfAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        genericPaidTo.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        genericAmountAt.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), cleanMerchant(m.groups["merchant"]!!.value), TransactionType.DEBIT, text)
        }
        genericDepositOf.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), "Deposit / Refund", TransactionType.CREDIT, text)
        }
        genericDebit.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), "Unknown", TransactionType.DEBIT, text)
        }
        genericCredit.find(text)?.let { m ->
            return ParsedTransaction(parseAmount(m.groups["amount"]!!.value), "Unknown", TransactionType.CREDIT, text)
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildCombined(vararg parts: String?): String? {
        val nonBlank = parts.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }
        return if (nonBlank.isEmpty()) null else nonBlank.joinToString(separator = " ")
    }

    private fun parseAmount(raw: String): Double =
        raw.replace(",", "").toDoubleOrNull() ?: 0.0

    private fun cleanMerchant(raw: String): String {
        return raw.trim()
            .removePrefix("Cash App:")
            .removePrefix("Cash App")
            .removePrefix("Cash Card:")
            .removePrefix("Cash Card")
            .removeSuffix(".")
            .removeSuffix(",")
            .removeSuffix(";")
            .replace(Regex("""\s+on\s+\d{1,2}/\d{1,2}(?:/\d{2,4})?.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+using\s+card.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
