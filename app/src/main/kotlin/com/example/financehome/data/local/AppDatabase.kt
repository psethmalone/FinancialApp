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
        Bill::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao
    abstract fun marketPriceDao(): MarketPriceDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun billDao(): BillDao

    companion object {
        const val DATABASE_NAME = "financehome.db"

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN dueDayOfMonth INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE debts ADD COLUMN isPaidThisMonth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE debts ADD COLUMN lastPaidTimestampMillis INTEGER")
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
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
