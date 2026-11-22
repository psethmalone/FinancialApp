package com.example.financehome.di

import android.content.Context
import androidx.room.Room
import com.example.financehome.data.local.AppDatabase
import com.example.financehome.data.local.CategoryRuleDao
import com.example.financehome.data.local.DebtDao
import com.example.financehome.data.local.MarketPriceDao
import com.example.financehome.data.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = AppDatabase.getDatabase(context)

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideDebtDao(db: AppDatabase): DebtDao = db.debtDao()

    @Provides
    fun provideMarketPriceDao(db: AppDatabase): MarketPriceDao = db.marketPriceDao()

    @Provides
    fun provideCategoryRuleDao(db: AppDatabase): CategoryRuleDao = db.categoryRuleDao()
}
