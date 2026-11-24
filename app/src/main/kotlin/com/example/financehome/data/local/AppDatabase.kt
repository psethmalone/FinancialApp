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
        com.example.financehome.data.model.PortfolioTrade::class
    ],
    version = 5,
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
