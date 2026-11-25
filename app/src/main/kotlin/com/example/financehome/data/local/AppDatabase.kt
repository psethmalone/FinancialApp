package com.example.financehome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.CategoryRule
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.MarketPrice
import com.example.financehome.data.model.Transaction

@Database(
    entities = [
        Transaction::class,
        Debt::class,
        MarketPrice::class,
        CategoryRule::class,
        Bill::class,
        com.example.financehome.data.model.PortfolioHolding::class,
        com.example.financehome.data.model.PortfolioTrade::class,
        com.example.financehome.data.model.ReceiptItem::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao
    abstract fun marketPriceDao(): MarketPriceDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun billDao(): BillDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun receiptDao(): ReceiptDao

    companion object {
        const val DATABASE_NAME = "financehome.db"

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN dueDayOfMonth INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE debts ADD COLUMN isPaidThisMonth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE debts ADD COLUMN lastPaidTimestampMillis INTEGER")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `portfolio_holdings` (
                        `ticker` TEXT NOT NULL,
                        `totalShares` REAL NOT NULL,
                        `totalCostBasis` REAL NOT NULL,
                        `averageBuyPrice` REAL NOT NULL,
                        `lastUpdatedMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`ticker`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `portfolio_trades` (
                        `id` TEXT NOT NULL,
                        `ticker` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `shares` REAL NOT NULL,
                        `pricePerShare` REAL NOT NULL,
                        `totalAmount` REAL NOT NULL,
                        `timestampMillis` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `rawNotificationText` TEXT,
                        `notes` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptImageUri TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN hasItemizedReceipt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `receipt_items` (
                        `id` TEXT NOT NULL,
                        `transactionId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `price` REAL NOT NULL,
                        `quantity` REAL NOT NULL DEFAULT 1.0,
                        `unitPrice` REAL,
                        `category` TEXT NOT NULL DEFAULT 'GROCERY',
                        `notes` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipt_items_transactionId` ON `receipt_items` (`transactionId`)")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN noReceiptNeeded INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN linkedBillId TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN linkedDebtId TEXT")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'MONTHLY'")
                db.execSQL("ALTER TABLE bills ADD COLUMN nextDueDateEpochDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN lastSettledDateEpochDay INTEGER")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
