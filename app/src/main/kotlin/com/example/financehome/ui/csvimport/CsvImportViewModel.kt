package com.example.financehome.ui.csvimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.repository.TransactionRepository
import com.opencsv.CSVReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

sealed class CsvImportState {
    object Idle : CsvImportState()
    object Parsing : CsvImportState()
    data class Preview(val totalRows: Int) : CsvImportState()
    object Importing : CsvImportState()
    data class Success(val count: Int) : CsvImportState()
    data class Error(val message: String) : CsvImportState()
}

data class CsvParsedRow(
    val merchant: String,
    val amount: Double,
    val type: TransactionType,
    val dateText: String,
    val timestampMillis: Long,
    val category: String = "Uncategorized",
    val isDiscretionary: Boolean = true,
    val isRecurringRent: Boolean = false
)

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    val state = MutableStateFlow<CsvImportState>(CsvImportState.Idle)
    val parsedRows = MutableStateFlow<List<CsvParsedRow>>(emptyList())

    fun parseFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            state.value = CsvImportState.Parsing
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Could not open file")

                    val reader = CSVReader(InputStreamReader(inputStream))
                    val allLines = reader.readAll()
                    reader.close()

                    if (allLines.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            state.value = CsvImportState.Error("CSV file is empty.")
                        }
                        return@withContext
                    }

                    val headers = allLines.first().map { it.trim().lowercase(Locale.US) }
                    val dataRows = allLines.drop(1)

                    val parsed = parseRowsWithHeaders(headers, dataRows)

                    if (parsed.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            state.value = CsvImportState.Error("Could not find recognizable transactions in this CSV.")
                        }
                        return@withContext
                    }

                    withContext(Dispatchers.Main) {
                        parsedRows.value = parsed
                        state.value = CsvImportState.Preview(parsed.size)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        state.value = CsvImportState.Error(e.message ?: "Failed to read CSV")
                    }
                }
            }
        }
    }

    private suspend fun parseRowsWithHeaders(headers: List<String>, rows: List<Array<String>>): List<CsvParsedRow> {
        val isCashApp = headers.any { it.contains("transaction type") || it.contains("currency amount") || it.contains("cash app") }

        val dateIdx = headers.indexOfFirst { it.contains("date") }.takeIf { it >= 0 } ?: 0
        val amountIdx = headers.indexOfFirst { it.contains("amount") || it.contains("total") || it.contains("debit") || it.contains("credit") }
        val descIdx = headers.indexOfFirst { it.contains("description") || it.contains("merchant") || it.contains("to/from") || it.contains("name") }

        if (amountIdx < 0) return emptyList()

        val results = mutableListOf<CsvParsedRow>()
        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("MM/dd/yyyy", Locale.US),
            SimpleDateFormat("MM/dd/yy", Locale.US)
        )

        for (row in rows) {
            if (row.size <= amountIdx) continue

            val rawAmount = row.getOrNull(amountIdx)?.trim()?.replace("$", "")?.replace(",", "") ?: continue
            val amountNum = rawAmount.toDoubleOrNull() ?: continue
            if (amountNum == 0.0) continue

            val merchant = (if (descIdx >= 0) row.getOrNull(descIdx) else null)?.trim()?.ifEmpty { "Bank Transaction" } ?: "Bank Transaction"
            val rawDate = row.getOrNull(dateIdx)?.trim() ?: ""

            var timestamp = System.currentTimeMillis()
            for (df in dateFormats) {
                try {
                    val parsedDate = df.parse(rawDate)
                    if (parsedDate != null) {
                        timestamp = parsedDate.time
                        break
                    }
                } catch (_: Exception) {}
            }

            val isDebit = amountNum < 0 || (isCashApp && row.any { it.contains("payment", ignoreCase = true) || it.contains("sent", ignoreCase = true) })
            val absAmount = abs(amountNum)

            // Auto-categorize via repository rules
            val match = transactionRepo.categorizeMerchant(merchant)

            results.add(
                CsvParsedRow(
                    merchant = merchant,
                    amount = absAmount,
                    type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT,
                    dateText = rawDate,
                    timestampMillis = timestamp,
                    category = match.category,
                    isDiscretionary = match.isDiscretionary,
                    isRecurringRent = match.isRecurringRent
                )
            )
        }

        return results
    }

    fun confirmImport() {
        val rows = parsedRows.value
        if (rows.isEmpty()) return

        viewModelScope.launch {
            state.value = CsvImportState.Importing
            val entities = rows.map { r ->
                val rentNote = if (r.isRecurringRent) "Monthly Rent / Fixed Obligation" else "Imported from CSV (${r.dateText})"
                Transaction(
                    id = UUID.randomUUID().toString(),
                    amount = r.amount,
                    merchant = r.merchant,
                    category = r.category,
                    type = r.type,
                    source = TransactionSource.CSV,
                    timestampMillis = r.timestampMillis,
                    rawNotificationText = null,
                    notes = rentNote,
                    isDiscretionary = r.isDiscretionary,
                    isSynced = false
                )
            }
            transactionRepo.insertTransactions(entities)
            state.value = CsvImportState.Success(entities.size)
        }
    }

    fun reset() {
        parsedRows.value = emptyList()
        state.value = CsvImportState.Idle
    }
}
