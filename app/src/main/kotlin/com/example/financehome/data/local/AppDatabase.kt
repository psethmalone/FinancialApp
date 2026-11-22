package com.example.financehome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.financehome.data.model.CategoryRule
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.MarketPrice
import com.example.financehome.data.model.Transaction

@Database(
    entities = [
        Transaction::class,
        Debt::class,
        MarketPrice::class,
        CategoryRule::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao
    abstract fun marketPriceDao(): MarketPriceDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        const val DATABASE_NAME = "financehome.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
