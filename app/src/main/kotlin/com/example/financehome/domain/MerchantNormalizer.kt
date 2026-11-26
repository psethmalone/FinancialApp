package com.example.financehome.domain

import java.util.Locale

object MerchantNormalizer {

    /**
     * Normalizes a merchant name string for matching:
     * - Lowercases
     * - Strips store/location identifiers (#1234, *1234, store 1234)
     * - Strips corporate suffixes (inc, llc, corp, ltd, co)
     * - Strips non-alphanumeric punctuation and collapses whitespace
     */
    fun normalize(merchant: String): String {
        var cleaned = merchant.lowercase(Locale.US).trim()
        if (cleaned.isEmpty()) return ""

        // Remove store IDs or reference codes like #1234, *1234, * 1234, store 1234
        cleaned = cleaned.replace(Regex("""[#*]\s*\d+"""), " ")
        cleaned = cleaned.replace(Regex("""\bstore\s*\d+\b"""), " ")
        cleaned = cleaned.replace(Regex("""\b(inc|llc|corp|corporation|ltd|co)\b\.?"""), " ")
        // Remove trailing or isolated reference numbers (4+ digits)
        cleaned = cleaned.replace(Regex("""\b\d{4,}\b"""), " ")
        // Remove special punctuation, keep letters, numbers and basic spaces
        cleaned = cleaned.replace(Regex("""[^a-z0-9\s]"""), " ")
        // Collapse whitespace
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

        return cleaned
    }
}
