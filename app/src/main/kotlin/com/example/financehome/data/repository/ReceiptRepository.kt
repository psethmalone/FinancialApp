package com.example.financehome.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.financehome.data.local.CategorySpendTotal
import com.example.financehome.data.local.ReceiptDao
import com.example.financehome.data.local.TransactionDao
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.service.ParsedReceipt
import com.example.financehome.service.ReceiptParser
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ReceiptRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptDao: ReceiptDao,
    private val transactionDao: TransactionDao
) {

    private val parser = ReceiptParser()

    /**
     * Creates a new temporary file in app internal storage for camera capture
     * and returns a FileProvider content URI that can be passed to Camera TakePicture intent.
     */
    fun createReceiptCaptureFile(): Pair<File, Uri> {
        val dir = File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }
        val file = File(dir, "receipt_${UUID.randomUUID()}.jpg")
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(file, contentUri)
    }

    /**
     * Copies an external image URI (e.g. from photo picker or gallery) to app internal storage
     * to ensure permanent access without URI permission expiration.
     * Returns the persistent file URI as a string.
     */
    suspend fun savePickedImageToInternalStorage(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }
        val destFile = File(dir, "receipt_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile.toURI().toString()
    }

    /**
     * Processes a receipt image using ML Kit on-device Text Recognition,
     * parses the text into itemized line items, and persists them to Room DB.
     * Also updates the parent transaction with receipt metadata.
     */
    suspend fun processAndAttachReceipt(
        imageUriString: String,
        transactionId: String
    ): ParsedReceipt = withContext(Dispatchers.IO) {
        val imageUri = Uri.parse(imageUriString)
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val visionText = recognizer.process(inputImage).awaitResult()
        val parsed = parser.parse(visionText.text)

        // Convert parsed items to database entities
        val entities = parsed.items.map { parsedItem ->
            ReceiptItem(
                id = UUID.randomUUID().toString(),
                transactionId = transactionId,
                name = parsedItem.name,
                price = parsedItem.price,
                quantity = parsedItem.quantity,
                unitPrice = parsedItem.unitPrice,
                category = parsedItem.category,
                notes = parsedItem.rawLine
            )
        }

        // Persist items to Room
        receiptDao.deleteReceiptItemsForTransaction(transactionId)
        if (entities.isNotEmpty()) {
            receiptDao.insertReceiptItems(entities)
        }

        // Update transaction record
        val tx = transactionDao.getTransactionById(transactionId)
        if (tx != null) {
            val updatedTx = tx.copy(
                receiptImageUri = imageUriString,
                hasItemizedReceipt = entities.isNotEmpty()
            )
            transactionDao.updateTransaction(updatedTx)
        }

        parsed
    }

    fun getReceiptItems(transactionId: String): Flow<List<ReceiptItem>> {
        return receiptDao.getReceiptItemsForTransaction(transactionId)
    }

    suspend fun getReceiptItemsSync(transactionId: String): List<ReceiptItem> {
        return receiptDao.getReceiptItemsForTransactionSync(transactionId)
    }

    suspend fun parseReceiptFromUri(imageUriString: String): ParsedReceipt = withContext(Dispatchers.IO) {
        val imageUri = Uri.parse(imageUriString)
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val visionText = recognizer.process(inputImage).awaitResult()
        parser.parse(visionText.text)
    }

    suspend fun insertReceiptItems(items: List<ReceiptItem>) = withContext(Dispatchers.IO) {
        if (items.isNotEmpty()) {
            receiptDao.insertReceiptItems(items)
        }
    }

    fun getAllCategorySpends(): Flow<List<CategorySpendTotal>> {
        return receiptDao.getAllCategorySpends()
    }

    suspend fun addReceiptItem(item: ReceiptItem) = withContext(Dispatchers.IO) {
        receiptDao.insertReceiptItem(item)
        val tx = transactionDao.getTransactionById(item.transactionId)
        if (tx != null && !tx.hasItemizedReceipt) {
            transactionDao.updateTransaction(tx.copy(hasItemizedReceipt = true))
        }
    }

    suspend fun updateReceiptItem(item: ReceiptItem) = withContext(Dispatchers.IO) {
        receiptDao.updateReceiptItem(item)
    }

    suspend fun deleteReceiptItem(item: ReceiptItem) = withContext(Dispatchers.IO) {
        receiptDao.deleteReceiptItem(item)
        val remaining = receiptDao.getReceiptItemsForTransactionSync(item.transactionId)
        if (remaining.isEmpty()) {
            val tx = transactionDao.getTransactionById(item.transactionId)
            if (tx != null && tx.hasItemizedReceipt) {
                transactionDao.updateTransaction(tx.copy(hasItemizedReceipt = false))
            }
        }
    }

    suspend fun removeReceipt(transactionId: String) = withContext(Dispatchers.IO) {
        val tx = transactionDao.getTransactionById(transactionId)
        if (tx != null) {
            // Delete internal file if it exists
            tx.receiptImageUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    if (uri.scheme == "file") {
                        val file = File(uri.path ?: "")
                        if (file.exists()) file.delete()
                    }
                } catch (_: Exception) {}
            }
            // Clear items and update transaction
            receiptDao.deleteReceiptItemsForTransaction(transactionId)
            transactionDao.updateTransaction(
                tx.copy(
                    receiptImageUri = null,
                    hasItemizedReceipt = false
                )
            )
        }
    }
}

/**
 * Await extension helper for Google Play Tasks using suspendCancellableCoroutine.
 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
}
